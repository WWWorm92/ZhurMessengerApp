package com.pulsemessenger.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.pulsemessenger.android.BuildConfig

private val PulseCallGreen = Color(0xFF16A34A)
private val PulseCallRed = Color(0xFFEF4444)
private val PulseCallBlue = Color(0xFF0EA5E9)
private val PulseCallDeepBlue = Color(0xFF075985)
private val PulseCallDark = Color(0xFF07111F)

@Composable
fun IncomingCallOverlay(
    callerName: String,
    callerAvatarUrl: String = "",
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallBackgroundBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CallAvatar(
                    name = callerName,
                    avatarUrl = callerAvatarUrl,
                    size = 124,
                )

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Входящий звонок",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(58.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallAnswerButton(
                        label = "Отклонить",
                        color = PulseCallRed,
                        onClick = onReject,
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Отклонить")
                    }

                    CallAnswerButton(
                        label = "Ответить",
                        color = PulseCallGreen,
                        onClick = onAccept,
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Ответить")
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallWindow(
    peerName: String,
    peerAvatarUrl: String = "",
    statusText: String,
    durationSeconds: Int,
    muted: Boolean,
    speakerEnabled: Boolean,
    onBackToDialog: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    BackHandler(onBack = onBackToDialog)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallBackgroundBrush()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBackToDialog) {
                        Text("← В диалог", color = Color.White)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = if (durationSeconds > 0) formatCallDuration(durationSeconds) else "--:--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }

                Spacer(modifier = Modifier.weight(0.7f))

                CallAvatar(
                    name = peerName,
                    avatarUrl = peerAvatarUrl,
                    size = 132,
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(34.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallControlButton(
                            label = if (muted) "Включить" else "Микрофон",
                            active = muted,
                            onClick = onToggleMute,
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
                            )
                        }

                        CallControlButton(
                            label = if (speakerEnabled) "Динамик" else "Телефон",
                            active = speakerEnabled,
                            onClick = onToggleSpeaker,
                        ) {
                            Icon(
                                imageVector = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (speakerEnabled) "Выключить громкую связь" else "Включить громкую связь",
                            )
                        }

                        CallControlButton(
                            label = "Завершить",
                            destructive = true,
                            onClick = onEnd,
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Завершить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimizedCallBanner(
    peerName: String,
    peerAvatarUrl: String = "",
    statusText: String,
    durationSeconds: Int,
    onOpen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 14.dp, end = 14.dp)
            .zIndex(18f),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PulseCallBlue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = resolveCallAvatarUrl(peerAvatarUrl)
                    if (model.isNotBlank()) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = PulseCallBlue,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (durationSeconds > 0) {
                            "${formatCallDuration(durationSeconds)} • $statusText"
                        } else {
                            statusText
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Открыть",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PulseCallBlue,
                )
            }
        }
    }
}

@Composable
private fun CallAnswerButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = color,
                contentColor = Color.White,
            ),
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun CallControlButton(
    label: String,
    active: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (destructive) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PulseCallRed,
                    contentColor = Color.White,
                ),
            ) {
                icon()
            }
        } else {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (active) PulseCallBlue else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                icon()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CallAvatar(
    name: String,
    avatarUrl: String,
    size: Int,
) {
    val model = resolveCallAvatarUrl(avatarUrl)
    val initials = name
        .split(" ", "_", "-", ".")
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "Z" }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        PulseCallBlue.copy(alpha = 0.95f),
                        PulseCallDeepBlue.copy(alpha = 0.95f),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (model.isNotBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CallBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        listOf(
            PulseCallDeepBlue,
            PulseCallBlue.copy(alpha = 0.86f),
            PulseCallDark,
        )
    )
}

private fun resolveCallAvatarUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) return value

    val base = BuildConfig.BASE_URL.trimEnd('/')
    val path = if (value.startsWith('/')) value else "/$value"
    return "$base$path"
}

fun formatCallDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return "%d:%02d".format(minutes, rest)
}
