package ru.alemak.studentapp.ui.teachers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.alemak.studentapp.data.model.Teacher
import ru.alemak.studentapp.data.model.TeacherLesson
import ru.alemak.studentapp.data.repository.ScheduleRepository
import ru.alemak.studentapp.data.repository.TeachersRepository
import ru.alemak.studentapp.util.TeacherUtils

data class TeachersUiState(
    val teachers: List<Teacher> = emptyList(),
    val filtered: List<Teacher> = emptyList(),
    val departments: List<String> = listOf("Все"),
    val selectedDept: String = "Все",
    val query: String = "",
    val isLoading: Boolean = true,
    val usingCachedData: Boolean = false,
    val error: String? = null,
)

data class TeacherTodayUiState(
    val lessons: List<TeacherLesson> = emptyList(),
    val isLoading: Boolean = false,
    val loadedFor: String? = null,
)

@HiltViewModel
class TeachersViewModel @Inject constructor(
    private val teachersRepository: TeachersRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeachersUiState())
    val uiState: StateFlow<TeachersUiState> = _uiState.asStateFlow()

    private val _todayState = MutableStateFlow(TeacherTodayUiState())
    val todayState: StateFlow<TeacherTodayUiState> = _todayState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, usingCachedData = false) }
            try {
                // Show Room cache first so VPN does not blank the list for 10+ seconds.
                val cached = teachersRepository.getTeachersFromCacheOnly()
                if (cached.teachers.isNotEmpty()) {
                    val sortedCached = TeacherUtils.sort(cached.teachers)
                    val deptsCached = listOf("Все") + TeacherUtils.departments(sortedCached)
                    _uiState.update {
                        it.copy(
                            teachers = sortedCached,
                            departments = deptsCached,
                            isLoading = false,
                            usingCachedData = true,
                            error = null,
                        ).let { state -> applyFilter(state) }
                    }
                }

                val result = teachersRepository.getTeachers(forceRefresh = true)
                val sorted = TeacherUtils.sort(result.teachers)
                val depts = listOf("Все") + TeacherUtils.departments(sorted)
                _uiState.update {
                    it.copy(
                        teachers = sorted,
                        departments = depts,
                        isLoading = false,
                        usingCachedData = result.fromCache,
                        error = if (sorted.isEmpty()) "Список преподавателей пуст" else null,
                    ).let { state -> applyFilter(state) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Ошибка загрузки",
                    )
                }
            }
        }
    }

    fun loadTodayLessons(teacherName: String) {
        if (teacherName.isBlank()) return
        val current = _todayState.value
        // Cache empty results too — "нет пар сегодня" is a valid answer, don't re-hit VPN.
        if (current.loadedFor == teacherName && !current.isLoading) {
            return
        }
        viewModelScope.launch {
            _todayState.update {
                it.copy(isLoading = true, lessons = emptyList(), loadedFor = teacherName)
            }
            val result = scheduleRepository.scheduleByTeacher(query = teacherName, day = "today")
            // Ignore stale response if user already opened another teacher.
            if (_todayState.value.loadedFor != teacherName) return@launch
            _todayState.update {
                it.copy(
                    isLoading = false,
                    lessons = result.lessons,
                    loadedFor = teacherName,
                )
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { applyFilter(it.copy(query = query)) }
    }

    fun setDepartment(dept: String) {
        _uiState.update { applyFilter(it.copy(selectedDept = dept)) }
    }

    fun indexOf(teacher: Teacher): Int = _uiState.value.teachers.indexOf(teacher).coerceAtLeast(0)

    private fun applyFilter(state: TeachersUiState): TeachersUiState {
        val filtered = state.teachers.filter { teacher ->
            val q = state.query.trim()
            val matchesSearch = q.isEmpty() ||
                teacher.name.contains(q, ignoreCase = true) ||
                teacher.position.contains(q, ignoreCase = true) ||
                teacher.subjects.any { it.contains(q, ignoreCase = true) }

            val dept = TeacherUtils.extractDepartment(teacher)
            val matchesDept = when (state.selectedDept) {
                "Все" -> true
                "Другие" -> dept.isEmpty()
                else -> dept.equals(state.selectedDept, ignoreCase = true)
            }
            matchesSearch && matchesDept
        }
        return state.copy(filtered = filtered)
    }
}
