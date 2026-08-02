package ru.alemak.studentapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light theme keeps classic KGTA look: blue screens, white cards.
// (Home/Teachers/Reminders/Campus use BlueKGTA as page background.)
private val LightColors = lightColorScheme(
    primary = BlueKGTA,
    onPrimary = Color.White,
    primaryContainer = BlueKGTA,
    onPrimaryContainer = Color.White,
    secondary = AccentGold,
    onSecondary = BlueKGTADark,
    background = BlueKGTA,
    onBackground = Color.White,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFCCD5E0),
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7B9FD4),
    onPrimary = DarkNavy,
    primaryContainer = DarkButton,
    onPrimaryContainer = DarkOnSurface,
    secondary = AccentGold,
    onSecondary = DarkNavy,
    background = DarkNavy,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkButtonBorder,
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
