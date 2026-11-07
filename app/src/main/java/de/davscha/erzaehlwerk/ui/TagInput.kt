package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.TagField
import de.davscha.erzaehlwerk.data.TagLibrary
import de.davscha.erzaehlwerk.i18n.tr

/** Die Tags eines Textes als Liste (ohne Leerzeichen am Rand) */
private fun tokens(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Tag-Feld mit Vorschlägen beim Tippen (aus der Danbooru-Liste) und Baukasten zum Antippen.
 * [extra] erscheint rechts neben dem Baukasten-Knopf (z. B. Zauberstab oder Würfel).
 */
@Composable
fun TagInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    field: TagField,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    minLines: Int = 2,
    extra: @Composable () -> Unit = {},
) {
    var picker by remember { mutableStateOf(false) }
    if (picker) TagPickerDialog(field, value, onChange) { picker = false }
    // Das Wort, das gerade getippt wird: alles nach dem letzten Komma
    val current = value.substringAfterLast(',').trim()
    val suggestions = remember(current, field) { TagLibrary.suggest(current, field, 12) }
        .filter { it.name != current }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
                minLines = minLines,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { picker = true }, enabled = enabled && TagLibrary.loaded) {
                Icon(Icons.Default.Widgets, tr("Tag-Baukasten"))
            }
            extra()
        }
        if (enabled && suggestions.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions, key = { it.name }) { t ->
                    SuggestionChip(
                        onClick = {
                            // Angefangenes Wort durch den Tag ersetzen
                            val before = value.substringBeforeLast(',', "").trim()
                            onChange(listOf(before, t.name).filter { it.isNotEmpty() }.joinToString(", ") + ", ")
                        },
                        label = { Text(t.name, fontSize = 13.sp) },
                    )
                }
            }
        }
    }
}

/** Reiter des Baukastens je Feld: Anzeige-Schlüssel für tr() -> Fach */
private fun tabsFor(field: TagField): List<Pair<String, String>> = when (field) {
    TagField.APPEARANCE -> listOf("Haare" to "appearance:hair", "Augen" to "appearance:eyes", "Brille" to "clothing:eyewear")
    TagField.CLOTHING -> listOf(
        "Oberteil" to "clothing:top", "Unterteil" to "clothing:bottom", "Outfit" to "clothing:outfit", "Schuhe" to "clothing:footwear",
        "Beine" to "clothing:legwear", "Kopf" to "clothing:headwear", "Accessoires" to "clothing:accents", "Brille" to "clothing:eyewear",
        "Gegenstände" to "equipment:handheld",
    )
    TagField.LOCATION -> listOf("Ort" to "scenery:setting", "Requisiten" to "scenery:props", "Stimmung" to "scenery:atmosphere", "Tiere" to "creature:animal")
    TagField.SCENE -> listOf(
        "Ausdruck" to "pose:expression", "Haltung" to "pose:posture", "Gesten" to "pose:gesture", "Kamera" to "pose:camera",
        "Bewegung" to "pose:kinetic", "Gegenstände" to "equipment:handheld", "Bildaufbau" to "other:layout",
    )
    TagField.ANY -> listOf(
        "Ort" to "scenery:setting", "Stimmung" to "scenery:atmosphere", "Ausdruck" to "pose:expression", "Haltung" to "pose:posture",
        "Kamera" to "pose:camera", "Gegenstände" to "equipment:handheld",
    )
}

/** Baukasten: die beliebtesten Tags je Fach zum Antippen (an/aus), mit Suche */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(field: TagField, value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val tabs = tabsFor(field)
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(value) }
    val chosen = tokens(text).map { it.lowercase() }.toSet()
    fun toggle(name: String) {
        val list = tokens(text).toMutableList()
        if (list.any { it.equals(name, true) }) list.removeAll { it.equals(name, true) } else list += name
        text = list.joinToString(", ")
        onChange(text)
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Tag-Baukasten")) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(tr("Suchen (Englisch)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.trim().length < 2) {
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.forEachIndexed { i, (label, _) ->
                            FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(tr(label)) })
                        }
                    }
                }
                val list = if (search.trim().length >= 2) TagLibrary.suggest(search, field, 60) else TagLibrary.top(tabs[tab].second, 150)
                FlowRow(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    list.forEach { t ->
                        FilterChip(selected = t.name.lowercase() in chosen, onClick = { toggle(t.name) }, label = { Text(t.name, fontSize = 13.sp) })
                    }
                }
                Text(
                    tr("Ausgewählt: %s", text.ifBlank { "–" }),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Fertig")) } },
    )
}
