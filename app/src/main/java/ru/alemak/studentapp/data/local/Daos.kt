package ru.alemak.studentapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM cached_schedules WHERE cacheKey = :key LIMIT 1")
    suspend fun getSchedule(key: String): ScheduleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(entity: ScheduleCacheEntity)

    @Query("SELECT * FROM cached_groups WHERE course = :course LIMIT 1")
    suspend fun getGroups(course: Int): GroupsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(entity: GroupsCacheEntity)
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM cached_teachers ORDER BY name ASC")
    suspend fun getAll(): List<TeacherCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TeacherCacheEntity>)

    @Query("DELETE FROM cached_teachers")
    suspend fun clear()
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM cached_news ORDER BY sortOrder ASC")
    suspend fun getAll(): List<NewsCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NewsCacheEntity>)

    @Query("DELETE FROM cached_news")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dateTimeMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dateTimeMillis ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)
}
