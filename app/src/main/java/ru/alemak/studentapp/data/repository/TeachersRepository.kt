package ru.alemak.studentapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import ru.alemak.studentapp.data.local.TeacherCacheEntity
import ru.alemak.studentapp.data.local.TeacherDao
import ru.alemak.studentapp.data.model.Teacher
import ru.alemak.studentapp.data.remote.ScheduleApi

data class TeachersLoadResult(
    val teachers: List<Teacher>,
    /** true only if API failed and we show Room cache */
    val fromCache: Boolean,
)

@Singleton
class TeachersRepository @Inject constructor(
    private val api: ScheduleApi,
    private val teacherDao: TeacherDao,
) {
    suspend fun getTeachersFromCacheOnly(): TeachersLoadResult {
        val cached = ensureLeadership(teacherDao.getAll().map { it.toDomain() })
        return TeachersLoadResult(cached, fromCache = cached.isNotEmpty())
    }

    suspend fun getTeachers(forceRefresh: Boolean = false): TeachersLoadResult {
        val remote = runCatching {
            api.getTeachers().teachers.map { it.toDomain() }
        }.getOrNull()

        if (remote != null) {
            val withLeaders = ensureLeadership(remote)
            teacherDao.clear()
            teacherDao.upsertAll(withLeaders.map { TeacherCacheEntity.fromDomain(it) })
            return TeachersLoadResult(withLeaders, fromCache = false)
        }

        val cached = ensureLeadership(teacherDao.getAll().map { it.toDomain() })
        return TeachersLoadResult(cached, fromCache = cached.isNotEmpty())
    }

    /** Rector may be missing from scraped кафедра pages — keep him in the list. */
    private fun ensureLeadership(teachers: List<Teacher>): List<Teacher> {
        val existing = teachers.map { it.name.lowercase().replace('ё', 'е') }.toSet()
        val extras = mutableListOf<Teacher>()
        val rector = Teacher(
            name = "Егоров Алексей Васильевич",
            position = "Ректор",
        )
        if (rector.name.lowercase().replace('ё', 'е') !in existing) {
            extras += rector
        }
        return if (extras.isEmpty()) teachers else extras + teachers
    }
}
