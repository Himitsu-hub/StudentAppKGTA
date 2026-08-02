package ru.alemak.studentapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.R

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val (w, s, d) = loadSnapshot(context)
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            views.setTextViewText(R.id.widget_week, w)
            views.setTextViewText(R.id.widget_subject, s)
            views.setTextViewText(R.id.widget_details, d)

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val PREFS = "schedule_widget"
        private const val K_WEEK = "week"
        private const val K_SUBJ = "subject"
        private const val K_DET = "details"

        fun saveSnapshot(context: Context, week: String, subject: String, details: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_WEEK, week)
                .putString(K_SUBJ, subject)
                .putString(K_DET, details)
                .apply()
        }

        fun loadSnapshot(context: Context): Triple<String, String, String> {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Triple(
                p.getString(K_WEEK, "—") ?: "—",
                p.getString(K_SUBJ, "Откройте приложение") ?: "Откройте приложение",
                p.getString(K_DET, "") ?: "",
            )
        }
    }
}
