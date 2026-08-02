package ru.alemak.studentapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.alemak.studentapp.ui.theme.BlueKGTA

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlueKGTA)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Добро пожаловать",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Сначала выберите курс, группу и подгруппу.\n" +
                "Это нужно один раз — потом сразу откроется ваше расписание.\n\n" +
                "Сменить можно в любой момент в разделе «Расписание» " +
                "(например, когда перейдёте на следующий курс).",
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(28.dp))

        StepTitle("1. Курс")
        RowWrap {
            (1..4).forEach { course ->
                ChoiceChip(
                    label = "$course курс",
                    selected = state.course == course,
                    onClick = { viewModel.selectCourse(course) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StepTitle("2. Группа")
        when {
            state.loadingGroups -> {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(12.dp))
            }
            state.groups.isEmpty() -> {
                Text(
                    "Группы не загрузились. Проверьте интернет и нажмите «Обновить».",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.reloadGroups() }) {
                    Text("Обновить", color = Color.White)
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    state.groups.keys.sorted().forEach { group ->
                        ChoiceChip(
                            label = group,
                            selected = state.group == group,
                            onClick = { viewModel.selectGroup(group) },
                            fullWidth = true,
                        )
                    }
                }
            }
        }

        if (!state.group.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            StepTitle("3. Подгруппа")
            val subs = state.groups[state.group].orEmpty()
            if (subs.isEmpty()) {
                Text(
                    "Подгруппы не указаны — будет общая группа",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    subs.forEach { sub ->
                        ChoiceChip(
                            label = sub,
                            selected = state.subgroup == sub,
                            onClick = { viewModel.selectSubgroup(sub) },
                            fullWidth = true,
                        )
                    }
                }
            }
        }

        if (!state.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = Color(0xFFFFB4A8), fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { viewModel.finish(onFinished) },
            enabled = state.canFinish && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BlueKGTA,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                disabledContentColor = BlueKGTA.copy(alpha = 0.5f),
            ),
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), color = BlueKGTA)
            } else {
                Text("Начать", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    )
}

@Composable
private fun RowWrap(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    fullWidth: Boolean = false,
) {
    val bg = if (selected) Color.White else Color.White.copy(alpha = 0.15f)
    val fg = if (selected) BlueKGTA else Color.White
    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth())
            .height(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
    }
}
