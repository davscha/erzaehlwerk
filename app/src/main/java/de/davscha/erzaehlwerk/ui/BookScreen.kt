package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book

/** Ein Buch: Titel, Genre, Idee. Plot, Kapitel und Szenen folgen in den nächsten Etappen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScreen(vm: AppViewModel, bookId: String) {
    val book = vm.book(bookId) ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${book.title}“ löschen?") },
            text = { Text("Das Buch mit allem, was dazugehört, wird gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteBook(book.id) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    fun update(b: Book) = vm.saveBook(b)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(book.emoji + "  " + book.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = book.title, onValueChange = { update(book.copy(title = it)) }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Book.genres.forEach { g ->
                        FilterChip(
                            selected = book.genre == g,
                            onClick = { update(book.copy(genre = if (book.genre == g) "" else g, emoji = Book.emojis[g] ?: book.emoji)) },
                            label = { Text("${Book.emojis[g] ?: ""} $g") },
                        )
                    }
                }
                OutlinedTextField(
                    value = book.idea,
                    onValueChange = { update(book.copy(idea = it)) },
                    label = { Text("Worum soll es gehen?") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Als Nächstes kommen hier hin: Figuren und Orte, der Kapitelplan aus deiner Idee (auch aus Bildern) und das Schreiben Szene für Szene.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
