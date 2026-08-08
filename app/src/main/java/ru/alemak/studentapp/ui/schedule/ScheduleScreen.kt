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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import ru.alemak.studentapp.ui.components.UpdatedAtLabel
import ru.alemak.studentapp.ui.components.swipeBack
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.DarkButton
import ru.alemak.studentapp.ui.theme.DarkNavy
import ru.alemak.studentapp.util.DateUtils
import ru.alemak.studentapp.util.HolidayUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    // Light schedule was always light-gray (not brand blue); dark uses theme background
    val isLightBrand = scheme.background == BlueKGTA
    val scheduleBg = if (isLightBrand) Color(0xFFF5F7FA) else scheme.background
    val accent = if (isLightBrand) BlueKGTA else scheme.primary

    var showCourseDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showSubgroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            AppTopBar(
                title = "Расписание",
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
            )
        },
        containerColor = scheduleBg,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(scheduleBg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheduleBg),
            ) {
                OfflineBanner(
                    visible = state.usingCachedData,
                    updatedLabel = state.updatedLabel,
                )
                if (!state.usingCachedData) {
                    UpdatedAtLabel(
                        text = state.updatedLabel,
                        color = if (isLightBrand) Color(0xFF5F6B7A) else scheme.onSurfaceVariant,
                    )
                }

                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Неделя: ${state.weekType}",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )

                    Text(
                        text = "Нажмите, чтобы сменить (курс / группа / подгруппа)",
                        color = if (isLightBrand) Color(0xFF5F6B7A) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    SelectionChip(
                        text = "Курс: ${state.course}",
                        onClick = { showCourseDialog = true },
                    )
                    SelectionChip(
                        text = "Группа: ${state.group ?: "выбрать"}",
                        onClick = { if (state.groups.isNotEmpty()) showGroupDialog = true },
                    )
                    SelectionChip(
                        text = "Подгруппа: ${state.subgroup ?: "выбрать"}",
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
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
    // Dark theme: navy chip + pure white text (primaryContainer was too dim)
    val chipBg = if (isLightBrand) BlueKGTA else DarkButton
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = chipBg),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
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
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
    // High contrast: dark navy dialog + white labels (readable in dark theme)
    val dialogBg = if (isLightBrand) Color.White else DarkNavy
    val itemBg = if (isLightBrand) Color(0xFFF0F3F8) else DarkButton
    val titleColor = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val itemText = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val cancelColor = if (isLightBrand) BlueKGTA else Color.White
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = titleColor) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.first }) { (label, value) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = itemBg),
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Text(
                            text = label,
                            color = itemText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = cancelColor, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = dialogBg,
    )
}

@Composable
private fun DayScheduleCard(day: ScheduleDay) {
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
    val cardBg = if (isLightBrand) Color.White else scheme.surface
    val accent = if (isLightBrand) BlueKGTA else scheme.primary
    val muted = if (isLightBrand) Color(0xFF5F6B7A) else scheme.onSurfaceVariant
    val holidayName = HolidayUtils.getHolidayName(DateUtils.getDateForDay(day.dayName))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = day.dayName,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            when {
                holidayName != null || day.lessons.any { it.type.equals("праздник", true) } -> {
                    val name = holidayName
                        ?: day.lessons.firstOrNull { it.type.equals("праздник", true) }?.subject
                        ?: "Праздничный день"
                    Text(name, color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("Занятий нет", color = muted)
                }
                day.lessons.isEmpty() -> Text("Пар нет", color = muted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.lessons.forEach { LessonCard(it) }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson) {
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
    val lessonBg = if (isLightBrand) Color(0xFFF0F3F8) else scheme.surfaceVariant
    val accent = if (isLightBrand) BlueKGTA else scheme.primary
    val textDark = if (isLightBrand) Color(0xFF1A1A1A) else scheme.onSurface
    val muted = if (isLightBrand) Color(0xFF5F6B7A) else scheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = lessonBg),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lesson.time, color = accent, fontWeight = FontWeight.Bold)
                Text(
                    text = lesson.type,
                    color = when (lesson.type.lowercase()) {
                        "лекция" -> if (isLightBrand) Color(0xFF1976D2) else Color(0xFF64B5F6)
                        "практика" -> if (isLightBrand) Color(0xFF388E3C) else Color(0xFF81C784)
                        "лабораторная" -> if (isLightBrand) Color(0xFFF57C00) else Color(0xFFFFB74D)
                        else -> muted
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(lesson.subject, fontWeight = FontWeight.SemiBold, color = textDark)
            if (lesson.teacher.isNotBlank()) {
                Text(lesson.teacher, color = muted)
            }
            if (lesson.room.isNotBlank()) {
                Text("Аудитория: ${lesson.room}", color = muted)
            }
        }
    }
}
