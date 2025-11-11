package ru.alemak.studentapp.utils

import java.util.*

object DateUtils {
    fun getCurrentWeekType(): String {
        val calendar = Calendar.getInstance()

        val startOfSemester = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val current = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffInMillis = current.timeInMillis - startOfSemester.timeInMillis
        val weeksDiff = (diffInMillis / (7 * 24 * 60 * 60 * 1000)).toInt()

        return if (weeksDiff % 2 == 0) "Числитель" else "Знаменатель"
    }

    fun getTodayName(): String {
        val days = listOf(
            "Понедельник", "Вторник", "Среда",
            "Четверг", "Пятница", "Суббота", "Воскресенье"
        )
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return days[(dayOfWeek + 5) % 7]
    }

    fun getDateForDay(dayName: String): Date {
        val dayMap = mapOf(
            "Понедельник" to Calendar.MONDAY,
            "Вторник" to Calendar.TUESDAY,
            "Среда" to Calendar.WEDNESDAY,
            "Четверг" to Calendar.THURSDAY,
            "Пятница" to Calendar.FRIDAY,
            "Суббота" to Calendar.SATURDAY,
            "Воскресенье" to Calendar.SUNDAY
        )

        val calendar = Calendar.getInstance()

        // Устанавливаем календарь на начало текущей недели (понедельник)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        // Получаем нужный день
        val targetDay = dayMap[dayName] ?: Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, targetDay)

        return calendar.time
    }


}
