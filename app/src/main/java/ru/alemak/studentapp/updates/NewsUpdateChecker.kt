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
import ru.alemak.studentapp.data.remote.NewsUpdatesDto

/**
 * Polls GET /api/news-updates and notifies when the news feed fingerprint changes
 * (new post appeared on dksta.ru / server cache).
 */
object NewsUpdateChecker {

    suspend fun check(context: Context, notify: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val prefs = UserPreferences(context.applicationContext)
                val remote = fetchUpdates() ?: return@withContext false
                val remoteVersion = remote.version.ifBlank { remote.fingerprint }
                if (remoteVersion.isBlank()) return@withContext false

                val known = prefs.getNewsVersion()
                if (known.isBlank()) {
                    // First run — remember, do not spam
                    prefs.setNewsVersion(remoteVersion)
                    return@withContext false
                }

                if (known != remoteVersion) {
                    prefs.setNewsVersion(remoteVersion)
                    if (notify) {
                        NewsUpdateNotifier.show(
                            context.applicationContext,
                            title = remote.latestTitle.ifBlank { "Появилась новая запись" },
                            date = remote.latestDate,
                        )
                    }
                    return@withContext true
                }
                false
            } catch (_: Exception) {
                false
            }
        }

    private fun fetchUpdates(): NewsUpdatesDto? {
        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val base = BuildConfig.BASE_URL.trimEnd('/') + "/"
        val request = Request.Builder()
            .url(base + "api/news-updates")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return Gson().fromJson(body, NewsUpdatesDto::class.java)
        }
    }
}
