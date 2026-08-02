package ru.alemak.studentapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.util.DateUtils

@Singleton
class ScheduleWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val scheduleRepository: ScheduleRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAsync() {
        scope.launch { updateNow() }
    }

    suspend fun updateNow() {
        val selection = userPreferences.selection.first()
        val week = DateUtils.getCurrentWeekType()
        var line1 = week
        var line2 = "Выберите группу в приложении"
        var line3 = ""

        if (!selection.group.isNullOrBlank()) {
            val result = runCatching {
                scheduleRepository.getSchedule(selection.course, selection.group, selection.subgroup)
            }.getOrNull()
            val lesson = result?.let { scheduleRepository.findNextLesson(it.schedule) }
            line1 = week
            if (lesson != null) {
                line2 = lesson.subject.ifBlank { "Пара" }
                val t = buildString {
                    if (lesson.time.isNotBlank()) append(lesson.time)
                    if (lesson.room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("каб. ${lesson.room}")
                    }
                }
                line3 = t.ifBlank { lesson.teacher }
            } else {
                line2 = "Сейчас пар нет"
                line3 = selection.group.orEmpty()
            }
        }

        ScheduleWidgetProvider.saveSnapshot(context, line1, line2, line3)
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
