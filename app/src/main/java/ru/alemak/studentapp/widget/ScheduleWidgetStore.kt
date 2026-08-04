package ru.alemak.studentapp.widget

import android.content.Context

object ScheduleWidgetStore {
    private const val PREFS = "schedule_widget"
    private const val K_WEEK = "week"
    private const val K_GROUP = "group"
    private const val K_LABEL = "label"
    private const val K_SUBJ = "subject"
    private const val K_DET = "details"
    private const val K_HINT = "hint"

    data class Snapshot(
        val weekLine: String,
        val groupLine: String,
        val label: String,
        val subject: String,
        val details: String,
        val hint: String,
    )

    fun save(context: Context, snap: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_WEEK, snap.weekLine)
            .putString(K_GROUP, snap.groupLine)
            .putString(K_LABEL, snap.label)
            .putString(K_SUBJ, snap.subject)
            .putString(K_DET, snap.details)
            .putString(K_HINT, snap.hint)
            .apply()
    }

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            weekLine = p.getString(K_WEEK, "Неделя: —") ?: "Неделя: —",
            groupLine = p.getString(K_GROUP, "") ?: "",
            label = p.getString(K_LABEL, "Следующая пара") ?: "Следующая пара",
            subject = p.getString(K_SUBJ, "Откройте приложение") ?: "Откройте приложение",
            details = p.getString(K_DET, "Выберите группу в расписании") ?: "",
            hint = p.getString(K_HINT, "Нажмите, чтобы открыть") ?: "Нажмите, чтобы открыть",
        )
    }
}
