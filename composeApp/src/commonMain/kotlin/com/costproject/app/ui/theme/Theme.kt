package com.costproject.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
        typography = CostProjectTypography,
        content = content
    )
}
