package de.davscha.erzaehlwerk.ui

import de.davscha.erzaehlwerk.Edition

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import de.davscha.erzaehlwerk.data.Gender
import de.davscha.erzaehlwerk.data.Person
import de.davscha.erzaehlwerk.data.Place
import de.davscha.erzaehlwerk.data.Role
import de.davscha.erzaehlwerk.data.TagField
import de.davscha.erzaehlwerk.data.TagLibrary
import kotlinx.coroutines.launch

// ==================== Figuren ====================

@Composable
fun PeopleTab(vm: AppViewModel, book: Book) {
    var edit by remember { mutableStateOf<Person?>(null) }
    var suggest by remember { mutableStateOf(false) }
    var cfImport by remember { mutableStateOf(false) }
    edit?.let { PersonDialog(vm, it) { edit = null } }
    if (suggest) SuggestDialog(
        title = "Figuren vorschlagen",
        load = { vm.suggestPeople(book) },
        label = { p -> "${p.emoji} ${p.name} · ${p.role.label}" },
        detail = { p -> p.description },
        onTake = { list -> list.forEach { vm.savePerson(it) } },
        onClose = { suggest = false },
    )
    if (cfImport) ChooseDialog(
        title = "Figuren aus Character Fun",
        items = remember { vm.cfPeople() },
        label = { "${it.person.emoji} ${it.person.name}" },
        onTake = { vm.importPeople(it) },
        onClose = { cfImport = false },
    )
    Column(Modifier.fillMaxSize()) {
        ActionRow {
            FilledTonalButton(onClick = { edit = Person() }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Figur") }
            OutlinedButton(onClick = { suggest = true }) { Text("✨ Vorschlagen lassen") }
            if (vm.canImportCf) OutlinedButton(onClick = { cfImport = true }) { Text("Aus Character Fun") }
        }
        if (vm.world.people.isEmpty()) Hint("Noch keine Figuren. Lass dir welche passend zur Idee vorschlagen oder leg selbst eine an.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(vm.world.people, key = { it.id }) { p ->
                ItemCard(vm.image(p.preview), p.emoji, p.name, "${p.role.label} · ${p.description}", vm.painting[p.id]) { edit = p }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonDialog(vm: AppViewModel, original: Person, onClose: () -> Unit) {
    var p by remember { mutableStateOf(original) }
    var helping by remember { mutableStateOf(false) }
    var outfitMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = vm.world.people.none { it.id == original.id }
    val preview = vm.world.people.firstOrNull { it.id == p.id }?.preview ?: p.preview
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neue Figur" else p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p.name, onValueChange = { p = p.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = p.emoji, onValueChange = { p = p.copy(emoji = it.take(8)) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(90.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Role.entries.forEach { r -> FilterChip(selected = p.role == r, onClick = { p = p.copy(role = r) }, label = { Text(r.label) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gender.entries.forEach { g -> FilterChip(selected = p.gender == g, onClick = { p = p.copy(gender = g) }, label = { Text(g.label) }) }
                }
                OutlinedTextField(
                    value = p.description,
                    onValueChange = { p = p.copy(description = it) },
                    label = { Text("Persönlichkeit, Hintergrund, Sprechweise") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (Edition.PRO) {
                LooksFields(vm, p) { p = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { outfitMenu = true }) { Text("👗 " + (vm.outfit(p.outfitId)?.name ?: "Keine besondere Kleidung")) }
                        DropdownMenu(expanded = outfitMenu, onDismissRequest = { outfitMenu = false }) {
                            DropdownMenuItem(text = { Text("Keine besondere Kleidung") }, onClick = { outfitMenu = false; p = p.copy(outfitId = "") })
                            vm.fundus.clothing.forEach { o -> DropdownMenuItem(text = { Text(o.name) }, onClick = { outfitMenu = false; p = p.copy(outfitId = o.id) }) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(
                        value = if (p.seed > 0) p.seed.toString() else "",
                        onValueChange = { v -> p = p.copy(seed = v.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0) },
                        label = { Text("Startwert") },
                        singleLine = true,
                        modifier = Modifier.width(150.dp),
                    )
                    IconButton(onClick = { p = p.copy(seed = (1..999_999_999L).random()) }) { Icon(Icons.Default.Casino, "Würfeln") }
                }
                }
                PaintBox(vm, p.id, vm.image(preview), p.emoji, p.name.isNotBlank()) { vm.paintPerson(p.copy(preview = preview)) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.savePerson(p.copy(name = p.name.trim(), preview = preview)); onClose() }, enabled = p.name.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deletePerson(p.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

// ==================== Orte ====================

@Composable
fun PlacesTab(vm: AppViewModel, book: Book) {
    var edit by remember { mutableStateOf<Place?>(null) }
    var suggest by remember { mutableStateOf(false) }
    var cfImport by remember { mutableStateOf(false) }
    edit?.let { PlaceDialog(vm, it) { edit = null } }
    if (suggest) SuggestDialog(
        title = "Orte vorschlagen",
        load = { vm.suggestPlaces(book) },
        label = { it.name },
        detail = { it.description },
        onTake = { list -> list.forEach { vm.savePlace(it) } },
        onClose = { suggest = false },
    )
    if (cfImport) ChooseDialog(
        title = "Orte aus Character Fun",
        items = remember { vm.cfPlaces() },
        label = { it.first.name },
        onTake = { vm.importPlaces(it) },
        onClose = { cfImport = false },
    )
    Column(Modifier.fillMaxSize()) {
        ActionRow {
            FilledTonalButton(onClick = { edit = Place() }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Ort") }
            OutlinedButton(onClick = { suggest = true }) { Text("✨ Vorschlagen lassen") }
            if (vm.canImportCf) OutlinedButton(onClick = { cfImport = true }) { Text("Aus Character Fun") }
        }
        if (vm.world.places.isEmpty()) Hint("Noch keine Orte. Wo spielt deine Geschichte?")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(vm.world.places, key = { it.id }) { pl ->
                ItemCard(vm.image(pl.preview), "🏞️", pl.name, pl.description.ifBlank { pl.background }, vm.painting[pl.id]) { edit = pl }
            }
        }
    }
}

@Composable
private fun PlaceDialog(vm: AppViewModel, original: Place, onClose: () -> Unit) {
    var pl by remember { mutableStateOf(original) }
    var helping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isNew = vm.world.places.none { it.id == original.id }
    val preview = vm.world.places.firstOrNull { it.id == pl.id }?.preview ?: pl.preview
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neuer Ort" else pl.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = pl.name, onValueChange = { pl = pl.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = pl.description,
                    onValueChange = { pl = pl.copy(description = it) },
                    label = { Text("Wie ist es dort? (für den Text)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (Edition.PRO) PlaceFields(vm, pl) { pl = it }
                PaintBox(vm, pl.id, vm.image(preview), "🏞️", pl.background.isNotBlank()) { vm.paintPlace(pl.copy(preview = preview)) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.savePlace(pl.copy(name = pl.name.trim(), preview = preview)); onClose() }, enabled = pl.name.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deletePlace(pl.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

// ==================== Gemeinsames ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
}

/** Gemma schlägt etwas vor – du hakst ab, was übernommen wird */
@Composable
private fun <T> SuggestDialog(
    title: String,
    load: suspend () -> List<T>,
    label: (T) -> String,
    detail: (T) -> String,
    onTake: (List<T>) -> Unit,
    onClose: () -> Unit,
) {
    var items by remember { mutableStateOf<List<T>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    val chosen = remember { mutableStateListOf<Int>() }
    androidx.compose.runtime.LaunchedEffect(round) {
        items = null; error = null; chosen.clear()
        runCatching { load() }.onSuccess { items = it; chosen.addAll(it.indices) }.onFailure { error = it.message ?: "Fehler" }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = items
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Die KI denkt sich etwas aus …")
                    }
                    else -> list.forEachIndexed { i, it ->
                        Row(Modifier.fillMaxWidth().clickable { if (i in chosen) chosen.remove(i) else chosen.add(i) }, verticalAlignment = Alignment.Top) {
                            Checkbox(checked = i in chosen, onCheckedChange = { c -> if (c) chosen.add(i) else chosen.remove(i) })
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(label(it), fontWeight = FontWeight.SemiBold)
                                Text(detail(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { items?.let { l -> onTake(chosen.sorted().map { l[it] }) }; onClose() }, enabled = items != null && chosen.isNotEmpty()) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { round++ }, enabled = items != null || error != null) { Text("Neu würfeln") }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

/** Auswahl mit Häkchen (z. B. Import aus Character Fun) */
@Composable
private fun <T> ChooseDialog(title: String, items: List<T>, label: (T) -> String, onTake: (List<T>) -> Unit, onClose: () -> Unit) {
    val chosen = remember { mutableStateListOf<Int>() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) Text("Nichts gefunden.")
                items.forEachIndexed { i, it ->
                    Row(Modifier.fillMaxWidth().clickable { if (i in chosen) chosen.remove(i) else chosen.add(i) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in chosen, onCheckedChange = { c -> if (c) chosen.add(i) else chosen.remove(i) })
                        Text(label(it))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onTake(chosen.sorted().map { items[it] }); onClose() }, enabled = chosen.isNotEmpty()) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}
