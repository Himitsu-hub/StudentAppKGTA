package ru.alemak.studentapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import ru.alemak.studentapp.data.local.NewsCacheEntity
import ru.alemak.studentapp.data.local.NewsDao
import ru.alemak.studentapp.data.model.NewsItem
import ru.alemak.studentapp.data.remote.ScheduleApi

data class NewsLoadResult(
    val news: List<NewsItem>,
    /** true only if we failed to reach API and fell back to local Room cache */
    val fromCache: Boolean,
    val updatedAtMillis: Long = 0L,
)

@Singleton
class NewsRepository @Inject constructor(
    private val api: ScheduleApi,
    private val newsDao: NewsDao,
) {
    suspend fun getNewsFromCacheOnly(limit: Int = 15): NewsLoadResult {
        val cachedEntities = newsDao.getAll().take(limit)
        val cached = cachedEntities.map { it.toDomain() }
        val updatedAt = cachedEntities.maxOfOrNull { it.updatedAt } ?: 0L
        return NewsLoadResult(
            news = cached,
            fromCache = cached.isNotEmpty(),
            updatedAtMillis = updatedAt,
        )
    }

    suspend fun getNews(limit: Int = 15, force: Boolean = false): NewsLoadResult {
        // Try network; on VPN failure fall back to Room instantly usable cache.
        val response = runCatching { api.getNews(limit = limit, force = force) }.getOrNull()
        val remote = response?.news?.map { it.toDomain() }

        if (remote != null && remote.isNotEmpty()) {
            val serverTs = response.updatedAt?.let { (it * 1000.0).toLong() }?.takeIf { it > 0 }
            val now = serverTs ?: System.currentTimeMillis()
            newsDao.clear()
            newsDao.upsertAll(
                remote.mapIndexed { index, item ->
                    NewsCacheEntity.fromDomain(item, index).copy(updatedAt = now)
                },
            )
            return NewsLoadResult(remote, fromCache = false, updatedAtMillis = now)
        }

        val cachedEntities = newsDao.getAll()
        val cached = cachedEntities.map { it.toDomain() }
        val updatedAt = cachedEntities.maxOfOrNull { it.updatedAt } ?: 0L
        // Empty remote (or fail) → keep previous cache rather than blank home
        if (remote != null && remote.isEmpty() && cached.isNotEmpty()) {
            return NewsLoadResult(cached, fromCache = true, updatedAtMillis = updatedAt)
        }
        return NewsLoadResult(
            news = cached,
            fromCache = cached.isNotEmpty(),
            updatedAtMillis = updatedAt,
        )
    }
}
