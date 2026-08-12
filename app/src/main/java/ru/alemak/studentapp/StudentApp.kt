package ru.alemak.studentapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import ru.alemak.studentapp.ui.theme.ThemePrefs
import ru.alemak.studentapp.updates.ScheduleUpdateScheduler

@HiltAndroidApp
class StudentApp : Application() {

    override fun attachBaseContext(base: Context) {
        ThemePrefs.applyNightModeFromPrefs(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applyNightModeFromPrefs(this)
        createReminderChannel()
        createScheduleUpdatesChannel()
        createNewsUpdatesChannel()
        ScheduleUpdateScheduler.schedule(this)
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

    private fun createScheduleUpdatesChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            SCHEDULE_UPDATES_CHANNEL_ID,
            "Обновления расписания",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Когда на сервер загрузили новый файл расписания"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createNewsUpdatesChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NEWS_UPDATES_CHANNEL_ID,
            "Новости КГТУ",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Когда на сайте вуза появляется новая новость"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REMINDERS_CHANNEL_ID = "reminders"
        const val SCHEDULE_UPDATES_CHANNEL_ID = "schedule_updates"
        const val NEWS_UPDATES_CHANNEL_ID = "news_updates"
    }
}
