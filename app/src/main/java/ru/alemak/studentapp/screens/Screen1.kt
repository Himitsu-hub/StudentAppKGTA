package ru.alemak.studentapp.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alemak.studentapp.parsing.ExcelParser
import ru.alemak.studentapp.parsing.Lesson
import ru.alemak.studentapp.parsing.ScheduleDay
import ru.alemak.studentapp.utils.DateUtils
import ru.alemak.studentapp.screens.HolidayUtils

// === DataStore ===
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("user_prefs")

class SchedulePrefs(private val context: Context) {
    companion object {
        private val COURSE = intPreferencesKey("selected_course")
        private val GROUP = stringPreferencesKey("selected_group")
        private val SUBGROUP = stringPreferencesKey("selected_subgroup")
    }

    val selectedCourse = context.dataStore.data.map { it[COURSE] ?: 1 }
    val selectedGroup = context.dataStore.data.map { it[GROUP] }
    val selectedSubgroup = context.dataStore.data.map { it[SUBGROUP] }

    suspend fun save(course: Int, group: String?, subgroup: String?) {
        context.dataStore.edit { prefs ->
            prefs[COURSE] = course
            if (group != null) prefs[GROUP] = group
            if (subgroup != null) prefs[SUBGROUP] = subgroup
        }
    }
}

