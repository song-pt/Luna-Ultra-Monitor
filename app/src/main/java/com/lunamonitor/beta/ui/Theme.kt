package com.lunamonitor.beta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MonitorColors = darkColorScheme(
    primary = Color(0xFFD8D8D4),
    onPrimary = Color(0xFF111212),
    secondary = Color(0xFFA7AAA6),
    background = Color(0xFF080909),
    onBackground = Color(0xFFE9EAE6),
    surface = Color(0xFF111313),
    surfaceVariant = Color(0xFF1A1C1C),
    onSurface = Color(0xFFE9EAE6),
    onSurfaceVariant = Color(0xFFAAACA8),
    outline = Color(0xFF373A39),
    error = Color(0xFFE7A5A0),
)

@Composable
fun LunaMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonitorColors) {
        Surface(color = MonitorColors.background, contentColor = MonitorColors.onBackground, content = content)
    }
}
