package ru.alemak.studentapp.ui.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextDecoration
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
import kotlinx.coroutines.delay
import ru.alemak.studentapp.data.model.Reminder
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.EmptyState
import ru.alemak.studentapp.ui.components.swipeBack
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.DarkButton
import ru.alemak.studentapp.ui.theme.DarkNavy
import ru.alemak.studentapp.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    var editor by remember { mutableStateOf<Reminder?>(null) }
    var completingIds by remember { mutableStateOf(setOf<String>()) }

    fun markComplete(id: String) {
        completingIds = completingIds + id
    }

    fun undoComplete(id: String) {
        completingIds = completingIds - id
    }

    fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

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
        modifier = Modifier.swipeBack(onBack),
        topBar = { AppTopBar(title = "Напоминания", onBack = onBack) },
        containerColor = scheme.background,
        floatingActionButton = {
            val onBlue = scheme.background == BlueKGTA
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
                containerColor = if (onBlue) Color.White else scheme.primaryContainer,
                contentColor = if (onBlue) BlueKGTA else scheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(scheme.background)
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
                            isCompleting = reminder.id in completingIds,
                            onEdit = { editor = reminder },
                            onToggleComplete = {
                                if (reminder.id in completingIds) {
                                    undoComplete(reminder.id)
                                } else {
                                    markComplete(reminder.id)
                                }
                            },
                            onAutoDelete = {
                                viewModel.delete(reminder)
                                undoComplete(reminder.id)
                            },
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
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = scheme.onTertiaryContainer)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Нужны уведомления",
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer,
                )
                Text(
                    "Без разрешения напоминания не сработают",
                    color = scheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRequest) {
                Text("Разрешить", color = scheme.primary)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    isCompleting: Boolean,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onAutoDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val formatter = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    val alpha by animateFloatAsState(if (isCompleting) 0.55f else 1f, label = "rowAlpha")
    val circleColor = if (isCompleting) SuccessGreen else scheme.outline

    LaunchedEffect(isCompleting, reminder.id) {
        if (isCompleting) {
            delay(2000)
            onAutoDelete()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface.copy(alpha = alpha),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f * alpha)),
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(2.dp, circleColor, CircleShape)
                    .background(if (isCompleting) SuccessGreen else Color.Transparent)
                    .clickable(onClick = onToggleComplete),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleting) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Выполнено",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
            ) {
                Text(
                    reminder.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (isCompleting) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (isCompleting) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatter.format(Date(reminder.dateTimeMillis)),
                    color = scheme.onSurfaceVariant,
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
    val scheme = MaterialTheme.colorScheme
    val isLightBrand = scheme.background == BlueKGTA
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

    // Dark navy dialog + pure white action labels (readable in dark theme)
    val dialogBg = if (isLightBrand) Color.White else DarkNavy
    val fieldBg = if (isLightBrand) Color(0xFFF0F3F8) else DarkButton
    val titleColor = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val bodyColor = if (isLightBrand) Color(0xFF1A1A1A) else Color.White
    val mutedColor = if (isLightBrand) Color(0xFF5F6B7A) else Color(0xFFA8B2C4)
    val actionWhite = Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = dialogBg) {
            Column(modifier = Modifier.padding(16.dp).width(320.dp)) {
                Text(
                    if (isEditing) "Редактировать" else "Новое напоминание",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = titleColor,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = bodyColor,
                        unfocusedTextColor = bodyColor,
                        disabledTextColor = mutedColor,
                        cursorColor = if (isLightBrand) BlueKGTA else Color.White,
                        focusedLabelColor = if (isLightBrand) BlueKGTA else Color.White,
                        unfocusedLabelColor = mutedColor,
                        focusedBorderColor = if (isLightBrand) BlueKGTA else Color.White,
                        unfocusedBorderColor = mutedColor,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("Дата и время", fontWeight = FontWeight.Medium, color = bodyColor)
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
                        color = fieldBg,
                        border = BorderStroke(1.dp, mutedColor.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            Modifier.fillMaxSize().padding(start = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "%02d.%02d.%04d".format(day, month + 1, year),
                                color = bodyColor,
                            )
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
                        color = fieldBg,
                        border = BorderStroke(1.dp, mutedColor.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            Modifier.fillMaxSize().padding(start = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "%02d:%02d".format(hour, minute),
                                color = bodyColor,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Отмена",
                            color = if (isLightBrand) BlueKGTA else actionWhite,
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLightBrand) BlueKGTA else Color(0xFF3A5A8C),
                            contentColor = Color.White,
                            disabledContainerColor = if (isLightBrand) Color(0xFFCCD5E0) else Color(0xFF243049),
                            disabledContentColor = if (isLightBrand) Color(0xFF5F6B7A) else Color(0xFF6A7A94),
                        ),
                    ) {
                        Text(
                            if (isEditing) "Сохранить" else "Добавить",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
