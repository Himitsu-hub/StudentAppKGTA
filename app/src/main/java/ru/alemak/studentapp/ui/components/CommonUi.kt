package ru.alemak.studentapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.alemak.studentapp.ui.theme.BlueKGTA
import ru.alemak.studentapp.ui.theme.OfflineAmber

@Composable
fun OfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
    updatedLabel: String? = null,
) {
    if (!visible) return
    val text = if (updatedLabel.isNullOrBlank()) {
        "Нет сети — показаны сохранённые данные"
    } else {
        "Нет сети · $updatedLabel"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OfflineAmber)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Subtle line under banners: "Обновлено: 5 мин назад" */
@Composable
fun UpdatedAtLabel(
    text: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        actions = {
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // Light: classic blue bar; Dark: dark surface
            containerColor = if (scheme.background == BlueKGTA) BlueKGTA else scheme.surface,
            titleContentColor = if (scheme.background == BlueKGTA) Color.White else scheme.onSurface,
            navigationIconContentColor = if (scheme.background == BlueKGTA) Color.White else scheme.onSurface,
            actionIconContentColor = if (scheme.background == BlueKGTA) Color.White else scheme.onSurface,
        ),
    )
}

@Composable
fun LoadingState(message: String = "Загрузка…") {
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = if (onBlue) Color.White else scheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = if (onBlue) Color.White.copy(alpha = 0.85f) else scheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Не удалось загрузить",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                Text("Повторить")
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    val scheme = MaterialTheme.colorScheme
    val onBlue = scheme.background == BlueKGTA
    val color = if (onBlue) Color.White.copy(alpha = 0.9f) else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, textAlign = TextAlign.Center, color = color.copy(alpha = 0.85f))
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}
