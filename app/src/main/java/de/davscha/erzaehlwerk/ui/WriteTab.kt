package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book

/** Schreiben: Szene wählen, von der KI schreiben lassen, weiterschreiben, umschreiben – oder selbst tippen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteTab(vm: AppViewModel, book: Book) {
    val all = vm.plot.chapters.flatMapIndexed { ci, ch -> ch.scenes.mapIndexed { si, sc -> Triple("${ci + 1}.${si + 1}", ch, sc) } }
    if (all.isEmpty()) {
        Text("Noch keine Szenen. Erst unter „Plot“ den Kapitelplan erstellen.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        return
    }
    var currentId by rememberSaveable { mutableStateOf(all.firstOrNull { it.third.text.isBlank() }?.third?.id ?: all.first().third.id) }
    val idx = all.indexOfFirst { it.third.id == currentId }.coerceAtLeast(0)
    val (label, chapter, scene) = all[idx]
    val writing = vm.writingSceneId != null
    var showStyle by remember { mutableStateOf(false) }
    var rewrite by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val sceneWords = scene.text.split(Regex("\\s+")).count { it.isNotBlank() }

    if (rewrite) RewriteDialog(onClose = { rewrite = false }) { vm.writeScene(book, chapter.id, scene.id, AppViewModel.WriteMode.REWRITE, it) }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Alle leeren Szenen schreiben?") },
        text = { Text("Die KI schreibt ${all.count { it.third.text.isBlank() }} Szenen nacheinander. Das kann eine Weile dauern – du kannst jederzeit stoppen.") },
        confirmButton = { TextButton(onClick = { confirmAll = false; vm.writeAll(book) }) { Text("Los") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Abbrechen") } },
    )

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- Schreibstil ----
            TextButton(onClick = { showStyle = !showStyle }) {
                Text("🖋️ Schreibstil: ${book.style} · ${Book.perspectives.firstOrNull { it.first == book.perspective }?.second ?: book.perspective} · ${book.tense} · ${Book.lengths.firstOrNull { it.first == book.sceneWords }?.second ?: "${book.sceneWords} Wörter"} " + if (showStyle) "▲" else "▼")
            }
            if (showStyle) StyleSettings(vm, book)

            // ---- Szenen-Auswahl ----
            LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(all, key = { it.third.id }) { (l, _, sc) ->
                    FilterChip(
                        selected = sc.id == currentId,
                        onClick = { currentId = sc.id },
                        enabled = !writing || sc.id == vm.writingSceneId,
                        label = { Text((if (vm.writingSceneId == sc.id) "✍️ " else if (sc.text.isNotBlank()) "✓ " else "") + l) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { currentId = all[idx - 1].third.id }, enabled = idx > 0 && !writing) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorige Szene") }
                Column(Modifier.weight(1f)) {
                    Text("Kapitel ${label.substringBefore('.')}: ${chapter.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$label  ${scene.title}", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = { currentId = all[idx + 1].third.id }, enabled = idx < all.lastIndex && !writing) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächste Szene") }
            }
            Text(scene.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val tags = listOfNotNull(vm.place(scene.placeId)?.let { "📍 ${it.name}" }) + scene.peopleIds.mapNotNull { vm.person(it)?.let { p -> "${p.emoji} ${p.name}" } }
            if (tags.isNotEmpty()) Text(tags.joinToString("   "), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)

            // ---- Knöpfe ----
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (writing) {
                    FilledTonalButton(onClick = { vm.stopWriting() }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stopp") }
                } else if (scene.text.isBlank()) {
                    Button(onClick = { vm.writeScene(book, chapter.id, scene.id, AppViewModel.WriteMode.NEW) }) { Text("✍️ Szene schreiben") }
                } else {
                    Button(onClick = { vm.writeScene(book, chapter.id, scene.id, AppViewModel.WriteMode.CONTINUE) }) { Text("⏩ Weiterschreiben") }
                    OutlinedButton(onClick = { rewrite = true }) { Text("🔁 Umschreiben …") }
                    OutlinedButton(onClick = { vm.writeScene(book, chapter.id, scene.id, AppViewModel.WriteMode.NEW) }) { Text("🎲 Ganz neu") }
                }
                if (!writing && all.any { it.third.text.isBlank() }) OutlinedButton(onClick = { confirmAll = true }) { Text("📚 Alle leeren Szenen schreiben") }
                if (vm.paintAllStatus != null) FilledTonalButton(onClick = { vm.stopPaintAll() }) { Text("Bilder stoppen (${vm.paintAllStatus})") }
                else if (!writing && all.any { it.third.image.isBlank() && it.third.text.isNotBlank() }) OutlinedButton(onClick = { vm.paintAllScenes() }, enabled = vm.comfyReady) { Text("🖼️ Bilder für alle Szenen") }
            }
            vm.writeStatus?.let {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(it, fontSize = 13.sp) }
            }
            vm.writeError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            // ---- Szenenbild ----
            SceneImageBlock(vm, chapter.id, scene)
            ComicBlock(vm, book, chapter)

            // ---- Text ----
            OutlinedTextField(
                value = scene.text,
                onValueChange = { vm.setSceneText(chapter.id, scene.id, it) },
                placeholder = { Text("Hier erscheint der Text – du kannst auch selbst schreiben oder alles ändern.") },
                textStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp),
                enabled = !writing,
                minLines = 14,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$sceneWords Wörter in dieser Szene · ${vm.writtenWords} im ganzen Buch", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (scene.recap.isNotBlank()) Text("Gemerkt: ${scene.recap}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSettings(vm: AppViewModel, book: Book) {
    var narratorMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stil", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WritingStyleChips(vm, book.style) { s -> if (s != null) vm.saveBook(book.copy(style = s.name)) }
        vm.writingStyle(book.style)?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Erzählperspektive", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Book.perspectives.forEach { (k, l) -> FilterChip(selected = book.perspective == k, onClick = { vm.saveBook(book.copy(perspective = k)) }, label = { Text(l) }) }
        }
        if (book.perspective == "ich") Box {
            TextButton(onClick = { narratorMenu = true }) { Text("Wer erzählt? " + (vm.person(book.narratorId)?.name ?: "wählen")) }
            DropdownMenu(expanded = narratorMenu, onDismissRequest = { narratorMenu = false }) {
                vm.world.people.forEach { p -> DropdownMenuItem(text = { Text("${p.emoji} ${p.name}") }, onClick = { narratorMenu = false; vm.saveBook(book.copy(narratorId = p.id)) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zeitform:", fontSize = 13.sp)
            Book.tenses.forEach { t -> FilterChip(selected = book.tense == t, onClick = { vm.saveBook(book.copy(tense = t)) }, label = { Text(t) }) }
            Spacer(Modifier.width(12.dp))
            Text("Länge:", fontSize = 13.sp)
            Book.lengths.forEach { (w, l) -> FilterChip(selected = book.sceneWords == w, onClick = { vm.saveBook(book.copy(sceneWords = w)) }, label = { Text(l) }) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Umschreiben mit Wunsch – Vorschläge zum Antippen oder eigener Text */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RewriteDialog(onClose: () -> Unit, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val ideas = listOf("mehr Dialog", "spannender", "mehr Gefühle", "kürzer und knapper", "mehr Beschreibung der Umgebung", "lustiger", "düsterer", "mit einer überraschenden Wendung")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Umschreiben") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ideas.forEach { i -> FilterChip(selected = text == i, onClick = { text = i }, label = { Text(i) }) }
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Wie soll es werden?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onGo(text); onClose() }) { Text("Umschreiben") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}

