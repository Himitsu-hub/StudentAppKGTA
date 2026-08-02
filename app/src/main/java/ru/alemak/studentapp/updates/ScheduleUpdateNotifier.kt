package ru.alemak.studentapp.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.R
import ru.alemak.studentapp.StudentApp

object ScheduleUpdateNotifier {
    private const val NOTIFICATION_ID = 91001

    fun show(context: Context, course: Int) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SCHEDULE, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, StudentApp.SCHEDULE_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Расписание обновлено")
            .setContentText("На сервере новое расписание для $course курса. Откройте приложение.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Загружен новый файл расписания для $course курса. Откройте раздел «Расписание», чтобы увидеть актуальные пары."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + course, notification)
        }
    }

    const val EXTRA_OPEN_SCHEDULE = "open_schedule"
}
