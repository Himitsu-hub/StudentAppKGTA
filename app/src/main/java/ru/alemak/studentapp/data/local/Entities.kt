package ru.alemak.studentapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.alemak.studentapp.data.model.NewsItem
import ru.alemak.studentapp.data.model.Reminder
import ru.alemak.studentapp.data.model.ScheduleDay
import ru.alemak.studentapp.data.model.Teacher

@Entity(tableName = "cached_schedules")
data class ScheduleCacheEntity(
    @PrimaryKey val cacheKey: String,
    val course: Int,
    val groupName: String,
    val subgroup: String,
    val weekType: String,
    val schedule: List<ScheduleDay>,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_groups")
data class GroupsCacheEntity(
    @PrimaryKey val course: Int,
    val groups: Map<String, List<String>>,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_teachers")
data class TeacherCacheEntity(
    @PrimaryKey val name: String,
    val profileUrl: String,
    val photoUrl: String,
    val position: String,
    val email: String,
    val subjects: List<String>,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain() = Teacher(name, profileUrl, photoUrl, position, email, subjects)

    companion object {
        fun fromDomain(t: Teacher) = TeacherCacheEntity(
            name = t.name,
            profileUrl = t.profileUrl,
            photoUrl = t.photoUrl,
            position = t.position,
            email = t.email,
            subjects = t.subjects,
        )
    }
}

@Entity(tableName = "cached_news")
data class NewsCacheEntity(
    @PrimaryKey val url: String,
    val title: String,
    val imageUrl: String,
    val date: String,
    val description: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain() = NewsItem(title, url, imageUrl, date, description)

    companion object {
        fun fromDomain(item: NewsItem, order: Int) = NewsCacheEntity(
            url = item.url.ifBlank { item.title },
            title = item.title,
            imageUrl = item.imageUrl,
            date = item.date,
            description = item.description,
            sortOrder = order,
        )
    }
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val text: String,
    val dateTimeMillis: Long,
    val isCompleted: Boolean = false,
) {
    fun toDomain() = Reminder(id, text, dateTimeMillis, isCompleted)

    companion object {
        fun fromDomain(r: Reminder) = ReminderEntity(
            id = r.id,
            text = r.text,
            dateTimeMillis = r.dateTimeMillis,
            isCompleted = r.isCompleted,
        )
    }
}
