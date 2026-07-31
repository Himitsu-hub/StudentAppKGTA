package ru.alemak.studentapp.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ru.alemak.studentapp.ui.components.OfflineBanner
import ru.alemak.studentapp.ui.theme.BlueKGTA

/** Spacing between news cards. */
private val NewsCardSpacing = 5.dp
private val NewsListPadding = 5.dp
/** 3 full cards + half of the 4th. */
private const val VisibleNewsCards = 3.5f
/** Slightly compact cards (a few px less top/bottom). */
private val MinNewsCardHeight = 68.dp

@Composable
fun HomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenReminders: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // No auto-refresh on every return to Home — only network restore (in ViewModel)
    // and the first open (init). Avoids half-second flicker.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueKGTA)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        OfflineBanner(visible = state.usingCachedData)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Compact top so news window fits ~3.5 cards
            Image(
                painter = painterResource(id = R.drawable.kgta_logo),
                contentDescription = "Логотип КГТУ",
                modifier = Modifier
                    .size(168.dp)
                    .offset(y = (-6).dp),
            )

            Text(
                text = state.weekType,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )

            Spacer(Modifier.height(4.dp))

            when {
                state.isLoadingLesson -> {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                }
                !state.hasGroup -> {
                    Text(
                        text = "Выберите группу в разделе «Расписание»",
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                else -> NextLessonBlock(state.nextLesson)
            }

            Spacer(Modifier.weight(0.08f))

            NavButton("Расписание", onOpenSchedule)
            Spacer(Modifier.height(4.dp))
            NavButton("Преподаватели", onOpenTeachers)
            Spacer(Modifier.height(4.dp))
            NavButton("Напоминания", onOpenReminders)

            Spacer(Modifier.height(6.dp))

            // Larger news area → ~3.5 posts visible
            when {
                state.isLoadingNews -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(4.2f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                state.news.isNotEmpty() -> {
                    NewsSection(
                        news = state.news,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(4.2f),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(4.2f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Новости пока недоступны",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NextLessonBlock(lesson: Lesson?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (lesson != null) {
            Text(
                text = "Следующая пара",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = lesson.subject,
                color = Color.White,
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
                Text(details, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
            }
            if (lesson.teacher.isNotBlank()) {
                Text(
                    text = lesson.teacher,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = "Сейчас пар нет",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun NavButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = BlueKGTA,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun NewsSection(
    news: List<NewsItem>,
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
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = "листайте ↓",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
        }

        // Card height from real window size → always ~3 full + half of 4th
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            // Inner list area (minus list padding)
            val listViewport = (maxHeight - NewsListPadding * 2).coerceAtLeast(0.dp)
            // 3 gaps between 3.5 cards — force 3.5 visibility (don't inflate card min too high)
            val spacings = NewsCardSpacing * 3
            val cardHeight = ((listViewport - spacings) / VisibleNewsCards)
                .coerceIn(MinNewsCardHeight, 88.dp)

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
                        cardHeight = cardHeight,
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
                                    BlueKGTA.copy(alpha = 0.75f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Прокрутите вниз",
                        tint = Color.White,
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
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                        color = Color.Black,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.date.ifBlank { "Дата не указана" },
                        color = Color(0xFF5F6B7A),
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
                        color = Color.DarkGray,
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
                        colors = ButtonDefaults.buttonColors(containerColor = BlueKGTA),
                    ) {
                        Text("Подробнее на сайте")
                    }
                }
            }
        }
    }
}
