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
}
