package ru.alemak.studentapp.screens

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

/**
 * Класс данных для напоминания.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val dateTime: Date,
    val isCompleted: Boolean = false
) {
    fun getFormattedDateTime(): String {
        val cal = Calendar.getInstance().apply { time = dateTime }
        return String.format(
            Locale.getDefault(),
            "%02d.%02d.%04d %02d:%02d",
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }
}

/**
 * Менеджер для работы с напоминаниями.
 * Сохраняет список в JSON в internal storage.
 */
object RemindersManager {
    private val gson = Gson()
    private var _reminders: MutableList<Reminder> = mutableListOf()
    private const val FILE_NAME = "reminders.json"

    /** Публичный доступ — только чтение */
    val reminders: List<Reminder>
        get() = _reminders

    /**
     * Загружает список напоминаний из файла.
     */
    fun load(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)

            if (!file.exists()) {
                _reminders = mutableListOf()
                return
            }

            val json = file.readText()
            val type = object : TypeToken<MutableList<Reminder>>() {}.type

            _reminders = gson.fromJson(json, type) ?: mutableListOf()

        } catch (e: Exception) {
            e.printStackTrace()
            _reminders = mutableListOf()
        }
    }

    /**
     * Сохраняет список напоминаний.
     */
    private fun save(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(gson.toJson(_reminders))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Добавляет новое напоминание.
     */
    fun addReminder(context: Context, reminder: Reminder) {
        _reminders.add(reminder)
        save(context)
    }

    /**
     * Обновляет существующее напоминание по ID.
     */
    fun updateReminder(context: Context, updated: Reminder) {
        val index = _reminders.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            _reminders[index] = updated
            save(context)
        }
    }

    /**
     * Удаляет напоминание по ID.
     */
    fun deleteReminder(context: Context, id: String) {
        _reminders.removeAll { it.id == id }
        save(context)
    }

    /**
     * Полная очистка (не используется, но полезно для тестов).
     */
    fun clear(context: Context) {
        _reminders.clear()
        save(context)
    }
}