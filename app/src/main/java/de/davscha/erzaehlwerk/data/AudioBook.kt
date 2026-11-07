package de.davscha.erzaehlwerk.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Hörbuch mit der Android-Sprachausgabe: Text in Stücke teilen, jedes Stück als WAV sprechen lassen,
 * alles zu einem Kapitel zusammenfügen (mit kleinen Pausen) und als M4A (AAC) speichern.
 */
class AudioBook(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** Startet die Sprachausgabe (einmal) */
    suspend fun ready(): TextToSpeech {
        tts?.let { return it }
        return suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context) { status ->
                val e = engine!!
                if (status == TextToSpeech.SUCCESS) {
                    e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) {}
                        override fun onDone(id: String) { waiting.remove(id)?.complete(true) }
                        @Deprecated("alt") override fun onError(id: String) { waiting.remove(id)?.complete(false) }
                        override fun onError(id: String, errorCode: Int) { waiting.remove(id)?.complete(false) }
                    })
                    tts = e
                    if (cont.isActive) cont.resume(e)
                } else if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("Sprachausgabe nicht verfügbar")))
            }
        }
    }

    /** Deutsche Stimmen (offline zuerst) */
    suspend fun voices(): List<Voice> = ready().voices.orEmpty()
        .filter { it.locale.language == "de" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))

    private fun apply(e: TextToSpeech, voiceName: String, pitch: Float, rate: Float) {
        val v = e.voices?.firstOrNull { it.name == voiceName }
        if (v != null) e.voice = v else e.language = java.util.Locale.GERMAN
        e.setPitch(pitch)
        e.setSpeechRate(rate)
    }

    /** Hörprobe */
    suspend fun preview(text: String, voiceName: String, pitch: Float, rate: Float) {
        val e = ready()
        apply(e, voiceName, pitch, rate)
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "probe")
    }

    fun stopSpeaking() { tts?.stop() }

    fun shutdown() { tts?.shutdown(); tts = null }

    /** Text in Stücke teilen, die die Sprachausgabe auf einmal schafft (an Satzenden) */
    fun chunks(text: String): List<String> {
        val max = (TextToSpeech.getMaxSpeechInputLength() - 200).coerceIn(500, 3000)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?…“])\\s+"))) {
            if (sb.length + sentence.length + 1 > max && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
            if (sentence.length > max) sentence.chunked(max).forEach { out += it } else sb.append(sentence).append(' ')
        }
        if (sb.isNotBlank()) out += sb.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Ein Stück Text als WAV-Datei sprechen lassen */
    private suspend fun speakToFile(text: String, file: File): Boolean {
        val e = ready()
        val id = "teil-${System.nanoTime()}"
        val done = CompletableDeferred<Boolean>()
        waiting[id] = done
        val r = e.synthesizeToFile(text, Bundle(), file, id)
        if (r != TextToSpeech.SUCCESS) { waiting.remove(id); return false }
        return done.await()
    }

    /** PCM-Daten und Format aus einer WAV-Datei */
    private class Wav(val sampleRate: Int, val channels: Int, val bits: Int, val dataOffset: Long, val dataSize: Long)

    private fun readWav(f: File): Wav? = runCatching {
        RandomAccessFile(f, "r").use { raf ->
            val head = ByteArray(12); raf.readFully(head)
            if (String(head, 0, 4) != "RIFF" || String(head, 8, 4) != "WAVE") return null
            var sr = 22050; var ch = 1; var bits = 16
            while (raf.filePointer < raf.length()) {
                val idB = ByteArray(4); raf.readFully(idB)
                val sizeB = ByteArray(4); raf.readFully(sizeB)
                val size = ByteBuffer.wrap(sizeB).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val id = String(idB)
                if (id == "fmt ") {
                    val fmt = ByteArray(size.toInt()); raf.readFully(fmt)
                    val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    ch = b.getShort(2).toInt(); sr = b.getInt(4); bits = b.getShort(14).toInt()
                } else if (id == "data") {
                    val realSize = minOf(size, raf.length() - raf.filePointer)
                    return Wav(sr, ch, bits, raf.filePointer, realSize)
                } else raf.seek(raf.filePointer + size + (size and 1))
            }
            null
        }
    }.getOrNull()

    /**
     * Ein Kapitel einlesen: [parts] sind Absatzgruppen (z. B. Szenen) – dazwischen kommt eine kleine Pause.
     * [onProgress] meldet erledigte Stücke / alle Stücke. Ergebnis: Adresse der M4A-Datei.
     */
    suspend fun recordChapter(
        parts: List<String>, voiceName: String, pitch: Float, rate: Float,
        folder: String, fileName: String, onProgress: (Int, Int) -> Unit,
    ): Uri? = withContext(Dispatchers.IO) {
        val e = ready()
        apply(e, voiceName, pitch, rate)
        val tmp = File(context.cacheDir, "hoerbuch").apply { deleteRecursively(); mkdirs() }
        val raw = File(tmp, "kapitel.pcm")
        var format: Wav? = null
        val pieces = parts.map { chunks(it) }
        val total = pieces.sumOf { it.size }
        var done = 0
        FileOutputStream(raw).use { out ->
            pieces.forEachIndexed { pi, list ->
                list.forEach { text ->
                    ensureActive()
                    val wavFile = File(tmp, "teil.wav")
                    if (speakToFile(text, wavFile)) {
                        readWav(wavFile)?.let { w ->
                            if (format == null) format = w
                            FileInputStream(wavFile).use { input ->
                                input.skip(w.dataOffset)
                                val buf = ByteArray(64 * 1024)
                                var left = w.dataSize
                                while (left > 0) {
                                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                    if (n < 0) break
                                    out.write(buf, 0, n); left -= n
                                }
                            }
                        }
                    }
                    done++
                    onProgress(done, total)
                }
                // Pause zwischen den Teilen (Szenen)
                format?.let { f -> if (pi < pieces.lastIndex) out.write(ByteArray((f.sampleRate * f.channels * (f.bits / 8) * 0.9).toInt() / 2 * 2)) }
            }
        }
        val f = format ?: return@withContext null
        val uri = createAudio(folder, fileName) ?: return@withContext null
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd -> encodeAac(raw, f.sampleRate, f.channels, pfd.fileDescriptor) }
        tmp.deleteRecursively()
        uri
    }

    private fun createAudio(folder: String, fileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Audiobooks/Erzaehlwerk/$folder")
            put(MediaStore.Audio.Media.IS_AUDIOBOOK, 1)
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    /** Rohe 16-Bit-PCM-Daten mit dem eingebauten Encoder in AAC (M4A) umwandeln – viel kleiner als WAV */
    private fun encodeAac(raw: File, sampleRate: Int, channels: Int, fd: java.io.FileDescriptor) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()
        val bytesPerSecond = sampleRate * channels * 2L
        var read = 0L
        var inputDone = false
        FileInputStream(raw).use { input ->
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val chunk = ByteArray(minOf(buf.capacity(), 8192))
                        val n = input.read(chunk)
                        val pts = read * 1_000_000L / bytesPerSecond
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(chunk, 0, n)
                            codec.queueInputBuffer(i, 0, n, pts, 0)
                            read += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    o >= 0 -> {
                        val out = codec.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                            out.position(info.offset); out.limit(info.offset + info.size)
                            muxer.writeSampleData(track, out, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        }
        codec.stop(); codec.release()
        if (track >= 0) muxer.stop()
        muxer.release()
    }
}
