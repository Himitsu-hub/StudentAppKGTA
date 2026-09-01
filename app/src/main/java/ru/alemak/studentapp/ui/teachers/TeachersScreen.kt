package ru.alemak.studentapp.ui.teachers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.alemak.studentapp.data.model.FacultyCatalog
import ru.alemak.studentapp.data.model.Teacher
import ru.alemak.studentapp.data.model.TeacherLesson
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.ErrorState
import ru.alemak.studentapp.ui.components.LoadingState
import ru.alemak.studentapp.ui.components.OfflineBanner
import ru.alemak.studentapp.ui.components.swipeBack
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.DarkButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(
    onBack: () -> Unit,
    onOpenTeacher: (String) -> Unit,
    viewModel: TeachersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            AppTopBar(
                title = "Преподаватели",
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
            )
        },
        containerColor = scheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(scheme.background),
        ) {
            OfflineBanner(visible = state.usingCachedData)

            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("Поиск по имени, предмету…", color = scheme.onSurfaceVariant)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surface,
                        unfocusedContainerColor = scheme.surface,
                        disabledContainerColor = scheme.surface,
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                        focusedBorderColor = scheme.outline,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = scheme.primary,
                        focusedPlaceholderColor = scheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.departments.forEach { dept ->
                        FilterChip(
                            selected = state.selectedDept == dept,
                            onClick = { viewModel.setDepartment(dept) },
                            label = {
                                Text(dept, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (scheme.background == BlueKGTA) {
                                    Color.White.copy(alpha = 0.2f)
                                } else {
                                    scheme.surfaceVariant
                                },
                                labelColor = if (scheme.background == BlueKGTA) Color.White else scheme.onSurfaceVariant,
                                selectedContainerColor = if (scheme.background == BlueKGTA) Color.White else scheme.primaryContainer,
                                selectedLabelColor = if (scheme.background == BlueKGTA) BlueKGTA else scheme.onPrimaryContainer,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${state.filtered.size} преподавателей",
                    color = if (scheme.background == ru.alemak.studentapp.ui.theme.BlueKGTA) {
                        Color.White.copy(alpha = 0.75f)
                    } else {
                        scheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))

                when {
                    state.isLoading -> LoadingState("Загружаем преподавателей…")
                    state.error != null && state.teachers.isEmpty() -> ErrorState(state.error!!) {
                        viewModel.refresh()
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.filtered, key = { it.name }) { teacher ->
                                TeacherRow(teacher) {
                                    onOpenTeacher(teacher.name)
                                }
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherRow(teacher: Teacher, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeacherAvatar(teacher, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = teacher.name,
                color = scheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (teacher.position.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = teacher.position,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun TeacherDetailScreen(
    teacherName: String,
    onBack: () -> Unit,
    viewModel: TeachersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today by viewModel.todayState.collectAsStateWithLifecycle()
    val teacher = state.teachers.find { it.name == teacherName }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(teacherName) {
        if (state.teachers.none { it.name == teacherName }) {
            viewModel.refresh()
        }
        viewModel.loadTodayLessons(teacherName)
    }

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = { AppTopBar(title = "Преподаватель", onBack = onBack) },
        containerColor = scheme.background,
    ) { padding ->
        if (state.isLoading && teacher == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(scheme.background),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState("Загрузка…")
            }
            return@Scaffold
        }
        if (teacher == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(scheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text("Преподаватель не найден", color = scheme.onBackground)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(scheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                TeacherAvatar(teacher, size = 120.dp)
                Spacer(Modifier.height(16.dp))
                val onBlue = scheme.background == BlueKGTA
                Text(
                    teacher.name,
                    color = if (onBlue) Color.White else scheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    teacher.position,
                    color = if (onBlue) Color.White.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                TeacherTodayBlock(
                    lessons = today.lessons,
                    isLoading = today.isLoading && today.loadedFor == teacherName,
                    onBlue = onBlue,
                )
                Spacer(Modifier.height(12.dp))

                if (teacher.email.isNotEmpty()) {
                    val emailCardBg = if (onBlue) {
                        Color.White.copy(alpha = 0.15f)
                    } else {
                        DarkButton
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("email", teacher.email))
                                Toast.makeText(context, "Email скопирован", Toast.LENGTH_SHORT).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = emailCardBg),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Email", color = Color.White.copy(alpha = 0.75f))
                            Text(
                                teacher.email,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Нажмите, чтобы скопировать",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (teacher.subjects.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Предметы",
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            teacher.subjects.forEach { subject ->
                                Text(
                                    "• $subject",
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
            ) {
                Text("Назад")
            }
        }
    }
}

@Composable
private fun TeacherTodayBlock(
    lessons: List<TeacherLesson>,
    isLoading: Boolean,
    onBlue: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val cardBg = scheme.surface
    val lessonBg = if (onBlue || scheme.background == BlueKGTA) {
        Color(0xFFF0F3F8)
    } else {
        scheme.surfaceVariant
    }
    val muted = scheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Сегодня",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                isLoading && lessons.isEmpty() -> {
                    Text("Загружаем пары…", color = muted, style = MaterialTheme.typography.bodySmall)
                }
                lessons.isEmpty() -> {
                    Text(
                        "Пар на сегодня не найдено (или выходной).",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lessons.forEach { lesson ->
                            TeacherTodayLessonCard(lesson, lessonBg, muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherTodayLessonCard(
    lesson: TeacherLesson,
    lessonBg: Color,
    muted: Color,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = lessonBg),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lesson.time, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                if (lesson.type.isNotBlank()) {
                    Text(lesson.type, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(lesson.subject, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lesson.room.isNotBlank()) {
                    Text("каб. ${lesson.room}", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (lesson.group.isNotBlank()) {
                    Text(lesson.group, style = MaterialTheme.typography.bodySmall, color = muted)
                }
                if (lesson.faculty.isNotBlank()) {
                    Text(
                        FacultyCatalog.shortName(lesson.faculty),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherAvatar(teacher: Teacher, size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    if (teacher.photoUrl.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(teacher.photoUrl)
                .crossfade(true)
                .build(),
            contentDescription = teacher.name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                teacher.name.take(1),
                color = scheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.5f).sp,
            )
        }
    }
}
