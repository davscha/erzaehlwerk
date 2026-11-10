package de.davscha.erzaehlwerk.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Outfit
import de.davscha.erzaehlwerk.data.Person
import de.davscha.erzaehlwerk.data.Place
import kotlinx.coroutines.launch

/** Auf Deutsch beschreiben – Gemma füllt die englischen Felder */
@Composable
fun GemmaHelper(label: String, placeholder: String, run: suspend (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, label = { Text(label) }, placeholder = { Text(placeholder) },
            maxLines = 3, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            busy = true
            scope.launch {
                runCatching { run(text) }.onFailure { Toast.makeText(context, it.message ?: "Fehler", Toast.LENGTH_LONG).show() }
                busy = false
            }
        }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoFixHigh, "Gemma füllt aus")
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, placeholder = { Text(placeholder) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Caption(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/** Aussehen einer Figur für MOSS Image (Englisch) */
@Composable
fun LooksFields(vm: AppViewModel, p: Person, onChange: (Person) -> Unit) {
    Caption("Aussehen fürs Bild (Englisch)")
    GemmaHelper("Aussehen auf Deutsch beschreiben", "z. B. Mitte 20, dunkler welliger Bob, Sommersprossen, sportlich") { onChange(vm.describeLooks(p, it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = p.age, onValueChange = { onChange(p.copy(age = it.take(12))) }, label = { Text("Alter") }, singleLine = true, modifier = Modifier.width(100.dp))
        OutlinedTextField(value = p.hair, onValueChange = { onChange(p.copy(hair = it)) }, label = { Text("Haare") }, placeholder = { Text("chin length wavy bob, dark brown") }, maxLines = 3, modifier = Modifier.weight(1f))
    }
    Field("Gesicht", p.face, "angular face, high cheekbones, straight nose, full lips") { onChange(p.copy(face = it)) }
    Field("Augen", p.eyes, "almond-shaped green eyes, calm intense gaze") { onChange(p.copy(eyes = it)) }
    Field("Körper", p.body, "slender athletic build, average height") { onChange(p.copy(body = it)) }
    Field("Haut", p.skin, "fair skin, light freckles across the nose") { onChange(p.copy(skin = it)) }
    Field("Besonderheiten", p.appearance, "glasses, small scar on the chin") { onChange(p.copy(appearance = it)) }
}

/** Ort für MOSS Image (Englisch) */
@Composable
fun PlaceFields(vm: AppViewModel, pl: Place, onChange: (Place) -> Unit) {
    Caption("Ort fürs Bild (Englisch)")
    GemmaHelper("Auf Deutsch beschreiben (oder leer: aus der Beschreibung oben)", "z. B. alte Fabrikhalle, rostige Treppe, Sonnenstrahlen") { onChange(vm.describePlace(pl, it)) }
    Field("Hintergrund", pl.background, "vast abandoned warehouse hall, rusted steel staircase, cracked concrete floor") { onChange(pl.copy(background = it)) }
    Field("Licht", pl.lights, "sunbeams through the roof windows, hazy light shafts") { onChange(pl.copy(lights = it)) }
}

/** Kleidung Teil für Teil (Englisch) */
@Composable
fun ClothingPieces(vm: AppViewModel, o: Outfit, onChange: (Outfit) -> Unit) {
    Caption("Teile fürs Bild (Englisch)")
    GemmaHelper("Auf Deutsch beschreiben", "z. B. braune Feldjacke offen, rotes Hemd, Jeans, Lederstiefel") { onChange(vm.describeOutfit(o, it)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        o.pieces.forEachIndexed { i, (part, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = part, onValueChange = { v -> onChange(o.copy(pieces = o.pieces.toMutableList().also { it[i] = v to text })) },
                    label = { Text("Teil") }, placeholder = { Text("jacket") }, singleLine = true, modifier = Modifier.width(120.dp),
                )
                OutlinedTextField(
                    value = text, onValueChange = { v -> onChange(o.copy(pieces = o.pieces.toMutableList().also { it[i] = part to v })) },
                    label = { Text("Beschreibung") }, maxLines = 3, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(o.copy(pieces = o.pieces.filterIndexed { k, _ -> k != i })) }) { Icon(Icons.Default.Close, "Entfernen") }
            }
        }
        TextButton(onClick = { onChange(o.copy(pieces = o.pieces + ("" to ""))) }) { Icon(Icons.Default.Add, null); Text(" Teil hinzufügen") }
        if (o.pieces.isEmpty()) Text("Noch keine Teile.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
