package ru.alemak.studentapp.updates

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fast poll for news fingerprint (~2 min) so local notifications feel near-real-time.
 * Schedule Excel checks stay on the slower ScheduleUpdateScheduler.
 */
object NewsUpdateScheduler {
    /** How often the app asks: «есть ли новая новость?» */
    const val INTERVAL_MS: Long = 2 * 60 * 1000L // 2 minutes

    private const val REQUEST_CODE = 77211
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val app = context.applicationContext
        scheduleNext(app)
        scope.launch {
            try {
                NewsUpdateChecker.check(app, notify = true)
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NewsUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class NewsUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                NewsUpdateChecker.check(app, notify = true)
            } finally {
                NewsUpdateScheduler.scheduleNext(app)
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
