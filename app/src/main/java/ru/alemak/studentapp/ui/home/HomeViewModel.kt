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

data class HomeUiState(
    val weekType: String = DateUtils.getCurrentWeekType(),
    val nextLesson: Lesson? = null,
    val news: List<NewsItem> = emptyList(),
    val isLoadingLesson: Boolean = true,
    val isLoadingNews: Boolean = true,
    /** true only when data was taken from local cache because server was unreachable */
    val usingCachedData: Boolean = false,
    val hasGroup: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val newsRepository: NewsRepository,
    private val userPreferences: UserPreferences,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isOnline = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        refresh(showLoading = true)
        // When network comes back — silent reload (no loading flicker)
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

    /**
     * @param showLoading if true, shows spinners (first open / manual).
     *                    if false, updates data in place without blanking the UI.
     */
    fun refresh(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update {
                it.copy(
                    weekType = DateUtils.getCurrentWeekType(),
                    isLoadingLesson = true,
                    isLoadingNews = true,
                    error = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(weekType = DateUtils.getCurrentWeekType(), error = null)
            }
        }
        viewModelScope.launch {
            coroutineScope {
                val lessonJob = async { loadNextLesson() }
                val newsJob = async { loadNews() }
                val lessonFromCache = lessonJob.await()
                val newsFromCache = newsJob.await()
                _uiState.update {
                    it.copy(
                        usingCachedData = lessonFromCache || newsFromCache,
                    )
                }
            }
        }
    }

    /** @return true if schedule came from cache */
    private suspend fun loadNextLesson(): Boolean {
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
                return false
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
            result.isOffline
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingLesson = false,
                    error = e.message ?: "Ошибка загрузки расписания",
                )
            }
            true
        }
    }

    /** @return true if news came from cache */
    private suspend fun loadNews(): Boolean {
        return try {
            val result = newsRepository.getNews(10)
            _uiState.update {
                it.copy(
                    news = result.news,
                    isLoadingNews = false,
                )
            }
            result.fromCache
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoadingNews = false,
                    error = e.message,
                )
            }
            true
        }
    }
}
