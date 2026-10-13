package com.tv.wnotes.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Monochrome palette: the app only uses black, white and greys.
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Grey950 = Color(0xFF0D0D0D)
val Grey900 = Color(0xFF161616)
val Grey850 = Color(0xFF1F1F1F)
val Grey800 = Color(0xFF2A2A2A)
val Grey700 = Color(0xFF3D3D3D)
val Grey600 = Color(0xFF5C5C5C)
val Grey500 = Color(0xFF7A7A7A)
val Grey400 = Color(0xFF9E9E9E)
val Grey300 = Color(0xFFBDBDBD)
val Grey200 = Color(0xFFDADADA)
val Grey150 = Color(0xFFE6E6E6)
val Grey100 = Color(0xFFEFEFEF)
val Grey50 = Color(0xFFF7F7F7)

// Legacy brand names, kept so existing references resolve to greys.
val Iris = Grey800
val IrisLight = Grey200
val IrisDeep = Black
val Coral = Grey600
val CoralLight = Grey300
val Lagoon = Grey500
val LagoonLight = Grey400

// Light surfaces
val Paper = White
val PaperDim = Grey100
val Ink = Black
val InkMuted = Grey600

// Dark surfaces
val Midnight = Black
val MidnightHigh = Grey850
val Moon = White
val MoonMuted = Grey400

val BrandGradient = Brush.linearGradient(listOf(Grey900, Grey500, Grey300))

/** Accent + soft tint pairs used to give each note card its own personality. */
data class NoteTint(val accent: Color, val light: Color, val dark: Color)

val NoteTints = listOf(
    NoteTint(Grey900, Grey100, Grey850),
    NoteTint(Grey700, Grey150, Grey800),
    NoteTint(Grey500, Grey50, Grey900),
    NoteTint(Grey600, Grey200, Grey700),
)

fun noteTintFor(id: Long): NoteTint = NoteTints[Math.floorMod(id, NoteTints.size.toLong()).toInt()]
