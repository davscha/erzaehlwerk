package de.davscha.erzaehlwerk.ui

import de.davscha.erzaehlwerk.Edition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** Bild, Platzhalter-Emoji oder Ladekreis mit Sekunden */
@Composable
fun PreviewImage(file: File?, emoji: String, paintingSeconds: Int?, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (file != null) AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(emoji, fontSize = 44.sp)
        if (paintingSeconds != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("$paintingSeconds s", fontSize = 12.sp)
                }
            }
        }
    }
}

/** Karte mit Bild, Titel und Untertitel (Figuren, Orte, Kleidung) */
@Composable
fun ItemCard(file: File?, emoji: String, title: String, subtitle: String, paintingSeconds: Int?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        PreviewImage(file, emoji, paintingSeconds, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Vorschau im Dialog mit „Vorschau malen“ */
@Composable
fun PaintBox(vm: AppViewModel, id: String, file: File?, emoji: String, canPaint: Boolean, onPaint: () -> Unit) {
    if (Edition.LITE) {
        // Lite malt nicht – ein Bild aus der Pro-Version wird trotzdem gezeigt
        if (file != null) PreviewImage(file, emoji, null, Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
        return
    }
    Row(verticalAlignment = Alignment.Top) {
        PreviewImage(file, emoji, vm.painting[id], Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column {
            OutlinedButton(onClick = onPaint, enabled = vm.comfyReady && canPaint && !vm.painting.containsKey(id)) {
                Icon(Icons.Default.Brush, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (file == null) "Vorschau malen" else "Neu malen")
            }
            if (!vm.comfyReady) Text("PC/ComfyUI nicht erreichbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            vm.paintError[id]?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** Fortschritt beim Video: Status, Leiste mit Standbildern, Fehler */
@Composable
fun VideoProgress(vm: AppViewModel) {
    if (vm.videoStatus == null && vm.videoError == null) return
    Column {
        vm.videoStatus?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(it, fontSize = 13.sp)
            }
        }
        if (vm.videoStatus != null && vm.videoFrames.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(vm.videoFrames.size) { i ->
                    PreviewImage(vm.image(vm.videoFrames[i]), "🎬", null, Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)))
                }
            }
        }
        vm.videoError?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
    }
}
