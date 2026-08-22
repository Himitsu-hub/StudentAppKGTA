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
 * Polls news fingerprint so local notifications feel near-real-time.
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] (not inexact
 * setAndAllowWhileIdle) — otherwise Doze batches polls to ≥9–15 minutes
 * and users only see news when they open the app.
 *
 * WorkManager ([NewsUpdateWorker]) is a backup when exact alarms are deferred.
 */
object NewsUpdateScheduler {
    /** Target interval while the device is awake / recently used. */
    const val INTERVAL_MS: Long = 2 * 60 * 1000L // 2 minutes

    private const val REQUEST_CODE = 77211
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val app = context.applicationContext
        NewsUpdateWorker.enqueue(app)
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

        // Prefer exact alarms so Doze does not stretch a "2 min" poll into 15+.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pi,
                )
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            return
        } catch (_: SecurityException) {
            // No SCHEDULE_EXACT_ALARM — fall through
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
