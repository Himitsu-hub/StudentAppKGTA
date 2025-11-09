package ru.alemak.studentapp.utils

import java.util.*

object DateUtils {
    fun getCurrentWeekType(): String {
        val calendar = Calendar.getInstance()

        // Фиксированная дата начала семестра - 2 сентября 2024 года
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
}