@Composable
fun Screen1(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { SchedulePrefs(context) }
    val coroutineScope = rememberCoroutineScope()

    // Состояния
    var schedule by remember { mutableStateOf<List<ScheduleDay>>(emptyList()) }
    var availableGroups by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var selectedCourse by remember { mutableStateOf(1) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedSubgroup by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Добавили флаг — загружены ли prefs
    var isPrefsLoaded by remember { mutableStateOf(false) }

    var showCourseDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showSubgroupDialog by remember { mutableStateOf(false) }

    val currentWeekType = remember { DateUtils.getCurrentWeekType() }

    // ===== Функция загрузки расписания =====
    val loadSchedule: suspend (Context, Int, String, String?) -> Unit = { ctx, course, group, subgroup ->
        try {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                ExcelParser.parseScheduleForGroup(ctx, course, group, subgroup)
            }
            schedule = result
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки расписания"
            schedule = emptyList()
        } finally {
            isLoading = false
        }
    }

    // ===== 1. Сначала загружаем prefs =====
    LaunchedEffect(Unit) {
        selectedCourse = prefs.selectedCourse.first()
        selectedGroup = prefs.selectedGroup.first()
        selectedSubgroup = prefs.selectedSubgroup.first()
        isPrefsLoaded = true
    }

    // ===== 2. Теперь загружаем группы и расписание =====
    LaunchedEffect(isPrefsLoaded, selectedCourse) {
        if (!isPrefsLoaded) return@LaunchedEffect

        try {
            isLoading = true
            val groups = withContext(Dispatchers.IO) {
                ExcelParser.getAvailableGroupsWithSubgroups(context, selectedCourse)
            }
            availableGroups = groups

            val groupToLoad = selectedGroup ?: groups.keys.firstOrNull()
            val subgroupToLoad = selectedSubgroup ?: groups[groupToLoad]?.firstOrNull()

            if (groupToLoad != null) {
                selectedGroup = groupToLoad
                selectedSubgroup = subgroupToLoad
                loadSchedule(context, selectedCourse, groupToLoad, subgroupToLoad)
            } else {
                errorMessage = "Группы не найдены для курса $selectedCourse"
                isLoading = false
            }

        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки списка групп"
            isLoading = false
        }
    }

    // ================= UI =================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFF5F5F5))
    ) {
        Spacer(Modifier.height(25.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Расписание занятий",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = "Текущая неделя: $currentWeekType",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )

        SelectionButton("Курс: $selectedCourse") { showCourseDialog = true }
        SelectionButton(selectedGroup ?: "Выбрать группу") {
            if (availableGroups.isNotEmpty()) showGroupDialog = true
        }
        SelectionButton(selectedSubgroup ?: "Выбрать подгруппу") {
            if (selectedGroup != null) showSubgroupDialog = true
        }

        if (selectedGroup != null) {
            Text(
                text = "Текущая: $selectedGroup" +
                        if (selectedSubgroup != null && selectedSubgroup != "Основная") " • $selectedSubgroup" else "",
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> LoadingState()
            errorMessage != null -> ErrorState(errorMessage!!) {
                coroutineScope.launch {
                    selectedGroup?.let {
                        loadSchedule(context, selectedCourse, it, selectedSubgroup)
                    }
                }
            }
            schedule.isEmpty() -> EmptyState()
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(schedule) { day -> DayScheduleCard(day) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = { navController.navigateUp() }, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }

    // ==== Диалоги ====

    if (showCourseDialog) CourseDialog(
        selected = selectedCourse,
        onSelect = { course ->
            selectedCourse = course
            coroutineScope.launch { prefs.save(course, selectedGroup, selectedSubgroup) }
            showCourseDialog = false
        },
        onDismiss = { showCourseDialog = false }
    )

    if (showGroupDialog && availableGroups.isNotEmpty()) GroupDialog(
        groups = availableGroups.keys.toList(),
        onSelect = { group ->
            selectedGroup = group
            selectedSubgroup = availableGroups[group]?.firstOrNull()
            coroutineScope.launch {
                prefs.save(selectedCourse, group, selectedSubgroup)
                loadSchedule(context, selectedCourse, group, selectedSubgroup)
            }
            showGroupDialog = false
        },
        onDismiss = { showGroupDialog = false }
    )

    if (showSubgroupDialog && selectedGroup != null) SubgroupDialog(
        subgroups = availableGroups[selectedGroup] ?: emptyList(),
        groupName = selectedGroup!!,
        onSelect = { subgroup ->
            selectedSubgroup = subgroup
            coroutineScope.launch {
                prefs.save(selectedCourse, selectedGroup, subgroup)
                loadSchedule(context, selectedCourse, selectedGroup!!, subgroup)
            }
            showSubgroupDialog = false
        },
        onDismiss = { showSubgroupDialog = false }
    )
}

// =============================================
// Вспомогательные UI-компоненты
// =============================================

@Composable
fun SelectionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text)
    }
}

@Composable
fun CourseDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите курс", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            LazyColumn {
                items((1..4).toList()) { course ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { onSelect(course) }
                    ) {
                        Text("$course курс", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    )
}

@Composable
fun GroupDialog(groups: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите группу", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            LazyColumn {
                items(groups) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { onSelect(group) }
                    ) {
                        Text(group, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    )
}

@Composable
fun SubgroupDialog(subgroups: List<String>, groupName: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подгруппа для $groupName", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            LazyColumn {
                items(subgroups) { subgroup ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { onSelect(subgroup) }
                    ) {
                        Text(subgroup, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    )
}

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Загружаем расписание...", color = Color.Gray)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ошибка 😔", color = Color.Red, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Нет занятий 📚", color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Для выбранной группы расписание отсутствует",
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DayScheduleCard(day: ScheduleDay) {
    val holidayName = HolidayUtils.getHolidayName(DateUtils.getDateForDay(day.dayName))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(day.dayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            when {
                holidayName != null -> {
                    Text(holidayName, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Text("Праздничный день 🎉", color = Color.Gray)
                }
                day.lessons.isEmpty() -> Text("Пар нет", color = Color.Gray)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.lessons.forEach { LessonItem(it) }
                }
            }
        }
    }
}

@Composable
fun LessonItem(lesson: Lesson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lesson.time, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    lesson.type,
                    color = when (lesson.type.lowercase()) {
                        "лекция" -> Color(0xFF1976D2)
                        "практика" -> Color(0xFF388E3C)
                        "лабораторная" -> Color(0xFFF57C00)
                        else -> Color.Gray
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(lesson.subject, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (lesson.teacher.isNotBlank()) Text(lesson.teacher, color = Color.Gray)
            if (lesson.room.isNotBlank()) Text("Аудитория: ${lesson.room}", color = Color.Gray)
        }
    }
}
