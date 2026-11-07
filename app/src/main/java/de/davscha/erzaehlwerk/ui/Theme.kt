package de.davscha.erzaehlwerk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dunkles Design wie bei character.ai */
private val Colors = darkColorScheme(
    primary = Color(0xFFE3A857),
    onPrimary = Color(0xFF1A1714),
    secondary = Color(0xFFB79CFF),
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF231F1B),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF2E2924),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ErzaehlwerkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
