package com.costproject.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BluePrimary = Color(0xFF1565C0)
val DarkBluePrimary = Color(0xFF448AFF)
val TealAccent = Color(0xFF00BFA5)
val LightBackground = Color(0xFFF5F7FA)
val DarkBackground = Color(0xFF121212)
val SurfaceColor = Color(0xFFFFFFFF)
val DarkSurface = Color(0xFF1E1E1E)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    secondary = TealAccent,
    background = LightBackground,
    surface = SurfaceColor
)

private val DarkColors = darkColorScheme(
    primary = DarkBluePrimary,
    secondary = TealAccent,
    background = DarkBackground,
    surface = DarkSurface
)

@Composable
fun CostProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}