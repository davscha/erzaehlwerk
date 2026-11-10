package de.davscha.erzaehlwerk

/** Pro: mit Ollama und ComfyUI auf dem PC. Lite: alles läuft nur auf dem Gerät. */
object Edition {
    val PRO: Boolean get() = BuildConfig.PRO
    val LITE: Boolean get() = !BuildConfig.PRO
}
