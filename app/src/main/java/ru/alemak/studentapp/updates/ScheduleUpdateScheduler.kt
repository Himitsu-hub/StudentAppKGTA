package ru.alemak.studentapp.updates

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ScheduleUpdateScheduler {
    /**
     * How often the app asks: «есть ли новое расписание?»
     * ~60s so students get a local notification quickly after admin upload
     * (true push would need FCM; this is best-effort polling).
     */
    const val INTERVAL_MS: Long = 60 * 1000L // 1 minute

    private const val REQUEST_CODE = 77201
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val app = context.applicationContext
        scheduleNext(app)
        // Immediate check after start (does not block UI)
        scope.launch {
            try {
                ScheduleUpdateChecker.check(app, notify = true)
            } catch (_: Exception) {
            }
            scheduleNext(app)
        }
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(app)

        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        // Exact so Doze does not stretch the 1-min poll into 9–15+ minutes
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            return
        } catch (_: SecurityException) {
            // fall through
        } catch (_: Exception) {
            // fall through
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (_: Exception) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
