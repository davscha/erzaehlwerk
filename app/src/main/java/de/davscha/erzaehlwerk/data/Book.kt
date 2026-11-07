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
            createdAt = o.optLong("erstellt"),
            updatedAt = o.optLong("geaendert"),
        )

        val genres = listOf("Fantasy", "Krimi", "Science-Fiction", "Abenteuer", "Liebesroman", "Märchen", "Horror", "Komödie", "Historisch", "Alltag")
        val emojis = mapOf(
            "Fantasy" to "🐉", "Krimi" to "🕵️", "Science-Fiction" to "🚀", "Abenteuer" to "🗺️", "Liebesroman" to "💌",
            "Märchen" to "🏰", "Horror" to "👻", "Komödie" to "🎭", "Historisch" to "📜", "Alltag" to "☕",
        )
    }
}
