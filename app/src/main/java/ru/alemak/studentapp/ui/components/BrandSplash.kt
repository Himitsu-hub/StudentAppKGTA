package ru.alemak.studentapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.alemak.studentapp.R

/**
 * Splash: theme color + university logo (half of the previous oversized size).
 */
@Composable
fun BrandSplash(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (darkTheme) Color(0xFF0A1020) else Color(0xFF1A336C)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.kgta_logo),
            contentDescription = "КГТУ им. В.А. Дегтярева",
            contentScale = ContentScale.Fit,
            // Half of previous 0.96 / 0.50
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .fillMaxHeight(0.25f)
                .padding(horizontal = 8.dp),
        )
    }
}
