package ru.alemak.studentapp.ui.campus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.theme.BlueKGTA

/**
 * Placeholder for campus navigation maps.
 * Full floor plans with room labels will be added later as assets.
 * Placement: Home → «Кампус» — separate screen so maps can grow without crowding schedule.
 */
private data class CampusBuilding(
    val title: String,
    val subtitle: String,
    val note: String,
)

private val buildings = listOf(
    CampusBuilding(
        title = "Главный корпус",
        subtitle = "Учебные аудитории, деканаты",
        note = "Карта этажей появится здесь позже",
    ),
    CampusBuilding(
        title = "Корпус лабораторий",
        subtitle = "Лаб. занятия, кафедры",
        note = "Схемы этажей — в следующей версии",
    ),
    CampusBuilding(
        title = "Спортивный комплекс",
        subtitle = "Физкультура, секции",
        note = "Как пройти — добавим на карте",
    ),
    CampusBuilding(
        title = "Общежития / столовая",
        subtitle = "Быт и питание",
        note = "Точки на карте кампуса — позже",
    ),
)

@Composable
fun CampusScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueKGTA)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AppTopBar(title = "Кампус", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Для первокурсников",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Здесь будет карта корпусов и аудиторий. " +
                    "Пока — список зданий; планы этажей добавим отдельными картинками.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            buildings.forEach { building ->
                BuildingCard(building)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun BuildingCard(building: CampusBuilding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = building.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BlueKGTA,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = building.subtitle,
                color = Color.DarkGray,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = building.note,
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
    }
}
