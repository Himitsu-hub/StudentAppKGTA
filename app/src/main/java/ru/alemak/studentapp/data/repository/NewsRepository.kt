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
)

@Singleton
class NewsRepository @Inject constructor(
    private val api: ScheduleApi,
    private val newsDao: NewsDao,
) {
    suspend fun getNews(limit: Int = 10): NewsLoadResult {
        // Always try the server first — don't trust ConnectivityManager alone
        val remote = runCatching {
            api.getNews(limit).news.map { it.toDomain() }
        }.getOrNull()

        if (remote != null) {
            newsDao.clear()
            newsDao.upsertAll(
                remote.mapIndexed { index, item ->
                    NewsCacheEntity.fromDomain(item, index)
                },
            )
            return NewsLoadResult(remote, fromCache = false)
        }

        val cached = newsDao.getAll().map { it.toDomain() }
        return NewsLoadResult(cached, fromCache = cached.isNotEmpty())
    }
}
