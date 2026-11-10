package de.davscha.erzaehlwerk.data

import android.os.Environment
import org.json.JSONObject
import java.io.File

/** Liest Figuren und Fundus aus Character Fun (Ordner /CharacterFun), damit nichts doppelt angelegt werden muss. */
object CharacterFunImport {
    private val dir get() = File(Environment.getExternalStorageDirectory(), "CharacterFun")

    val available get() = File(dir, "figuren.json").exists() && Environment.isExternalStorageManager()

    /** Eine Figur aus Character Fun – mit Bilddatei (Vorschau oder Avatar), falls vorhanden */
    class CfPerson(val person: Person, val image: File?)

    fun people(): List<CfPerson> = runCatching {
        val arr = JSONObject(File(dir, "figuren.json").readText()).getJSONArray("figuren")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val desc = listOf(o.optString("shortDescription"), o.optString("definition"), o.optString("longDescription"))
                .filter { it.isNotBlank() }.joinToString("\n")
            val preview = o.optString("aussehenVorschau").takeIf { it.isNotBlank() }?.let { File(dir, "Fundus/$it") }?.takeIf { it.exists() }
            val avatar = o.optString("avatarFile").takeIf { it.isNotBlank() }?.let { File(dir, "Avatare/$it") }?.takeIf { it.exists() }
            CfPerson(
                Person(
                    name = o.optString("name"),
                    emoji = o.optString("emoji").ifBlank { "🙂" },
                    gender = Gender.of(o.optString("geschlecht")),
                    description = desc,
                    appearance = o.optString("aussehen"),
                    outfitId = o.optString("standardOutfit"),
                    seed = o.optLong("bildStartwert"),
                ),
                preview ?: avatar,
            )
        }
    }.getOrDefault(emptyList())

    private fun fundusJson(): JSONObject? = runCatching { JSONObject(File(dir, "fundus.json").readText()) }.getOrNull()

    /** Orte aus dem Fundus von Character Fun */
    fun places(): List<Pair<Place, File?>> {
        val o = fundusJson() ?: return emptyList()
        val a = o.optJSONArray("orte") ?: return emptyList()
        return List(a.length()) { i ->
            val p = a.getJSONObject(i)
            val img = p.optString("vorschau").takeIf { it.isNotBlank() }?.let { File(dir, "Fundus/$it") }?.takeIf { it.exists() }
            Place(name = p.optString("name"), tags = p.optString("tags"), background = p.optString("tags")) to img
        }
    }

    /** Kleidung und Stile aus Character Fun (Ids bleiben gleich, damit Standard-Outfits passen) */
    fun clothingAndStyles(): Triple<List<Pair<Outfit, File?>>, List<Style>, String> {
        val o = fundusJson() ?: return Triple(emptyList(), emptyList(), "")
        val c = o.optJSONArray("kleidung")
        val clothing = if (c == null) emptyList() else List(c.length()) { i ->
            val p = c.getJSONObject(i)
            val img = p.optString("vorschau").takeIf { it.isNotBlank() }?.let { File(dir, "Fundus/$it") }?.takeIf { it.exists() }
            Outfit(id = p.optString("id"), name = p.optString("name"), tags = p.optString("tags"), model = p.optString("model").ifEmpty { "1girl" }, pieces = p.optString("tags").takeIf { it.isNotBlank() }?.let { listOf("outfit" to it) }.orEmpty()) to img
        }
        val s = o.optJSONArray("stile")
        val styles = if (s == null) emptyList() else List(s.length()) { i -> Style.fromJson(s.getJSONObject(i)) }
        return Triple(clothing, styles, o.optString("standardStil"))
    }
}
