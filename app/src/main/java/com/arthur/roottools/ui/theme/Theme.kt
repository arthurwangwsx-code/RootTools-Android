package com.arthur.roottools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RootToolsColors = darkColorScheme(
    primary = Color(0xFFA9F5D0),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF174D3A),
    onPrimaryContainer = Color(0xFFC9FFE5),
    secondary = Color(0xFFB9C8FF),
    onSecondary = Color(0xFF13275D),
    secondaryContainer = Color(0xFF283C72),
    onSecondaryContainer = Color(0xFFDCE2FF),
    tertiary = Color(0xFFFFC56F),
    onTertiary = Color(0xFF432C00),
    background = Color(0xFF0C1114),
    onBackground = Color(0xFFE5ECE8),
    surface = Color(0xFF11181C),
    onSurface = Color(0xFFE5ECE8),
    surfaceVariant = Color(0xFF1A2428),
    onSurfaceVariant = Color(0xFFBAC8C3),
    outline = Color(0xFF53615D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun RootToolsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RootToolsColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

