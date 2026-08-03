package ru.alemak.studentapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.alemak.studentapp.ui.navigation.AppNavigation
import ru.alemak.studentapp.ui.navigation.ThemeViewModel
import ru.alemak.studentapp.ui.theme.StudentAppTheme
import ru.alemak.studentapp.ui.theme.ThemePrefs
import ru.alemak.studentapp.updates.ScheduleUpdateScheduler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = ThemePrefs.isDark(this)
        setTheme(
            if (dark) R.style.Theme_StudentApp_SplashDark
            else R.style.Theme_StudentApp_Splash,
        )
        super.onCreate(savedInstanceState)

        val bg = if (dark) 0xFF0A1020.toInt() else 0xFF1A336C.toInt()
        window.setBackgroundDrawable(bg.toDrawable())
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = bg
            window.navigationBarColor = bg
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(bg)

        setTheme(
            if (dark) R.style.Theme_StudentApp_Dark
            else R.style.Theme_StudentApp,
        )

        enableEdgeToEdge()
        ensureNotificationPermission()
        ScheduleUpdateScheduler.schedule(this)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val darkCompose by themeViewModel.darkTheme.collectAsStateWithLifecycle()
            StudentAppTheme(darkTheme = darkCompose) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (darkCompose) Color(0xFF0A1020) else Color(0xFF1A336C),
                ) {
                    AppNavigation(themeViewModel = themeViewModel)
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
