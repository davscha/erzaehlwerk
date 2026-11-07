package de.davscha.erzaehlwerk.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Spricht mit ComfyUI auf dem PC: Workflow abschicken, warten, Bild abholen.
 * Der Workflow (API-Format) ist die Vorlage – ausgetauscht werden nur Prompts, Startwert, LoRA und Größe.
 */
class ComfyClient(private val host: String, private val port: Int) {

    private fun url(path: String) = URL("http://$host:$port$path")

    private fun open(path: String, timeout: Int = 8000) = (url(path).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeout
        readTimeout = timeout
    }

    private fun get(path: String, timeout: Int = 8000): String {
        val c = open(path, timeout)
        try {
            if (c.responseCode !in 200..299) throw IOException("ComfyUI antwortet mit ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    /** Verbindungstest: liefert z. B. "ComfyUI 0.42.0 · NVIDIA GeForce RTX 5060" */
    suspend fun test(): String = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/system_stats", 5000))
        val sys = o.optJSONObject("system")
        val version = sys?.optString("comfyui_version").orEmpty()
        val gpu = o.optJSONArray("devices")?.optJSONObject(0)?.optString("name").orEmpty()
            .substringAfter(':').substringBefore(":").trim()
        listOf("ComfyUI $version".trim(), gpu).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Alle LoRAs, die ComfyUI kennt */
    suspend fun loras(): List<String> = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/object_info/LoraLoaderModelOnly"))
        val arr = o.optJSONObject("LoraLoaderModelOnly")?.optJSONObject("input")?.optJSONObject("required")
            ?.optJSONArray("lora_name")?.optJSONArray(0) ?: JSONArray()
        List(arr.length()) { arr.optString(it) }.sorted()
    }

    /** Erzeugt ein Bild und gibt die PNG-Bytes zurück. [onProgress] meldet die Wartezeit in Sekunden. */
    suspend fun generate(
        template: String,
        positive: String,
        negative: String,
        seed: Long,
        lora: String?,
        loraStrength: Float,
        width: Int? = null,
        height: Int? = null,
        onProgress: (Int) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        val wf = prepare(JSONObject(template), positive, negative, seed, lora, loraStrength, width, height)
        val body = JSONObject().put("prompt", wf).put("client_id", UUID.randomUUID().toString()).toString()
        val c = open("/prompt", 15000).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val answer = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IOException("ComfyUI lehnt den Auftrag ab: " + errorText(answer))
        val id = JSONObject(answer).getString("prompt_id")

        // Warten, bis das Bild fertig ist (höchstens 5 Minuten)
        val start = System.currentTimeMillis()
        while (true) {
            delay(1000)
            val secs = ((System.currentTimeMillis() - start) / 1000).toInt()
            onProgress(secs)
            if (secs > 300) throw IOException("ComfyUI braucht zu lange")
            val hist = JSONObject(get("/history/$id"))
            val entry = hist.optJSONObject(id) ?: continue
            entry.optJSONObject("status")?.let { st ->
                if (st.optString("status_str") == "error") throw IOException("ComfyUI meldet einen Fehler beim Malen")
            }
            val outputs = entry.optJSONObject("outputs") ?: continue
            for (key in outputs.keys()) {
                val img = outputs.getJSONObject(key).optJSONArray("images")?.optJSONObject(0) ?: continue
                val q = "filename=${enc(img.optString("filename"))}&subfolder=${enc(img.optString("subfolder"))}&type=${enc(img.optString("type"))}"
                val v = open("/view?$q", 30000)
                try {
                    return@withContext v.inputStream.use { it.readBytes() }
                } finally {
                    v.disconnect()
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ByteArray(0)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun errorText(answer: String): String = runCatching {
        val o = JSONObject(answer)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: o.optJSONObject("node_errors")?.toString()?.take(300)
            ?: answer.take(300)
    }.getOrDefault(answer.take(300))

    companion object {
        /** Setzt Prompts, Startwert, LoRA und Größe in die Workflow-Vorlage ein. */
        fun prepare(
            wf: JSONObject, positive: String, negative: String, seed: Long,
            lora: String?, loraStrength: Float, width: Int?, height: Int?,
        ): JSONObject {
            val sampler = wf.keys().asSequence().map { it to wf.getJSONObject(it) }
                .firstOrNull { (_, n) -> n.optString("class_type").startsWith("KSampler") }?.second
                ?: throw IllegalArgumentException("Im Workflow fehlt ein KSampler")
            val inputs = sampler.getJSONObject("inputs")
            if (inputs.has("seed")) inputs.put("seed", seed)
            if (inputs.has("noise_seed")) inputs.put("noise_seed", seed)
            fun setText(link: String, text: String) {
                val ref = inputs.optJSONArray(link) ?: return
                val node = wf.optJSONObject(ref.optString(0)) ?: return
                node.getJSONObject("inputs").put("text", text)
            }
            setText("positive", positive)
            setText("negative", negative)
            for (key in wf.keys()) {
                val n = wf.getJSONObject(key)
                val inp = n.optJSONObject("inputs") ?: continue
                when (n.optString("class_type")) {
                    "LoraLoaderModelOnly", "LoraLoader" -> if (!lora.isNullOrBlank()) {
                        inp.put("lora_name", lora)
                        inp.put("strength_model", loraStrength.toDouble())
                        if (inp.has("strength_clip")) inp.put("strength_clip", loraStrength.toDouble())
                    } else {
                        inp.put("strength_model", 0.0)
                        if (inp.has("strength_clip")) inp.put("strength_clip", 0.0)
                    }
                    "EmptyLatentImage", "EmptySD3LatentImage" -> {
                        width?.let { inp.put("width", it) }
                        height?.let { inp.put("height", it) }
                    }
                }
            }
            return wf
        }

        /** Davids Workflow als eingebaute Vorlage (SDXL + PandorasBoxStyle, 12 Schritte, 1024×1024) */
        val DEFAULT_WORKFLOW = """
        {
          "1": {"inputs": {"ckpt_name": "yiffymix_v64Noobxl.safetensors"}, "class_type": "CheckpointLoaderSimple"},
          "2": {"inputs": {"text": "", "clip": ["1", 1]}, "class_type": "CLIPTextEncode"},
          "3": {"inputs": {"text": "", "clip": ["1", 1]}, "class_type": "CLIPTextEncode"},
          "4": {"inputs": {"seed": 1, "steps": 12, "cfg": 4, "sampler_name": "euler", "scheduler": "simple", "denoise": 1,
                "model": ["7", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["8", 0]}, "class_type": "KSampler"},
          "5": {"inputs": {"samples": ["4", 0], "vae": ["1", 2]}, "class_type": "VAEDecode"},
          "6": {"inputs": {"filename_prefix": "CharacterFun", "images": ["5", 0]}, "class_type": "SaveImage"},
          "7": {"inputs": {"lora_name": "NoobAi\\PandorasBoxStyle.safetensors", "strength_model": 1, "model": ["1", 0]}, "class_type": "LoraLoaderModelOnly"},
          "8": {"inputs": {"width": 1024, "height": 1024, "batch_size": 1}, "class_type": "EmptyLatentImage"}
        }
        """.trimIndent()
    }
}
