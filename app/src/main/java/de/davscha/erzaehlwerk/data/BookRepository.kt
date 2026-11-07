package de.davscha.erzaehlwerk.data

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Speichert die Bücher – jedes in einem eigenen Ordner unter /Erzaehlwerk/Buecher/<id>/buch.json.
 * Mit "Zugriff auf alle Dateien" überstehen sie eine Neuinstallation.
 */
class BookRepository(private val context: Context) {

    private val sharedDir get() = File(Environment.getExternalStorageDirectory(), "Erzaehlwerk")
    val dataDir: File get() = (if (Environment.isExternalStorageManager()) sharedDir else context.filesDir).apply { mkdirs() }
    private val booksDir: File get() = File(dataDir, "Buecher").apply { mkdirs() }

    fun bookDir(id: String) = File(booksDir, id).apply { mkdirs() }

    fun loadBooks(): List<Book> =
        booksDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { d -> runCatching { Book.fromJson(JSONObject(File(d, "buch.json").readText())) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun saveBook(book: Book) {
        val dir = bookDir(book.id)
        val tmp = File(dir, "buch.json.tmp")
        tmp.writeText(book.toJson().toString(2))
        tmp.renameTo(File(dir, "buch.json"))
    }

    fun deleteBook(id: String) {
        File(booksDir, id).deleteRecursively()
    }

    // ---------- Welt eines Buchs: Figuren und Orte ----------

    fun loadWorld(bookId: String): World =
        runCatching { World.fromJson(JSONObject(File(bookDir(bookId), "welt.json").readText())) }.getOrDefault(World())

    fun saveWorld(bookId: String, world: World) {
        val dir = bookDir(bookId)
        val tmp = File(dir, "welt.json.tmp")
        tmp.writeText(world.toJson().toString(2))
        tmp.renameTo(File(dir, "welt.json"))
    }

    fun loadPlot(bookId: String): Plot =
        runCatching { Plot.fromJson(JSONObject(File(bookDir(bookId), "plot.json").readText())) }.getOrDefault(Plot())

    fun savePlot(bookId: String, plot: Plot) {
        val dir = bookDir(bookId)
        val tmp = File(dir, "plot.json.tmp")
        tmp.writeText(plot.toJson().toString(2))
        tmp.renameTo(File(dir, "plot.json"))
    }

    /** Bild aus der Galerie in den Buchordner holen (verkleinert auf 1024 px) */
    fun importImage(bookId: String, uri: android.net.Uri): String? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        val name = "idee-${System.currentTimeMillis()}.jpg"
        File(imagesDir(bookId), name).outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        name
    }.getOrNull()

    fun imagesDir(bookId: String) = File(bookDir(bookId), "Bilder").apply { mkdirs() }

    fun image(bookId: String, name: String): File? =
        name.takeIf { it.isNotEmpty() }?.let { File(imagesDir(bookId), it) }?.takeIf { it.exists() }

    fun saveImage(bookId: String, prefix: String, png: ByteArray): String {
        val name = "$prefix-${System.currentTimeMillis()}.png"
        File(imagesDir(bookId), name).writeBytes(png)
        return name
    }

    // ---------- Fundus für alle Bücher ----------

    val fundusDir: File get() = File(dataDir, "Fundus").apply { mkdirs() }
    private val fundusFile get() = File(dataDir, "fundus.json")

    fun loadFundus(): Fundus {
        if (!fundusFile.exists()) return Fundus.starter.also { saveFundus(it) }
        return runCatching { Fundus.fromJson(JSONObject(fundusFile.readText())) }.getOrElse { Fundus.starter }
    }

    fun saveFundus(f: Fundus) {
        val tmp = File(dataDir, "fundus.json.tmp")
        tmp.writeText(f.toJson().toString(2))
        tmp.renameTo(fundusFile)
    }

    fun fundusImage(name: String): File? = name.takeIf { it.isNotEmpty() }?.let { File(fundusDir, it) }?.takeIf { it.exists() }

    fun saveFundusImage(prefix: String, png: ByteArray): String {
        val name = "$prefix-${System.currentTimeMillis()}.png"
        File(fundusDir, name).writeBytes(png)
        return name
    }
}
