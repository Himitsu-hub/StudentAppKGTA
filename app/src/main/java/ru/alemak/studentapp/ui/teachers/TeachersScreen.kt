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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import ru.alemak.studentapp.data.model.Teacher
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.ErrorState
import ru.alemak.studentapp.ui.components.LoadingState
import ru.alemak.studentapp.ui.components.OfflineBanner
import ru.alemak.studentapp.ui.theme.BlueKGTA
// LoadingState used by list + detail screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(
    onBack: () -> Unit,
    onOpenTeacher: (String) -> Unit,
    viewModel: TeachersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Преподаватели",
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BlueKGTA),
        ) {
            OfflineBanner(visible = state.usingCachedData)

            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск по имени, предмету…") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedTextColor = Color(0xFF1A1A1A),
                        unfocusedTextColor = Color(0xFF1A1A1A),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = BlueKGTA,
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
                                containerColor = Color.White.copy(alpha = 0.2f),
                                labelColor = Color.White,
                                selectedContainerColor = Color.White,
                                selectedLabelColor = BlueKGTA,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${state.filtered.size} преподавателей",
                    color = Color.White.copy(alpha = 0.75f),
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

// White cards need fixed dark text (system dark theme otherwise paints names white → invisible)
private val TeacherNameColor = Color(0xFF1A1A1A)
private val TeacherMetaColor = Color(0xFF5F6B7A)

@Composable
private fun TeacherRow(teacher: Teacher, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeacherAvatar(teacher, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = teacher.name,
                color = TeacherNameColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (teacher.position.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = teacher.position,
                    color = TeacherMetaColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // subjects/disciplines hidden — often empty from API
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
    val teacher = state.teachers.find { it.name == teacherName }
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(teacherName) {
        if (state.teachers.none { it.name == teacherName }) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Преподаватель", onBack = onBack) },
    ) { padding ->
        if (state.isLoading && teacher == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BlueKGTA),
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
                    .background(BlueKGTA),
                contentAlignment = Alignment.Center,
            ) {
                Text("Преподаватель не найден", color = Color.White)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BlueKGTA)
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
                Text(
                    teacher.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    teacher.position,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                if (teacher.email.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("email", teacher.email))
                                Toast.makeText(context, "Email скопирован", Toast.LENGTH_SHORT).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(teacher.email, color = Color(0xFF2980B9), fontWeight = FontWeight.Medium)
                            Text(
                                "Нажмите, чтобы скопировать",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                // disciplines block removed (data usually empty)
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Назад")
            }
        }
    }
}

@Composable
private fun TeacherAvatar(teacher: Teacher, size: androidx.compose.ui.unit.Dp) {
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
                .background(Color(0xFF1A5276)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                teacher.name.take(1),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.5f).sp,
            )
        }
    }
}
