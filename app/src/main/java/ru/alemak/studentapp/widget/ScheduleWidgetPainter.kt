package ru.alemak.studentapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import ru.alemak.studentapp.MainActivity
import ru.alemak.studentapp.R

object ScheduleWidgetPainter {

    fun paint(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        layoutId: Int,
        compact: Boolean,
    ) {
        val snap = ScheduleWidgetStore.load(context)
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutId)
            if (compact) {
                // Square: shorter week text
                val shortWeek = snap.weekLine
                    .removePrefix("Неделя: ")
                    .ifBlank { snap.weekLine }
                views.setTextViewText(R.id.widget_week, shortWeek)
                views.setTextViewText(R.id.widget_group, snap.groupLine)
                views.setViewVisibility(
                    R.id.widget_group,
                    if (snap.groupLine.isBlank()) View.GONE else View.VISIBLE,
                )
                views.setTextViewText(
                    R.id.widget_label,
                    if (snap.label.contains("Ближайш", ignoreCase = true)) "Ближ. пара"
                    else "След. пара",
                )
                // One line details for square
                val detailsOneLine = snap.details
                    .replace("\n", " · ")
                    .replace(Regex("\\s*·\\s*·\\s*"), " · ")
                views.setTextViewText(R.id.widget_subject, snap.subject)
                views.setTextViewText(R.id.widget_details, detailsOneLine)
                views.setViewVisibility(R.id.widget_hint, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_week, snap.weekLine)
                views.setTextViewText(R.id.widget_group, snap.groupLine)
                views.setTextViewText(R.id.widget_label, snap.label)
                views.setTextViewText(R.id.widget_subject, snap.subject)
                views.setTextViewText(R.id.widget_details, snap.details)
                views.setTextViewText(R.id.widget_hint, snap.hint)
            }

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                layoutId, // unique request code per layout
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
