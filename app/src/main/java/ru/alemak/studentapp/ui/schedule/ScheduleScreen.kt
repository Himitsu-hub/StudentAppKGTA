package ru.alemak.studentapp.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.ScheduleDay
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.EmptyState
import ru.alemak.studentapp.ui.components.ErrorState
import ru.alemak.studentapp.ui.components.LoadingState
import ru.alemak.studentapp.ui.components.OfflineBanner
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.util.DateUtils
import ru.alemak.studentapp.util.HolidayUtils

// Explicit light palette so schedule stays readable in any system theme
private val ScheduleBg = Color(0xFFF5F7FA)
private val CardWhite = Color.White
private val LessonBg = Color(0xFFF0F3F8)
private val TextDark = Color(0xFF1A1A1A)
private val TextMuted = Color(0xFF5F6B7A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCourseDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showSubgroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Расписание",
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
            )
        },
        containerColor = ScheduleBg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScheduleBg),
        ) {
            OfflineBanner(visible = state.usingCachedData)

            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Неделя: ${state.weekType}",
                    color = BlueKGTA,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                SelectionChip(
                    text = "Курс: ${state.course}",
                    onClick = { showCourseDialog = true },
                )
                SelectionChip(
                    text = state.group ?: "Выбрать группу",
                    onClick = { if (state.groups.isNotEmpty()) showGroupDialog = true },
                )
                SelectionChip(
                    text = state.subgroup ?: "Выбрать подгруппу",
                    onClick = {
                        if (!state.group.isNullOrBlank() &&
                            state.groups[state.group].orEmpty().isNotEmpty()
                        ) {
                            showSubgroupDialog = true
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                when {
                    state.isLoading || !state.prefsLoaded -> LoadingState("Загружаем расписание…")
                    state.error != null && state.schedule.isEmpty() -> ErrorState(state.error!!) {
                        viewModel.refresh()
                    }
                    state.schedule.isEmpty() -> EmptyState(
                        "Нет занятий",
                        "Для выбранной группы расписание отсутствует",
                    )
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.schedule, key = { it.dayName }) { day ->
                                DayScheduleCard(day)
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showCourseDialog) {
        SimpleListDialog(
            title = "Выберите курс",
            items = (1..4).map { "$it курс" to it },
            onSelect = {
                viewModel.selectCourse(it)
                showCourseDialog = false
            },
            onDismiss = { showCourseDialog = false },
        )
    }

    if (showGroupDialog) {
        SimpleListDialog(
            title = "Выберите группу",
            items = state.groups.keys.sorted().map { it to it },
            onSelect = {
                viewModel.selectGroup(it)
                showGroupDialog = false
            },
            onDismiss = { showGroupDialog = false },
        )
    }

    if (showSubgroupDialog) {
        val group = state.group.orEmpty()
        SimpleListDialog(
            title = "Подгруппа для $group",
            items = state.groups[group].orEmpty().map { it to it },
            onSelect = {
                viewModel.selectSubgroup(it)
                showSubgroupDialog = false
            },
            onDismiss = { showSubgroupDialog = false },
        )
    }
}

@Composable
private fun SelectionChip(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BlueKGTA),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun <T> SimpleListDialog(
    title: String,
    items: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextDark) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.first }) { (label, value) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = LessonBg),
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Text(
                            text = label,
                            color = TextDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = BlueKGTA) }
        },
        containerColor = CardWhite,
    )
}

@Composable
private fun DayScheduleCard(day: ScheduleDay) {
    val holidayName = HolidayUtils.getHolidayName(DateUtils.getDateForDay(day.dayName))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = day.dayName,
                style = MaterialTheme.typography.titleLarge,
                color = BlueKGTA,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            when {
                holidayName != null || day.lessons.any { it.type.equals("праздник", true) } -> {
                    val name = holidayName
                        ?: day.lessons.firstOrNull { it.type.equals("праздник", true) }?.subject
                        ?: "Праздничный день"
                    Text(name, color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("Занятий нет", color = TextMuted)
                }
                day.lessons.isEmpty() -> Text("Пар нет", color = TextMuted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.lessons.forEach { LessonCard(it) }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LessonBg),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lesson.time, color = BlueKGTA, fontWeight = FontWeight.Bold)
                Text(
                    text = lesson.type,
                    color = when (lesson.type.lowercase()) {
                        "лекция" -> Color(0xFF1976D2)
                        "практика" -> Color(0xFF388E3C)
                        "лабораторная" -> Color(0xFFF57C00)
                        else -> TextMuted
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(lesson.subject, fontWeight = FontWeight.SemiBold, color = TextDark)
            if (lesson.teacher.isNotBlank()) {
                Text(lesson.teacher, color = TextMuted)
            }
            if (lesson.room.isNotBlank()) {
                Text("Аудитория: ${lesson.room}", color = TextMuted)
            }
        }
    }
}
