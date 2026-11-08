package de.davscha.erzaehlwerk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book
import de.davscha.erzaehlwerk.data.Chapter
import de.davscha.erzaehlwerk.data.Scene

/** Plot: Ideen-Bilder, Exposé, Kapitelplan mit Szenen */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlotTab(vm: AppViewModel, book: Book) {
    var chapterCount by remember { mutableStateOf(8f) }
    var scenesPer by remember { mutableStateOf(3f) }
    var confirmReplan by remember { mutableStateOf(false) }
    var editChapter by remember { mutableStateOf<Chapter?>(null) }
    var editScene by remember { mutableStateOf<Pair<String, Scene>?>(null) }
    val busy = vm.plotBusy != null
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.addIdeaImage(uri) }

    editChapter?.let { ChapterDialog(vm, it) { editChapter = null } }
    editScene?.let { (cid, s) -> SceneDialog(vm, cid, s) { editScene = null } }
    if (confirmReplan) AlertDialog(
        onDismissRequest = { confirmReplan = false },
        title = { Text("Kapitelplan neu erstellen?") },
        text = { Text("Der bisherige Plan mit allen Kapiteln und Szenen wird ersetzt.") },
        confirmButton = { TextButton(onClick = { confirmReplan = false; vm.writeChapterPlan(book, chapterCount.toInt(), scenesPer.toInt()) }) { Text("Neu erstellen") } },
        dismissButton = { TextButton(onClick = { confirmReplan = false }) { Text("Abbrechen") } },
    )

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- Ideen-Bilder ----
            Text("🖼️ Ideen-Bilder", fontWeight = FontWeight.SemiBold)
            Text("Bilder als Anregung – Gemma beschreibt, was darauf passiert, und nimmt das mit in Exposé und Kapitel.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.plot.images.forEach { img ->
                Row(verticalAlignment = Alignment.Top) {
                    PreviewImage(vm.image(img.file), "🖼️", null, Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (vm.describing[img.file] == true) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Gemma schaut sich das Bild an …", fontSize = 13.sp)
                        } else OutlinedTextField(
                            value = img.description, onValueChange = { vm.setIdeaDescription(img.file, it) },
                            label = { Text("Was passiert auf dem Bild?") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column {
                        IconButton(onClick = { vm.describeIdeaImage(img.file) }, enabled = vm.describing[img.file] != true) { Icon(Icons.Default.Refresh, "Neu beschreiben") }
                        IconButton(onClick = { vm.removeIdeaImage(img.file) }) { Icon(Icons.Default.Close, "Entfernen") }
                    }
                }
            }
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Bild hinzufügen")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // ---- Exposé ----
            Text("📜 Exposé", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = vm.plot.synopsis, onValueChange = { vm.setSynopsis(it) },
                label = { Text("Die ganze Geschichte in wenigen Absätzen") }, minLines = 5, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            )
            var keepFrames by remember { mutableStateOf(true) }
            val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.synopsisFromVideo(book, uri, keepFrames) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.writeSynopsis(book) }, enabled = !busy && vm.videoStatus == null) { Text(if (vm.plot.synopsis.isBlank()) "Exposé schreiben lassen" else "Exposé neu schreiben lassen") }
                OutlinedButton(
                    onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    enabled = !busy && vm.videoStatus == null,
                ) { Text("🎬 Exposé aus Video") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = keepFrames, onCheckedChange = { keepFrames = it })
                Text("Standbilder aus dem Video als Ideen-Bilder übernehmen", fontSize = 13.sp)
            }
            VideoProgress(vm)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // ---- Kapitelplan ----
            Text("📚 Kapitelplan", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kapitel: ${chapterCount.toInt()}", modifier = Modifier.width(110.dp))
                Slider(value = chapterCount, onValueChange = { chapterCount = it }, valueRange = 3f..20f, steps = 16, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Szenen je Kapitel: ${scenesPer.toInt()}", modifier = Modifier.width(170.dp))
                Slider(value = scenesPer, onValueChange = { scenesPer = it }, valueRange = 1f..5f, steps = 3, modifier = Modifier.weight(1f))
            }
            Button(onClick = { if (vm.plot.chapters.isEmpty()) vm.writeChapterPlan(book, chapterCount.toInt(), scenesPer.toInt()) else confirmReplan = true }, enabled = !busy) {
                Text(if (vm.plot.chapters.isEmpty()) "Kapitelplan erstellen lassen" else "Kapitelplan neu erstellen")
            }
            Text("Tipp: Erst Figuren, Orte und Exposé – dann wird der Plan besser. Unbekannte Orte und Figuren legt die App automatisch an.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.plotBusy?.let {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(it) }
            }
            vm.plotError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            vm.plot.chapters.forEachIndexed { ci, ch ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(enabled = !busy) { editChapter = ch }) {
                                Text("Kapitel ${ci + 1}: ${ch.title}", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                if (ch.summary.isNotBlank()) Text(ch.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.moveChapter(ch.id, -1) }, enabled = ci > 0 && !busy) { Icon(Icons.Default.KeyboardArrowUp, "Nach oben") }
                            IconButton(onClick = { vm.moveChapter(ch.id, 1) }, enabled = ci < vm.plot.chapters.lastIndex && !busy) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
                        }
                        ch.scenes.forEachIndexed { si, sc ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !busy) { editScene = ch.id to sc }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                Text("${ci + 1}.${si + 1}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sc.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (sc.summary.isNotBlank()) Text(sc.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val tags = listOfNotNull(vm.place(sc.placeId)?.let { "📍 ${it.name}" }) + sc.peopleIds.mapNotNull { vm.person(it)?.let { p -> "${p.emoji} ${p.name}" } }
                                    if (tags.isNotEmpty()) Text(tags.joinToString("   "), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (sc.text.isNotBlank()) Text("✍️", fontSize = 14.sp)
                            }
                        }
                        TextButton(onClick = { editScene = ch.id to Scene() }, enabled = !busy) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Szene") }
                    }
                }
            }
            if (vm.plot.chapters.isNotEmpty() || !busy) {
                OutlinedButton(onClick = { editChapter = Chapter() }, enabled = !busy) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Kapitel von Hand") }
            }
        }
    }
}

