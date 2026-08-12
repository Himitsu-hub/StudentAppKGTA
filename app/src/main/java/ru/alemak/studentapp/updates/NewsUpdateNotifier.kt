package ru.alemak.studentapp.updates

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.R
import ru.alemak.studentapp.StudentApp

object NewsUpdateNotifier {
    private const val NOTIFICATION_ID = 91001

    fun show(context: Context, title: String, date: String = "") {
        val app = context.applicationContext
        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = buildString {
            if (date.isNotBlank()) append(date).append(" · ")
            append(title.ifBlank { "Откройте приложение, чтобы прочитать." })
        }
        val notification = NotificationCompat.Builder(app, StudentApp.NEWS_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Новая новость КГТУ")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
    }
}
