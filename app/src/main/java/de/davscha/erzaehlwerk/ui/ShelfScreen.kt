package de.davscha.erzaehlwerk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.erzaehlwerk.data.Book

/** Bücherregal: alle Romane, neuester zuerst. */
@Composable
fun ShelfScreen(vm: AppViewModel) {
    var newBook by remember { mutableStateOf(false) }
    if (newBook) NewBookDialog(vm) { newBook = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newBook = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Neuer Roman") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Erzählwerk", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.primary)
                    Text(vm.aiLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                TextButton(onClick = { vm.navigate(Screen.FundusPage) }) {
                    Text("🎭")
                    Spacer(Modifier.width(6.dp))
                    Text("Fundus")
                }
                TextButton(onClick = { vm.navigate(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Einstellungen")
                }
            }
            if (!vm.sharedStorage) {
                Text(
                    "Tipp: In den Einstellungen „Zugriff auf alle Dateien“ erlauben – dann überleben deine Bücher eine Neuinstallation, und die Modelle von Character Fun werden mitbenutzt.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            if (vm.books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 64.sp)
                        Text("Dein Regal ist noch leer.", fontSize = 18.sp)
                        Text("Tippe auf „Neuer Roman“ und erzähl, worum es gehen soll.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(vm.books, key = { it.id }) { b -> BookCover(b) { vm.navigate(Screen.BookPage(b.id)) } }
                }
            }
        }
    }
}

/** Buchrücken-Karte im Regal */
@Composable
private fun BookCover(b: Book, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.7f)
                .background(Brush.verticalGradient(listOf(Color(0xFF5B3A1E), Color(0xFF2E1D10)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(14.dp)) {
                Text(b.emoji, fontSize = 48.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    b.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFF3E2C3),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (b.genre.isNotBlank()) Text(b.genre, fontSize = 12.sp, color = Color(0xFFD9BE8F), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewBookDialog(vm: AppViewModel, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Neuer Roman") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titel (darf auch später kommen)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Genre", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Book.genres.forEach { g ->
                        FilterChip(selected = genre == g, onClick = { genre = if (genre == g) "" else g }, label = { Text("${Book.emojis[g] ?: ""} $g") })
                    }
                }
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it },
                    label = { Text("Worum soll es gehen?") },
                    placeholder = { Text("z. B. Eine junge Uhrmacherin findet in London eine Uhr, die die Zeit anhält …") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = vm.createBook(title, idea, genre)
                onClose()
                vm.navigate(Screen.BookPage(b.id))
            }, enabled = title.isNotBlank() || idea.isNotBlank()) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}
