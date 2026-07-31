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
import ru.alemak.studentapp.data.model.ScheduleDay
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.util.DateUtils

data class ScheduleUiState(
    val course: Int = 1,
    val group: String? = null,
    val subgroup: String? = null,
    val groups: Map<String, List<String>> = emptyMap(),
    val schedule: List<ScheduleDay> = emptyList(),
    val weekType: String = DateUtils.getCurrentWeekType(),
    val isLoading: Boolean = true,
    val usingCachedData: Boolean = false,
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
                    course = selection.course,
                    group = selection.group,
                    subgroup = selection.subgroup,
                    prefsLoaded = true,
                    weekType = DateUtils.getCurrentWeekType(),
                )
            }
            loadForCourse(selection.course, selection.group, selection.subgroup)
        }
    }

    fun selectCourse(course: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(course = course, group = null, subgroup = null) }
            loadForCourse(course, null, null)
        }
    }

    fun selectGroup(group: String) {
        viewModelScope.launch {
            val subgroups = _uiState.value.groups[group].orEmpty()
            val subgroup = subgroups.firstOrNull()
            _uiState.update { it.copy(group = group, subgroup = subgroup) }
            userPreferences.save(_uiState.value.course, group, subgroup)
            loadSchedule(_uiState.value.course, group, subgroup)
        }
    }

    fun selectSubgroup(subgroup: String) {
        viewModelScope.launch {
            val group = _uiState.value.group ?: return@launch
            _uiState.update { it.copy(subgroup = subgroup) }
            userPreferences.save(_uiState.value.course, group, subgroup)
            loadSchedule(_uiState.value.course, group, subgroup)
        }
    }

    fun refresh() {
        val s = _uiState.value
        viewModelScope.launch {
            loadForCourse(s.course, s.group, s.subgroup)
        }
    }

    private suspend fun loadForCourse(course: Int, preferredGroup: String?, preferredSubgroup: String?) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                usingCachedData = false,
                weekType = DateUtils.getCurrentWeekType(),
            )
        }
        try {
            val groups = scheduleRepository.getGroups(course)
            if (groups.isEmpty()) {
                _uiState.update {
                    it.copy(
                        groups = emptyMap(),
                        schedule = emptyList(),
                        isLoading = false,
                        error = "Группы для $course курса не найдены. Проверьте интернет.",
                    )
                }
                return
            }

            val group = preferredGroup?.takeIf { it in groups } ?: groups.keys.first()
            val subgroup = preferredSubgroup?.takeIf {
                groups[group]?.contains(it) == true
            } ?: groups[group]?.firstOrNull()

            userPreferences.save(course, group, subgroup)
            _uiState.update {
                it.copy(groups = groups, group = group, subgroup = subgroup, course = course)
            }
            loadSchedule(course, group, subgroup)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки групп",
                )
            }
        }
    }

    private suspend fun loadSchedule(course: Int, group: String, subgroup: String?) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val result = scheduleRepository.getSchedule(course, group, subgroup)
            _uiState.update {
                it.copy(
                    schedule = result.schedule,
                    weekType = result.weekType.ifBlank { DateUtils.getCurrentWeekType() },
                    isLoading = false,
                    usingCachedData = result.isOffline,
                    error = if (result.schedule.isEmpty()) {
                        if (result.isOffline) {
                            "Нет сохранённого расписания. Подключитесь к интернету."
                        } else {
                            "Расписание для группы пустое"
                        }
                    } else null,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки расписания",
                )
            }
        }
    }
}
