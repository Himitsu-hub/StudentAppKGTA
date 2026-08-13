package ru.alemak.studentapp.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager every ~15 minutes (and after boot) to poll schedule versions.
 */
class ScheduleUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                ScheduleUpdateChecker.check(app, notify = true)
                // News has its own faster scheduler; still check here as a backup.
                NewsUpdateChecker.check(app, notify = true)
            } finally {
                ScheduleUpdateScheduler.scheduleNext(app)
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
