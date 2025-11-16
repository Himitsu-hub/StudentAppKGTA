package ru.alemak.studentapp.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ReminderReceiver", "=== RECEIVER ЗАПУЩЕН ===")

        if (context == null || intent == null) {
            Log.e("ReminderReceiver", "❌ Context или Intent null")
            return
        }

        val text = intent.getStringExtra("text") ?: "Напоминание"
        val reminderId = intent.getStringExtra("reminderId")

        Log.d("ReminderReceiver", "📢 Получено напоминание: $text")
        Log.d("ReminderReceiver", "🆔 ID: $reminderId")

        // Создаем канал если его нет
        createNotificationChannel(context)

        // Проверка разрешения для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e("ReminderReceiver", "❌ Нет разрешения на уведомления")
                return
            }
        }

        try {
            val openIntent = Intent(context, Class.forName("ru.alemak.studentapp.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("from_notification", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                Random.nextInt(),
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, "reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Напоминание")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Звук + вибрация
                .build()

            val notificationId = reminderId?.hashCode() ?: Random.nextInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)

            Log.d("ReminderReceiver", "✅ Уведомление показано: $text")

        } catch (e: Exception) {
            Log.e("ReminderReceiver", "❌ Ошибка при показе уведомления: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            try {
                val channel = NotificationChannel(
                    "reminders",
                    "Напоминания",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Канал для напоминаний"
                    enableLights(true)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
                Log.d("ReminderReceiver", "✅ Канал уведомлений создан")
            } catch (e: Exception) {
                Log.d("ReminderReceiver", "ℹ️ Канал уже существует")
            }
        }
    }
}