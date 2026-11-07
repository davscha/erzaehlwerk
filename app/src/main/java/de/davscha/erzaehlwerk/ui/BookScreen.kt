package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book

/** Ein Buch mit Reitern: Übersicht, Figuren, Orte (Kapitel folgen in Etappe 3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScreen(vm: AppViewModel, bookId: String) {
    val book = vm.book(bookId) ?: return
    LaunchedEffect(bookId) { vm.openWorld(bookId) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${book.title}“ löschen?") },
            text = { Text("Das Buch mit Figuren, Orten und Bildern wird gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteBook(book.id) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(book.emoji + "  " + book.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.padding(horizontal = 24.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("📝 Übersicht") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("👤 Figuren (${if (vm.worldBookId == bookId) vm.world.people.size else 0})") })
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("🏞️ Orte (${if (vm.worldBookId == bookId) vm.world.places.size else 0})") })
            FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("📚 Plot (${if (vm.worldBookId == bookId) vm.plot.chapters.size else 0} Kap.)") })
            FilterChip(selected = tab == 4, onClick = { tab = 4 }, label = { Text("✍️ Schreiben") })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
        if (vm.worldBookId != bookId) return@Column
        when (tab) {
            0 -> Overview(vm, book)
            1 -> PeopleTab(vm, book)
            2 -> PlacesTab(vm, book)
            3 -> PlotTab(vm, book)
            4 -> WriteTab(vm, book)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(vm: AppViewModel, book: Book) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(value = book.title, onValueChange = { vm.saveBook(book.copy(title = it)) }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Book.genres.forEach { g ->
                    FilterChip(
                        selected = book.genre == g,
                        onClick = { vm.saveBook(book.copy(genre = if (book.genre == g) "" else g, emoji = Book.emojis[g] ?: book.emoji)) },
                        label = { Text("${Book.emojis[g] ?: ""} $g") },
                    )
                }
            }
            OutlinedTextField(value = book.idea, onValueChange = { vm.saveBook(book.copy(idea = it)) }, label = { Text("Worum soll es gehen?") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Text(
                "Tipp: Unter „Figuren“ und „Orte“ kann die KI dir passend zur Idee etwas vorschlagen. Unter „Plot“ entstehen aus Idee, Bildern, Figuren und Orten das Exposé und der Kapitelplan.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
