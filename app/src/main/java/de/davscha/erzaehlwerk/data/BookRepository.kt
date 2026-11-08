package de.davscha.erzaehlwerk.data

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Speichert die Bücher – jedes in einem eigenen Ordner unter /Erzaehlwerk/Buecher/<id>/buch.json.
 * Mit "Zugriff auf alle Dateien" überstehen sie eine Neuinstallation.
 */
class BookRepository(private val context: Context) {

    private val sharedDir get() = File(Environment.getExternalStorageDirectory(), "Erzaehlwerk")
    val dataDir: File get() = (if (Environment.isExternalStorageManager()) sharedDir else context.filesDir).apply { mkdirs() }
    private val booksDir: File get() = File(dataDir, "Buecher").apply { mkdirs() }

    fun bookDir(id: String) = File(booksDir, id).apply { mkdirs() }

    fun loadBooks(): List<Book> =
        booksDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { d -> runCatching { Book.fromJson(JSONObject(File(d, "buch.json").readText())) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun saveBook(book: Book) {
        val dir = bookDir(book.id)
        val tmp = File(dir, "buch.json.tmp")
        tmp.writeText(book.toJson().toString(2))
        tmp.renameTo(File(dir, "buch.json"))
    }

    fun deleteBook(id: String) {
        File(booksDir, id).deleteRecursively()
    }

    // ---------- Welt eines Buchs: Figuren und Orte ----------

    fun loadWorld(bookId: String): World =
        runCatching { World.fromJson(JSONObject(File(bookDir(bookId), "welt.json").readText())) }.getOrDefault(World())

    fun saveWorld(bookId: String, world: World) {
        val dir = bookDir(bookId)
        val tmp = File(dir, "welt.json.tmp")
        tmp.writeText(world.toJson().toString(2))
        tmp.renameTo(File(dir, "welt.json"))
    }

    fun loadPlot(bookId: String): Plot =
        runCatching { Plot.fromJson(JSONObject(File(bookDir(bookId), "plot.json").readText())) }.getOrDefault(Plot())

    fun savePlot(bookId: String, plot: Plot) {
        val dir = bookDir(bookId)
        val tmp = File(dir, "plot.json.tmp")
        tmp.writeText(plot.toJson().toString(2))
        tmp.renameTo(File(dir, "plot.json"))
    }

    /** Bild aus der Galerie in den Buchordner holen (verkleinert auf 1024 px) */
    fun importImage(bookId: String, uri: android.net.Uri): String? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        val name = "idee-${System.currentTimeMillis()}.jpg"
        File(imagesDir(bookId), name).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        name
    }.getOrNull()

    /** Standbilder aus einem Video ziehen (gleichmäßig verteilt). Ergebnis: Dateiname und Zeitpunkt in Sekunden. */
    fun extractFrames(bookId: String, uri: android.net.Uri, count: Int): List<Pair<String, Int>> {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val durMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return emptyList()
            val out = mutableListOf<Pair<String, Int>>()
            val stamp = System.currentTimeMillis()
            for (i in 0 until count) {
                // Mitte jedes Abschnitts – so kommen Anfang und Ende auch vor
                val tMs = durMs * (2 * i + 1) / (2 * count)
                val frame = r.getScaledFrameAtTime(tMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1024, 1024) ?: continue
                val name = "video-$stamp-$i.jpg"
                File(imagesDir(bookId), name).outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                frame.recycle()
                out += name to (tMs / 1000).toInt()
            }
            return out
        } finally {
            r.release()
        }
    }

    fun imagesDir(bookId: String) = File(bookDir(bookId), "Bilder").apply { mkdirs() }

    fun image(bookId: String, name: String): File? =
        name.takeIf { it.isNotEmpty() }?.let { File(imagesDir(bookId), it) }?.takeIf { it.exists() }

    fun saveImage(bookId: String, prefix: String, png: ByteArray): String {
        val name = "$prefix-${System.currentTimeMillis()}.png"
        File(imagesDir(bookId), name).writeBytes(png)
        return name
    }

    // ---------- Genres ----------

    private val genresFile get() = File(dataDir, "genres.json")

    fun loadGenres(): List<Genre> {
        if (!genresFile.exists()) return Genre.starter.also { saveGenres(it) }
        return runCatching { Genre.listFromJson(JSONObject(genresFile.readText())) }.getOrElse { Genre.starter }
    }

    fun saveGenres(l: List<Genre>) {
        val tmp = File(dataDir, "genres.json.tmp")
        tmp.writeText(Genre.listToJson(l).toString(2))
        tmp.renameTo(genresFile)
    }

    // ---------- Schreibstile ----------

    private val writingStylesFile get() = File(dataDir, "schreibstile.json")

    fun loadWritingStyles(): List<WritingStyle> {
        if (!writingStylesFile.exists()) return WritingStyle.starter.also { saveWritingStyles(it) }
        return runCatching { WritingStyle.listFromJson(JSONObject(writingStylesFile.readText())) }.getOrElse { WritingStyle.starter }
    }

    fun saveWritingStyles(l: List<WritingStyle>) {
        val tmp = File(dataDir, "schreibstile.json.tmp")
        tmp.writeText(WritingStyle.listToJson(l).toString(2))
        tmp.renameTo(writingStylesFile)
    }

    // ---------- Fundus für alle Bücher ----------

    val fundusDir: File get() = File(dataDir, "Fundus").apply { mkdirs() }
    private val fundusFile get() = File(dataDir, "fundus.json")

    fun loadFundus(): Fundus {
        if (!fundusFile.exists()) return Fundus.starter.also { saveFundus(it) }
        return runCatching { Fundus.fromJson(JSONObject(fundusFile.readText())) }.getOrElse { Fundus.starter }
    }

    fun saveFundus(f: Fundus) {
        val tmp = File(dataDir, "fundus.json.tmp")
        tmp.writeText(f.toJson().toString(2))
        tmp.renameTo(fundusFile)
    }

    fun fundusImage(name: String): File? = name.takeIf { it.isNotEmpty() }?.let { File(fundusDir, it) }?.takeIf { it.exists() }

    fun saveFundusImage(prefix: String, png: ByteArray): String {
        val name = "$prefix-${System.currentTimeMillis()}.png"
        File(fundusDir, name).writeBytes(png)
        return name
    }
}
