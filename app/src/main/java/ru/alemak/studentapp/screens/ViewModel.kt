package ru.alemak.studentapp.managers

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*

object RemindersManager {
    private val _reminders = mutableStateListOf<ru.alemak.studentapp.screens.Reminder>()
    val reminders: List<ru.alemak.studentapp.screens.Reminder> get() = _reminders

    fun addReminder(reminder: ru.alemak.studentapp.screens.Reminder) {
        _reminders.add(reminder)
    }

    fun deleteReminder(reminderId: String) {
        _reminders.removeAll { it.id == reminderId }
    }
}

data class Reminder(
    val id: String,
    val text: String,
    val dateTime: Date,
    val isCompleted: Boolean = false
) {
    fun getFormattedDateTime(): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return dateFormat.format(dateTime)
    }
}