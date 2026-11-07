package de.davscha.erzaehlwerk.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.model.ModelCatalog
import de.davscha.erzaehlwerk.model.ModelInfo

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text("Einstellungen", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("Speicher")
                if (vm.sharedStorage) {
                    Text("✓ Bücher liegen unter Erzaehlwerk/, die Modelle teilt sich die App mit Character Fun (CharacterFun/Modelle).", fontSize = 13.sp)
                } else {
                    Text("Ohne „Zugriff auf alle Dateien“ liegen Bücher und Modelle nur in der App und sind bei einer Neuinstallation weg.", fontSize = 13.sp)
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }) { Text("Zugriff erlauben") }
                }

                Section("KI auf dem Tablet (Gemma)")
                Text("Springt automatisch ein, wenn der PC nicht erreichbar ist.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ModelCatalog.all.forEach { ModelRow(vm, it) }

                Section("KI auf dem PC (Ollama)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ollama bevorzugen", modifier = Modifier.weight(1f))
                    Switch(checked = vm.preferOllama, onCheckedChange = { vm.changePreferOllama(it) })
                }
                if (vm.preferOllama) {
                    HostPort(vm.ollamaHost, vm.ollamaPort, { vm.changeOllamaHost(it) }, { vm.changeOllamaPort(it) })
                    StatusRow(vm.ollamaStatus) { vm.testOllama() }
                    if (vm.ollamaModels.isNotEmpty()) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { open = true }) { Text("Modell: ${vm.ollamaModel.ifBlank { "wählen" }}") }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                vm.ollamaModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("${m.name}  (%.1f GB)".format(m.sizeBytes / 1e9)) },
                                        onClick = { open = false; vm.selectOllamaModel(m.name) },
                                    )
                                }
                            }
                        }
                    }
                }

                Section("Bilder (ComfyUI auf dem PC)")
                HostPort(vm.comfyHost, vm.comfyPort, { vm.changeComfyHost(it) }, { vm.changeComfyPort(it) })
                StatusRow(vm.comfyStatus) { vm.testComfy() }
                Text("Ist der PC aus, werden alle Bild-Knöpfe ausgegraut.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = vm.basePositive, onValueChange = { vm.changeBasePositive(it) }, label = { Text("Basis positiv (steht immer vorne)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vm.baseNegative, onValueChange = { vm.changeBaseNegative(it) }, label = { Text("Basis negativ") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Dahinter setzt die App das Trigger-Tag des Stils (aus dem Fundus) und die Bausteine.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.canImportCf) {
                    var msg by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { val n = vm.importFundusFromCf(); msg = if (n > 0) "✓ $n Teile übernommen" else "Nichts Neues gefunden" }) { Text("Kleidung & Stile aus Character Fun übernehmen") }
                        Spacer(Modifier.width(12.dp))
                        Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Section("KI testen")
                Text("Aktiv: ${vm.aiLabel}", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.testAi() }, enabled = !vm.aiTesting) { Text(if (vm.aiTesting) "Schreibt …" else "Ersten Satz schreiben lassen") }
                }
                if (vm.aiTest.isNotBlank()) {
                    Text(vm.aiTest, fontStyle = FontStyle.Italic)
                }
                val s = vm.engineState
                if (s is EngineState.Loading) Text("${s.title} wird geladen …", fontSize = 12.sp)
                if (s is EngineState.Ready) Text("${s.model.title} geladen in %.1f s".format(s.seconds), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun HostPort(host: String, port: Int, onHost: (String) -> Unit, onPort: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = host, onValueChange = onHost, label = { Text("IP-Adresse") }, placeholder = { Text("192.168.178.41") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = port.toString(),
            onValueChange = { v -> v.filter { it.isDigit() }.take(5).toIntOrNull()?.let(onPort) },
            label = { Text("Port") },
            singleLine = true,
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun StatusRow(status: PcStatus, onTest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onTest, enabled = status !is PcStatus.Testing) { Text(if (status is PcStatus.Testing) "Teste …" else "Verbindung testen") }
        Spacer(Modifier.width(12.dp))
        when (status) {
            is PcStatus.Ok -> Text("✓ ${status.info}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            is PcStatus.Failed -> Text(status.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

@Composable
private fun ModelRow(vm: AppViewModel, m: ModelInfo) {
    val state = vm.downloads[m.id]
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.selectedModel == m, onClick = { vm.selectModel(m) }, enabled = state == DownloadState.Done)
                Column(Modifier.weight(1f)) {
                    Text(m.title, fontWeight = FontWeight.SemiBold)
                    Text("${m.description} · %.1f GB".format(m.sizeBytes / 1e9), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (state) {
                    DownloadState.Done -> TextButton(onClick = { vm.deleteModel(m) }) { Text("Löschen") }
                    is DownloadState.Running -> TextButton(onClick = { vm.cancelDownload(m) }) { Text("Pause") }
                    else -> TextButton(onClick = { vm.download(m) }) { Text("Laden") }
                }
            }
            if (state is DownloadState.Running) {
                LinearProgressIndicator(progress = { if (state.total > 0) state.downloaded.toFloat() / state.total else 0f }, modifier = Modifier.fillMaxWidth())
                Text("%.0f von %.0f MB".format(state.downloaded / 1e6, state.total / 1e6), fontSize = 12.sp)
            }
            if (state is DownloadState.Failed) Text(state.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
