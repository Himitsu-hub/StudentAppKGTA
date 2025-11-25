package ru.alemak.studentapp.parsing

import java.util.*

object DateUtils {

    private const val FIRST_SEMESTER_YEAR = 2025
    private const val FIRST_SEMESTER_MONTH = Calendar.SEPTEMBER
    private const val FIRST_SEMESTER_DAY = 1

    private const val SECOND_SEMESTER_YEAR = 2026
    private const val SECOND_SEMESTER_MONTH = Calendar.JANUARY
    private const val SECOND_SEMESTER_DAY = 13

    fun getCurrentWeekType(): String {
        val current = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val firstSemesterStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, FIRST_SEMESTER_YEAR)
            set(Calendar.MONTH, FIRST_SEMESTER_MONTH)
            set(Calendar.DAY_OF_MONTH, FIRST_SEMESTER_DAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val secondSemesterStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, SECOND_SEMESTER_YEAR)
            set(Calendar.MONTH, SECOND_SEMESTER_MONTH)
            set(Calendar.DAY_OF_MONTH, SECOND_SEMESTER_DAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }


        val semesterStart = when {
            current.after(secondSemesterStart) -> secondSemesterStart
            else -> firstSemesterStart
        }

        val diffInMillis = current.timeInMillis - semesterStart.timeInMillis
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
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val targetDay = dayMap[dayName] ?: Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, targetDay)

        return calendar.time
    }
}