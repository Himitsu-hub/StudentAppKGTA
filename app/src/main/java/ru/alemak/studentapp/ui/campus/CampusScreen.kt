package ru.alemak.studentapp.ui.campus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.alemak.studentapp.ui.components.AppTopBar
import ru.alemak.studentapp.ui.components.swipeBack
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.BlueKGTALight
import ru.alemak.studentapp.ui.theme.DarkButtonBorder
import ru.alemak.studentapp.ui.theme.DarkCard
import ru.alemak.studentapp.ui.theme.DarkOnSurfaceMuted

/**
 * Campus + official contacts from https://dksta.ru/kontakty-1
 * Floor maps with rooms/teachers — later.
 */

private const val MAIN_PHONE_DISPLAY = "8 (49232) 6-96-00"
private const val MAIN_PHONE_TEL = "tel:+74923269600"
private const val ADDRESS = "г. Ковров, ул. Маяковского, 19"
private const val MAPS_URI = "https://yandex.ru/maps/-/CDS77ZJZ"
private const val SITE_CONTACTS = "https://dksta.ru/kontakty-1"
private const val SITE_ALL = "https://dksta.ru/kontakty-2"
private const val WORK_HOURS = "Пн–Пт, 8:00–17:00"

private data class CampusBuilding(
    val title: String,
    val subtitle: String,
)

private data class ContactItem(
    val title: String,
    val role: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val note: String? = null,
    val webUri: String? = null,
)

private val buildings = listOf(
    CampusBuilding(
        title = "Главный корпус",
        subtitle = "Учебные аудитории, деканаты, ректорат",
    ),
    CampusBuilding(
        title = "Корпус лабораторий",
        subtitle = "Лаб. занятия, кафедры",
    ),
    CampusBuilding(
        title = "Спортивный комплекс",
        subtitle = "Физкультура, секции",
    ),
    CampusBuilding(
        title = "Общежитие / столовая",
        subtitle = "Быт и питание",
    ),
)

/** Most useful contacts for students (source: dksta.ru/kontakty-1). */
private val quickContacts = listOf(
    ContactItem(
        title = "Приёмная ректора",
        role = "Егоров А.В., и.о. ректора",
        phone = "доб. 246",
        email = "ksta@dksta.ru",
    ),
    ContactItem(
        title = "Учебно-методическое управление",
        role = "Хрусталёв П.Е. · расписание, УМУ",
        phone = "доб. 220",
        email = "umu@dksta.ru",
    ),
    ContactItem(
        title = "Приёмная комиссия",
        role = "Шварёва И.С. · довузовская подготовка",
        phone = "доб. 100 · 6-96-02",
        email = "pk@dksta.ru",
    ),
    ContactItem(
        title = "Научно-техническая библиотека",
        role = "Красавина Н.С.",
        phone = "доб. 126–129",
        email = "ntb@dksta.ru",
    ),
)

private val deaneries = listOf(
    ContactItem(
        title = "Деканат МТФ",
        role = "Механико-технологический факультет · Грачёва И.В.",
        phone = "доб. 206 / 207",
        email = "mtf@dksta.ru",
    ),
    ContactItem(
        title = "Деканат ФАиЭ",
        role = "Факультет автоматики и электроники · Митрофанов А.А.",
        phone = "доб. 326 / 327",
        email = "aie@dksta.ru",
    ),
    ContactItem(
        title = "Деканат ФЭиМ",
        role = "Факультет экономики и менеджмента · Быкова А.В.",
        phone = "доб. 400 / 404 / 409",
        email = "eim@dksta.ru",
    ),
    ContactItem(
        title = "Энергомеханический колледж",
        role = "Антонова М.Е., директор ЭМК",
        phone = "доб. 28",
        email = "emk@dksta.ru",
    ),
)

