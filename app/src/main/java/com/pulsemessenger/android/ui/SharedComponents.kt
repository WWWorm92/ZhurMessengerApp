package com.pulsemessenger.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsemessenger.android.core.network.MessageReactionDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DialogAvatar(
    displayName: String,
    avatarUrl: String = "",
    modifier: Modifier = Modifier.size(48.dp),
) {
    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = resolveBackendMediaUrl(avatarUrl),
            contentDescription = displayName,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val initial = displayName.trim().firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ReactionChip(reaction: MessageReactionDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reaction.reactedByMe) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            }
        )
    ) {
        Text(
            text = "${reaction.emoji} ${reaction.count}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (reaction.reactedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun UnreadBadge(count: Int) {
    if (count > 0) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun OnlineDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFF4CAF50))
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun ListSkeletonCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(modifier = Modifier.width(120.dp).height(16.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth().height(12.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            SkeletonBlock(modifier = Modifier.width(36.dp).height(12.dp))
        }
    }
}

@Composable
fun ChatBubbleSkeleton(isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(if (isMine) 180.dp else 210.dp)
                .height(if (isMine) 62.dp else 74.dp),
            shape = RoundedCornerShape(22.dp),
        )
    }
}

fun formatFileSize(size: Long?): String {
    val value = size ?: return ""
    if (value < 1024) return "$value B"
    if (value < 1024 * 1024) return "${value / 1024} KB"
    return String.format("%.1f MB", value / 1024f / 1024f)
}

fun formatDateTime(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString.take(19)) ?: return isoString
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val sameDay = android.text.format.DateUtils.isToday(date.time)
        when {
            diff < 60_000 -> "только что"
            diff < 3600_000 -> "${diff / 60_000} мин. назад"
            sameDay -> {
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                fmt.format(date)
            }
            diff < 172_800_000 -> "вчера"
            diff < 604_800_000 -> {
                val fmt = SimpleDateFormat("EEEE", Locale("ru"))
                fmt.format(date)
            }
            else -> {
                val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                fmt.format(date)
            }
        }
    } catch (_: Exception) {
        isoString.take(10)
    }
}

fun formatMessageTime(value: String?): String {
    val millis = parseUtcMillis(value) ?: return ""
    val date = java.time.Instant
        .ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())

    return java.time.format.DateTimeFormatter
        .ofPattern("HH:mm", java.util.Locale("ru"))
        .format(date)
}

fun formatChatListTime(value: String?): String {
    val millis = parseUtcMillis(value) ?: return ""
    val date = java.time.Instant
        .ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())

    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())

    return if (date.toLocalDate() == today) {
        java.time.format.DateTimeFormatter
            .ofPattern("HH:mm", java.util.Locale("ru"))
            .format(date)
    } else {
        java.time.format.DateTimeFormatter
            .ofPattern("d MMM", java.util.Locale("ru"))
            .format(date)
            .replace(".", "")
    }
}

fun formatDayLabel(value: String?): String {
    val millis = parseUtcMillis(value) ?: return ""
    val date = java.time.Instant
        .ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())

    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    val messageDate = date.toLocalDate()

    return when (java.time.temporal.ChronoUnit.DAYS.between(messageDate, today).toInt()) {
        0 -> "Сегодня"
        1 -> "Вчера"
        else -> java.time.format.DateTimeFormatter
            .ofPattern("d MMMM", java.util.Locale("ru"))
            .format(date)
    }
}

fun formatLastSeen(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    val millis = parseUtcMillis(isoString) ?: return ""
    val zone = java.time.ZoneId.systemDefault()
    val dateTime = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val date = dateTime.toLocalDate()
    val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale("ru")).format(dateTime)

    return when (java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()) {
        0 -> "был(а) в $time"
        1 -> "был(а) Вчера $time"
        else -> "был(а) ${java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy", java.util.Locale("ru")).format(dateTime)}"
    }
}

fun parseUtcMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null

    val text = value.trim()

    // Формат ISO: 2026-07-09T19:40:45.529Z
    runCatching {
        return java.time.Instant.parse(text).toEpochMilli()
    }

    // Формат SQLite: 2026-07-09 19:40:45
    runCatching {
        return java.time.LocalDateTime
            .parse(
                text.replace(" ", "T"),
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
            .atZone(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    return null
}
