package ru.alemak.studentapp.screens

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import ru.alemak.studentapp.ui.theme.BlueKGTA
import java.util.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Info

@Composable
fun Screen2(navController: NavController) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(emptyList<Reminder>()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionWarning(context)
        }
    }

    LaunchedEffect(Unit) {
        RemindersManager.load(context)
        reminders = RemindersManager.reminders.toList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var showEditorDialog by remember { mutableStateOf<Reminder?>(null) }
    var showCompletionDialog by remember { mutableStateOf<Reminder?>(null) }

    fun updateReminders() {
        reminders = RemindersManager.reminders.toList()
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
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = remember(reminders) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (!hasNotificationPermission) {
                WarningCard {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasPermission) {
                            showPermissionWarning(context)
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@FloatingActionButton
                        }
                    }

                    showEditorDialog = Reminder(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        dateTime = Date(System.currentTimeMillis() + 60_000)
                    )
                },
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
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет напоминаний", color = Color.White, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderItem(
                        reminder = reminder,
                        onToggleCompleted = { showCompletionDialog = it },
                        onEdit = { showEditorDialog = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigateUp() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text("Назад", color = BlueKGTA, fontSize = 18.sp)
        }
    }

    showEditorDialog?.let { reminder ->
        ReminderEditorDialog(
            reminder = reminder,
            onDismiss = { showEditorDialog = null },
            onSave = { updated ->
                if (RemindersManager.reminders.any { it.id == updated.id }) {
                    RemindersManager.updateReminder(context, updated)
                    cancelReminderAlarm(context, updated)
                } else {
                    RemindersManager.addReminder(context, updated)
                }
                scheduleReminderAlarm(context, updated)
                updateReminders()
                showEditorDialog = null
            }
        )
    }

    showCompletionDialog?.let { reminder ->
        CompleteReminderDialog(
            reminder = reminder,
            onDismiss = { showCompletionDialog = null },
            onConfirmComplete = {
                RemindersManager.deleteReminder(context, it.id)
                cancelReminderAlarm(context, it)
                updateReminders()
                showCompletionDialog = null
            }
        )
    }
}

@Composable
fun WarningCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
        border = BorderStroke(1.dp, Color(0xFFFFEAA7))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Предупреждение",
                tint = Color(0xFF856404),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Разрешение на уведомления",
                    color = Color(0xFF856404),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Для работы напоминаний нужно разрешение",
                    color = Color(0xFF856404),
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = onRequestPermission,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF856404))
            ) {
                Text("Разрешить")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderItem(
    reminder: Reminder,
    onToggleCompleted: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(reminder) },
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(2.dp, Color.Gray, CircleShape)
                    .clickable { onToggleCompleted(reminder) }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(reminder.getFormattedDateTime(), fontSize = 12.sp, color = Color.Gray)
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
        text = { Text("Отметить \"${reminder.text}\" как выполненное?") },
        confirmButton = {
            Button(onClick = { onConfirmComplete(reminder) }) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Да")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun ReminderEditorDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val isEditing = reminder.text.isNotBlank()
    val context = LocalContext.current

    var reminderText by remember { mutableStateOf(reminder.text) }

    val calendar = Calendar.getInstance().apply { time = reminder.dateTime }

    var selectedDate by remember { mutableStateOf(calendar) }
    var selectedHour by remember { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(calendar.get(Calendar.MINUTE)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate.set(year, month, day)
                showDatePicker = false
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showTimePicker) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                showTimePicker = false
            },
            selectedHour,
            selectedMinute,
            true
        ).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp).width(300.dp)) {

                Text(
                    if (isEditing) "Редактировать напоминание" else "Новое напоминание",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = reminderText,
                    onValueChange = { reminderText = it },
                    label = { Text("Текст") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                Text("Дата и время", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                Row {
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp).clickable { showDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(Modifier.fillMaxSize().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("%02d.%02d.%04d".format(
                                selectedDate.get(Calendar.DAY_OF_MONTH),
                                selectedDate.get(Calendar.MONTH) + 1,
                                selectedDate.get(Calendar.YEAR)
                            ))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp).clickable { showTimePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(Modifier.fillMaxSize().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("%02d:%02d".format(selectedHour, selectedMinute))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(
                                    selectedDate.get(Calendar.YEAR),
                                    selectedDate.get(Calendar.MONTH),
                                    selectedDate.get(Calendar.DAY_OF_MONTH),
                                    selectedHour,
                                    selectedMinute,
                                    0
                                )
                            }

                            val testReminderDate = Date(System.currentTimeMillis() + 30_000)

                            if (reminderText.isNotBlank()) {
                                onSave(
                                    Reminder(
                                        id = reminder.id,
                                        text = reminderText,
                                        dateTime = testReminderDate
                                    )
                                )
                            }
                        },
                        enabled = reminderText.isNotBlank()
                    ) {
                        Text(if (isEditing) "Сохранить" else "Добавить")
                    }
                }
            }
        }
    }
}

private fun scheduleReminderAlarm(context: Context, reminder: Reminder) {
    createNotificationChannel(context)

    if (reminder.dateTime.time <= System.currentTimeMillis()) {
        return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("text", reminder.text)
        putExtra("reminderId", reminder.id)
        data = "reminder://${reminder.id}".toUri()
        action = "REMINDER_ACTION_${reminder.id}"
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.dateTime.time,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                reminder.dateTime.time,
                pendingIntent
            )
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun cancelReminderAlarm(context: Context, reminder: Reminder) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        data = "reminder://${reminder.id}".toUri()
        action = "REMINDER_ACTION_${reminder.id}"
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    pendingIntent?.let {
        alarmManager.cancel(it)
        it.cancel()
    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            val channel = NotificationChannel(
                "reminders",
                "Напоминания",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для напоминаний"
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        } catch (e: Exception) {
        }
    }
}

private fun showPermissionWarning(context: Context) {
    Toast.makeText(
        context,
        "Разрешите уведомления для работы напоминаний",
        Toast.LENGTH_LONG
    ).show()
}