package de.davscha.erzaehlwerk.data

import org.json.JSONObject
import java.util.UUID

/** Ein Roman im Bücherregal. Kapitel, Szenen und Figuren kommen in den nächsten Etappen dazu. */
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    /** Worum es gehen soll – deine Vorgabe für die KI */
    val idea: String = "",
    val genre: String = "",
    /** Titelbild (Dateiname im Buchordner, oder leer) */
    val cover: String = "",
    /** Emoji als Platzhalter, solange es kein Titelbild gibt */
    val emoji: String = "📖",
    // ---- Schreibstil ----
    val style: String = "lebendig und bildhaft",
    /** "3. Person" (nah an der Hauptfigur), "auktorial" oder "ich" */
    val perspective: String = "3. Person",
    /** Bei Ich-Perspektive: wer erzählt (Figuren-Id) */
    val narratorId: String = "",
    val tense: String = "Präteritum",
    /** Ungefähre Wörter pro Szene */
    val sceneWords: Int = 600,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("titel", title)
        .put("idee", idea)
        .put("genre", genre)
        .put("titelbild", cover)
        .put("emoji", emoji)
        .put("stil", style)
        .put("perspektive", perspective)
        .put("erzaehler", narratorId)
        .put("zeitform", tense)
        .put("szenenWoerter", sceneWords)
        .put("erstellt", createdAt)
        .put("geaendert", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = Book(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            title = o.optString("titel"),
            idea = o.optString("idee"),
            genre = o.optString("genre"),
            cover = o.optString("titelbild"),
            emoji = o.optString("emoji").ifEmpty { "📖" },
            style = o.optString("stil").ifBlank { "lebendig und bildhaft" },
            perspective = o.optString("perspektive").ifBlank { "3. Person" },
            narratorId = o.optString("erzaehler"),
            tense = o.optString("zeitform").ifBlank { "Präteritum" },
            sceneWords = o.optInt("szenenWoerter", 600),
            createdAt = o.optLong("erstellt"),
            updatedAt = o.optLong("geaendert"),
        )

        val styles = listOf("lebendig und bildhaft", "spannend und knapp", "düster und atmosphärisch", "humorvoll", "poetisch", "einfach (Kinderbuch)", "romantisch")
        val perspectives = listOf("3. Person" to "Er/Sie (nah an der Hauptfigur)", "auktorial" to "Allwissender Erzähler", "ich" to "Ich-Erzähler")
        val tenses = listOf("Präteritum", "Präsens")
        val lengths = listOf(300 to "kurz", 600 to "mittel", 1000 to "lang")
        val genres = listOf("Fantasy", "Krimi", "Science-Fiction", "Abenteuer", "Liebesroman", "Märchen", "Horror", "Komödie", "Historisch", "Alltag")
        val emojis = mapOf(
            "Fantasy" to "🐉", "Krimi" to "🕵️", "Science-Fiction" to "🚀", "Abenteuer" to "🗺️", "Liebesroman" to "💌",
            "Märchen" to "🏰", "Horror" to "👻", "Komödie" to "🎭", "Historisch" to "📜", "Alltag" to "☕",
        )
    }
}
