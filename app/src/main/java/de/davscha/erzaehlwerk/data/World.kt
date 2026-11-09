package de.davscha.erzaehlwerk.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Rolle einer Figur im Roman */
enum class Role(val label: String) { HAUPT("Hauptfigur"), NEBEN("Nebenfigur"), GEGEN("Gegenspieler") }

/** Geschlecht – für Anrede, Grammatik und das Bild-Tag */
enum class Gender(val label: String, val tag: String, val word: String) {
    UNKNOWN("keine Angabe", "", "a person"), MALE("männlich", "1boy", "a man"), FEMALE("weiblich", "1girl", "a woman"), DIVERSE("divers", "1other", "a person");

    companion object {
        fun of(n: String?) = entries.firstOrNull { it.name == n } ?: UNKNOWN
    }
}

/** Eine Figur im Roman */
data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🙂",
    val role: Role = Role.NEBEN,
    val gender: Gender = Gender.UNKNOWN,
    /** Persönlichkeit, Hintergrund, Sprechweise – für den Text */
    val description: String = "",
    /** Aussehen als Bild-Tags (ohne Kleidung) */
    val appearance: String = "",
    /** Vorschaubild (Dateiname im Bilder-Ordner des Buchs) */
    val preview: String = "",
    /** Standard-Kleidung aus dem Fundus (Id) */
    val outfitId: String = "",
    /** Fester Startwert, damit die Figur auf allen Bildern ähnlich aussieht */
    val seed: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("rolle", role.name)
        .put("geschlecht", gender.name).put("beschreibung", description).put("aussehen", appearance)
        .put("vorschau", preview).put("outfit", outfitId).put("startwert", seed)

    companion object {
        fun fromJson(o: JSONObject) = Person(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("emoji").ifEmpty { "🙂" },
            runCatching { Role.valueOf(o.optString("rolle")) }.getOrDefault(Role.NEBEN), Gender.of(o.optString("geschlecht")),
            o.optString("beschreibung"), o.optString("aussehen"), o.optString("vorschau"), o.optString("outfit"), o.optLong("startwert"),
        )
    }
}

/** Ein Ort im Roman */
data class Place(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Wie der Ort ist – für den Text */
    val description: String = "",
    /** Bild-Tags */
    val tags: String = "",
    val preview: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("beschreibung", description).put("tags", tags).put("vorschau", preview)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("beschreibung"),
            o.optString("tags"), o.optString("vorschau"),
        )
    }
}

/** Die Welt eines Buches: Figuren und Orte */
data class World(val people: List<Person> = emptyList(), val places: List<Place> = emptyList()) {
    fun toJson(): JSONObject = JSONObject()
        .put("figuren", JSONArray(people.map { it.toJson() }))
        .put("orte", JSONArray(places.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = World(
            o.optJSONArray("figuren")?.let { a -> List(a.length()) { Person.fromJson(a.getJSONObject(it)) } }.orEmpty(),
            o.optJSONArray("orte")?.let { a -> List(a.length()) { Place.fromJson(a.getJSONObject(it)) } }.orEmpty(),
        )
    }
}

/** Kleidungsstück im Fundus (gilt für alle Bücher) */
data class Outfit(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val tags: String = "",
    val preview: String = "",
    /** Wer die Vorschau trägt: 1girl oder 1boy */
    val model: String = "1girl",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("tags", tags).put("vorschau", preview).put("model", model)

    companion object {
        fun fromJson(o: JSONObject) = Outfit(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("tags"),
            o.optString("vorschau"), o.optString("model").ifEmpty { "1girl" },
        )
    }
}

/** Bildstil in Worten (MOSS Image versteht Sätze) – z. B. „classic Disney animation style, soft colors“ */
data class Style(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Beschreibung des Stils auf Englisch, steht vorne im Bild-Prompt */
    val prompt: String = "",
    /** Alt (NoobAI-LoRA) – wird nicht mehr benutzt */
    val lora: String = "",
    val strength: Float = 1f,
) {
    /** Was in den Prompt kommt */
    val trigger: String get() = prompt

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("prompt", prompt).put("lora", lora).put("staerke", strength.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = Style(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"),
            // Alte LoRA-Stile: Stil-Beschreibung nachliefern
            o.optString("prompt").ifBlank { if (o.optString("lora").isNotBlank()) "anime illustration, clean lineart, soft colors" else "" },
            o.optString("lora"), o.optDouble("staerke", 1.0).toFloat(),
        )
    }
}

/** Fundus für alle Bücher: Kleidung und Stile */
data class Fundus(val clothing: List<Outfit> = emptyList(), val styles: List<Style> = emptyList(), val defaultStyleId: String = "") {
    val defaultStyle: Style? get() = styles.firstOrNull { it.id == defaultStyleId } ?: styles.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("kleidung", JSONArray(clothing.map { it.toJson() }))
        .put("stile", JSONArray(styles.map { it.toJson() }))
        .put("standardStil", defaultStyleId)

