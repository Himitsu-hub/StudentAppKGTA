package ru.alemak.studentapp.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.alemak.studentapp.parsing.ExcelParser
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import ru.alemak.studentapp.parsing.Lesson
import ru.alemak.studentapp.parsing.ScheduleDay

@Composable
fun Screen1(navController: NavController) {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf<List<ScheduleDay>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var availableGroups by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedSubgroup by remember { mutableStateOf<String?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showSubgroupDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Функция для загрузки расписания группы и подгруппы
    val loadSchedule: suspend (Context, String, String?) -> Unit = { ctx, group, subgroup ->
        try {
            Log.d("Screen1", "Загружаем расписание для группы: $group, подгруппа: $subgroup")
            val result = withContext(Dispatchers.IO) {
                ExcelParser.parseScheduleForGroup(ctx, group, subgroup)
            }
            schedule = result
            errorMessage = null
            Log.d("Screen1", "Загрузка завершена, дней: ${result.size}")
        } catch (e: Exception) {
            Log.e("Screen1", "Ошибка загрузки расписания: ${e.message}", e)
            errorMessage = "Ошибка загрузки расписания"
            schedule = emptyList()
        } finally {
            isLoading = false
        }
    }

    // Загружаем список групп при первом открытии
    LaunchedEffect(Unit) {
        try {
            Log.d("Screen1", "Загружаем список групп с подгруппами...")
            val groups = withContext(Dispatchers.IO) {
                ExcelParser.getAvailableGroupsWithSubgroups(context)
            }
            availableGroups = groups
            Log.d("Screen1", "Найдено групп: ${groups.size}")

            // Автоматически выбираем первую группу и первую подгруппу если есть
            if (groups.isNotEmpty()) {
                val firstGroup = groups.keys.first()
                selectedGroup = firstGroup
                val firstSubgroup = groups[firstGroup]?.firstOrNull()
                selectedSubgroup = firstSubgroup
                loadSchedule(context, firstGroup, firstSubgroup)
            } else {
                isLoading = false
                errorMessage = "Группы не найдены в файле"
            }
        } catch (e: Exception) {
            Log.e("Screen1", "Ошибка загрузки групп: ${e.message}", e)
            isLoading = false
            errorMessage = "Ошибка загрузки списка групп"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFF5F5F5)) // Светло-серый фон
    ) {
        // Заголовок с отступом 20dp снизу
        Text(
            text = "Расписание занятий",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            textAlign = TextAlign.Center
        )

        // Кнопка выбора группы
        Button(
            onClick = {
                Log.d("Screen1", "Открываем диалог выбора группы")
                showGroupDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            enabled = availableGroups.isNotEmpty()
        ) {
            Text(selectedGroup ?: "Выбрать группу")
        }

        // Кнопка выбора подгруппы
        Button(
            onClick = {
                Log.d("Screen1", "Открываем диалог выбора подгруппы")
                showSubgroupDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = selectedGroup != null && availableGroups[selectedGroup]?.size ?: 0 > 1
        ) {
            Text(selectedSubgroup ?: "Выбрать подгруппу")
        }

        // Отображаем текущую выбранную группу и подгруппу
        if (selectedGroup != null) {
            Text(
                text = "Текущая: $selectedGroup" +
                        if (selectedSubgroup != null && selectedSubgroup != "Основная") " • $selectedSubgroup" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Загрузка расписания...")
                        if (selectedGroup != null) {
                            Text(
                                text = "для $selectedGroup" +
                                        if (selectedSubgroup != null && selectedSubgroup != "Основная") " • $selectedSubgroup" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ошибка загрузки",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Неизвестная ошибка",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            selectedGroup?.let { group ->
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    loadSchedule(context, group, selectedSubgroup)
                                }
                            }
                        }) {
                            Text("Повторить")
                        }
                    }
                }
            }
            schedule.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Расписание не найдено",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedGroup != null) {
                                "для $selectedGroup" +
                                        if (selectedSubgroup != null && selectedSubgroup != "Основная") " • $selectedSubgroup" else ""
                            } else {
                                "Выберите группу"
                            },
                            color = Color.Gray
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(schedule) { day ->
                        DayScheduleCard(day = day)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigateUp() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Назад")
        }
    }

    // Диалог выбора группы
    if (showGroupDialog && availableGroups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = {
                Text(
                    "Выберите группу",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                LazyColumn {
                    items(availableGroups.keys.toList()) { group ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            onClick = {
                                Log.d("Screen1", "Выбрана группа: $group")
                                selectedGroup = group
                                // Автоматически выбираем первую подгруппу для этой группы
                                selectedSubgroup = availableGroups[group]?.firstOrNull()
                                showGroupDialog = false
                                isLoading = true
                                coroutineScope.launch {
                                    loadSchedule(context, group, selectedSubgroup)
                                }
                            }
                        ) {
                            Text(
                                text = group,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGroupDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог выбора подгруппы
    if (showSubgroupDialog && selectedGroup != null) {
        val subgroups = availableGroups[selectedGroup] ?: emptyList()
        AlertDialog(
            onDismissRequest = { showSubgroupDialog = false },
            title = {
                Text(
                    "Выберите подгруппу для $selectedGroup",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                LazyColumn {
                    items(subgroups) { subgroup ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            onClick = {
                                Log.d("Screen1", "Выбрана подгруппа: $subgroup")
                                selectedSubgroup = subgroup
                                showSubgroupDialog = false
                                isLoading = true
                                coroutineScope.launch {
                                    loadSchedule(context, selectedGroup!!, subgroup)
                                }
                            }
                        ) {
                            Text(
                                text = subgroup,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSubgroupDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}



@Composable
fun DayScheduleCard(day: ScheduleDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = day.dayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (day.lessons.isEmpty()) {
                Text(
                    text = "Пар нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    day.lessons.forEach { lesson ->
                        LessonItem(lesson = lesson)
                    }
                }
            }
        }
    }
}

@Composable
fun LessonItem(lesson: Lesson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lesson.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = lesson.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (lesson.type) {
                        "лекция" -> Color(0xFF1976D2)
                        "практика" -> Color(0xFF388E3C)
                        "лабораторная" -> Color(0xFFF57C00)
                        else -> Color.Gray
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lesson.subject,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )

            if (lesson.teacher.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lesson.teacher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            if (lesson.room.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Аудитория: ${lesson.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}