package ru.alemak.studentapp.util

import java.util.Calendar
import java.util.Date

object DateUtils {

    fun getCurrentWeekType(now: Calendar = Calendar.getInstance()): String {
        val current = (now.clone() as Calendar).apply { clearTime() }
        val (first, second) = getSemesterStarts(current)
        val semesterStart = if (!current.before(second)) second else first
        val diffInMillis = current.timeInMillis - semesterStart.timeInMillis
        val weeksDiff = (diffInMillis / (7L * 24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        return if (weeksDiff % 2 == 0) "Числитель" else "Знаменатель"
    }

    fun getSemesterStarts(now: Calendar = Calendar.getInstance()): Pair<Calendar, Calendar> {
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val first: Calendar
        val second: Calendar
        if (month >= Calendar.SEPTEMBER) {
            first = calendarOf(year, Calendar.SEPTEMBER, 1)
            second = calendarOf(year + 1, Calendar.JANUARY, 13)
        } else {
            first = calendarOf(year - 1, Calendar.SEPTEMBER, 1)
            second = calendarOf(year, Calendar.JANUARY, 13)
        }
        return first to second
    }

    fun getTodayName(now: Calendar = Calendar.getInstance()): String {
        val days = listOf(
            "Понедельник", "Вторник", "Среда",
            "Четверг", "Пятница", "Суббота", "Воскресенье",
        )
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        return days[(dayOfWeek + 5) % 7]
    }

    fun getDateForDay(dayName: String, now: Calendar = Calendar.getInstance()): Date {
        val dayMap = mapOf(
            "Понедельник" to Calendar.MONDAY,
            "Вторник" to Calendar.TUESDAY,
            "Среда" to Calendar.WEDNESDAY,
            "Четверг" to Calendar.THURSDAY,
            "Пятница" to Calendar.FRIDAY,
            "Суббота" to Calendar.SATURDAY,
            "Воскресенье" to Calendar.SUNDAY,
        )
        val calendar = (now.clone() as Calendar)
        val targetDay = dayMap[dayName] ?: Calendar.MONDAY
        val current = calendar.get(Calendar.DAY_OF_WEEK)
        var diff = targetDay - current
        if (diff < 0) diff += 7
        calendar.add(Calendar.DAY_OF_YEAR, diff)
        return calendar.time
    }

    private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
