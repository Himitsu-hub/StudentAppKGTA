package ru.alemak.studentapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScheduleCacheEntity::class,
        GroupsCacheEntity::class,
        TeacherCacheEntity::class,
        NewsCacheEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun teacherDao(): TeacherDao
    abstract fun newsDao(): NewsDao
    abstract fun reminderDao(): ReminderDao
}
