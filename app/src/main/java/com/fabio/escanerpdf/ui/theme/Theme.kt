package com.fabio.escanerpdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    primaryContainer = Verde,
    onPrimaryContainer = Color.White,
    secondaryContainer = Verde,
    onSecondaryContainer = Color.White,
    outline = Verde,
)

private val LightColorScheme = lightColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    primaryContainer = Verde,
    onPrimaryContainer = Color.White,
    secondaryContainer = Verde,
    onSecondaryContainer = Color.White,
    outline = Verde,
)

@Composable
fun EscanerPDFTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
