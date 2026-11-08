package de.davscha.erzaehlwerk.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Genre: Name, Emoji, Beschreibung für die KI und Standards für neue Bücher */
data class Genre(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "📖",
    /** Was das Genre ausmacht: Stimmung, typische Elemente, was nicht vorkommen soll */
    val description: String = "",
    /** Standard-Schreibstil für neue Bücher (leer = nichts vorgeben) */
    val style: String = "",
    val perspective: String = "",
    val tense: String = "",
    /** Bildstil aus dem Fundus (leer = Standard-Stil) */
    val imageStyleId: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("beschreibung", description)
        .put("stil", style).put("perspektive", perspective).put("zeitform", tense).put("bildstil", imageStyleId)

    companion object {
        fun fromJson(o: JSONObject) = Genre(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("emoji").ifBlank { "📖" },
            o.optString("beschreibung"), o.optString("stil"), o.optString("perspektive"), o.optString("zeitform"), o.optString("bildstil"),
        )

        fun listToJson(l: List<Genre>) = JSONObject().put("genres", JSONArray(l.map { it.toJson() }))
        fun listFromJson(o: JSONObject) = o.optJSONArray("genres")?.let { a -> List(a.length()) { fromJson(a.getJSONObject(it)) } }.orEmpty()

        /** Die vorgegebenen Genres – mit Beschreibung, die man ändern kann */
        val starter = listOf(
            Genre("genre-fantasy", "Fantasy", "🐉", "Magische Welten, Fabelwesen, Abenteuer und Prüfungen. Eine Welt mit eigenen Regeln für Magie, ein Held oder eine Heldin wächst an der Aufgabe.", "lebendig und bildhaft"),
            Genre("genre-krimi", "Krimi", "🕵️", "Ein Verbrechen, Spuren, Verdächtige und falsche Fährten. Spannung durch Rätseln, die Lösung ist am Ende logisch und fair aus den Hinweisen ableitbar.", "spannend und knapp"),
            Genre("genre-scifi", "Science-Fiction", "🚀", "Zukunft, Technik, Raumfahrt oder fremde Welten. Was wäre wenn – eine Idee wird ernsthaft zu Ende gedacht, mit Folgen für Menschen und Gesellschaft.", "lebendig und bildhaft"),
            Genre("genre-abenteuer", "Abenteuer", "🗺️", "Reisen, Gefahren, unbekannte Orte und Mut. Tempo, Hindernisse und Freundschaft, am Ende ist die Hauptfigur verändert.", "spannend und knapp"),
            Genre("genre-liebe", "Liebesroman", "💌", "Zwei Menschen, Hindernisse zwischen ihnen und Gefühle, die wachsen. Viel Innenleben und Dialog, ein warmes, hoffnungsvolles Ende. Keine expliziten Szenen.", "romantisch"),
            Genre("genre-maerchen", "Märchen", "🏰", "Es war einmal: sprechende Tiere, Zauber, gute und böse Gestalten, klare Moral. Einfache, bildhafte Sprache, auch für Kinder geeignet.", "einfach (Kinderbuch)", "auktorial", "Präteritum"),
            Genre("genre-horror", "Horror", "👻", "Unheimliches, das langsam näher kommt. Grusel durch Andeutungen, Geräusche und Stille statt durch Blut. Spannung bis zum Schluss.", "düster und atmosphärisch"),
            Genre("genre-komoedie", "Komödie", "🎭", "Verwicklungen, Missverständnisse, schräge Figuren und Wortwitz. Leicht, schnell, mit Herz – niemand wird bloßgestellt.", "humorvoll"),
            Genre("genre-historisch", "Historisch", "📜", "Eine echte Epoche mit passenden Lebensumständen, Sprache und Gegenständen. Erfundene Figuren erleben die Geschichte aus der Nähe.", "lebendig und bildhaft"),
            Genre("genre-alltag", "Alltag", "☕", "Gewöhnliche Menschen, kleine Momente, echte Gefühle. Ruhig erzählt, nah an den Figuren, ohne große Action.", "lebendig und bildhaft"),
        )
    }
}
