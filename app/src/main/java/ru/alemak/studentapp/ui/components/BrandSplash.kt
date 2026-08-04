package ru.alemak.studentapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.alemak.studentapp.R

/**
 * Compact centered logo on theme background.
 * Same updated university mark as on the home screen (kgta_logo / LogoAppHome).
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
            contentDescription = "КГТУ",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 168.dp, height = 80.dp),
        )
    }
}
