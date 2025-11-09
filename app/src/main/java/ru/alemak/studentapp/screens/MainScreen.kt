package ru.alemak.studentapp.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import ru.alemak.studentapp.R
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.utils.DateUtils
import java.util.*

@Composable
fun MainScreen(navController: NavController) {
    val currentWeekType = DateUtils.getCurrentWeekType()

    Column(
        modifier = Modifier
            .background(BlueKGTA)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(
            painter = painterResource(id = R.drawable.kgta_logo),
            contentDescription = "Логотип КГТА",
            modifier = Modifier
                .size(225.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Белый овал с текстом числитель/знаменатель
        Box(
            modifier = Modifier
                .width(265.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentWeekType,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(300.dp))

        // Row с кнопками - выравнивание по центру
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center, // ← Изменили на Center
            verticalAlignment = Alignment.Top
        ) {
            // Column с первой и второй кнопками (овалы)
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Первая кнопка (овал)
                Button(
                    onClick = { navController.navigate("screen1") },
                    modifier = Modifier
                        .width(250.dp) // ← Фиксированная ширина вместо fillMaxWidth
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        "Расписание",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                    )
                }

                // Вторая кнопка (овал)
                Button(
                    onClick = { navController.navigate("screen2") },
                    modifier = Modifier
                        .width(250.dp) // ← Фиксированная ширина вместо fillMaxWidth
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        "Напоминания",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Третья кнопка с иконкой
            Button(
                onClick = { navController.navigate("screen3") },
                modifier = Modifier
                    .width(120.dp)
                    .height(130.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                contentPadding = PaddingValues(0.dp) // ← Убираем внутренние отступы кнопки
            ) {
                Image(
                    painter = painterResource(id = R.drawable.teacher_icon),
                    contentDescription = "Иконка преподавателя",
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}

/**
 * Функция для определения текущей недели (числитель/знаменатель)
 * Должна быть идентична функции в ExcelParser
 */
fun getCurrentWeekType(): String {
    val calendar = Calendar.getInstance()

    // Используем ТОЧНО ТАКОЙ ЖЕ алгоритм как в ExcelParser
    // Фиксированная дата начала семестра - 2 сентября 2024 года
    val startOfSemester = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2024) // Укажите актуальный год
        set(Calendar.MONTH, Calendar.SEPTEMBER)
        set(Calendar.DAY_OF_MONTH, 2) // Первый понедельник сентября
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val current = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Вычисляем разницу в неделях (такой же алгоритм как в ExcelParser)
    val diffInMillis = current.timeInMillis - startOfSemester.timeInMillis
    val weeksDiff = (diffInMillis / (7 * 24 * 60 * 60 * 1000)).toInt()

    // Если разница четная - числитель, нечетная - знаменатель
    return if (weeksDiff % 2 == 0) {
        "Числитель"
    } else {
        "Знаменатель"
    }
}