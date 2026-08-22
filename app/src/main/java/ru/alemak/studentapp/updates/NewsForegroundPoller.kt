package ru.alemak.studentapp.updates

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * While the app process is in the foreground (any activity started),
 * poll news every ~30s so notifications are near-instant without waiting
 * for the next AlarmManager tick.
 */
object NewsForegroundPoller {
    private const val INTERVAL_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = scope.launch {
                        // Immediate check on foreground
                        runCatching { NewsUpdateChecker.check(app, notify = true) }
                        while (isActive) {
                            delay(INTERVAL_MS)
                            runCatching { NewsUpdateChecker.check(app, notify = true) }
                        }
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = null
                    // Re-arm background chain as soon as user leaves
                    NewsUpdateScheduler.scheduleNext(app)
                }
            },
        )
    }
}
