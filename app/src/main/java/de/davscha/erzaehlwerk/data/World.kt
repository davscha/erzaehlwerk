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
    /** Besonderheiten (früher: Aussehen als Tags) – kommt als „details“ ins Bild */
    val appearance: String = "",
    /** Aussehen für MOSS Image, auf Englisch */
    val age: String = "",
    val face: String = "",
    val eyes: String = "",
    val body: String = "",
    val skin: String = "",
    val hair: String = "",
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
        .put("alter", age).put("gesicht", face).put("augen", eyes).put("koerper", body).put("haut", skin).put("haare", hair)

    companion object {
        fun fromJson(o: JSONObject) = Person(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("emoji").ifEmpty { "🙂" },
            runCatching { Role.valueOf(o.optString("rolle")) }.getOrDefault(Role.NEBEN), Gender.of(o.optString("geschlecht")),
            o.optString("beschreibung"), o.optString("aussehen"), o.optString("vorschau"), o.optString("outfit"), o.optLong("startwert"),
            o.optString("alter"), o.optString("gesicht"), o.optString("augen"), o.optString("koerper"), o.optString("haut"), o.optString("haare"),
        )
    }
}

/** Ein Ort im Roman */
data class Place(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** Wie der Ort ist – für den Text */
    val description: String = "",
    /** Alt: Bild-Tags (werden beim Laden zum Hintergrund) */
    val tags: String = "",
    val preview: String = "",
    /** Für MOSS Image, auf Englisch: was man sieht und wie das Licht ist */
    val background: String = "",
    val lights: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("beschreibung", description).put("tags", tags).put("vorschau", preview)
        .put("hintergrund", background).put("licht", lights)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("beschreibung"),
            o.optString("tags"), o.optString("vorschau"),
            o.optString("hintergrund").ifBlank { o.optString("tags") }, o.optString("licht"),
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
    /** Einzelne Teile für MOSS Image: Teil (z. B. jacket) → Beschreibung auf Englisch */
    val pieces: List<Pair<String, String>> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("tags", tags).put("vorschau", preview).put("model", model)
        .put("teile", JSONArray(pieces.map { (k, v) -> JSONObject().put("teil", k).put("text", v) }))

    companion object {
        fun fromJson(o: JSONObject): Outfit {
            val teile = o.optJSONArray("teile")?.let { a -> List(a.length()) { a.getJSONObject(it).let { t -> t.optString("teil") to t.optString("text") } } }.orEmpty()
            return Outfit(
                o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("name"), o.optString("tags"),
                o.optString("vorschau"), o.optString("model").ifEmpty { "1girl" },
                // Alte Tag-Kleidung wird zu einem Teil „outfit“
                teile.ifEmpty { if (o.optString("tags").isNotBlank()) listOf("outfit" to o.optString("tags")) else emptyList() },
            )
        }
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
            o.optString("prompt"),
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
            Style(id = "stil-kino", name = "Kino-Foto", prompt = "cinematic photography, faded glow theme, vignette, film grain"),
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

/** Prompts für MOSS Image: ein JSON mit Stil, Figuren, Handlung, Hintergrund, Licht und Kamera */
object ImagePrompt {
    fun join(vararg parts: String?): String = parts.filterNotNull()
        .flatMap { it.split(',', '\n') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

    /** Was eine Figur pro Bild macht: eigene Haltung und Gesichtsausdruck */
    data class Moment(val pose: String = "", val expression: String = "")

    private fun JSONObject.putText(k: String, v: String?) = apply { if (!v.isNullOrBlank()) put(k, v.trim()) }

    fun clothing(o: Outfit?): JSONObject? {
        val parts = o?.pieces?.filter { it.second.isNotBlank() }.orEmpty()
        if (parts.isEmpty()) return null
        return JSONObject().apply { parts.forEach { (k, v) -> put(k.trim().ifBlank { "outfit" }, v.trim()) } }
    }

    /** Eine Figur so, wie MOSS sie versteht */
    fun figure(p: Person, outfit: Outfit?, m: Moment = Moment()): JSONObject {
        val o = JSONObject().putText("name", p.name)
        p.age.trim().toIntOrNull()?.let { o.put("age", it) } ?: o.putText("age", p.age)
        when (p.gender) { Gender.MALE -> o.put("gender", "male"); Gender.FEMALE -> o.put("gender", "female"); Gender.DIVERSE -> o.put("gender", "non-binary"); else -> {} }
        val shapes = JSONObject().putText("face", p.face).putText("eyes", p.eyes).putText("body", p.body)
        if (shapes.length() > 0) o.put("shapes", shapes)
        o.putText("skin", p.skin).putText("hair", p.hair).putText("details", p.appearance)
        clothing(outfit)?.let { o.put("clothing", it) }
        o.putText("pose", m.pose).putText("face_expression", m.expression)
        return o
    }

    /** Das ganze Bild als JSON-Text */
    fun build(
        style: String, figures: List<JSONObject> = emptyList(), action: String = "", background: String = "", lights: String = "",
        camera: String = "", extra: Map<String, String> = emptyMap(),
    ): String {
        val o = JSONObject().putText("style", style)
        if (figures.isNotEmpty()) o.put("figures", JSONArray(figures))
        o.putText("action", action).putText("background", background).putText("lights", lights).putText("camera", camera)
        extra.forEach { (k, v) -> o.putText(k, v) }
        return o.toString(2)
    }

    /** Aussehen in einer Zeile – für die Texte der KI */
    fun looks(p: Person, outfit: Outfit?) = join(
        p.gender.word, p.age.takeIf { it.isNotBlank() }?.let { "age $it" }, p.face, p.eyes, p.body, p.skin, p.hair, p.appearance,
        outfit?.pieces?.joinToString(", ") { it.second }?.takeIf { it.isNotBlank() }?.let { "wearing $it" },
    )

    fun person(style: String, p: Person, outfit: Outfit?) = build(
        style, listOf(figure(p, outfit, Moment("standing, relaxed, looking at the camera", "calm, friendly"))),
        background = "plain neutral studio background", lights = "soft even studio light", camera = "cowboy shot, front view, single person",
    )

    fun place(style: String, pl: Place) = build(
        style, action = "empty scene, no people", background = pl.background.ifBlank { pl.tags }, lights = pl.lights, camera = "wide establishing shot",
    )

    fun outfit(style: String, o: Outfit): String {
        val f = JSONObject().put("gender", if (o.model == "1boy") "male" else "female").put("age", 25)
        clothing(o)?.let { f.put("clothing", it) }
        f.put("pose", "standing straight, arms relaxed at the sides")
        return build(style, listOf(f), background = "plain white studio background", lights = "soft even studio light", camera = "full body shot, front view, single person")
    }

    const val SINGLE_NEGATIVE = "multiple views, reference sheet, character sheet"
}
