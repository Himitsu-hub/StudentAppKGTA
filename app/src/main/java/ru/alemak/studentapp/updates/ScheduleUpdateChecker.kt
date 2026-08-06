package ru.alemak.studentapp.updates

import android.content.Context
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.alemak.studentapp.BuildConfig
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.remote.ScheduleUpdatesDto

/**
 * Polls GET /api/schedule-updates and notifies if the Excel for the
 * selected course changed on the server.
 */
object ScheduleUpdateChecker {

    suspend fun check(context: Context, notify: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val prefs = UserPreferences(context.applicationContext)
                val course = prefs.getSelectedCourse()
                val updates = fetchUpdates() ?: return@withContext false

                val remote = updates.courses.firstOrNull { it.course == course }
                val remoteVersion = remote?.version.orEmpty()
                if (remoteVersion.isBlank() || remote?.available != true) {
                    return@withContext false
                }

                val known = prefs.getScheduleVersion(course)
                if (known.isBlank()) {
                    // First run — remember, do not notify
                    prefs.setScheduleVersion(course, remoteVersion)
                    return@withContext false
                }

                if (known != remoteVersion) {
                    prefs.setScheduleVersion(course, remoteVersion)
                    if (notify) {
                        ScheduleUpdateNotifier.show(context.applicationContext, course)
                    }
                    return@withContext true
                }
                false
            } catch (_: Exception) {
                false
            }
        }

    private fun fetchUpdates(): ScheduleUpdatesDto? {
        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val base = BuildConfig.BASE_URL.trimEnd('/') + "/"
        val request = Request.Builder()
            .url(base + "api/schedule-updates")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return Gson().fromJson(body, ScheduleUpdatesDto::class.java)
        }
    }
}
