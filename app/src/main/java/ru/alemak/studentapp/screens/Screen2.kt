package ru.alemak.studentapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import ru.alemak.studentapp.screens.RemindersManager
import ru.alemak.studentapp.screens.Reminder
import ru.alemak.studentapp.ui.theme.BlueKGTA
import java.util.*

@Composable
fun Screen2(navController: NavController) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(emptyList<Reminder>()) }

    // Загружаем сохранённые напоминания при старте
    LaunchedEffect(Unit) {
        RemindersManager.load(context)
        reminders = RemindersManager.reminders
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf<Reminder?>(null) }

    fun updateReminders() {
        reminders = RemindersManager.reminders
    }


    Column(
        modifier = Modifier
            .background(BlueKGTA)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Напоминания",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить напоминание",
                    tint = BlueKGTA
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет напоминаний", color = Color.White, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderItem(
                        reminder = reminder,
                        onToggleCompleted = { reminderToComplete ->
                            showCompletionDialog = reminderToComplete
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text("Назад", color = BlueKGTA, fontSize = 18.sp)
        }
    }

    // Диалог добавления напоминания
    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onAddReminder = { newReminder ->
                RemindersManager.addReminder(context, newReminder)
                updateReminders()
                showAddDialog = false
            }
        )
    }

    // Диалог завершения напоминания
    showCompletionDialog?.let { reminder ->
        CompleteReminderDialog(
            reminder = reminder,
            onDismiss = { showCompletionDialog = null },
            onConfirmComplete = { reminderToDelete ->
                RemindersManager.deleteReminder(context, reminderToDelete.id)
                updateReminders()
                showCompletionDialog = null
            }
        )
    }
}

@Composable
fun ReminderItem(
    reminder: Reminder,
    onToggleCompleted: (Reminder) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(25.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(2.dp, Color.Gray, CircleShape)
                    .clickable { onToggleCompleted(reminder) },
                contentAlignment = Alignment.Center
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reminder.getFormattedDateTime(),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAddReminder: (Reminder) -> Unit
) {
    var reminderText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedHour by remember { mutableStateOf(12) }
    var selectedMinute by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .width(300.dp)
            ) {
                Text(
                    text = "Новое напоминание",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { reminderText = it },
                    label = { Text("Текст напоминания") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true
                )

                Text(
                    text = "Дата и время:",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = String.format(
                            Locale.getDefault(),
                            "%02d.%02d.%04d",
                            selectedDate.get(Calendar.DAY_OF_MONTH),
                            selectedDate.get(Calendar.MONTH) + 1,
                            selectedDate.get(Calendar.YEAR)
                        ),
                        onValueChange = {},
                        label = { Text("Дата") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                        onValueChange = {},
                        label = { Text("Время") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (reminderText.isNotBlank()) {
                                val calendar = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
                                    set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
                                    set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
                                    set(Calendar.HOUR_OF_DAY, selectedHour)
                                    set(Calendar.MINUTE, selectedMinute)
                                    set(Calendar.SECOND, 0)
                                }

                                val newReminder = Reminder(
                                    text = reminderText,
                                    dateTime = calendar.time
                                )
                                onAddReminder(newReminder)
                            }
                        },
                        enabled = reminderText.isNotBlank()
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
    }
}

@Composable
fun CompleteReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onConfirmComplete: (Reminder) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Завершить напоминание?") },
        text = {
            Text("Вы уверены, что хотите отметить напоминание \"${reminder.text}\" как выполненное?")
        },
        confirmButton = {
            Button(
                onClick = { onConfirmComplete(reminder) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Подтвердить",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Да, завершить", fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", fontSize = 16.sp)
            }
        }
    )
}
