package org.wexcoder.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WexDark = darkColorScheme(
    primary = Color(0xFFD97757),          // Warm terracotta accent
    onPrimary = Color(0xFF2A1107),
    background = Color(0xFF1B1B1D),
    onBackground = Color(0xFFE7E7E7),
    surface = Color(0xFF242427),
    onSurface = Color(0xFFE7E7E7),
    surfaceVariant = Color(0xFF313135),
    onSurfaceVariant = Color(0xFFB7B7BC),
    secondary = Color(0xFF8A8A90),
    outline = Color(0xFF45454A),
)

@Composable
fun WexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WexDark, content = content)
}