/** Bild zur Szene: malen (mit Auswahl, wer drauf ist), groß ansehen, neu malen, entfernen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneImageBlock(vm: AppViewModel, chapterId: String, scene: de.davscha.erzaehlwerk.data.Scene) {
    var focusId by remember(scene.id) { mutableStateOf(if (scene.peopleIds.size > 1) ALL else vm.defaultFocus(scene)?.id) }
    var full by remember { mutableStateOf(false) }
    val file = vm.image(scene.image)
    val busy = vm.painting.containsKey(scene.id)
    if (full && file != null) FullImage(file) { full = false }
    if (file != null || busy) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PreviewImage(file, "🖼️", vm.painting[scene.id],
                Modifier.size(420.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(enabled = file != null) { full = true })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📷 Im Bild:", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        FilterChip(selected = focusId == null, onClick = { focusId = null }, label = { Text("niemand") })
        if (scene.peopleIds.size > 1) FilterChip(selected = focusId == ALL, onClick = { focusId = ALL }, label = { Text("👥 alle") })
        scene.peopleIds.mapNotNull { vm.person(it) }.forEach { p ->
            FilterChip(selected = focusId == p.id, onClick = { focusId = p.id }, label = { Text("${p.emoji} ${p.name}") })
        }
        OutlinedButton(onClick = { vm.paintScene(chapterId, scene.id, focusId) }, enabled = vm.comfyReady && !busy && (scene.text.isNotBlank() || scene.summary.isNotBlank())) {
            Text(if (file == null) "Szenenbild malen" else "Neu malen")
        }
        if (file != null) TextButton(onClick = { vm.removeSceneImage(chapterId, scene.id) }) { Text("Bild entfernen") }
    }
    if (!vm.comfyReady) Text("PC/ComfyUI nicht erreichbar – Bilder ausgegraut", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    vm.paintError[scene.id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
}

/** Bild bildschirmfüllend – Tipp schließt */
@Composable
fun FullImage(file: java.io.File, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(model = file, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }
}

/** Das ganze Kapitel als Comicseite – MOSS malt alle Panels in einem Bild */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComicBlock(vm: AppViewModel, book: Book, chapter: de.davscha.erzaehlwerk.data.Chapter) {
    var panels by remember { mutableStateOf(6) }
    var full by remember { mutableStateOf(false) }
    val id = "comic-${chapter.id}"
    val file = vm.image(chapter.comic)
    val busy = vm.painting.containsKey(id)
    if (full && file != null) FullImage(file) { full = false }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("💬 Kapitel als Comicseite: ${chapter.title}", fontWeight = FontWeight.SemiBold)
    if (file != null || busy) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PreviewImage(file, "💬", vm.painting[id],
                Modifier.size(width = 330.dp, height = 440.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(enabled = file != null) { full = true })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Panels:", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        listOf(4, 6, 8, 9).forEach { n -> FilterChip(selected = panels == n, onClick = { panels = n }, label = { Text("$n") }) }
        OutlinedButton(onClick = { vm.paintComic(book, chapter.id, panels) }, enabled = vm.comfyReady && !busy && chapter.scenes.isNotEmpty()) {
            Text(if (file == null) "Comicseite malen" else "Neu malen")
        }
        if (file != null) TextButton(onClick = { vm.removeComic(chapter.id) }) { Text("Entfernen") }
    }
    vm.paintError[id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
}
