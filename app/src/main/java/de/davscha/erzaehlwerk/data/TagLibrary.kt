package de.davscha.erzaehlwerk.data

import android.content.Context
import java.util.zip.GZIPInputStream

/** Ein Bild-Tag aus der Danbooru-Liste: Name (mit Leerzeichen), Fach (z. B. clothing:top) und Beliebtheit. */
class Tag(val name: String, val slot: String, val count: Int)

/** Für welches Feld Tags gedacht sind – bestimmt, welche Fächer erlaubt sind. */
enum class TagField(val allowed: (String) -> Boolean) {
    /** Aussehen: Haare, Augen, Körpermerkmale – keine Kleidung, kein Ort, keine Pose */
    APPEARANCE({ it.startsWith("appearance:") || it == "clothing:eyewear" }),
    /** Kleidung und Accessoires */
    CLOTHING({ it.startsWith("clothing:") || it == "equipment:handheld" }),
    /** Ort: Umgebung, Requisiten, Stimmung */
    LOCATION({ it.startsWith("scenery:") || it == "creature:animal" }),
    /** Szene: was gerade passiert – Pose, Ausdruck, Kamera, Gegenstände */
    SCENE({ it.startsWith("pose:") || it.startsWith("equipment:") || it == "scenery:props" || it == "other:layout" || it == "creature:animal" }),
    /** Freies Bild: alles */
    ANY({ true }),
}

/**
 * Die Tag-Liste (gut 18.000 Tags, aus Davids tags.csv, ohne Figuren- und Seriennamen).
 * Wird einmal aus den App-Daten geladen und danach im Speicher gehalten.
 */
object TagLibrary {
    @Volatile private var tags: List<Tag> = emptyList()
    private var byName: Map<String, Tag> = emptyMap()
    private var aliases: Map<String, Tag> = emptyMap()

    val loaded get() = tags.isNotEmpty()

    /** Fächer, die nur auftauchen, wenn man gezielt danach tippt */
    private val quiet = setOf("pose:physiological", "pose:interaction", "clothing:underwear", "appearance:anatomy")

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val list = ArrayList<Tag>(19_000)
            val names = HashMap<String, Tag>(40_000)
            val al = HashMap<String, Tag>(40_000)
            runCatching {
                GZIPInputStream(context.assets.open("tags.tsv.gz")).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val p = line.split('\t')
                        if (p.size < 3) return@forEach
                        val t = Tag(p[0], p[1], p[2].toIntOrNull() ?: 0)
                        list += t
                        names[t.name] = t
                        if (p.size > 3 && p[3].isNotEmpty()) p[3].split('|').forEach { a -> al.putIfAbsent(a, t) }
                    }
                }
            }
            byName = names
            aliases = al
            tags = list // nach Beliebtheit sortiert
        }
    }

    private fun norm(raw: String) = raw.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

    /** Der echte Tag zu einer Eingabe (auch Schreibvarianten wie "smiling" → smile) */
    fun find(raw: String): Tag? {
        val n = norm(raw)
        return byName[n] ?: aliases[n]
    }

    /** Vorschläge beim Tippen: Anfang des Tags oder eines Wortes darin, beliebteste zuerst */
    fun suggest(prefix: String, field: TagField = TagField.ANY, limit: Int = 10): List<Tag> {
        val p = norm(prefix)
        if (p.length < 2 || !loaded) return emptyList()
        val out = ArrayList<Tag>(limit)
        for (t in tags) {
            if (!field.allowed(t.slot) && t.slot != "other:unclassified") continue
            if (t.slot in quiet && p.length < 4) continue
            if (t.name.startsWith(p) || t.name.contains(" $p")) {
                out += t
                if (out.size >= limit) break
            }
        }
        // Schreibvariante getippt? Dann den echten Tag vorne anbieten
        aliases[p]?.let { a -> if (a !in out) out.add(0, a) }
        return out
    }

    /** Die beliebtesten Tags eines Fachs (für den Baukasten) */
    fun top(slot: String, limit: Int = 150): List<Tag> = tags.asSequence().filter { it.slot == slot }.take(limit).toList()

    /**
     * Räumt Tags auf, die Gemma geschrieben hat: Schreibvarianten → echter Tag,
     * Tags aus dem falschen Fach raus (z. B. Kleidung im Aussehen), Doppelte raus.
     * Unbekannte kurze Tags bleiben (das Bildmodell versteht oft auch freie Begriffe).
     */
    fun clean(text: String, field: TagField): String {
        if (!loaded) return text
        val seen = LinkedHashSet<String>()
        for (raw in text.split(',', '\n')) {
            val n = norm(raw).trim('.', '"', '\'', '*', '-', ' ')
            if (n.isEmpty()) continue
            val t = find(n)
            when {
                t != null && field.allowed(t.slot) -> seen += t.name
                t != null && t.slot == "other:unclassified" -> seen += t.name
                t != null -> {} // falsches Fach – kommt von woanders (Schrank, Ort, Aussehen)
                n.split(' ').size <= 3 -> seen += n
            }
        }
        return seen.joinToString(", ")
    }

    // ---------- Aussehen würfeln ----------

    private val hairLengths = listOf("short hair", "medium hair", "long hair", "very long hair")
    private val extras = listOf("freckles", "mole under eye", "ahoge", "hair ornament", "hairclip", "hair ribbon", "glasses", "pointy ears", "tan", "dark skin", "hair flower")

    /** Würfelt ein Aussehen zusammen: Haarlänge, -farbe, Frisur, Augenfarbe, ein Merkmal */
    fun randomAppearance(): String {
        val hair = top("appearance:hair", 400)
        val colors = hair.filter { it.name.endsWith(" hair") && it.name.split(' ').size == 2 && it.name !in hairLengths && it.name != "messy hair" && it.name != "wavy hair" && it.name != "straight hair" && it.name != "floating hair" && it.name != "multicolored hair" }.take(14)
        val styles = hair.filter { !it.name.endsWith(" hair") && it.count > 20_000 && !it.name.contains("alternate") }.take(30)
        val eyes = top("appearance:eyes", 60).filter { it.name.endsWith(" eyes") && it.name.split(' ').size == 2 && it.name != "closed eyes" }.take(12)
        val parts = mutableListOf(hairLengths.random())
        colors.randomOrNull()?.let { parts += it.name }
        if (Math.random() < 0.7) styles.randomOrNull()?.let { parts += it.name }
        eyes.randomOrNull()?.let { parts += it.name }
        if (Math.random() < 0.6) parts += extras.random()
        return parts.joinToString(", ")
    }

    /** Beliebte Beispiele aus einem Fach als Text (für Gemmas Anweisung) */
    fun examples(slot: String, n: Int): String = top(slot, n).joinToString(", ") { it.name }
}