private val services = listOf(
    ContactItem(
        title = "Общежитие",
        role = "Кочергина Г.А. · Илясов Н.И.",
        phone = "доб. 114 / 193",
        email = "otel@dksta.ru",
    ),
    ContactItem(
        title = "Иностранные студенты",
        role = "Крылова Э.Ю.",
        phone = "доб. 219",
        email = "inostr@dksta.ru",
    ),
    ContactItem(
        title = "Молодёжная политика",
        role = "Демьянова Е.В. · Жук А.А.",
        phone = "доб. 248 / 230",
        email = "molodezhnaya_politika@dksta.ru",
    ),
    ContactItem(
        title = "Военный учебный центр",
        role = "Баженов Ю.В.",
        phone = "доб. 14",
        email = "voenka@dksta.ru",
    ),
    ContactItem(
        title = "Бухгалтерия",
        role = "Шитова Н.Н., главный бухгалтер",
        phone = "доб. 680",
        email = "buh@dksta.ru",
    ),
    ContactItem(
        title = "IT / техподдержка",
        role = "Кузнецов Д.А.",
        phone = "доб. 229",
        email = "admin@dksta.ru",
    ),
    ContactItem(
        title = "Юрист",
        role = "Торопова Т.Е.",
        phone = "доб. 999",
        email = "urist@dksta.ru",
    ),
    ContactItem(
        title = "Все контакты на сайте",
        role = "Полный список подразделений",
        webUri = SITE_ALL,
        note = "dksta.ru/kontakty-2",
    ),
)

