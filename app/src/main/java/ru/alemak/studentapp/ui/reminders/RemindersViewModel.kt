package ru.alemak.studentapp.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.model.Reminder
import ru.alemak.studentapp.data.repository.RemindersRepository
import ru.alemak.studentapp.reminders.ReminderScheduler

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val remindersRepository: RemindersRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = remindersRepository.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, text: String, dateTimeMillis: Long) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val reminder = Reminder(
                id = id ?: UUID.randomUUID().toString(),
                text = text.trim(),
                dateTimeMillis = dateTimeMillis,
            )
            // Reschedule: cancel old if editing
            scheduler.cancel(reminder)
            remindersRepository.save(reminder)
            scheduler.schedule(reminder)
        }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch {
            scheduler.cancel(reminder)
            remindersRepository.delete(reminder.id)
        }
    }
}
