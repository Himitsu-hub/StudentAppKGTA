package ru.alemak.studentapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BlueKGTA,
    onPrimary = Color.White,
    primaryContainer = BlueKGTALight,
    onPrimaryContainer = Color.White,
    secondary = AccentGold,
    onSecondary = BlueKGTADark,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE8ECF2),
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BlueKGTALight,
    onPrimary = Color.White,
    primaryContainer = BlueKGTA,
    onPrimaryContainer = Color.White,
    secondary = AccentGold,
    onSecondary = Color.Black,
    background = BlueKGTADark,
    onBackground = Color.White,
    surface = Color(0xFF162447),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E2F55),
    onSurfaceVariant = Color(0xFFB8C2D4),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

@Composable
fun StudentAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
