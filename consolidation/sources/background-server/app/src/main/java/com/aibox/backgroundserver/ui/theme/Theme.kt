package com.aibox.backgroundserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF2457D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E9FF),
    onPrimaryContainer = Color(0xFF0A2B71),
    secondary = Color(0xFF52606F),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
    outline = Color(0xFFD9DCE2),
)

@Composable
fun BackgroundServerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
