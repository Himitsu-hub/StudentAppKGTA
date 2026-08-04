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
import ru.alemak.studentapp.widget.ScheduleWidgetStore.Snapshot

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
        val weekType = DateUtils.getCurrentWeekType()
        val weekLine = "Неделя: $weekType"

        val snap = if (selection.group.isNullOrBlank()) {
            Snapshot(
                weekLine = weekLine,
                groupLine = "",
                label = "Следующая пара",
                subject = "Группа не выбрана",
                details = "Откройте приложение и выберите курс / группу",
                hint = "Нажмите, чтобы настроить",
            )
        } else {
            val groupLabel = buildString {
                append(selection.group)
                if (!selection.subgroup.isNullOrBlank()) {
                    append(" · ")
                    append(selection.subgroup)
                }
            }
            val result = runCatching {
                scheduleRepository.getSchedule(
                    selection.course,
                    selection.group!!,
                    selection.subgroup,
                )
            }.getOrNull()

            val next = result?.let { scheduleRepository.findNextLessonInfo(it.schedule) }
            if (next != null) {
                val lesson = next.lesson
                val dayLabel = if (next.isToday) "сегодня" else shortDay(next.dayName)
                val details = buildString {
                    if (lesson.time.isNotBlank()) append(lesson.time)
                    if (lesson.room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("каб. ${lesson.room}")
                    }
                    if (isNotEmpty()) append(" · ")
                    append(dayLabel)
                    if (lesson.type.isNotBlank() &&
                        !lesson.type.equals("праздник", ignoreCase = true)
                    ) {
                        append(" · ")
                        append(lesson.type)
                    }
                }
                val teacherLine = lesson.teacher.trim()
                Snapshot(
                    weekLine = weekLine,
                    groupLine = groupLabel,
                    label = if (next.isToday) "Следующая пара" else "Ближайшая пара",
                    subject = lesson.subject.ifBlank { "Пара" },
                    details = listOf(details, teacherLine)
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                    hint = "Нажмите, чтобы открыть расписание",
                )
            } else {
                Snapshot(
                    weekLine = weekLine,
                    groupLine = groupLabel,
                    label = "Следующая пара",
                    subject = "Сейчас пар нет",
                    details = if (result?.isOffline == true) {
                        "Нет данных · проверьте сеть в приложении"
                    } else {
                        "На этой неделе занятий не найдено"
                    },
                    hint = "Нажмите, чтобы открыть приложение",
                )
            }
        }

        ScheduleWidgetStore.save(context, snap)
        notifyProviders()
    }

    private fun notifyProviders() {
        val mgr = AppWidgetManager.getInstance(context)
        listOf(
            ScheduleWidgetProviderWide::class.java,
            ScheduleWidgetProviderSquare::class.java,
        ).forEach { cls ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    private fun shortDay(dayName: String): String = when {
        dayName.startsWith("Пон", ignoreCase = true) -> "пн"
        dayName.startsWith("Вто", ignoreCase = true) -> "вт"
        dayName.startsWith("Сре", ignoreCase = true) -> "ср"
        dayName.startsWith("Чет", ignoreCase = true) -> "чт"
        dayName.startsWith("Пят", ignoreCase = true) -> "пт"
        dayName.startsWith("Суб", ignoreCase = true) -> "сб"
        dayName.startsWith("Вос", ignoreCase = true) -> "вс"
        else -> dayName.take(2).lowercase()
    }
}