    companion object {
        fun fromJson(o: JSONObject) = Fundus(
            o.optJSONArray("kleidung")?.let { a -> List(a.length()) { Outfit.fromJson(a.getJSONObject(it)) } }.orEmpty(),
            o.optJSONArray("stile")?.let { a -> List(a.length()) { Style.fromJson(a.getJSONObject(it)) } }.orEmpty(),
            o.optString("standardStil"),
        )

        /** Stile für MOSS Image (in Worten) */
        val mossStyles = listOf(
            Style(id = "stil-disney", name = "Disney-Zeichentrick", prompt = "classic Disney animation style, hand-drawn, soft colors, expressive faces"),
            Style(id = "stil-anime", name = "Anime", prompt = "anime illustration, clean lineart, vibrant colors"),
            Style(id = "stil-comic", name = "Comic", prompt = "western comic book art, bold ink outlines, flat colors"),
            Style(id = "stil-aquarell", name = "Aquarell", prompt = "soft watercolor painting, storybook illustration, gentle colors"),
            Style(id = "stil-foto", name = "Foto (realistisch)", prompt = "realistic photo, cinematic lighting, 35mm lens"),
        )

        val starter: Fundus get() {
            val pandora = mossStyles.first()
            return Fundus(
                clothing = listOf(
                    Outfit(name = "Alltag mit Mantel", tags = "brown coat, white sweater, turtleneck, black pants"),
                    Outfit(name = "Abendkleid", tags = "black dress, evening gown, long dress, earrings"),
                    Outfit(name = "Reisekleidung", tags = "travel cloak, hood, leather boots, satchel"),
                    Outfit(name = "Rüstung", tags = "armor, breastplate, gauntlets, cape", model = "1boy"),
                    Outfit(name = "Anzug", tags = "black suit, white shirt, necktie", model = "1boy"),
                ),
                styles = mossStyles,
                defaultStyleId = pandora.id,
            )
        }
    }
}

/** Prompts für ComfyUI: Basis + Stil-Trigger + Bausteine */
object ImagePrompt {
    fun join(vararg parts: String?): String = parts.filterNotNull()
        .flatMap { it.split(',', '\n') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

    /** Figur in Worten: „a woman, long red hair, green eyes, wearing brown coat, white sweater“ */
    fun looks(p: Person, outfit: Outfit?) = join(p.gender.word, p.appearance, outfit?.tags?.takeIf { it.isNotBlank() }?.let { "wearing $it" })

    fun person(p: Person, outfit: Outfit?) = "Portrait of a single character: ${looks(p, outfit)}. Upper body, looking at the viewer, plain background."
    fun place(pl: Place) = "Scenery without people: ${join(pl.tags)}."
    fun outfit(o: Outfit) = "Full body front view of ${if (o.model == "1boy") "a man" else "a young woman"} wearing ${join(o.tags)}, standing, plain white background."
    const val SINGLE_NEGATIVE = "multiple views, reference sheet, character sheet, multiple girls, multiple boys"
}
