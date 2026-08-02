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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.alemak.studentapp.ui.navigation.AppNavigation
import ru.alemak.studentapp.ui.navigation.ThemeViewModel
import ru.alemak.studentapp.ui.theme.StudentAppTheme
import ru.alemak.studentapp.updates.ScheduleUpdateScheduler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StudentApp)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        ScheduleUpdateScheduler.schedule(this)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val dark by themeViewModel.darkTheme.collectAsStateWithLifecycle()
            StudentAppTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
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
