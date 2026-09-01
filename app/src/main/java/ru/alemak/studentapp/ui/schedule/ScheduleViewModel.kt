package ru.alemak.studentapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.model.FacultyCatalog
import ru.alemak.studentapp.data.model.ScheduleDay
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.util.DateUtils
import ru.alemak.studentapp.util.TimeFormat

data class ScheduleUiState(
    val faculty: String = FacultyCatalog.FAE,
    val course: Int = 1,
    val group: String? = null,
    val subgroup: String? = null,
    val groups: Map<String, List<String>> = emptyMap(),
    val schedule: List<ScheduleDay> = emptyList(),
    val weekType: String = DateUtils.getCurrentWeekType(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val usingCachedData: Boolean = false,
    val updatedLabel: String? = null,
    val error: String? = null,
    val prefsLoaded: Boolean = false,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val selection = userPreferences.selection.first()
            _uiState.update {
                it.copy(
                    faculty = selection.faculty,
                    course = selection.course,
                    group = selection.group,
                    subgroup = selection.subgroup,
                    prefsLoaded = true,
                    weekType = DateUtils.getCurrentWeekType(),
                )
            }
            loadForSelection(
                selection.faculty,
                selection.course,
                selection.group,
                selection.subgroup,
            )
        }
    }

    fun selectFaculty(faculty: String) {
        viewModelScope.launch {
            val fid = FacultyCatalog.normalize(faculty)
            _uiState.update {
                it.copy(faculty = fid, course = 1, group = null, subgroup = null, schedule = emptyList())
            }
            loadForSelection(fid, 1, null, null)
        }
    }

    fun selectCourse(course: Int) {
        viewModelScope.launch {
            val faculty = _uiState.value.faculty
            _uiState.update { it.copy(course = course, group = null, subgroup = null, schedule = emptyList()) }
            loadForSelection(faculty, course, null, null)
        }
    }

    fun selectGroup(group: String) {
        viewModelScope.launch {
            val subgroups = _uiState.value.groups[group].orEmpty()
            val subgroup = subgroups.firstOrNull()
            val s = _uiState.value
            _uiState.update { it.copy(group = group, subgroup = subgroup) }
            userPreferences.save(s.faculty, s.course, group, subgroup)
            loadSchedule(s.faculty, s.course, group, subgroup)
        }
    }

    fun selectSubgroup(subgroup: String) {
        viewModelScope.launch {
            val s = _uiState.value
            val group = s.group ?: return@launch
            _uiState.update { it.copy(subgroup = subgroup) }
            userPreferences.save(s.faculty, s.course, group, subgroup)
            loadSchedule(s.faculty, s.course, group, subgroup)
        }
    }

    fun refresh() {
        val s = _uiState.value
        viewModelScope.launch {
            loadForSelection(s.faculty, s.course, s.group, s.subgroup, pullRefresh = true)
        }
    }

    private suspend fun loadForSelection(
        faculty: String,
        course: Int,
        preferredGroup: String?,
        preferredSubgroup: String?,
        pullRefresh: Boolean = false,
    ) {
        val fid = FacultyCatalog.normalize(faculty)
        _uiState.update {
            it.copy(
                isLoading = !pullRefresh && it.schedule.isEmpty(),
                isRefreshing = pullRefresh || it.schedule.isNotEmpty(),
                error = null,
                usingCachedData = false,
                weekType = DateUtils.getCurrentWeekType(),
            )
        }
        try {
            val groups = scheduleRepository.getGroups(fid, course)
            if (groups.isEmpty()) {
                _uiState.update {
                    it.copy(
                        groups = emptyMap(),
                        schedule = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        error = "Группы для ${FacultyCatalog.shortName(fid)}, $course курса не найдены. Проверьте интернет.",
                    )
                }
                return
            }

            val group = preferredGroup?.takeIf { it in groups } ?: groups.keys.first()
            val subgroup = preferredSubgroup?.takeIf {
                groups[group]?.contains(it) == true
            } ?: groups[group]?.firstOrNull()

            userPreferences.save(fid, course, group, subgroup)
            _uiState.update {
                it.copy(
                    faculty = fid,
                    groups = groups,
                    group = group,
                    subgroup = subgroup,
                    course = course,
                )
            }
            loadSchedule(fid, course, group, subgroup)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Ошибка загрузки групп",
                )
            }
        }
    }

    private suspend fun loadSchedule(
        faculty: String,
        course: Int,
        group: String,
        subgroup: String?,
    ) {
        _uiState.update { it.copy(isLoading = it.schedule.isEmpty(), error = null) }
        try {
            val result = scheduleRepository.getSchedule(faculty, course, group, subgroup)
            _uiState.update {
                it.copy(
                    schedule = result.schedule,
                    weekType = result.weekType.ifBlank { DateUtils.getCurrentWeekType() },
                    isLoading = false,
                    isRefreshing = false,
                    usingCachedData = result.isOffline,
                    updatedLabel = TimeFormat.updatedAtLabel(result.updatedAtMillis),
                    error = if (result.schedule.isEmpty()) {
                        if (result.isOffline) {
                            "Нет сохранённого расписания. Подключитесь к интернету."
                        } else {
                            "Расписание для группы пустое"
                        }
                    } else {
                        null
                    },
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Ошибка загрузки расписания",
                )
            }
        }
    }
}
