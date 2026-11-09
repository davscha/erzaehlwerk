package de.davscha.erzaehlwerk.data

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Baut aus einem Buch ein PDF (A5, Buchsatz) oder ein EPUB – mit oder ohne Bilder. */
object BookExporter {

    /** Bild-Datei zu einem Namen im Buchordner */
    fun interface Images { fun file(name: String): File? }

    private fun safeName(t: String) = t.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().replace(' ', '-').take(50).ifBlank { "Roman" }

    private fun store(context: Context, fileName: String, mime: String, write: (OutputStream) -> Unit): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Erzaehlwerk")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use(write)
        return uri
    }

    /** Absätze aus dem Szenentext */
    private fun paragraphs(text: String) = text.split(Regex("\n\\s*\n|\n")).map { it.trim() }.filter { it.isNotEmpty() }

    // ==================== PDF ====================

    private const val W = 420   // A5 in Punkt
    private const val H = 595
    private const val M = 42f

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var no = 0
        var y = M
        val paper = 0xFFFFFBF2.toInt()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF8A7A60.toInt(); textAlign = Paint.Align.CENTER }
        val canvas: Canvas get() = page!!.canvas
        val width = (W - 2 * M).toInt()
        var numbered = true

        fun newPage(numbers: Boolean = true) {
            finish()
            no++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, no).create())
            canvas.drawColor(paper)
            y = M
            numbered = numbers
        }

        fun finish() {
            page?.let { p ->
                if (numbered) p.canvas.drawText("$no", W / 2f, H - 20f, footer)
                doc.finishPage(p)
            }
            page = null
        }

        val room get() = H - M - 14 - y

        /** Text, der über Seitenenden weiterläuft */
        fun text(content: CharSequence, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, spacingAfter: Float = 6f) {
            if (page == null) newPage()
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
                .setAlignment(align).setLineSpacing(2.5f, 1f).setJustificationMode(if (align == Layout.Alignment.ALIGN_NORMAL) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                val startY = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - startY <= room) end++
                if (end == line) { newPage(); continue }
                canvas.save()
                canvas.translate(M, y - startY)
                canvas.clipRect(0f, startY.toFloat(), width.toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += layout.getLineBottom(end - 1) - startY
                line = end
                if (line < layout.lineCount) newPage()
            }
            y += spacingAfter
        }

        fun image(f: File, maxHeightPart: Float) {
            val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return
            val maxW = W - 2 * M
            val maxH = H * maxHeightPart
            val scale = minOf(maxW / bmp.width, maxH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            if (page == null || h > room) newPage()
            canvas.drawBitmap(bmp, null, RectF((W - w) / 2, y, (W + w) / 2, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 14
            bmp.recycle()
        }
    }

    private fun italicSpans(text: String): CharSequence {
        // *Hervorhebungen* kursiv, falls die KI welche setzt
        val sb = SpannableStringBuilder()
        val parts = text.split('*')
        parts.forEachIndexed { i, p ->
            val s = sb.length
            sb.append(p)
            if (i % 2 == 1 && i < parts.lastIndex) sb.setSpan(StyleSpan(Typeface.ITALIC), s, sb.length, 0)
        }
        return sb
    }

    fun pdf(context: Context, book: Book, plot: Plot, author: String, withImages: Boolean, images: Images): Uri? {
        val doc = PdfDocument()
        val w = Writer(doc)
        val serif = Typeface.SERIF
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; typeface = serif; color = 0xFF231C14.toInt() }
        val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; typeface = Typeface.create(serif, Typeface.BOLD); color = 0xFF2A1E12.toInt() }
        val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.create(serif, Typeface.ITALIC); color = 0xFF6A5232.toInt() }
        val chapP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.create(serif, Typeface.BOLD); color = 0xFF2A1E12.toInt() }
        val chapNo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = serif; color = 0xFF8A6A3A.toInt() }
        val tocP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = serif; color = 0xFF231C14.toInt() }

        // Titelseite
        w.newPage(numbers = false)
        if (withImages) images.file(book.cover)?.let { w.image(it, 0.5f) } ?: run { w.y = H * 0.3f }
        else w.y = H * 0.3f
        w.text(book.title, titleP, Layout.Alignment.ALIGN_CENTER, 10f)
        if (author.isNotBlank()) w.text(author, subP, Layout.Alignment.ALIGN_CENTER, 6f)
        if (book.genre.isNotBlank()) w.text(book.genre, subP, Layout.Alignment.ALIGN_CENTER)

        // Inhaltsverzeichnis
        val chapters = plot.chapters.filter { ch -> ch.scenes.any { it.text.isNotBlank() } }
        w.newPage(numbers = false)
        w.text("Inhalt", chapP, Layout.Alignment.ALIGN_CENTER, 16f)
        chapters.forEachIndexed { i, ch -> w.text("${i + 1}.  ${ch.title}", tocP, Layout.Alignment.ALIGN_NORMAL, 4f) }

        // Kapitel
        chapters.forEachIndexed { i, ch ->
            w.newPage()
            w.y = H * 0.18f
            w.text("Kapitel ${i + 1}", chapNo, Layout.Alignment.ALIGN_CENTER, 4f)
            w.text(ch.title, chapP, Layout.Alignment.ALIGN_CENTER, 22f)
            // Comicseite des Kapitels: eigene Seite direkt nach der Überschrift
            if (withImages) images.file(ch.comic)?.let { w.image(it, 0.75f) }
            ch.scenes.filter { it.text.isNotBlank() }.forEachIndexed { k, sc ->
                if (k > 0) w.text("✦", chapNo, Layout.Alignment.ALIGN_CENTER, 8f)
                if (withImages) images.file(sc.image)?.let { w.image(it, 0.42f) }
                paragraphs(sc.text).forEach { p -> w.text(italicSpans(p), body, Layout.Alignment.ALIGN_NORMAL, 5f) }
            }
        }
        // Ende
        w.newPage(numbers = false)
        w.y = H * 0.45f
        w.text("Ende", subP, Layout.Alignment.ALIGN_CENTER)
        w.finish()

        val uri = store(context, "${safeName(book.title)}-${System.currentTimeMillis() % 100000}.pdf", "application/pdf") { doc.writeTo(it) }
        doc.close()
        return uri
    }

    // ==================== EPUB ====================

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun xhtml(title: String, body: String) = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="de" lang="de">
<head><meta charset="utf-8"/><title>${esc(title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
$body
</body>
</html>"""

    private val css = """
body { font-family: serif; line-height: 1.5; margin: 0 5%; }
h1 { text-align: center; margin-top: 3em; font-size: 1.6em; }
h2 { text-align: center; margin-top: 2.5em; font-size: 1.3em; }
.kapnr { text-align: center; color: #8a6a3a; margin-top: 3em; font-size: 0.9em; }
p { text-indent: 1.2em; margin: 0 0 0.3em 0; text-align: justify; }
.trenner { text-align: center; margin: 1em 0; text-indent: 0; }
.bild { text-align: center; margin: 1em 0; text-indent: 0; }
.bild img { max-width: 100%; max-height: 60vh; }
.titel { text-align: center; text-indent: 0; }
""".trimIndent()

    fun epub(context: Context, book: Book, plot: Plot, author: String, withImages: Boolean, images: Images): Uri? {
        val chapters = plot.chapters.filter { ch -> ch.scenes.any { it.text.isNotBlank() } }
        val uid = "urn:uuid:" + UUID.nameUUIDFromBytes(book.id.toByteArray())
        val pics = mutableListOf<Pair<String, File>>() // Name im EPUB -> Datei
        fun pic(name: String): String? {
            if (!withImages) return null
            val f = images.file(name) ?: return null
            val n = "bilder/" + f.name
            if (pics.none { it.first == n }) pics += n to f
            return n
        }
        val cover = pic(book.cover)

        val pages = mutableListOf<Pair<String, String>>() // Datei -> Inhalt
        pages += "titel.xhtml" to xhtml(book.title, buildString {
            if (cover != null) append("<p class=\"bild\"><img src=\"$cover\" alt=\"Titelbild\"/></p>\n")
            append("<h1>${esc(book.title)}</h1>\n")
            if (author.isNotBlank()) append("<p class=\"titel\"><em>${esc(author)}</em></p>\n")
        })
        chapters.forEachIndexed { i, ch ->
            pages += "kapitel${i + 1}.xhtml" to xhtml(ch.title, buildString {
                append("<p class=\"kapnr\">Kapitel ${i + 1}</p>\n<h2>${esc(ch.title)}</h2>\n")
                pic(ch.comic)?.let { append("<p class=\"bild\"><img src=\"$it\" alt=\"Comicseite\"/></p>\n") }
                ch.scenes.filter { it.text.isNotBlank() }.forEachIndexed { k, sc ->
                    if (k > 0) append("<p class=\"trenner\">✦</p>\n")
                    pic(sc.image)?.let { append("<p class=\"bild\"><img src=\"$it\" alt=\"${esc(sc.title)}\"/></p>\n") }
                    paragraphs(sc.text).forEach { p ->
                        val t = esc(p).replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
                        append("<p>$t</p>\n")
                    }
                }
            })
        }
        val nav = xhtml("Inhalt", buildString {
            append("<nav epub:type=\"toc\" id=\"toc\"><h2>Inhalt</h2><ol>\n")
            chapters.forEachIndexed { i, ch -> append("<li><a href=\"kapitel${i + 1}.xhtml\">${esc(ch.title)}</a></li>\n") }
            append("</ol></nav>")
        })
        val ncx = """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="$uid"/></head>
<docTitle><text>${esc(book.title)}</text></docTitle><navMap>
${chapters.mapIndexed { i, ch -> "<navPoint id=\"k${i + 1}\" playOrder=\"${i + 1}\"><navLabel><text>${esc(ch.title)}</text></navLabel><content src=\"kapitel${i + 1}.xhtml\"/></navPoint>" }.joinToString("\n")}
</navMap></ncx>"""
        fun mime(n: String) = if (n.endsWith(".png")) "image/png" else "image/jpeg"
        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="de">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">$uid</dc:identifier><dc:title>${esc(book.title)}</dc:title><dc:language>de</dc:language>
${if (author.isNotBlank()) "<dc:creator>${esc(author)}</dc:creator>" else ""}
<meta property="dcterms:modified">${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}</meta>
${if (cover != null) "<meta name=\"cover\" content=\"titelbild\"/>" else ""}
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
<item id="css" href="style.css" media-type="text/css"/>
${pages.mapIndexed { i, (f, _) -> "<item id=\"s$i\" href=\"$f\" media-type=\"application/xhtml+xml\"/>" }.joinToString("\n")}
${pics.mapIndexed { i, (n, _) -> "<item id=\"${if (n == cover) "titelbild" else "b$i"}\" href=\"$n\" media-type=\"${mime(n)}\"${if (n == cover) " properties=\"cover-image\"" else ""}/>" }.joinToString("\n")}
</manifest>
<spine toc="ncx">
${pages.indices.joinToString("\n") { "<itemref idref=\"s$it\"/>" }}
</spine>
</package>"""

        return store(context, "${safeName(book.title)}-${System.currentTimeMillis() % 100000}.epub", "application/epub+zip") { out ->
            ZipOutputStream(out).use { zip ->
                // "mimetype" muss als erste Datei unkomprimiert drinstehen
                val mt = "application/epub+zip".toByteArray()
                val e = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED; size = mt.size.toLong(); compressedSize = mt.size.toLong()
                    crc = CRC32().apply { update(mt) }.value
                }
                zip.putNextEntry(e); zip.write(mt); zip.closeEntry()
                fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
                put("META-INF/container.xml", """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
                put("OEBPS/content.opf", opf.toByteArray())
                put("OEBPS/toc.ncx", ncx.toByteArray())
                put("OEBPS/nav.xhtml", nav.toByteArray())
                put("OEBPS/style.css", css.toByteArray())
                pages.forEach { (f, c) -> put("OEBPS/$f", c.toByteArray()) }
                pics.forEach { (n, f) -> put("OEBPS/$n", f.readBytes()) }
            }
        }
    }
}
