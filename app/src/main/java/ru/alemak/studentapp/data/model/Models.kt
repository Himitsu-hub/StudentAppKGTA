package ru.alemak.studentapp.data.model

data class Lesson(
    val time: String = "",
    val subject: String = "",
    val teacher: String = "",
    val room: String = "",
    val type: String = "",
)

/** Next class for home / widget (with day label). */
data class NextLessonInfo(
    val lesson: Lesson,
    val dayName: String,
    val isToday: Boolean,
    /** Academic week of this pair (may differ from «today» on Sun→Mon boundary). */
    val weekType: String = "",
)

data class ScheduleDay(
    val dayName: String,
    val lessons: List<Lesson> = emptyList(),
)

data class ScheduleResult(
    val course: Int,
    val group: String,
    val subgroup: String?,
    val weekType: String,
    val schedule: List<ScheduleDay>,
    val fromCache: Boolean = false,
    val isOffline: Boolean = false,
    /** When this data was last saved/fetched (epoch millis). */
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class Teacher(
    val name: String,
    val profileUrl: String = "",
    val photoUrl: String = "",
    val position: String = "",
    val email: String = "",
    val subjects: List<String> = emptyList(),
)

data class NewsItem(
    val title: String,
    val url: String = "",
    val imageUrl: String = "",
    val date: String = "",
    val description: String = "",
)

data class Reminder(
    val id: String,
    val text: String,
    val dateTimeMillis: Long,
    val isCompleted: Boolean = false,
)

data class CourseInfo(
    val course: Int,
    val available: Boolean,
    val faculty: String = FacultyCatalog.FAE,
)

data class FacultyInfo(
    val id: String,
    val short: String,
    val name: String,
    val courses: List<CourseInfo> = emptyList(),
)

/** Lesson from GET /api/schedule/by-teacher */
data class TeacherLesson(
    val dayName: String = "",
    val time: String = "",
    val subject: String = "",
    val teacher: String = "",
    val room: String = "",
    val type: String = "",
    val faculty: String = "",
    val course: Int = 0,
    val group: String = "",
    val subgroup: String = "",
)

data class TeacherScheduleResult(
    val query: String = "",
    val weekType: String = "",
    val day: String = "",
    val count: Int = 0,
    val lessons: List<TeacherLesson> = emptyList(),
)
