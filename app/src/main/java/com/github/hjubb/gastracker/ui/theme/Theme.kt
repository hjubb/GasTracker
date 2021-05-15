package com.github.hjubb.gastracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = Color(0xfff35258),
    primaryVariant = Color(0xffff8585),
    secondary = Color(0xffffc285),
    onPrimary = Color.White,
    onSecondary = Color.Black,
)

private val LightColorPalette = lightColors(
    primary = Color(0xffff8585),
    primaryVariant = Color(0xfff35258),
    secondary = Color(0xffffc285),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    /* Other default colors to override
    background = Color.White,
    surface = Color.White,

    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun GasTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}