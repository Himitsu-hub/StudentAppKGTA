package ru.alemak.studentapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NewsItem
import ru.alemak.studentapp.data.repository.NewsRepository
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.util.DateUtils
import ru.alemak.studentapp.util.NetworkMonitor
import ru.alemak.studentapp.util.TimeFormat
import ru.alemak.studentapp.widget.ScheduleWidgetUpdater

data class HomeUiState(
    val weekType: String = DateUtils.getCurrentWeekType(),
    val nextLesson: Lesson? = null,
    val news: List<NewsItem> = emptyList(),
    val isLoadingLesson: Boolean = true,
    val isLoadingNews: Boolean = true,
    val isRefreshing: Boolean = false,
    /** true only when data was taken from local cache because server was unreachable */
    val usingCachedData: Boolean = false,
    val hasGroup: Boolean = false,
    val error: String? = null,
    /** "Обновлено: …" for UI */
    val updatedLabel: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val newsRepository: NewsRepository,
    private val userPreferences: UserPreferences,
    private val widgetUpdater: ScheduleWidgetUpdater,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isOnline = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        refresh(showLoading = true)
        viewModelScope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .drop(1)
                .collect { online ->
                    if (online) {
                        refresh(showLoading = false)
                    } else {
                        _uiState.update { it.copy(usingCachedData = true) }
                    }
                }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update {
                it.copy(
                    weekType = DateUtils.getCurrentWeekType(),
                    isLoadingLesson = true,
                    isLoadingNews = true,
                    isRefreshing = true,
                    error = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    weekType = DateUtils.getCurrentWeekType(),
                    isRefreshing = true,
                    error = null,
                )
            }
        }
        viewModelScope.launch {
            coroutineScope {
                val lessonJob = async { loadNextLesson() }
                val newsJob = async { loadNews() }
                val lessonMeta = lessonJob.await()
                val newsMeta = newsJob.await()
                val latest = listOfNotNull(lessonMeta.updatedAt, newsMeta.updatedAt)
                    .filter { it > 0L }
                    .maxOrNull() ?: 0L
                _uiState.update {
                    it.copy(
                        usingCachedData = lessonMeta.fromCache || newsMeta.fromCache,
                        updatedLabel = TimeFormat.updatedAtLabel(latest),
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    private data class LoadMeta(val fromCache: Boolean, val updatedAt: Long)

    private suspend fun loadNextLesson(): LoadMeta {
        return try {
            val selection = userPreferences.selection.first()
            if (selection.group.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        nextLesson = null,
                        hasGroup = false,
                        isLoadingLesson = false,
                    )
                }
                return LoadMeta(fromCache = false, updatedAt = 0L)
            }
            val result = scheduleRepository.getSchedule(
                selection.course,
                selection.group,
                selection.subgroup,
            )
            val lesson = scheduleRepository.findNextLesson(result.schedule)
            _uiState.update {
                it.copy(
                    nextLesson = lesson,
                    hasGroup = true,
                    isLoadingLesson = false,
                )
            }
            widgetUpdater.updateAsync()
            LoadMeta(fromCache = result.isOffline, updatedAt = result.updatedAtMillis)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingLesson = false,
                    error = e.message ?: "Ошибка загрузки расписания",
                )
            }
            LoadMeta(fromCache = true, updatedAt = 0L)
        }
    }

    private suspend fun loadNews(): LoadMeta {
        return try {
            val result = newsRepository.getNews(10)
            _uiState.update {
                it.copy(
                    news = result.news,
                    isLoadingNews = false,
                )
            }
            LoadMeta(fromCache = result.fromCache, updatedAt = result.updatedAtMillis)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingNews = false,
                    error = e.message,
                )
            }
            LoadMeta(fromCache = true, updatedAt = 0L)
        }
    }
}
