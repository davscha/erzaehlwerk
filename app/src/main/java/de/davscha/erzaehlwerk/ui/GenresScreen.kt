package de.davscha.erzaehlwerk.ui

import de.davscha.erzaehlwerk.Edition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book
import de.davscha.erzaehlwerk.data.Genre
import de.davscha.erzaehlwerk.data.WritingStyle
import androidx.compose.runtime.saveable.rememberSaveable

/** Eigener Bereich: Genres mit Beschreibung für die KI und Standards für neue Bücher */
@Composable
fun GenresScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var edit by remember { mutableStateOf<Genre?>(null) }
    var editStyle by remember { mutableStateOf<WritingStyle?>(null) }
    edit?.let { GenreDialog(vm, it) { edit = null } }
    editStyle?.let { WritingStyleDialog(vm, it) { editStyle = null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Column(Modifier.weight(1f)) {
                Text("Genres & Schreibstile", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Die KI bekommt die Beschreibungen bei allem mit, was sie für ein Buch schreibt", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("🏷️ Genres (${vm.genres.size})") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("🖋️ Schreibstile (${vm.writingStyles.size})") })
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { if (tab == 0) edit = Genre() else editStyle = WritingStyle() }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Hinzufügen")
            }
        }
        if (tab == 1) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.writingStyles, key = { it.id }) { st ->
                    val used = vm.books.count { it.style == st.name }
                    Card(Modifier.fillMaxWidth().clickable { editStyle = st }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🖋️ " + st.name.ifBlank { "Ohne Namen" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (used > 0) Text(if (used == 1) "1 Buch" else "$used Bücher", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(st.description.ifBlank { "Noch keine Beschreibung" }, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(vm.genres, key = { it.id }) { g ->
                val used = vm.books.count { it.genreId == g.id }
                Card(
                    Modifier.fillMaxWidth().clickable { edit = g },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.emoji, fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name.ifBlank { "Ohne Namen" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                if (used > 0) Text(if (used == 1) "1 Buch" else "$used Bücher", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(g.description.ifBlank { "Noch keine Beschreibung" }, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val defaults = listOfNotNull(
                            g.style.takeIf { it.isNotBlank() },
                            g.perspective.takeIf { it.isNotBlank() }?.let { p -> Book.perspectives.firstOrNull { it.first == p }?.second ?: p },
                            g.tense.takeIf { it.isNotBlank() },
                            vm.fundus.styles.firstOrNull { it.id == g.imageStyleId }?.let { "🎨 ${it.name}" },
                        )
                        if (defaults.isNotEmpty()) Text(defaults.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private val genreEmojis = listOf("📖", "🐉", "🕵️", "🚀", "🗺️", "💌", "🏰", "👻", "🎭", "📜", "☕", "⚔️", "🧙", "🤖", "🌊", "🌲", "🏙️", "🎪", "🐾", "🔮", "💀", "🌸", "🏴‍☠️", "🤠", "🦸", "🧸", "🍷", "⚽", "🎵", "🌌")

/** Genre anlegen oder bearbeiten. onSaved bekommt das gespeicherte Genre (z. B. um es gleich auszuwählen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreDialog(vm: AppViewModel, start: Genre, onSaved: (Genre) -> Unit = {}, onClose: () -> Unit) {
    var g by remember { mutableStateOf(start) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = vm.genres.none { it.id == start.id }
    val used = vm.books.count { it.genreId == start.id }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Genre löschen?") },
        text = { Text(if (used > 0) "$used Buch/Bücher behalten den Namen „${start.name}“, aber ohne Beschreibung." else "„${start.name}“ wird gelöscht.") },
        confirmButton = { TextButton(onClick = { vm.deleteGenre(start.id); confirmDelete = false; onClose() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neues Genre" else "Genre bearbeiten") },
        text = {
            Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(end = 10.dp)) { Text(g.emoji, fontSize = 32.sp) }
                    OutlinedTextField(value = g.name, onValueChange = { g = g.copy(name = it) }, label = { Text("Name") }, placeholder = { Text("z. B. Cozy Fantasy") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    genreEmojis.forEach { e ->
                        Text(e, fontSize = 22.sp, modifier = Modifier.clickable { g = g.copy(emoji = e) }.padding(4.dp))
                    }
                }
                OutlinedTextField(
                    value = g.description, onValueChange = { g = g.copy(description = it) },
                    label = { Text("Beschreibung für die KI") },
                    placeholder = { Text("Was macht das Genre aus? Stimmung, typische Elemente, was nicht vorkommen soll …") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                Text("Standards für neue Bücher (darf leer bleiben)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Stil", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WritingStyleChips(vm, g.style) { s -> g = g.copy(style = s?.name ?: "") }
                Text("Erzählperspektive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Book.perspectives.forEach { (k, label) -> FilterChip(selected = g.perspective == k, onClick = { g = g.copy(perspective = if (g.perspective == k) "" else k) }, label = { Text(label) }) }
                }
                Text("Zeitform", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Book.tenses.forEach { t -> FilterChip(selected = g.tense == t, onClick = { g = g.copy(tense = if (g.tense == t) "" else t) }, label = { Text(t) }) }
                }
                if (Edition.PRO) Text("Bildstil", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (Edition.PRO) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = g.imageStyleId.isBlank(), onClick = { g = g.copy(imageStyleId = "") }, label = { Text("Standard") })
                    vm.fundus.styles.forEach { s -> FilterChip(selected = g.imageStyleId == s.id, onClick = { g = g.copy(imageStyleId = s.id) }, label = { Text("🎨 ${s.name}") }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { val saved = g.copy(name = g.name.trim()); vm.saveGenre(saved); onSaved(saved); onClose() }, enabled = g.name.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

/** Genre-Chips mit „➕ Eigenes …“ zum schnellen Anlegen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(vm: AppViewModel, selectedId: String, onSelect: (Genre?) -> Unit) {
    var create by remember { mutableStateOf(false) }
    if (create) GenreDialog(vm, Genre(), onSaved = { onSelect(it) }) { create = false }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        vm.genres.forEach { g ->
            FilterChip(selected = selectedId == g.id, onClick = { onSelect(if (selectedId == g.id) null else g) }, label = { Text("${g.emoji} ${g.name}") })
        }
        FilterChip(selected = false, onClick = { create = true }, label = { Text("➕ Eigenes …") })
    }
}

/** Schreibstil anlegen oder bearbeiten */
@Composable
fun WritingStyleDialog(vm: AppViewModel, start: WritingStyle, onSaved: (WritingStyle) -> Unit = {}, onClose: () -> Unit) {
    var st by remember { mutableStateOf(start) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = vm.writingStyles.none { it.id == start.id }
    val used = vm.books.count { it.style == start.name }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Schreibstil löschen?") },
        text = { Text(if (used > 0) "$used Buch/Bücher behalten den Namen „${start.name}“, aber ohne Beschreibung." else "„${start.name}“ wird gelöscht.") },
        confirmButton = { TextButton(onClick = { vm.deleteWritingStyle(start.id); confirmDelete = false; onClose() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neuer Schreibstil" else "Schreibstil bearbeiten") },
        text = {
            Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = st.name, onValueChange = { st = st.copy(name = it) }, label = { Text("Name") }, placeholder = { Text("z. B. wie ein Hörspiel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = st.description, onValueChange = { st = st.copy(description = it) },
                    label = { Text("Beschreibung für die KI") },
                    placeholder = { Text("Wie soll geschrieben werden? Satzlänge, Ton, Wortwahl, Dialoganteil, Vorbilder …") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { val saved = st.copy(name = st.name.trim()); vm.saveWritingStyle(saved); onSaved(saved); onClose() },
                enabled = st.name.isNotBlank() && vm.writingStyles.none { it.id != st.id && it.name.equals(st.name.trim(), true) }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

/** Schreibstil-Chips mit „➕ Eigener …“ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingStyleChips(vm: AppViewModel, selected: String, onSelect: (WritingStyle?) -> Unit) {
    var create by remember { mutableStateOf(false) }
    if (create) WritingStyleDialog(vm, WritingStyle(), onSaved = { onSelect(it) }) { create = false }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        vm.writingStyles.forEach { s ->
            FilterChip(selected = selected.equals(s.name, true), onClick = { onSelect(if (selected.equals(s.name, true)) null else s) }, label = { Text(s.name) })
        }
        // Alter Stil, den es nicht mehr gibt, bleibt sichtbar
        if (selected.isNotBlank() && vm.writingStyle(selected) == null) FilterChip(selected = true, onClick = {}, label = { Text(selected) })
        FilterChip(selected = false, onClick = { create = true }, label = { Text("➕ Eigener …") })
    }
}
