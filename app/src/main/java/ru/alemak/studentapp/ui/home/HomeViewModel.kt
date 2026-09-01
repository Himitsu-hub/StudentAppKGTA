package ru.alemak.studentapp.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NewsItem
import ru.alemak.studentapp.data.repository.NewsRepository
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.updates.NewsUpdateChecker
import ru.alemak.studentapp.util.DateUtils
import ru.alemak.studentapp.util.HolidayUtils
import ru.alemak.studentapp.util.NetworkMonitor
import ru.alemak.studentapp.util.TimeFormat
import ru.alemak.studentapp.widget.ScheduleWidgetUpdater

data class HomeUiState(
    val weekType: String = DateUtils.getCurrentWeekType(),
    val nextLesson: Lesson? = null,
    /** Summer break (Jul–Aug): show «Каникулы» instead of next pair */
    val isVacation: Boolean = HolidayUtils.isSummerVacation(),
    val vacationTitle: String? = HolidayUtils.academicBreakTitle(),
    val vacationSubtitle: String? = HolidayUtils.academicBreakSubtitle(),
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
    @ApplicationContext private val appContext: Context,
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
        // While home is open: refresh news + poll schedule/news versions often
        // so admin Excel uploads produce a notification within ~1 minute.
        viewModelScope.launch {
            while (isActive) {
                delay(45_000L)
                loadNewsOnly()
                try {
                    NewsUpdateChecker.check(appContext, notify = true)
                } catch (_: Exception) {
                }
                try {
                    ru.alemak.studentapp.updates.ScheduleUpdateChecker.check(
                        appContext,
                        notify = true,
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun loadNewsOnly() {
        try {
            // force=false: warm cache from API (server scrapes in background)
            val result = newsRepository.getNews(limit = 15, force = false)
            _uiState.update {
                it.copy(
                    news = result.news,
                    usingCachedData = if (result.fromCache) true else it.usingCachedData,
                    updatedLabel = TimeFormat.updatedAtLabel(result.updatedAtMillis)
                        ?: it.updatedLabel,
                )
            }
        } catch (_: Exception) {
            // keep previous news
        }
    }

    fun refresh(showLoading: Boolean = true) {
        val vacation = HolidayUtils.isSummerVacation()
        val weekLabel = if (vacation) {
            "Каникулы"
        } else {
            DateUtils.getCurrentWeekType()
        }
        if (showLoading) {
            _uiState.update {
                it.copy(
                    weekType = weekLabel,
                    isVacation = vacation,
                    vacationTitle = HolidayUtils.academicBreakTitle(),
                    vacationSubtitle = HolidayUtils.academicBreakSubtitle(),
                    isLoadingLesson = true,
                    isLoadingNews = true,
                    isRefreshing = true,
                    error = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    weekType = weekLabel,
                    isVacation = vacation,
                    vacationTitle = HolidayUtils.academicBreakTitle(),
                    vacationSubtitle = HolidayUtils.academicBreakSubtitle(),
                    isRefreshing = true,
                    error = null,
                )
            }
        }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val lessonJob = async { loadNextLesson() }
                    val newsJob = async { loadNews() }
                    val lessonMeta = lessonJob.await()
                    val newsMeta = newsJob.await()
                    val latest = listOfNotNull(lessonMeta.updatedAt, newsMeta.updatedAt)
                        .filter { it > 0L }
                        .maxOrNull() ?: 0L
                    // Show offline banner only when offline (not merely when one source used cache)
                    val offline = !isOnline.value
                    _uiState.update {
                        it.copy(
                            usingCachedData = offline && (lessonMeta.fromCache || newsMeta.fromCache),
                            updatedLabel = TimeFormat.updatedAtLabel(latest) ?: it.updatedLabel,
                            isRefreshing = false,
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isRefreshing = false, isLoadingNews = false, isLoadingLesson = false) }
            }
        }
    }

    private data class LoadMeta(val fromCache: Boolean, val updatedAt: Long)

    private suspend fun loadNextLesson(): LoadMeta {
        return try {
            // July–August: no pairs until 1 September
            if (HolidayUtils.isSummerVacation()) {
                _uiState.update {
                    it.copy(
                        nextLesson = null,
                        isVacation = true,
                        vacationTitle = HolidayUtils.academicBreakTitle(),
                        vacationSubtitle = HolidayUtils.academicBreakSubtitle(),
                        weekType = "Каникулы",
                        hasGroup = true,
                        isLoadingLesson = false,
                    )
                }
                widgetUpdater.updateAsync()
                return LoadMeta(fromCache = false, updatedAt = 0L)
            }

            val selection = userPreferences.selection.first()
            if (selection.group.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        nextLesson = null,
                        isVacation = false,
                        hasGroup = false,
                        isLoadingLesson = false,
                    )
                }
                return LoadMeta(fromCache = false, updatedAt = 0L)
            }
            val result = scheduleRepository.getSchedule(
                faculty = selection.faculty,
                course = selection.course,
                group = selection.group,
                subgroup = selection.subgroup,
            )
            val lesson = scheduleRepository.findNextLesson(result.schedule)
            _uiState.update {
                it.copy(
                    nextLesson = lesson,
                    isVacation = false,
                    vacationTitle = null,
                    vacationSubtitle = null,
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
            val result = newsRepository.getNews(limit = 15, force = true)
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
