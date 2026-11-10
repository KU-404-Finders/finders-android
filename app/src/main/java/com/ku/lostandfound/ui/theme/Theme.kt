package com.ku.lostandfound.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// KUfinders is designed around bright surfaces. Keep a consistent light palette
// even when the phone is using dark mode, until all screens support true dark colors.
private val FindersColorScheme = lightColorScheme(
    primary = Color(0xFF1B6425),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F2E9),
    onPrimaryContainer = Color(0xFF14481E),
    secondary = Color(0xFF477C52),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF202A22),
    surface = Color.White,
    onSurface = Color(0xFF202A22),
    surfaceVariant = Color(0xFFF3F7F4),
    onSurfaceVariant = Color(0xFF506456),
    outline = Color(0xFF9AAF9E),
    error = Color(0xFFC43C3C),
    onError = Color.White,
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun KUfindersTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FindersColorScheme,
        typography = Typography,
        content = content,
    )
}
