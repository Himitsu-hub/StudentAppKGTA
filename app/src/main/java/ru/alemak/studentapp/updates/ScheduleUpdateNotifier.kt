package ru.alemak.studentapp.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.R
import ru.alemak.studentapp.StudentApp
import ru.alemak.studentapp.data.model.FacultyCatalog

object ScheduleUpdateNotifier {
    private const val NOTIFICATION_ID = 91001

    fun show(
        context: Context,
        faculty: String,
        course: Int,
    ) {
        val facShort = FacultyCatalog.shortName(faculty)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, MainActivity.ROUTE_SCHEDULE)
            putExtra(EXTRA_OPEN_SCHEDULE, true)
            putExtra(EXTRA_FACULTY, faculty)
            putExtra(EXTRA_COURSE, course)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + course + facShort.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, StudentApp.SCHEDULE_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Расписание обновлено")
            .setContentText("Новое расписание: $facShort, $course курс. Откройте приложение.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Загружен новый файл расписания для $facShort, $course курса. Откройте раздел «Расписание», чтобы увидеть актуальные пары."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID + course + facShort.hashCode(),
                notification,
            )
        }
    }

    /** Backward-compatible overload. */
    fun show(context: Context, course: Int) {
        show(context, FacultyCatalog.FAE, course)
    }

    const val EXTRA_OPEN_SCHEDULE = "open_schedule"
    const val EXTRA_FACULTY = "faculty"
    const val EXTRA_COURSE = "course"
}
