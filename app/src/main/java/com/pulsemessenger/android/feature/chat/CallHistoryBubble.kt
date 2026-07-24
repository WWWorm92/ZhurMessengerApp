package com.pulsemessenger.android.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.ui.formatMessageTime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon

private data class CallHistoryPresentation(
    val title: String,
    val subtitle: String,
    val accentKind: String,
    val direction: String,
)

private fun formatCallDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun callPresentation(
    message: DmMessageDto,
    isMine: Boolean,
): CallHistoryPresentation {
    val direction = if (isMine) "↗" else "↙"

    return when (message.callStatus.lowercase()) {
        "completed" -> CallHistoryPresentation(
            title = if (isMine) "Исходящий звонок" else "Входящий звонок",
            subtitle = "Длительность ${formatCallDuration(message.callDurationSeconds)}",
            accentKind = "success",
            direction = direction,
        )

        "rejected" -> CallHistoryPresentation(
            title = if (isMine) "Звонок отклонён" else "Отклонённый звонок",
            subtitle = "Вызов не состоялся",
            accentKind = "danger",
            direction = direction,
        )

        "missed" -> CallHistoryPresentation(
            title = if (isMine) "Нет ответа" else "Пропущенный звонок",
            subtitle = if (isMine) "Абонент не ответил" else "Вы не ответили",
            accentKind = "danger",
            direction = direction,
        )

        "cancelled" -> CallHistoryPresentation(
            title = if (isMine) "Отменённый звонок" else "Пропущенный звонок",
            subtitle = if (isMine) "Вы отменили вызов" else "Звонок был отменён",
            accentKind = "warning",
            direction = direction,
        )

        else -> CallHistoryPresentation(
            title = if (isMine) "Исходящий звонок" else "Входящий звонок",
            subtitle = message.content.ifBlank { "Аудиозвонок" },
            accentKind = "neutral",
            direction = direction,
        )
    }
}

@Composable
internal fun DmCallHistoryBubble(
    message: DmMessageDto,
    isMine: Boolean,
    onCallClick: () -> Unit,
) {
    val presentation = callPresentation(message, isMine)

    val accent = when (presentation.accentKind) {
        "danger" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        "success" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            Card(
                modifier = Modifier.widthIn(min = 270.dp, max = 360.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isMine) 20.dp else 7.dp,
                    bottomEnd = if (isMine) 7.dp else 20.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMine) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = presentation.direction,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = presentation.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = presentation.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable(onClick = onCallClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Позвонить снова",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }

            Text(
                text = formatMessageTime(message.createdAt),
                modifier = Modifier.padding(top = 3.dp, start = 8.dp, end = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
