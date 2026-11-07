package de.davscha.erzaehlwerk.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Bild als Ideengeber – Gemma beschreibt, was darauf ist */
data class IdeaImage(val file: String, val description: String = "") {
    fun toJson(): JSONObject = JSONObject().put("datei", file).put("beschreibung", description)

    companion object {
        fun fromJson(o: JSONObject) = IdeaImage(o.optString("datei"), o.optString("beschreibung"))
    }
}

/** Eine Szene: was passiert, wo, mit wem. Text und Bild kommen in den nächsten Etappen. */
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val summary: String = "",
    val placeId: String = "",
    val peopleIds: List<String> = emptyList(),
    /** Ausgeschriebener Text (Etappe 4) */
    val text: String = "",
    /** Szenenbild (Etappe 5) */
    val image: String = "",
    /** Kurze Zusammenfassung des geschriebenen Textes – für den roten Faden */
    val recap: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("titel", title).put("inhalt", summary).put("ort", placeId)
        .put("figuren", JSONArray(peopleIds)).put("text", text).put("bild", image).put("rueckblick", recap)

    companion object {
        fun fromJson(o: JSONObject) = Scene(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("titel"), o.optString("inhalt"), o.optString("ort"),
            o.optJSONArray("figuren")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(), o.optString("text"), o.optString("bild"), o.optString("rueckblick"),
        )
    }
}

data class Chapter(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val summary: String = "",
    val scenes: List<Scene> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("titel", title).put("inhalt", summary).put("szenen", JSONArray(scenes.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = Chapter(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("titel"), o.optString("inhalt"),
            o.optJSONArray("szenen")?.let { a -> List(a.length()) { Scene.fromJson(a.getJSONObject(it)) } }.orEmpty(),
        )
    }
}

/** Plot eines Buchs: Ideen-Bilder, Exposé und Kapitelplan */
data class Plot(
    val images: List<IdeaImage> = emptyList(),
    val synopsis: String = "",
    val chapters: List<Chapter> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("bilder", JSONArray(images.map { it.toJson() }))
        .put("expose", synopsis)
        .put("kapitel", JSONArray(chapters.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = Plot(
            o.optJSONArray("bilder")?.let { a -> List(a.length()) { IdeaImage.fromJson(a.getJSONObject(it)) } }.orEmpty(),
            o.optString("expose"),
            o.optJSONArray("kapitel")?.let { a -> List(a.length()) { Chapter.fromJson(a.getJSONObject(it)) } }.orEmpty(),
        )
    }
}
