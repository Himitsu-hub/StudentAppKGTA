package ru.alemak.studentapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.alemak.studentapp.data.local.ReminderDao
import ru.alemak.studentapp.data.local.ReminderEntity
import ru.alemak.studentapp.data.model.Reminder

@Singleton
class RemindersRepository @Inject constructor(
    private val reminderDao: ReminderDao,
) {
    fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(reminder: Reminder) {
        reminderDao.upsert(ReminderEntity.fromDomain(reminder))
    }

    suspend fun delete(id: String) {
        reminderDao.delete(id)
    }
}
