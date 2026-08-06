package ru.alemak.studentapp.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object HolidayUtils {
    private val format = SimpleDateFormat("dd.MM", Locale("ru"))

    /** Official public holidays (shown on schedule day cards). */
    private val holidayNames = mapOf(
        "01.01" to "Новый год",
        "02.01" to "Новогодние каникулы",
        "03.01" to "Новогодние каникулы",
        "04.01" to "Новогодние каникулы",
        "05.01" to "Новогодние каникулы",
        "06.01" to "Новогодние каникулы",
        "07.01" to "Рождество Христово",
        "08.01" to "Новогодние каникулы",
        "23.02" to "День защитника Отечества",
        "08.03" to "Международный женский день",
        "01.05" to "Праздник Весны и Труда",
        "09.05" to "День Победы",
        "12.06" to "День России",
        "04.11" to "День народного единства",
    )

    fun isHoliday(date: Date = Date()): Boolean =
        holidayNames.containsKey(format.format(date))

    fun getHolidayName(date: Date = Date()): String? =
        holidayNames[format.format(date)]

    /**
     * Summer academic break: July 1 … August 31 (studies from September 1).
     * Home / widgets show «Каникулы» instead of next lesson.
     */
    fun isSummerVacation(now: Calendar = Calendar.getInstance()): Boolean {
        val month = now.get(Calendar.MONTH) // 0-based
        return month == Calendar.JULY || month == Calendar.AUGUST
    }

    fun isSummerVacation(date: Date): Boolean {
        val c = Calendar.getInstance().apply { time = date }
        return isSummerVacation(c)
    }

    /** Non-null when home should show vacation instead of next pair. */
    fun academicBreakTitle(now: Calendar = Calendar.getInstance()): String? =
        if (isSummerVacation(now)) "Каникулы" else null

    fun academicBreakSubtitle(now: Calendar = Calendar.getInstance()): String? =
        if (isSummerVacation(now)) "Лето · занятия с 1 сентября" else null
}
