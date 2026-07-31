package ru.alemak.studentapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StudentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createReminderChannel()
    }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            REMINDERS_CHANNEL_ID,
            "Напоминания",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о ваших напоминаниях"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REMINDERS_CHANNEL_ID = "reminders"
    }
}
