package ru.alemak.studentapp.data.remote

import com.google.gson.annotations.SerializedName
import ru.alemak.studentapp.data.model.CourseInfo
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NewsItem
import ru.alemak.studentapp.data.model.ScheduleDay
import ru.alemak.studentapp.data.model.ScheduleResult
import ru.alemak.studentapp.data.model.Teacher

data class CourseInfoDto(
    val course: Int,
    val available: Boolean,
) {
    fun toDomain() = CourseInfo(course, available)
}

data class ScheduleResponseDto(
    val course: Int,
    val group: String,
    val subgroup: String?,
    val weekType: String,
    val fromCache: Boolean? = null,
    val schedule: List<ScheduleDayDto> = emptyList(),
) {
    fun toDomain(isOffline: Boolean = false) = ScheduleResult(
        course = course,
        group = group,
        subgroup = subgroup,
        weekType = weekType,
        schedule = schedule.map { it.toDomain() },
        fromCache = fromCache == true,
        isOffline = isOffline,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

data class ScheduleDayDto(
    val dayName: String,
    val lessons: List<LessonDto> = emptyList(),
) {
    fun toDomain() = ScheduleDay(dayName, lessons.map { it.toDomain() })
}

data class LessonDto(
    val time: String = "",
    val subject: String = "",
    val teacher: String = "",
    val room: String = "",
    val type: String = "",
) {
    fun toDomain() = Lesson(time, subject, teacher, room, type)
}

data class TeachersResponseDto(
    val teachers: List<TeacherDto> = emptyList(),
)

data class TeacherDto(
    val name: String = "",
    @SerializedName("profile_url") val profileUrl: String = "",
    @SerializedName("photo_url") val photoUrl: String = "",
    val position: String = "",
    val email: String = "",
    val subjects: List<String>? = emptyList(),
) {
    fun toDomain() = Teacher(
        name = name,
        profileUrl = profileUrl,
        photoUrl = photoUrl,
        position = position,
        email = email,
        subjects = subjects.orEmpty(),
    )
}

data class NewsResponseDto(
    val news: List<NewsItemDto> = emptyList(),
)

data class NewsItemDto(
    val title: String = "",
    val url: String = "",
    @SerializedName("image_url") val imageUrl: String = "",
    val date: String = "",
    val description: String = "",
) {
    fun toDomain() = NewsItem(title, url, imageUrl, date, description)
}

data class WeekTypeDto(
    val weekType: String,
)

data class ScheduleUpdatesDto(
    val courses: List<CourseUpdateDto> = emptyList(),
    val fingerprint: String = "",
)

data class CourseUpdateDto(
    val course: Int,
    val version: String = "",
    val updatedAt: String? = null,
    val available: Boolean = false,
)
