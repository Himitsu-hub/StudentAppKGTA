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
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.alemak.studentapp.R
import ru.alemak.studentapp.parsing.ExcelParser
import ru.alemak.studentapp.parsing.Lesson
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val currentWeekType = DateUtils.getCurrentWeekType()

    var nextLesson by remember { mutableStateOf<Lesson?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // === Загрузка ближайшей пары ===
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = SchedulePrefs(context)
                val course = prefs.selectedCourse.first()
                val group = prefs.selectedGroup.first()
                val subgroup = prefs.selectedSubgroup.first()

                if (group != null) {
                    val schedule = ExcelParser.parseScheduleForGroup(context, course, group, subgroup)
                    val today = DateUtils.getTodayName()
                    val todaySchedule = schedule.find { it.dayName.equals(today, ignoreCase = true) }

                    val upcoming = todaySchedule?.lessons?.firstOrNull()
                    nextLesson = upcoming
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // === Текущее число и день недели ===
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
    val todayDate = dateFormat.format(Date()).replaceFirstChar { it.uppercaseChar() }

    Column(
        modifier = Modifier
            .background(BlueKGTA)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Логотип
        Image(
            painter = painterResource(id = R.drawable.kgta_logo),
            contentDescription = "Логотип КГТА",
            modifier = Modifier.size(225.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Белый овал с числителем/знаменателем
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

        Spacer(modifier = Modifier.height(16.dp))

        // День и дата — теперь белым цветом
        Text(
            text = todayDate,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        // === Карточка “Следующая пара” ===
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            ) {
                if (nextLesson != null) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally // ⬅ центрируем всё содержимое
                    ) {
                        Text(
                            "Следующая пара:",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            nextLesson!!.subject,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "${nextLesson!!.time} — ${nextLesson!!.room}",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            nextLesson!!.teacher,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            nextLesson!!.type,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "Сегодня занятий нет 🎉",
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

        Spacer(modifier = Modifier.height(50.dp))

        // === Кнопки ===
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
                    modifier = Modifier
                        .width(250.dp)
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Расписание", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
                }

                Button(
                    onClick = { navController.navigate("screen2") },
                    modifier = Modifier
                        .width(250.dp)
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Напоминания", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { navController.navigate("screen3") },
                modifier = Modifier
                    .width(120.dp)
                    .height(130.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
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
}
