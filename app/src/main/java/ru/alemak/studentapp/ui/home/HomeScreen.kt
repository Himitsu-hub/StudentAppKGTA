package ru.alemak.studentapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
// Box used for theme toggle and news overlay
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.alemak.studentapp.R
import ru.alemak.studentapp.data.model.Lesson
import ru.alemak.studentapp.data.model.NewsItem
import androidx.compose.foundation.BorderStroke
import ru.alemak.studentapp.ui.components.OfflineBanner
import ru.alemak.studentapp.ui.components.UpdatedAtLabel
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.DarkButton
import ru.alemak.studentapp.ui.theme.DarkButtonBorder
import ru.alemak.studentapp.ui.theme.DarkCard
import ru.alemak.studentapp.ui.theme.DarkNavy
import ru.alemak.studentapp.ui.theme.DarkOnSurface
import ru.alemak.studentapp.ui.theme.DarkOnSurfaceMuted

/** Spacing between news cards. */
private val NewsCardSpacing = 5.dp
private val NewsListPadding = 5.dp
/** Fixed card height — list window is sized for exactly this many cards. */
private val NewsCardFixedHeight = 72.dp
/** Exactly 3 posts visible (no stretch to fill the screen). */
private const val VisibleNewsCards = 3
/** Total height of the scrollable news list (padding + 3 cards + 2 gaps). */
private val NewsListFixedHeight =
    NewsListPadding * 2 +
        NewsCardFixedHeight * VisibleNewsCards +
        NewsCardSpacing * (VisibleNewsCards - 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenCampus: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeBg = if (darkTheme) DarkNavy else BlueKGTA
    val onHome = if (darkTheme) DarkOnSurface else Color.White
    val onHomeMuted = if (darkTheme) DarkOnSurfaceMuted else Color.White.copy(alpha = 0.85f)

    // Fixed home layout. Pull-to-refresh ONLY on the upper chrome (logo / buttons).
    // News LazyColumn is outside PullToRefreshBox so scrolling news never refreshes the app.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(showLoading = false) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(
                    visible = state.usingCachedData,
                    updatedLabel = state.updatedLabel,
                )
                if (!state.usingCachedData) {
                    UpdatedAtLabel(text = state.updatedLabel, color = onHomeMuted)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (darkTheme) "Светлая тема" else "Тёмная тема",
                            tint = onHome,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kgta_logo),
                        contentDescription = "Логотип КГТУ",
                        modifier = Modifier
                            .size(152.dp)
                            .offset(y = (-4).dp),
                    )

                    Text(
                        text = state.weekType,
                        color = onHome,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )

                    Spacer(Modifier.height(4.dp))

                    when {
                        state.isLoadingLesson -> {
                            CircularProgressIndicator(color = onHome, modifier = Modifier.size(20.dp))
                        }
                        state.isVacation -> {
                            NextLessonBlock(
                                lesson = null,
                                darkTheme = darkTheme,
                                vacationTitle = null,
                                vacationSubtitle = state.vacationSubtitle
                                    ?: "Лето · занятия с 1 сентября",
                            )
                        }
                        !state.hasGroup -> {
                            Text(
                                text = "Выберите группу в разделе «Расписание»",
                                color = onHomeMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        else -> NextLessonBlock(state.nextLesson, darkTheme = darkTheme)
                    }

                    Spacer(Modifier.weight(1f))

                    NavButton("Расписание", onOpenSchedule, darkTheme = darkTheme)
                    Spacer(Modifier.height(4.dp))
                    NavButton("Преподаватели", onOpenTeachers, darkTheme = darkTheme)
                    Spacer(Modifier.height(4.dp))
                    NavButton("Напоминания", onOpenReminders, darkTheme = darkTheme)
                    Spacer(Modifier.height(4.dp))
                    NavButton("Кампус и контакты", onOpenCampus, darkTheme = darkTheme)
                }
            }
        }

        // News sits BELOW the pull zone — list scroll never triggers refresh.
        val newsPanelBg = if (darkTheme) DarkCard.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            when {
                state.isLoadingNews -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NewsListFixedHeight + 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(newsPanelBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = onHome, modifier = Modifier.size(22.dp))
                    }
                }
                state.news.isNotEmpty() -> {
                    NewsSection(
                        news = state.news,
                        darkTheme = darkTheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NewsListFixedHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(newsPanelBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Новости пока недоступны",
                            color = onHomeMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextLessonBlock(
    lesson: Lesson?,
    darkTheme: Boolean,
    vacationTitle: String? = null,
    vacationSubtitle: String? = null,
) {
    val primary = if (darkTheme) DarkOnSurface else Color.White
    val muted = if (darkTheme) DarkOnSurfaceMuted else Color.White.copy(alpha = 0.75f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (vacationTitle != null || vacationSubtitle != null) {
            if (vacationTitle != null) {
                Text(
                    text = vacationTitle,
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (!vacationSubtitle.isNullOrBlank()) {
                if (vacationTitle != null) Spacer(Modifier.height(4.dp))
                Text(
                    text = vacationSubtitle,
                    color = muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (lesson != null) {
            Text(
                text = "Следующая пара",
                color = muted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = lesson.subject,
                color = primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val details = buildString {
                if (lesson.time.isNotBlank()) append(lesson.time)
                if (lesson.room.isNotBlank()) {
                    if (isNotEmpty()) append("  •  ")
                    append("каб. ${lesson.room}")
                }
            }
            if (details.isNotBlank()) {
                Text(details, color = primary.copy(alpha = 0.9f), fontSize = 15.sp)
            }
            if (lesson.teacher.isNotBlank()) {
                Text(
                    text = lesson.teacher,
                    color = muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = "Сейчас пар нет",
                color = muted,
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun NavButton(text: String, onClick: () -> Unit, darkTheme: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        border = if (darkTheme) BorderStroke(1.dp, DarkButtonBorder) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (darkTheme) DarkButton else Color.White,
            contentColor = if (darkTheme) DarkOnSurface else BlueKGTA,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (darkTheme) 0.dp else 2.dp,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun NewsSection(
    news: List<NewsItem>,
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expandedUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val showBottomHint by remember {
        derivedStateOf { listState.canScrollForward }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Новости КГТУ",
                color = if (darkTheme) DarkOnSurface else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = "листайте ↓",
                color = if (darkTheme) DarkOnSurfaceMuted else Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
        }

        // Fixed window: exactly 3 cards tall — never stretches to 4–5
        val panelBg = if (darkTheme) DarkCard.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.1f)
        val fadeColor = if (darkTheme) DarkNavy else BlueKGTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NewsListFixedHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(panelBg),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 8.dp,
                    vertical = NewsListPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(NewsCardSpacing),
            ) {
                items(news, key = { it.url.ifBlank { it.title } }) { item ->
                    val expanded = expandedUrl == item.url
                    NewsCard(
                        item = item,
                        expanded = expanded,
                        cardHeight = NewsCardFixedHeight,
                        darkTheme = darkTheme,
                        onToggle = {
                            expandedUrl = if (expanded) null else item.url
                        },
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                )
                            }
                        },
                    )
                }
            }

            if (showBottomHint) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    fadeColor.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Прокрутите вниз",
                        tint = if (darkTheme) DarkOnSurface else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    expanded: Boolean,
    cardHeight: Dp,
    darkTheme: Boolean = false,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val cardBg = if (darkTheme) DarkButton else Color.White
    val titleColor = if (darkTheme) DarkOnSurface else Color.Black
    val metaColor = if (darkTheme) DarkOnSurfaceMuted else Color(0xFF5F6B7A)
    val bodyColor = if (darkTheme) DarkOnSurfaceMuted else Color.DarkGray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expanded) Modifier
                else Modifier.height(cardHeight)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (darkTheme) 0.dp else 2.dp),
        border = if (darkTheme) BorderStroke(1.dp, DarkButtonBorder) else null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .then(if (expanded) Modifier else Modifier.height(cardHeight))
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = titleColor,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.date.ifBlank { "Дата не указана" },
                        color = metaColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (expanded) {
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = bodyColor,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                if (item.url.isNotEmpty()) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (darkTheme) DarkCard else BlueKGTA,
                            contentColor = if (darkTheme) DarkOnSurface else Color.White,
                        ),
                    ) {
                        Text("Подробнее на сайте")
                    }
                }
            }
        }
    }
}
