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
    suspend fun getNews(limit: Int = 15): NewsLoadResult {
        // Always try the server first — replace local cache when remote is non-empty
        val remote = runCatching {
            api.getNews(limit).news.map { it.toDomain() }
        }.getOrNull()

        if (remote != null && remote.isNotEmpty()) {
            val now = System.currentTimeMillis()
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
