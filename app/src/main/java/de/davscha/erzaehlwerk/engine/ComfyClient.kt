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

    /** Erzeugt ein Bild und gibt die PNG-Bytes zurück. [onProgress] meldet die Wartezeit in Sekunden. */
    suspend fun generate(
        template: String,
        positive: String,
        negative: String,
        seed: Long,
        aspect: String = "4:3 (Standard)",
        megapixels: Float = 2f,
        onProgress: (Int) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        val wf = prepare(JSONObject(template), positive, negative, seed, aspect, megapixels)
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
            if (secs > 600) throw IOException("ComfyUI braucht zu lange")
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
        /** Setzt Prompt, Startwert, Seitenverhältnis und Megapixel in die Workflow-Vorlage ein. */
        fun prepare(wf: JSONObject, positive: String, negative: String, seed: Long, aspect: String, megapixels: Float): JSONObject {
            val sampler = wf.keys().asSequence().map { it to wf.getJSONObject(it) }
                .firstOrNull { (_, n) -> n.optString("class_type").startsWith("KSampler") }?.second
                ?: throw IllegalArgumentException("Im Workflow fehlt ein KSampler")
            val inputs = sampler.getJSONObject("inputs")
            if (inputs.has("seed")) inputs.put("seed", seed)
            if (inputs.has("noise_seed")) inputs.put("noise_seed", seed)
            fun setText(link: String, text: String) {
                val ref = inputs.optJSONArray(link) ?: return
                val node = wf.optJSONObject(ref.optString(0)) ?: return
                // Nur echte Text-Knoten (bei MOSS ist der Negativ-Prompt ein ConditioningZeroOut)
                if (node.optString("class_type") != "CLIPTextEncode") return
                node.getJSONObject("inputs").put("text", text)
            }
            setText("positive", positive)
            setText("negative", negative)
            for (key in wf.keys()) {
                val n = wf.getJSONObject(key)
                if (n.optString("class_type") == "ResolutionSelector") {
                    n.getJSONObject("inputs").put("aspect_ratio", aspect).put("megapixels", megapixels.toDouble())
                }
            }
            return wf
        }

        /** Seitenverhältnisse des ResolutionSelector */
        const val SQUARE = "1:1 (Square)"
        const val PORTRAIT = "3:4 (Portrait Standard)"
        const val LANDSCAPE = "4:3 (Standard)"
        const val WIDE = "16:9 (Widescreen)"

        /** Davids MOSS-Image-Workflow (RealisticFreedom + 4-Step-LoRA, Qwen-Textencoder, Flux2-VAE, 6 Schritte, CFG 1) */
        val DEFAULT_WORKFLOW = """
        {
          "20": {"inputs": {"unet_name": "RealisticFreedom.safetensors", "weight_dtype": "default"}, "class_type": "UNETLoader"},
          "36": {"inputs": {"lora_name": "moss_image_sft_4step_v2.0.safetensors", "strength_model": 1, "model": ["20", 0]}, "class_type": "LoraLoaderModelOnly"},
          "24": {"inputs": {"double_layers": "", "single_layers": "22", "start_percent": 0, "end_percent": 0.5, "model": ["36", 0]}, "class_type": "SkipLayerGuidanceDiTSimple"},
          "13": {"inputs": {"clip_name": "Qwen359B.safetensors", "type": "moss_image", "device": "default"}, "class_type": "CLIPLoader"},
          "45": {"inputs": {"text": "", "clip": ["13", 0]}, "class_type": "CLIPTextEncode"},
          "42": {"inputs": {"conditioning": ["45", 0]}, "class_type": "ConditioningZeroOut"},
          "22": {"inputs": {"aspect_ratio": "4:3 (Standard)", "megapixels": 2, "multiple": 16}, "class_type": "ResolutionSelector"},
          "6": {"inputs": {"width": ["22", 0], "height": ["22", 1], "batch_size": 1}, "class_type": "EmptyLatentImage"},
          "2": {"inputs": {"seed": 1, "steps": 6, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple", "denoise": 1,
                "model": ["24", 0], "positive": ["45", 0], "negative": ["42", 0], "latent_image": ["6", 0]}, "class_type": "KSampler"},
          "8": {"inputs": {"vae_name": "Flux2.safetensors"}, "class_type": "VAELoader"},
          "7": {"inputs": {"samples": ["2", 0], "vae": ["8", 0]}, "class_type": "VAEDecode"},
          "9": {"inputs": {"filename_prefix": "Erzaehlwerk", "images": ["7", 0]}, "class_type": "SaveImage"}
        }
        """.trimIndent()
    }
}
