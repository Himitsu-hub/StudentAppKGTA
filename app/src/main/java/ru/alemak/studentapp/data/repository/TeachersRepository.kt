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
        val cached = teacherDao.getAll().map { it.toDomain() }
        return TeachersLoadResult(cached, fromCache = cached.isNotEmpty())
    }

    suspend fun getTeachers(forceRefresh: Boolean = false): TeachersLoadResult {
        val remote = runCatching {
            api.getTeachers().teachers.map { it.toDomain() }
        }.getOrNull()

        if (remote != null) {
            teacherDao.clear()
            teacherDao.upsertAll(remote.map { TeacherCacheEntity.fromDomain(it) })
            return TeachersLoadResult(remote, fromCache = false)
        }

        val cached = teacherDao.getAll().map { it.toDomain() }
        return TeachersLoadResult(cached, fromCache = cached.isNotEmpty())
    }
}
