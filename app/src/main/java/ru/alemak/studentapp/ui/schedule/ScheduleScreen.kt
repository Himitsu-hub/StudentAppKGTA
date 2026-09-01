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
import ru.alemak.studentapp.data.model.FacultyCatalog
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

    var showFacultyDialog by remember { mutableStateOf(false) }
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
                        text = "Факультет / курс / группа / подгруппа",
                        color = if (isLightBrand) Color(0xFF5F6B7A) else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectionChip(
                            text = FacultyCatalog.shortName(state.faculty),
                            onClick = { showFacultyDialog = true },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        SelectionChip(
                            text = "${state.course} курс",
                            onClick = { showCourseDialog = true },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SelectionChip(
                        text = "Группа: ${state.group ?: "выбрать"}",
                        onClick = { if (state.groups.isNotEmpty()) showGroupDialog = true },
                        compact = true,
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
                        compact = true,
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

    if (showFacultyDialog) {
        SimpleListDialog(
            title = "Факультет",
            items = FacultyCatalog.all.map {
                "${it.short} — ${FacultyCatalog.fullName(it.id)}" to it.id
            },
            onSelect = {
                viewModel.selectFaculty(it)
                showFacultyDialog = false
            },
            onDismiss = { showFacultyDialog = false },
        )
    }

    if (showCourseDialog) {
        val courses = FacultyCatalog.courses(state.faculty)
        SimpleListDialog(
            title = "Выберите курс",
            items = courses.map { "$it курс" to it },
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
private fun SelectionChip(
    text: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
    // Dark theme: navy chip + pure white text (primaryContainer was too dim)
    val chipBg = if (isLightBrand) BlueKGTA else DarkButton
    val vPad = if (compact) 10.dp else 14.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 3.dp else 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        colors = CardDefaults.cardColors(containerColor = chipBg),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = vPad),
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
    // Always high-contrast: pure white labels in dark theme (never muted blue).
    val dialogBg = if (isLightBrand) Color.White else DarkNavy
    // Slightly lighter row than navy so white text pops.
    val itemBg = if (isLightBrand) Color(0xFFF0F3F8) else Color(0xFF2E3D5C)
    val titleColor = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val itemText = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val cancelColor = if (isLightBrand) BlueKGTA else Color.White
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.first }) { (label, value) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = itemBg,
                            contentColor = itemText,
                        ),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Text(
                            text = label,
                            color = itemText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = cancelColor, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = dialogBg,
        titleContentColor = titleColor,
        textContentColor = itemText,
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
            val isOnline = lesson.room.contains("онлайн", ignoreCase = true)
                || lesson.subject.contains("онлайн", ignoreCase = true)
            val onlineColor = Color(0xFFE8624D)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lesson.time, color = accent, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOnline) {
                        Text(
                            text = "ОНЛАЙН",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(onlineColor, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
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
            }
            Spacer(Modifier.height(4.dp))
            Text(lesson.subject, fontWeight = FontWeight.SemiBold, color = textDark)
            if (lesson.teacher.isNotBlank()) {
                Text(lesson.teacher, color = muted)
            }
            if (lesson.room.isNotBlank()) {
                val roomLabel = when {
                    isOnline && lesson.room.equals("онлайн", ignoreCase = true) -> "Формат: онлайн"
                    isOnline -> "Аудитория / формат: ${lesson.room}"
                    else -> "Аудитория: ${lesson.room}"
                }
                Text(roomLabel, color = if (isOnline) onlineColor else muted)
            }
        }
    }
}
