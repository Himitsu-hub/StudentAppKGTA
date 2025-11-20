// MainScreen.kt
package ru.alemak.studentapp.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.alemak.studentapp.R
import ru.alemak.studentapp.parsing.ExcelParser
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

/** Публичные data class для работы с расписанием */
data class Lesson(
    val subject: String,
    val time: String,
    val room: String,
    val teacher: String,
    val type: String
)

data class DaySchedule(
    val dayName: String,
    val lessons: List<Lesson>
)

/** Кеш расписания по группе */
object ScheduleCache {
    private val cache = mutableMapOf<String, List<DaySchedule>>()
    fun get(key: String): List<DaySchedule>? = cache[key]
    fun put(key: String, schedule: List<DaySchedule>) {
        cache[key] = schedule
    }
}

/** ViewModel для MainScreen */
/** ViewModel для MainScreen */
class MainScreenViewModel : ViewModel() {
    private val _nextLesson = MutableStateFlow<Lesson?>(null)
    val nextLesson: StateFlow<Lesson?> = _nextLesson

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadNextLesson(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = SchedulePrefs(context)
                val course = prefs.selectedCourse.first()
                val group = prefs.selectedGroup.first()
                val subgroup = prefs.selectedSubgroup.first()

                if (group != null) {
                    val schedule: List<DaySchedule> = ScheduleCache.get(group) ?: run {
                        val parsed = ExcelParser.parseScheduleForGroup(context, course, group, subgroup)
                            .map { day ->
                                DaySchedule(
                                    dayName = day.dayName,
                                    lessons = day.lessons.map { lesson ->
                                        Lesson(
                                            subject = lesson.subject,
                                            time = lesson.time,
                                            room = lesson.room,
                                            teacher = lesson.teacher,
                                            type = lesson.type
                                        )
                                    }
                                )
                            }
                        ScheduleCache.put(group, parsed)
                        parsed
                    }

                    _nextLesson.value = findNextLesson(schedule)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** ИСПРАВЛЕННЫЙ метод поиска следующей пары */
    private fun findNextLesson(schedule: List<DaySchedule>): Lesson? {
        val nowCal = Calendar.getInstance()
        val today = DateUtils.getTodayName()
        val sdf = SimpleDateFormat("HH:mm", Locale("ru"))

        // Находим расписание для сегодняшнего дня
        val todaySchedule = schedule.find { it.dayName.equals(today, ignoreCase = true) }

        // Ищем ближайшую пару сегодня
        val upcomingToday = todaySchedule?.lessons?.firstOrNull { lesson ->
            try {
                val startTime = lesson.time.split("-")[0].trim()

                val parsedLesson = sdf.parse(startTime) ?: return@firstOrNull false

                // Делам календарь "сегодня + время пары"
                val lessonCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parsedLesson.hours)
                    set(Calendar.MINUTE, parsedLesson.minutes)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                lessonCal.timeInMillis > nowCal.timeInMillis
            } catch (e: Exception) {
                false
            }
        }

        if (upcomingToday != null) return upcomingToday

        // Если сегодня пар больше нет — ищем ближайший день с парами
        val days = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val currIndex = days.indexOfFirst { it.equals(today, ignoreCase = true) }

        if (currIndex != -1) {
            for (i in 1..6) {
                val nextDayIndex = (currIndex + i) % days.size
                val nextDayName = days[nextDayIndex]
                val nextDaySchedule = schedule.find { it.dayName.equals(nextDayName, ignoreCase = true) }

                val firstLesson = nextDaySchedule?.lessons?.firstOrNull()
                if (firstLesson != null) return firstLesson
            }
        }

        return null
    }
}

// --------------------- Composable ---------------------

private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))

@Composable
fun MainScreen(navController: NavController, viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val currentWeekType = DateUtils.getCurrentWeekType()

    val nextLesson by viewModel.nextLesson.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val todayDate = remember { dateFormat.format(Date()).replaceFirstChar { it.uppercaseChar() } }

    // Запускаем загрузку один раз
    LaunchedEffect(Unit) {
        viewModel.loadNextLesson(context)
    }

    Column(
        modifier = Modifier
            .background(BlueKGTA)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        LogoSection()
        Spacer(modifier = Modifier.height(32.dp))
        WeekTypeBox(currentWeekType)
        Spacer(modifier = Modifier.height(16.dp))
        Text(todayDate, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            NextLessonCard(nextLesson)
        }

        Spacer(modifier = Modifier.height(50.dp))
        NavigationButtons(navController)
    }
}

@Composable
private fun LogoSection() {
    Image(
        painter = painterResource(id = R.drawable.kgta_logo),
        contentDescription = "Логотип КГТА",
        modifier = Modifier.size(225.dp)
    )
}

@Composable
private fun WeekTypeBox(currentWeekType: String) {
    Box(
        modifier = Modifier
            .width(265.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentWeekType,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun NextLessonCard(nextLesson: Lesson?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        if (nextLesson != null) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Следующая пара:", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(nextLesson.subject, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Text("${nextLesson.time} — ${nextLesson.room}", fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Text(nextLesson.teacher, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Text(nextLesson.type, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center)
            }
        } else {
            Text("Сегодня занятий нет 🎉",
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NavigationButtons(navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { navController.navigate("screen1") },
                modifier = Modifier.width(250.dp).height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Расписание", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
            }

            Button(
                onClick = { navController.navigate("screen2") },
                modifier = Modifier.width(250.dp).height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Напоминания", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = { navController.navigate("screen3") },
            modifier = Modifier.width(120.dp).height(130.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.teacher_icon),
                contentDescription = "Преподаватели",
                modifier = Modifier.size(80.dp)
            )
        }
    }
}