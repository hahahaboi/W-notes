package com.tv.wnotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Dark mode: black background, white text.
private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Grey800,
    onPrimaryContainer = White,
    secondary = Grey300,
    onSecondary = Black,
    secondaryContainer = Grey850,
    onSecondaryContainer = Grey100,
    tertiary = Grey400,
    onTertiary = Black,
    tertiaryContainer = Grey700,
    onTertiaryContainer = White,
    error = Grey200,
    onError = Black,
    errorContainer = Grey700,
    onErrorContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Grey850,
    onSurfaceVariant = Grey400,
    surfaceTint = Grey500,
    inverseSurface = White,
    inverseOnSurface = Black,
    inversePrimary = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Grey950,
    surfaceContainer = Grey900,
    surfaceContainerHigh = Grey850,
    surfaceContainerHighest = Grey800,
    outline = Grey500,
    outlineVariant = Grey800,
    scrim = Black,
)

// Bright mode: white background, black text.
private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Grey150,
    onPrimaryContainer = Black,
    secondary = Grey700,
    onSecondary = White,
    secondaryContainer = Grey100,
    onSecondaryContainer = Grey900,
    tertiary = Grey600,
    onTertiary = White,
    tertiaryContainer = Grey200,
    onTertiaryContainer = Black,
    error = Grey850,
    onError = White,
    errorContainer = Grey200,
    onErrorContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,
    surfaceTint = Grey500,
    inverseSurface = Black,
    inverseOnSurface = White,
    inversePrimary = White,
    surfaceContainerLowest = White,
    surfaceContainerLow = Grey50,
    surfaceContainer = Grey100,
    surfaceContainerHigh = Grey150,
    surfaceContainerHighest = Grey200,
    outline = Grey500,
    outlineVariant = Grey200,
    scrim = Black,
)

@Composable
fun WNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
