package ru.alemak.studentapp.ui.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import ru.alemak.studentapp.data.model.Reminder
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.EmptyState
import ru.alemak.studentapp.ui.theme.BlueKGTA

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<Reminder?>(null) }
    var completeCandidate by remember { mutableStateOf<Reminder?>(null) }

    fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // State that re-reads after system permission dialog
    var hasNotifPermission by remember { mutableStateOf(checkNotificationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotifPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Разрешите уведомления для работы напоминаний",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // When user returns from system settings / permission dialog — refresh banner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Напоминания", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!hasNotifPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@FloatingActionButton
                    }
                    editor = Reminder(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        dateTimeMillis = System.currentTimeMillis() + 60_000,
                    )
                },
                containerColor = Color.White,
                contentColor = BlueKGTA,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BlueKGTA)
                .padding(16.dp),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                PermissionCard {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (reminders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Нет напоминаний", "Нажмите +, чтобы добавить")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onEdit = { editor = reminder },
                            onComplete = { completeCandidate = reminder },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    editor?.let { reminder ->
        ReminderEditorDialog(
            reminder = reminder,
            onDismiss = { editor = null },
            onSave = { text, millis ->
                viewModel.save(reminder.id, text, millis)
                editor = null
            },
        )
    }

    completeCandidate?.let { reminder ->
        AlertDialog(
            onDismissRequest = { completeCandidate = null },
            title = { Text("Завершить напоминание?") },
            text = { Text("Удалить «${reminder.text}»?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(reminder)
                    completeCandidate = null
                }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Да")
                }
            },
            dismissButton = {
                TextButton(onClick = { completeCandidate = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF856404))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Нужны уведомления", fontWeight = FontWeight.Bold, color = Color(0xFF856404))
                Text(
                    "Без разрешения напоминания не сработают",
                    color = Color(0xFF856404),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRequest) { Text("Разрешить") }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
) {
    val formatter = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Gray, CircleShape)
                    .clickable(onClick = onComplete),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.text, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatter.format(Date(reminder.dateTimeMillis)),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onSave: (text: String, dateTimeMillis: Long) -> Unit,
) {
    val context = LocalContext.current
    val isEditing = reminder.text.isNotBlank()
    var text by remember { mutableStateOf(reminder.text) }

    val calendar = remember {
        Calendar.getInstance().apply { timeInMillis = reminder.dateTimeMillis }
    }
    var year by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp).width(320.dp)) {
                Text(
                    if (isEditing) "Редактировать" else "Новое напоминание",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        disabledTextColor = Color.Black,
                        cursorColor = BlueKGTA,
                        focusedLabelColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("Дата и время", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        year = y
                                        month = m
                                        day = d
                                    },
                                    year,
                                    month,
                                    day,
                                ).show()
                            },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                    ) {
                        Box(Modifier.fillMaxSize().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("%02d.%02d.%04d".format(day, month + 1, year))
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        hour = h
                                        minute = m
                                    },
                                    hour,
                                    minute,
                                    true,
                                ).show()
                            },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                    ) {
                        Box(Modifier.fillMaxSize().padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("%02d:%02d".format(hour, minute))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val millis = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            if (millis <= System.currentTimeMillis()) {
                                Toast.makeText(
                                    context,
                                    "Выберите время в будущем",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@Button
                            }
                            onSave(text.trim(), millis)
                        },
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueKGTA),
                    ) {
                        Text(if (isEditing) "Сохранить" else "Добавить")
                    }
                }
            }
        }
    }
}
