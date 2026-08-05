package ru.alemak.studentapp.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormat {

    /**
     * Human-readable "Обновлено …" for cache / last fetch timestamps.
     * @return null if [millis] is 0 or invalid
     */
    fun updatedAtLabel(millis: Long, nowMillis: Long = System.currentTimeMillis()): String? {
        if (millis <= 0L) return null
        val diff = (nowMillis - millis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)

        val relative = when {
            minutes < 1L -> "только что"
            minutes < 60L -> "$minutes мин назад"
            hours < 24L -> {
                val h = hours.toInt()
                when {
                    h == 1 -> "1 час назад"
                    h in 2..4 -> "$h часа назад"
                    else -> "$h часов назад"
                }
            }
            else -> {
                val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(millis))
                val day = when {
                    isSameDay(millis, nowMillis) -> "сегодня"
                    isYesterday(millis, nowMillis) -> "вчера"
                    else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(millis))
                }
                return "Обновлено: $day, $time"
            }
        }
        return "Обновлено: $relative"
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(millis: Long, nowMillis: Long): Boolean {
        val y = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return y.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            y.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    }
}
