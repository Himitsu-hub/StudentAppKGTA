package ru.alemak.studentapp.ui.campus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.theme.BlueKGTA

/**
 * Campus buildings (maps later) + useful university contacts.
 * Entry: Home → «Кампус» — one place for first-years without crowding schedule.
 */
private data class CampusBuilding(
    val title: String,
    val subtitle: String,
    val note: String,
)

private data class ContactItem(
    val title: String,
    val detail: String,
    val actionUri: String? = null,
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

/** Placeholder contacts — replace with official numbers when confirmed by the university. */
private val contacts = listOf(
    ContactItem("Сайт КГТУ (КГТА)", "dksta.ru", "https://dksta.ru"),
    ContactItem("Приёмная комиссия", "Смотрите актуальный телефон на сайте", "https://dksta.ru"),
    ContactItem("Учебное управление / УМУ", "Вопросы по расписанию", null),
    ContactItem("Дистанционное обучение", "Материалы и ЛК — ссылка с сайта вуза", "https://dksta.ru"),
)

@Composable
fun CampusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AppTopBar(title = "Кампус и контакты", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            val onBlue = scheme.background == BlueKGTA
            val hintColor = if (onBlue) Color.White.copy(alpha = 0.75f) else scheme.onSurfaceVariant
            SectionTitle("Корпуса")
            Text(
                text = "Для первокурсников. Планы этажей и подсветка кабинетов — когда будут чертежи.",
                color = hintColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            buildings.forEach { building ->
                BuildingCard(building)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Контакты")
            Text(
                text = "Быстрые ссылки. Номера можно уточнить у вуза и подставить официальные.",
                color = hintColor,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))

            contacts.forEach { item ->
                ContactCard(
                    item = item,
                    onClick = {
                        val uri = item.actionUri ?: return@ContactCard
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    Text(
        text = text,
        color = if (onBlue) Color.White else scheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun BuildingCard(building: CampusBuilding) {
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (onBlue) Color.White else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = building.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (onBlue) BlueKGTA else scheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = building.subtitle,
                color = if (onBlue) Color.DarkGray else scheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = building.note,
                color = if (onBlue) Color.Gray else scheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ContactCard(item: ContactItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.actionUri != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (onBlue) Color.White else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                color = if (onBlue) BlueKGTA else scheme.primary,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.detail,
                color = if (onBlue) Color.DarkGray else scheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            if (item.actionUri != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Нажмите, чтобы открыть",
                    color = if (onBlue) Color.Gray else scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