@Composable
fun CampusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    val hintColor = if (onBlue) Color.White.copy(alpha = 0.78f) else scheme.onSurfaceVariant
    val titleColor = if (onBlue) Color.White else scheme.onBackground

    fun open(uri: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    fun callMain() = open(MAIN_PHONE_TEL)
    fun mail(email: String) = open("mailto:$email")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .swipeBack(onBack),
    ) {
        AppTopBar(title = "Кампус и контакты", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            // ── Campus buildings ──
            SectionTitle("Корпуса", titleColor)
            Text(
                text = "Основные здания кампуса КГТУ",
                color = hintColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            buildings.forEach { building ->
                BuildingCard(building = building, onBlue = onBlue)
                Spacer(Modifier.height(10.dp))
            }

            // ── Contacts below ──
            Spacer(Modifier.height(22.dp))
            SectionTitle("Контакты", titleColor)
            Text(
                text = "Адрес, телефон и службы вуза",
                color = hintColor,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            HeaderCard(
                onBlue = onBlue,
                onCall = { callMain() },
                onMap = { open(MAPS_URI) },
                onSite = { open(SITE_CONTACTS) },
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("Быстрые контакты", titleColor)
            Text(
                text = "Базовый номер $MAIN_PHONE_DISPLAY — наберите и добавьте добавочный.",
                color = hintColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            quickContacts.forEach { item ->
                ContactCard(
                    item = item,
                    onBlue = onBlue,
                    onCall = { callMain() },
                    onEmail = { email -> mail(email) },
                    onWeb = { uri -> open(uri) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Деканаты", titleColor)
            Text(
                text = "Факультеты и колледж",
                color = hintColor,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            deaneries.forEach { item ->
                ContactCard(
                    item = item,
                    onBlue = onBlue,
                    onCall = { callMain() },
                    onEmail = { email -> mail(email) },
                    onWeb = { uri -> open(uri) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Службы", titleColor)
            Text(
                text = "Общежитие, ВУЦ, бухгалтерия и другие",
                color = hintColor,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            services.forEach { item ->
                ContactCard(
                    item = item,
                    onBlue = onBlue,
                    onCall = { callMain() },
                    onEmail = { email -> mail(email) },
                    onWeb = { uri -> open(uri) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Источник: dksta.ru/kontakty-1 · КГТУ им. В.А. Дегтярева",
                color = hintColor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HeaderCard(
    onBlue: Boolean,
    onCall: () -> Unit,
    onMap: () -> Unit,
    onSite: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardBg = if (onBlue) Color.White else DarkCard
    val titleC = if (onBlue) BlueKGTA else scheme.onSurface
    val muted = if (onBlue) Color(0xFF5F6B7A) else scheme.onSurfaceVariant
    val border = if (onBlue) null else BorderStroke(1.dp, DarkButtonBorder)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (onBlue) 3.dp else 0.dp),
        border = border,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.School, onBlue)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "КГТУ им. В.А. Дегтярева",
                        fontWeight = FontWeight.Bold,
                        color = titleC,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Ковров · студенческий кампус",
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = muted.copy(alpha = 0.25f))
            Spacer(Modifier.height(12.dp))

            InfoRow(
                icon = Icons.Outlined.Place,
                label = ADDRESS,
                muted = muted,
                titleC = titleC,
                onBlue = onBlue,
                actionLabel = "Карта",
                onAction = onMap,
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Outlined.Phone,
                label = MAIN_PHONE_DISPLAY,
                muted = muted,
                titleC = titleC,
                onBlue = onBlue,
                actionLabel = "Позвонить",
                onAction = onCall,
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Outlined.Schedule,
                label = WORK_HOURS,
                muted = muted,
                titleC = titleC,
                onBlue = onBlue,
            )
            Spacer(Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Outlined.Language,
                label = "dksta.ru/kontakty-1",
                muted = muted,
                titleC = titleC,
                onBlue = onBlue,
                actionLabel = "Сайт",
                onAction = onSite,
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    muted: Color,
    titleC: Color,
    onBlue: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (onBlue) BlueKGTA else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = titleC,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = if (onBlue) BlueKGTA else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, onBlue: Boolean) {
    val bg = if (onBlue) BlueKGTA.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val tint = if (onBlue) BlueKGTA else MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun BuildingCard(building: CampusBuilding, onBlue: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val cardBg = if (onBlue) Color.White else DarkCard
    val border = if (onBlue) null else BorderStroke(1.dp, DarkButtonBorder)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (onBlue) 2.dp else 0.dp),
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconBubble(Icons.Outlined.Apartment, onBlue)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = building.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (onBlue) BlueKGTA else scheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = building.subtitle,
                    color = if (onBlue) Color(0xFF5F6B7A) else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    item: ContactItem,
    onBlue: Boolean,
    onCall: () -> Unit,
    onEmail: (String) -> Unit,
    onWeb: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardBg = if (onBlue) Color.White else DarkCard
    val border = if (onBlue) null else BorderStroke(1.dp, DarkButtonBorder)
    val titleC = if (onBlue) BlueKGTA else scheme.onSurface
    val muted = if (onBlue) Color(0xFF5F6B7A) else scheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.webUri != null && item.phone == null && item.email == null) {
                    Modifier.clickable { onWeb(item.webUri) }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (onBlue) 2.dp else 0.dp),
        border = border,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                color = titleC,
                fontSize = 15.sp,
            )
            item.role?.let {
                Spacer(Modifier.height(3.dp))
                Text(text = it, color = muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            item.note?.let {
                Spacer(Modifier.height(3.dp))
                Text(text = it, color = muted, fontSize = 12.sp)
            }

            if (item.phone != null) {
                Spacer(Modifier.height(10.dp))
                ContactActionRow(
                    icon = Icons.Outlined.Phone,
                    text = item.phone,
                    button = "Позвонить",
                    onClick = onCall,
                    onBlue = onBlue,
                )
            }
            if (item.email != null) {
                Spacer(Modifier.height(8.dp))
                ContactActionRow(
                    icon = Icons.Outlined.Email,
                    text = item.email,
                    button = "Написать",
                    onClick = { onEmail(item.email) },
                    onBlue = onBlue,
                )
            }
            if (item.webUri != null && item.phone == null && item.email == null) {
                Spacer(Modifier.height(10.dp))
                ContactActionRow(
                    icon = Icons.Outlined.Info,
                    text = "Все контакты на сайте",
                    button = "Открыть",
                    onClick = { onWeb(item.webUri) },
                    onBlue = onBlue,
                )
            } else if (item.webUri != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Открыть на сайте",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BlueKGTA)
                        .clickable { onWeb(item.webUri) }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Phone/email chips:
 * - light theme: soft blue row + dark blue text
 * - dark theme: lighter navy row + pure white text
 */
@Composable
private fun ContactActionRow(
    icon: ImageVector,
    text: String,
    button: String,
    onClick: () -> Unit,
    onBlue: Boolean,
) {
    val rowBg = if (onBlue) Color(0xFFE3EBF8) else Color(0xFF2E3D5C)
    val iconTint = if (onBlue) BlueKGTA else DarkOnSurfaceMuted
    val textColor = if (onBlue) Color(0xFF0F1F45) else Color.White
    val btnBg = if (onBlue) BlueKGTA else BlueKGTALight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = button,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(btnBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
