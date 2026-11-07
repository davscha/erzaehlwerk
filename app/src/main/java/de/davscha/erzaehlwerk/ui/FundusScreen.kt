package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Outfit
import de.davscha.erzaehlwerk.data.Style
import de.davscha.erzaehlwerk.data.TagField

/** Fundus für alle Bücher: Kleidung und Bildstile */
@Composable
fun FundusScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var editOutfit by remember { mutableStateOf<Outfit?>(null) }
    var editStyle by remember { mutableStateOf<Style?>(null) }
    editOutfit?.let { OutfitDialog(vm, it) { editOutfit = null } }
    editStyle?.let { StyleDialog(vm, it) { editStyle = null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Column(Modifier.weight(1f)) {
                Text("Fundus", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Kleidung und Bildstile für alle Bücher", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("👗 Kleidung (${vm.fundus.clothing.size})") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("🎨 Stile (${vm.fundus.styles.size})") })
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { if (tab == 0) editOutfit = Outfit() else editStyle = Style() }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Hinzufügen")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tab == 0) items(vm.fundus.clothing, key = { it.id }) { o ->
                ItemCard(vm.fundusImage(o.preview), "👗", o.name, o.tags, vm.painting[o.id]) { editOutfit = o }
            } else items(vm.fundus.styles, key = { it.id }) { s ->
                Card(onClick = { editStyle = s }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text((if (vm.fundus.defaultStyle?.id == s.id) "⭐ " else "🎨 ") + s.name.ifBlank { s.trigger }, fontWeight = FontWeight.SemiBold)
                        Text("Trigger: ${s.trigger} · Stärke %.2f".format(s.strength), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitDialog(vm: AppViewModel, original: Outfit, onClose: () -> Unit) {
    var o by remember { mutableStateOf(original) }
    val isNew = vm.fundus.clothing.none { it.id == original.id }
    val preview = vm.outfit(o.id)?.preview ?: o.preview
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Kleidung") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = o.name, onValueChange = { o = o.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TagInput(value = o.tags, onChange = { o = o.copy(tags = it) }, label = "Tags", field = TagField.CLOTHING, placeholder = "brown coat, white sweater, black pants")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vorschau trägt:", fontSize = 13.sp)
                    FilterChip(selected = o.model == "1girl", onClick = { o = o.copy(model = "1girl") }, label = { Text("👩 Frau") })
                    FilterChip(selected = o.model == "1boy", onClick = { o = o.copy(model = "1boy") }, label = { Text("👨 Mann") })
                }
                PaintBox(vm, o.id, vm.fundusImage(preview), "👗", o.tags.isNotBlank()) { vm.paintOutfit(o.copy(preview = preview)) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveOutfit(o.copy(preview = preview)); onClose() }, enabled = o.name.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { vm.deleteOutfit(o.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun StyleDialog(vm: AppViewModel, original: Style, onClose: () -> Unit) {
    var s by remember { mutableStateOf(original) }
    var makeDefault by remember { mutableStateOf(vm.fundus.defaultStyle?.id == original.id) }
    var menu by remember { mutableStateOf(false) }
    val isNew = vm.fundus.styles.none { it.id == original.id }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Stil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = s.name, onValueChange = { s = s.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedTextField(
                        value = s.lora,
                        onValueChange = { s = s.copy(lora = it) },
                        label = { Text("LoRA-Datei") },
                        singleLine = true,
                        trailingIcon = { TextButton(onClick = { if (vm.loras.isEmpty()) vm.loadLoras() else menu = true }) { Text(if (vm.loras.isEmpty()) "Liste laden" else "Auswählen") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        vm.loras.forEach { l -> DropdownMenuItem(text = { Text(l, fontSize = 14.sp) }, onClick = { menu = false; s = s.copy(lora = l, name = s.name.ifBlank { Style(lora = l).trigger }) }) }
                    }
                }
                if (s.lora.isNotBlank()) Text("Trigger-Tag (automatisch): ${s.trigger}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Stärke: %.2f".format(s.strength), fontSize = 13.sp)
                Slider(value = s.strength, onValueChange = { s = s.copy(strength = (it * 20).toInt() / 20f) }, valueRange = 0f..1.5f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text("Standard-Stil")
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveStyle(s.copy(name = s.name.ifBlank { s.trigger }), makeDefault); onClose() }, enabled = s.lora.isNotBlank()) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew && vm.fundus.styles.size > 1) TextButton(onClick = { vm.deleteStyle(s.id); onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}
