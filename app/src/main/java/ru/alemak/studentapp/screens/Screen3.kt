package ru.alemak.studentapp.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import ru.alemak.studentapp.R
import ru.alemak.studentapp.ui.theme.BlueKGTA

@Composable
fun Screen3(navController: NavController) {
    var searchText by remember { mutableStateOf("") }
    val teachers = listOf(
        Teacher("Егоров  Алексей Васильевчи", "Ректор", R.drawable.rector),
        Teacher("Демьянова Елена Владимировна", "Молодёжная политика", R.drawable.molodej_politics),
        Teacher("Антонова Мария Евгеньевна", "Директор ЭМК", R.drawable.director_emk),
        Teacher("Митрофанов Андрей Анатольевич", "Декан АиЭ", R.drawable.aie_dekan),
        Teacher("Зяблицева Ольга Витальевна", "Базы данных, Программирование", R.drawable.zyabliceva),
        Teacher("Котов Владимир Валерьевич", "ЭВМ и ПУ, Поддержка ПО", R.drawable.kotov),
        Teacher("Марихов Иван Николаевич", "Математика", R.drawable.marihov),
        Teacher("Шенкман Людмила Владиславовна", "Механика, Теоретическая Механика", R.drawable.shenkman)
    )

    val filteredTeachers = teachers.filter { teacher ->
        teacher.name.contains(searchText, ignoreCase = true) ||
                teacher.subject.contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .background(BlueKGTA)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(25.dp))
        Text(
            text = "Преподаватели",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp)
        )

        // Поисковая строка
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (searchText.isEmpty()) {
                    Text(
                        "Поиск преподавателей...",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Список преподавателей с прокруткой
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredTeachers) { teacher ->
                TeacherItem(teacher = teacher)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки навигации
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { navController.navigateUp() },
                        modifier = Modifier
                        .fillMaxWidth()
                    .padding(horizontal = 50.dp)
            ) {
                Text("Назад")
            }
        }
    }
}

@Composable
fun TeacherItem(teacher: Teacher) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Фотка в кружочке
        Image(
            painter = painterResource(id = teacher.photoRes),
            contentDescription = "Фото ${teacher.name}",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Информация о преподавателе
        Column {
            Text(
                text = teacher.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = teacher.subject,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

data class Teacher(
    val name: String,
    val subject: String,
    val photoRes: Int
)