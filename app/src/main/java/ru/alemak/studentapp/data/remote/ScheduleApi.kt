package ru.alemak.studentapp.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ScheduleApi {
    @GET("api/courses")
    suspend fun getCourses(): List<CourseInfoDto>

    @GET("api/groups")
    suspend fun getGroups(@Query("course") course: Int): Map<String, List<String>>

    @GET("api/schedule")
    suspend fun getSchedule(
        @Query("course") course: Int,
        @Query("group") group: String,
        @Query("subgroup") subgroup: String? = null,
    ): ScheduleResponseDto

    @GET("api/teachers")
    suspend fun getTeachers(): TeachersResponseDto

    @GET("api/news")
    suspend fun getNews(
        @Query("limit") limit: Int = 10,
        @Query("force") force: Boolean = false,
    ): NewsResponseDto

    @GET("api/week-type")
    suspend fun getWeekType(): WeekTypeDto

    /** Lightweight poll: versions of Excel files on the server. */
    @GET("api/schedule-updates")
    suspend fun getScheduleUpdates(): ScheduleUpdatesDto

    @GET("health")
    suspend fun health(): Map<String, String>
}
