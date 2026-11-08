package de.davscha.erzaehlwerk.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Schreibstil: Name und Beschreibung, wie die KI schreiben soll */
data class WritingStyle(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("beschreibung", description)

    companion object {
        fun fromJson(o: JSONObject) = WritingStyle(o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("beschreibung"))
        fun listToJson(l: List<WritingStyle>) = JSONObject().put("stile", JSONArray(l.map { it.toJson() }))
        fun listFromJson(o: JSONObject) = o.optJSONArray("stile")?.let { a -> List(a.length()) { fromJson(a.getJSONObject(it)) } }.orEmpty()

        /** Die vorgegebenen Stile – Namen wie bisher, damit alte Bücher sie finden */
        val starter = listOf(
            WritingStyle("stil-lebendig", "lebendig und bildhaft", "Anschauliche Bilder für alle Sinne, abwechslungsreiche Sätze, Figuren zeigen Gefühle durch Handlungen statt sie zu benennen."),
            WritingStyle("stil-spannend", "spannend und knapp", "Kurze Sätze, schnelles Tempo, wenig Beschreibung. Jede Szene endet mit einer offenen Frage oder einem kleinen Haken."),
            WritingStyle("stil-duester", "düster und atmosphärisch", "Dichte, bedrückende Stimmung, Licht und Schatten, Geräusche und Stille. Andeutungen statt Erklärungen."),
            WritingStyle("stil-humor", "humorvoll", "Witzige Beobachtungen, schlagfertige Dialoge, komische Übertreibungen und überraschende Vergleiche – trotzdem mit Herz."),
            WritingStyle("stil-poetisch", "poetisch", "Melodische Sprache, ungewöhnliche Bilder und Metaphern, ruhiger Rhythmus, viel Raum für Stimmungen."),
            WritingStyle("stil-kinder", "einfach (Kinderbuch)", "Kurze, klare Sätze und einfache Wörter, viel wörtliche Rede, warm und freundlich, für Kinder ab etwa 6 Jahren."),
            WritingStyle("stil-romantisch", "romantisch", "Viel Innenleben, Blicke, kleine Gesten und Herzklopfen. Gefühle stehen im Mittelpunkt, keine expliziten Szenen."),
        )
    }
}
