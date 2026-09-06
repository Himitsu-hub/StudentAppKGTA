package ru.alemak.studentapp.data.repository

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import ru.alemak.studentapp.data.local.GroupsCacheEntity
import ru.alemak.studentapp.data.local.ScheduleCacheEntity
import ru.alemak.studentapp.data.local.ScheduleDao
import ru.alemak.studentapp.data.local.TeacherDayCacheEntity
import ru.alemak.studentapp.data.model.FacultyCatalog
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NextLessonInfo
import ru.alemak.studentapp.data.model.ScheduleResult
import ru.alemak.studentapp.data.model.TeacherLesson
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
        weekType: String = DateUtils.getCurrentWeekType(),
    ): ScheduleResult? {
        val fid = FacultyCatalog.normalize(faculty)
        val week = weekType.ifBlank { DateUtils.getCurrentWeekType() }
        val key = cacheKey(fid, course, group, subgroup, week)
        val cached = scheduleDao.getSchedule(key)
            ?: scheduleDao.getSchedule(legacyCacheKey(course, group, subgroup, week))
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

    /** Instant Room read for group picker (no network). */
    suspend fun getGroupsFromCacheOnly(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
    ): Map<String, List<String>> {
        val fid = FacultyCatalog.normalize(faculty)
        return scheduleDao.getGroups(groupsCacheKey(fid, course))?.groups.orEmpty()
    }

    suspend fun getGroups(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
    ): Map<String, List<String>> {
        val fid = FacultyCatalog.normalize(faculty)
        val key = groupsCacheKey(fid, course)
        // Prefer disk immediately so VPN never blocks the picker.
        val cached = scheduleDao.getGroups(key)?.groups.orEmpty()
        if (cached.isNotEmpty()) {
            // Soft refresh in background is done by prefetchGroups / explicit refresh.
            return cached
        }
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

    /** Force network refresh of groups (pull-to-refresh / first visit). */
    suspend fun refreshGroups(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
    ): Map<String, List<String>> {
        val fid = FacultyCatalog.normalize(faculty)
        val key = groupsCacheKey(fid, course)
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
        return scheduleDao.getGroups(key)?.groups.orEmpty()
    }

    /** Warm Room cache for all faculty×course group lists (VPN-friendly switching). */
    suspend fun prefetchAllGroups() {
        for (fac in FacultyCatalog.all) {
            for (course in FacultyCatalog.courses(fac.id)) {
                val key = groupsCacheKey(fac.id, course)
                if (scheduleDao.getGroups(key)?.groups?.isNotEmpty() == true) continue
                runCatching { api.getGroups(fac.id, course) }.getOrNull()?.let { remote ->
                    scheduleDao.upsertGroups(
                        GroupsCacheEntity(
                            cacheKey = key,
                            faculty = fac.id,
                            course = course,
                            groups = remote,
                        ),
                    )
                }
            }
        }
    }

    suspend fun getSchedule(
        faculty: String = FacultyCatalog.FAE,
        course: Int,
        group: String,
        subgroup: String?,
        weekType: String = DateUtils.getCurrentWeekType(),
    ): ScheduleResult {
        val fid = FacultyCatalog.normalize(faculty)
        val week = weekType.ifBlank { DateUtils.getCurrentWeekType() }
        val key = cacheKey(fid, course, group, subgroup, week)

        // Read cache first (does not wait for network).
        val cachedEntity = scheduleDao.getSchedule(key)
            ?: scheduleDao.getSchedule(legacyCacheKey(course, group, subgroup, week))
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
            api.getSchedule(fid, course, group, subgroup, week = week).toDomain(isOffline = false)
        }.getOrNull()

        if (remote != null) {
            val now = System.currentTimeMillis()
            val savedWeek = remote.weekType.ifBlank { week }
            scheduleDao.upsertSchedule(
                ScheduleCacheEntity(
                    cacheKey = cacheKey(fid, course, group, subgroup, savedWeek),
                    course = course,
                    groupName = group,
                    subgroup = subgroup.orEmpty(),
                    weekType = savedWeek,
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
            weekType = week,
            schedule = emptyList(),
            fromCache = false,
            isOffline = true,
            updatedAtMillis = 0L,
        )
    }

    suspend fun scheduleByTeacher(
        query: String,
        day: String = "today",
        weekType: String = DateUtils.getCurrentWeekType(),
    ): TeacherScheduleResult {
        val week = weekType.ifBlank { DateUtils.getCurrentWeekType() }
        val dayKey = if (day.equals("today", true) || day.equals("сегодня", true)) {
            DateUtils.getTodayName()
        } else {
            day
        }
        val cacheKey = teacherDayKey(query, dayKey, week)

        val remote = runCatching {
            api.getScheduleByTeacher(query = query, day = day, week = week).toDomain()
        }.getOrNull()
        if (remote != null) {
            scheduleDao.upsertTeacherDay(
                TeacherDayCacheEntity(
                    cacheKey = cacheKey,
                    query = query,
                    day = dayKey,
                    weekType = remote.weekType.ifBlank { week },
                    lessons = remote.lessons,
                ),
            )
            return remote
        }

        // Offline: dedicated by-teacher cache first.
        scheduleDao.getTeacherDay(cacheKey)?.let { cached ->
            return TeacherScheduleResult(
                query = query,
                weekType = cached.weekType,
                day = day,
                count = cached.lessons.size,
                lessons = cached.lessons,
            )
        }

        // Fallback: scan all locally cached group schedules for this teacher/day.
        val fromSchedules = findTeacherLessonsInCachedSchedules(query, dayKey, week)
        return TeacherScheduleResult(
            query = query,
            weekType = week,
            day = day,
            count = fromSchedules.size,
            lessons = fromSchedules,
        )
    }

    private suspend fun findTeacherLessonsInCachedSchedules(
        query: String,
        dayName: String,
        weekType: String,
    ): List<TeacherLesson> {
        val q = query.trim().lowercase().replace('ё', 'е')
        if (q.length < 2) return emptyList()
        val surname = q.split(Regex("\\s+")).firstOrNull().orEmpty()
        val out = LinkedHashMap<String, TeacherLesson>()
        for (entity in scheduleDao.getAllSchedules()) {
            if (entity.weekType.isNotBlank() &&
                !entity.weekType.equals(weekType, ignoreCase = true)
            ) {
                continue
            }
            val day = entity.schedule.firstOrNull {
                it.dayName.equals(dayName, ignoreCase = true)
            } ?: continue
            for (lesson in day.lessons) {
                val teacher = lesson.teacher.lowercase().replace('ё', 'е')
                if (teacher.isBlank()) continue
                if (surname !in teacher && q !in teacher) continue
                val key = "${lesson.time}|${lesson.subject}|${lesson.room}|${entity.groupName}"
                if (key in out) continue
                out[key] = TeacherLesson(
                    dayName = dayName,
                    time = lesson.time,
                    subject = lesson.subject,
                    teacher = lesson.teacher,
                    room = lesson.room,
                    type = lesson.type,
                    course = entity.course,
                    group = buildString {
                        append(entity.groupName)
                        if (entity.subgroup.isNotBlank()) {
                            append(" (")
                            append(entity.subgroup)
                            append(")")
                        }
                    },
                    subgroup = entity.subgroup,
                )
            }
        }
        return out.values.sortedBy { it.time }
    }

    private fun teacherDayKey(query: String, day: String, weekType: String): String =
        "${query.trim().lowercase().replace('ё', 'е')}|$day|$weekType"

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
