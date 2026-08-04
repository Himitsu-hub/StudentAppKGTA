package ru.alemak.studentapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.delay
import ru.alemak.studentapp.ui.components.BrandSplash
import ru.alemak.studentapp.ui.navigation.AppNavigation
import ru.alemak.studentapp.ui.navigation.ThemeViewModel
import ru.alemak.studentapp.ui.theme.StudentAppTheme
import ru.alemak.studentapp.ui.theme.ThemePrefs
import ru.alemak.studentapp.updates.ScheduleUpdateScheduler
import ru.alemak.studentapp.widget.ScheduleWidgetUpdater

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var widgetUpdater: ScheduleWidgetUpdater

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional */ }

    /** Hold system splash until Compose shows the same logo+name screen. */
    private val keepSplash = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = ThemePrefs.isDark(this)
        val bgColor = if (dark) 0xFF0A1020.toInt() else 0xFF1A336C.toInt()

        // Theme with logo+name windowBackground BEFORE system splash is configured
        setTheme(
            if (dark) R.style.Theme_StudentApp_SplashDark
            else R.style.Theme_StudentApp_Splash,
        )
        // Empty system icon → no separate "big emblem only" first screen
        installSplashScreen().setKeepOnScreenCondition { keepSplash.get() }
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(
            if (dark) R.drawable.splash_background_dark
            else R.drawable.splash_background,
        )
        window.decorView.setBackgroundColor(bgColor)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        enableEdgeToEdge()
        ensureNotificationPermission()
        ScheduleUpdateScheduler.schedule(this)
        widgetUpdater.updateAsync()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val darkCompose by themeViewModel.darkTheme.collectAsStateWithLifecycle()
            val prefsReady by themeViewModel.prefsReady.collectAsStateWithLifecycle()

            val splashDark = remember { dark }
            var showBrandSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                // System splash already shows same logo+name; hand off to Compose
                keepSplash.set(false)
            }

            LaunchedEffect(prefsReady) {
                if (prefsReady) {
                    delay(550)
                    showBrandSplash = false
                }
            }

            StudentAppTheme(darkTheme = darkCompose) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (prefsReady) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (darkCompose) Color(0xFF0A1020) else Color(0xFF1A336C),
                        ) {
                            AppNavigation(themeViewModel = themeViewModel)
                        }
                    }

                    AnimatedVisibility(
                        visible = showBrandSplash,
                        exit = fadeOut(tween(250)),
                    ) {
                        BrandSplash(darkTheme = splashDark)
                    }
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
