package ru.alemak.studentapp.data.repository

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import ru.alemak.studentapp.data.local.GroupsCacheEntity
import ru.alemak.studentapp.data.local.ScheduleCacheEntity
import ru.alemak.studentapp.data.local.ScheduleDao
import ru.alemak.studentapp.data.model.FacultyCatalog
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NextLessonInfo
import ru.alemak.studentapp.data.model.ScheduleResult
import ru.alemak.studentapp.data.model.TeacherScheduleResult
import ru.alemak.studentapp.data.remote.ScheduleApi
import ru.alemak.studentapp.util.DateUtils

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: ScheduleApi,
    private val scheduleDao: ScheduleDao,
) {
    /** Instant Room read for home/schedule first paint (VPN-friendly). */
    suspend fun getScheduleFromCacheOnly(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
        group: String,
        subgroup: String?,
    ): ScheduleResult? {
        val fid = FacultyCatalog.normalize(faculty)
        val weekType = DateUtils.getCurrentWeekType()
        val key = cacheKey(fid, course, group, subgroup, weekType)
        val cached = scheduleDao.getSchedule(key)
            ?: scheduleDao.getSchedule(legacyCacheKey(course, group, subgroup, weekType))
        return cached?.let {
            ScheduleResult(
                course = it.course,
                group = it.groupName,
                subgroup = it.subgroup.ifBlank { null },
                weekType = it.weekType,
                schedule = it.schedule,
                fromCache = true,
                isOffline = true,
                updatedAtMillis = it.updatedAt,
            )
        }
    }

    suspend fun getGroups(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
    ): Map<String, List<String>> {
        val fid = FacultyCatalog.normalize(faculty)
        val key = groupsCacheKey(fid, course)
        // Prefer disk when present so VPN latency does not block the picker.
        val cached = scheduleDao.getGroups(key)?.groups.orEmpty()
        val remote = runCatching { api.getGroups(fid, course) }.getOrNull()
        if (remote != null) {
            scheduleDao.upsertGroups(
                GroupsCacheEntity(
                    cacheKey = key,
                    faculty = fid,
                    course = course,
                    groups = remote,
                ),
            )
            return remote
        }
        return cached
    }

    suspend fun getSchedule(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
        group: String,
        subgroup: String?,
    ): ScheduleResult {
        val fid = FacultyCatalog.normalize(faculty)
        val weekType = DateUtils.getCurrentWeekType()
        val key = cacheKey(fid, course, group, subgroup, weekType)

        // Read cache first (does not wait for network).
        val cachedEntity = scheduleDao.getSchedule(key)
            ?: scheduleDao.getSchedule(legacyCacheKey(course, group, subgroup, weekType))
        val cached = cachedEntity?.let {
            ScheduleResult(
                course = it.course,
                group = it.groupName,
                subgroup = it.subgroup.ifBlank { null },
                weekType = it.weekType,
                schedule = it.schedule,
                fromCache = true,
                isOffline = true,
                updatedAtMillis = it.updatedAt,
            )
        }

        val remote = runCatching {
            api.getSchedule(fid, course, group, subgroup).toDomain(isOffline = false)
        }.getOrNull()

        if (remote != null) {
            val now = System.currentTimeMillis()
            scheduleDao.upsertSchedule(
                ScheduleCacheEntity(
                    cacheKey = key,
                    course = course,
                    groupName = group,
                    subgroup = subgroup.orEmpty(),
                    weekType = remote.weekType.ifBlank { weekType },
                    schedule = remote.schedule,
                    updatedAt = now,
                ),
            )
            return remote.copy(isOffline = false, fromCache = false, updatedAtMillis = now)
        }

        return cached ?: ScheduleResult(
            course = course,
            group = group,
            subgroup = subgroup,
            weekType = weekType,
            schedule = emptyList(),
            fromCache = false,
            isOffline = true,
            updatedAtMillis = 0L,
        )
    }

    suspend fun scheduleByTeacher(
        query: String,
        day: String = "today",
    ): TeacherScheduleResult {
        return runCatching {
            api.getScheduleByTeacher(query = query, day = day).toDomain()
        }.getOrElse {
            TeacherScheduleResult(query = query, day = day)
        }
    }

    suspend fun getNextLesson(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
        group: String,
        subgroup: String?,
    ): Lesson? {
        val result = getSchedule(faculty, course, group, subgroup)
        return findNextLesson(result.schedule)
    }

    fun findNextLesson(schedule: List<ru.alemak.studentapp.data.model.ScheduleDay>): Lesson? =
        findNextLessonInfo(schedule)?.lesson

    fun findNextLessonInfo(schedule: List<ru.alemak.studentapp.data.model.ScheduleDay>): NextLessonInfo? {
        val now = Calendar.getInstance()
        val todayName = DateUtils.getTodayName()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        val todaySchedule = schedule.find { it.dayName.equals(todayName, ignoreCase = true) }
        val upcomingToday = todaySchedule?.lessons?.firstOrNull { lesson ->
            if (lesson.type.equals("праздник", ignoreCase = true)) return@firstOrNull false
            if (lesson.subject.isBlank()) return@firstOrNull false
            try {
                val startTime = lesson.time.split("-").firstOrNull()?.trim().orEmpty()
                if (startTime.isBlank()) return@firstOrNull false
                val parsed = sdf.parse(startTime) ?: return@firstOrNull false
                val parsedCal = Calendar.getInstance().apply { time = parsed }
                val lessonCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                lessonCal.timeInMillis > now.timeInMillis
            } catch (_: Exception) {
                false
            }
        }
        if (upcomingToday != null) {
            return NextLessonInfo(upcomingToday, todayName, isToday = true)
        }

        val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val currIndex = days.indexOfFirst { it.equals(todayName, ignoreCase = true) }
        if (currIndex == -1) return null

        for (i in 1..6) {
            val nextDayName = days[(currIndex + i) % days.size]
            val first = schedule.find { it.dayName.equals(nextDayName, ignoreCase = true) }
                ?.lessons
                ?.firstOrNull { !it.type.equals("праздник", true) && it.subject.isNotBlank() }
            if (first != null) {
                return NextLessonInfo(first, nextDayName, isToday = false)
            }
        }
        return null
    }

    private fun groupsCacheKey(faculty: String, course: Int): String =
        "${FacultyCatalog.normalize(faculty)}_$course"

    private fun cacheKey(
        faculty: String,
        course: Int,
        group: String,
        subgroup: String?,
        weekType: String,
    ): String = "${FacultyCatalog.normalize(faculty)}:$course:$group:${subgroup.orEmpty()}:$weekType"

    /** Pre-multi-faculty installs used course-only keys. */
    private fun legacyCacheKey(
        course: Int,
        group: String,
        subgroup: String?,
        weekType: String,
    ): String = "$course:$group:${subgroup.orEmpty()}:$weekType"
}
