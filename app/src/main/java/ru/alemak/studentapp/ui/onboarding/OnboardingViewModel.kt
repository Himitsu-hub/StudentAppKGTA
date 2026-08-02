package ru.alemak.studentapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.local.UserPreferences
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.widget.ScheduleWidgetUpdater

data class OnboardingUiState(
    val course: Int = 1,
    val group: String? = null,
    val subgroup: String? = null,
    val groups: Map<String, List<String>> = emptyMap(),
    val loadingGroups: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val canFinish: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val userPreferences: UserPreferences,
    private val widgetUpdater: ScheduleWidgetUpdater,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        reloadGroups()
    }

    fun selectCourse(course: Int) {
        _uiState.update {
            it.copy(course = course, group = null, subgroup = null, canFinish = false, error = null)
        }
        reloadGroups()
    }

    fun selectGroup(group: String) {
        val subs = _uiState.value.groups[group].orEmpty()
        // One subgroup → auto-pick; several → user must tap one
        val sub = subs.singleOrNull()
        _uiState.update {
            it.copy(
                group = group,
                subgroup = sub,
                canFinish = canFinish(group, sub, subs),
                error = null,
            )
        }
    }

    fun selectSubgroup(subgroup: String) {
        _uiState.update {
            val group = it.group
            val subs = group?.let { g -> it.groups[g] }.orEmpty()
            it.copy(
                subgroup = subgroup,
                canFinish = canFinish(group, subgroup, subs),
                error = null,
            )
        }
    }

    private fun canFinish(group: String?, subgroup: String?, subs: List<String>): Boolean {
        if (group.isNullOrBlank()) return false
        if (subs.isEmpty()) return true
        return !subgroup.isNullOrBlank() && (subgroup in subs || subs.size == 1)
    }

    fun reloadGroups() {
        viewModelScope.launch {
            val course = _uiState.value.course
            _uiState.update { it.copy(loadingGroups = true, error = null) }
            try {
                val groups = scheduleRepository.getGroups(course)
                _uiState.update {
                    it.copy(
                        groups = groups,
                        loadingGroups = false,
                        error = if (groups.isEmpty()) "Нет групп для $course курса на сервере" else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingGroups = false,
                        error = e.message ?: "Не удалось загрузить группы",
                    )
                }
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        val s = _uiState.value
        val group = s.group ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            try {
                userPreferences.save(s.course, group, s.subgroup)
                userPreferences.setOnboardingDone(true)
                widgetUpdater.updateAsync()
                _uiState.update { it.copy(saving = false) }
                onDone()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saving = false, error = e.message ?: "Не удалось сохранить")
                }
            }
        }
    }
}
