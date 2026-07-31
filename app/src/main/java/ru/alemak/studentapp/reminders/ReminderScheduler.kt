package ru.alemak.studentapp.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.data.model.Reminder

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(reminder: Reminder) {
        if (reminder.dateTimeMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT)

        // setAlarmClock is the most reliable exact API on modern Android
        // (shows in status bar, wakes device, minimal delay)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    reminder.id.hashCode() + 17,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val info = AlarmManager.AlarmClockInfo(reminder.dateTimeMillis, showIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
                return
            }
        } catch (_: Exception) {
            // fall through
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.dateTimeMillis,
                    pendingIntent,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.dateTimeMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // No exact-alarm permission — open settings once, use inexact as last resort
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.dateTimeMillis,
                pendingIntent,
            )
        }
    }

    fun canScheduleExact(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun cancel(reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val existing = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intentFor(reminder),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(reminder: Reminder, flag: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intentFor(reminder),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )!!
    }

    private fun intentFor(reminder: Reminder): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = "REMINDER_ACTION_${reminder.id}"
            data = "reminder://${reminder.id}".toUri()
            putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
        }
}
