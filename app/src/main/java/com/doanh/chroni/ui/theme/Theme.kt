package com.doanh.chroni.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Identité Chroni : bleu nuit instrument, cyan (départ) et ambre (impact). */
object ChroniColors {
    val Night = Color(0xFF0B1620)
    val NightSurface = Color(0xFF13222E)
    val NightSurface2 = Color(0xFF1B2F3D)
    val Cyan = Color(0xFF2EC4B6)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF5D5D)
    val Paper = Color(0xFFF4F7F9)
    val Ink = Color(0xFF0B1620)

    /** Couleur du repère « départ ». */
    val Departure = Cyan
    /** Couleur du repère « impact ». */
    val Impact = Amber
}

private val Dark = darkColorScheme(
    primary = ChroniColors.Cyan,
    onPrimary = ChroniColors.Night,
    secondary = ChroniColors.Amber,
    onSecondary = ChroniColors.Night,
    background = ChroniColors.Night,
    onBackground = Color(0xFFE6EEF3),
    surface = ChroniColors.NightSurface,
    onSurface = Color(0xFFE6EEF3),
    surfaceVariant = ChroniColors.NightSurface2,
    onSurfaceVariant = Color(0xFFA9BAC6),
    surfaceContainer = ChroniColors.NightSurface,
    surfaceContainerHigh = ChroniColors.NightSurface2,
    error = ChroniColors.Red,
    outline = Color(0xFF3A5163),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    secondary = Color(0xFFB87400),
    onSecondary = Color.White,
    background = ChroniColors.Paper,
    onBackground = ChroniColors.Ink,
    surface = Color.White,
    onSurface = ChroniColors.Ink,
    surfaceVariant = Color(0xFFE3EAEF),
    onSurfaceVariant = Color(0xFF4A5C69),
    error = Color(0xFFC62828),
    outline = Color(0xFFB0BEC8),
)

@Composable
fun ChroniTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
