package de.davscha.erzaehlwerk.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book
import kotlinx.coroutines.launch

/** Ausgabe: Buch als PDF oder EPUB (Hörbuch folgt) */
@Composable
fun ExportTab(vm: AppViewModel, book: Book) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withImages by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    val written = vm.plot.chapters.sumOf { ch -> ch.scenes.count { it.text.isNotBlank() } }
    val total = vm.plot.chapters.sumOf { it.scenes.size }
    val imgs = vm.plot.chapters.sumOf { ch -> ch.scenes.count { it.image.isNotBlank() } }

    fun export(epub: Boolean) {
        result = null
        scope.launch {
            runCatching { vm.exportBook(book, epub, withImages) }
                .onSuccess { uri -> if (uri != null) result = uri to (if (epub) "application/epub+zip" else "application/pdf") else Toast.makeText(context, "Konnte nicht gespeichert werden", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Fehler", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📖 Buch", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("$written von $total Szenen geschrieben · ${vm.writtenWords} Wörter · $imgs Bilder", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (written < total) Text("Leere Szenen werden einfach ausgelassen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = vm.authorName, onValueChange = { vm.changeAuthor(it) }, label = { Text("Autor (steht auf der Titelseite)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mit Bildern (Titelbild und Szenenbilder)", modifier = Modifier.weight(1f))
                Switch(checked = withImages, onCheckedChange = { withImages = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { export(false) }, enabled = written > 0 && !vm.exporting) { Text("📄 Als PDF") }
                Button(onClick = { export(true) }, enabled = written > 0 && !vm.exporting) { Text("📱 Als EPUB") }
            }
            Text("PDF im Buchformat (A5) zum Lesen und Drucken. EPUB für E-Book-Reader und Lese-Apps – die Schrift passt sich dort an.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.exporting) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Buch wird gesetzt …")
            }
            result?.let { (uri, mime) ->
                Text("✓ Gespeichert unter Dokumente/Erzaehlwerk", color = MaterialTheme.colorScheme.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (e: Exception) { Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_LONG).show() }
                    }) { Text("Öffnen") }
                    TextButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                    }) { Text("Teilen") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("🎧 Hörbuch", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Kommt in der nächsten Etappe.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