@Composable
private fun ChapterDialog(vm: AppViewModel, original: Chapter, onClose: () -> Unit) {
    var c by remember { mutableStateOf(original) }
    val isNew = vm.plot.chapters.none { it.id == original.id }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neues Kapitel" else "Kapitel bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = c.title, onValueChange = { c = c.copy(title = it) }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = c.summary, onValueChange = { c = c.copy(summary = it) }, label = { Text("Was passiert?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveChapter(c); onClose() }, enabled = c.title.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deleteChapter(c.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneDialog(vm: AppViewModel, chapterId: String, original: Scene, onClose: () -> Unit) {
    var s by remember { mutableStateOf(original) }
    var placeMenu by remember { mutableStateOf(false) }
    val chapter = vm.plot.chapters.firstOrNull { it.id == chapterId }
    val isNew = chapter?.scenes?.none { it.id == original.id } ?: true
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neue Szene" else "Szene bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = s.title, onValueChange = { s = s.copy(title = it) }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = s.summary, onValueChange = { s = s.copy(summary = it) }, label = { Text("Was passiert genau?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Box {
                    TextButton(onClick = { placeMenu = true }) { Text("📍 " + (vm.place(s.placeId)?.name ?: "Ort wählen")) }
                    DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                        DropdownMenuItem(text = { Text("Kein Ort") }, onClick = { placeMenu = false; s = s.copy(placeId = "") })
                        vm.world.places.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { placeMenu = false; s = s.copy(placeId = p.id) }) }
                    }
                }
                Text("Wer ist dabei?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.world.people.forEach { p ->
                        val on = p.id in s.peopleIds
                        FilterChip(selected = on, onClick = { s = s.copy(peopleIds = if (on) s.peopleIds - p.id else s.peopleIds + p.id) }, label = { Text("${p.emoji} ${p.name}") })
                    }
                }
                if (chapter != null && !isNew) {
                    val i = chapter.scenes.indexOfFirst { it.id == s.id }
                    Row {
                        TextButton(onClick = { vm.moveScene(chapterId, s.id, -1); onClose() }, enabled = i > 0) { Text("↑ Früher") }
                        TextButton(onClick = { vm.moveScene(chapterId, s.id, 1); onClose() }, enabled = i < chapter.scenes.lastIndex) { Text("↓ Später") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveScene(chapterId, s); onClose() }, enabled = s.title.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deleteScene(chapterId, s.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}
