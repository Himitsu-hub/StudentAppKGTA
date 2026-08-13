package ru.alemak.studentapp.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After device reboot, re-arm schedule/news poll alarms so closed-app
 * notifications keep working without opening the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        ScheduleUpdateScheduler.schedule(app)
        NewsUpdateScheduler.schedule(app)
    }
}
