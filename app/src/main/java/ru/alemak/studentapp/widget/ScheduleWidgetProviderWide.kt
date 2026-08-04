package ru.alemak.studentapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import ru.alemak.studentapp.R

/** Wide 4×2 home-screen widget. */
class ScheduleWidgetProviderWide : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        triggerRefresh(context)
        ScheduleWidgetPainter.paint(
            context,
            appWidgetManager,
            appWidgetIds,
            R.layout.widget_schedule_wide,
            compact = false,
        )
    }

    override fun onEnabled(context: Context) {
        triggerRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == ACTION_REFRESH
        ) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: mgr.getAppWidgetIds(
                    android.content.ComponentName(context, ScheduleWidgetProviderWide::class.java),
                )
            if (ids.isNotEmpty()) {
                ScheduleWidgetPainter.paint(
                    context,
                    mgr,
                    ids,
                    R.layout.widget_schedule_wide,
                    compact = false,
                )
            }
        }
    }

    private fun triggerRefresh(context: Context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            ).widgetUpdater().updateAsync()
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.alemak.studentapp.widget.REFRESH_WIDE"
    }
}
