package com.pulsemessenger.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun IncomingCallOverlay(
    callerName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CallAvatar(name = callerName)

                Spacer(modifier = Modifier.height(28.dp))

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
                    color = Color.White.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(54.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onReject,
                            modifier = Modifier.size(68.dp),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Отклонить")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Отклонить", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.84f))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onAccept,
                            modifier = Modifier.size(68.dp),
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Принять")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Принять", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.84f))
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    peerName: String,
    statusText: String,
    muted: Boolean,
    speakerEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    var activeSeconds by remember(statusText) { mutableIntStateOf(0) }
    val isActive = statusText.contains("актив", ignoreCase = true)

    LaunchedEffect(isActive) {
        activeSeconds = 0
        while (isActive) {
            delay(1000)
            activeSeconds += 1
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CallAvatar(name = peerName)

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isActive && activeSeconds > 0) {
                        "${formatCallDuration(activeSeconds)} • $statusText"
                    } else {
                        statusText
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(44.dp))

                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallControlButton(
                            label = if (muted) "Микрофон выкл." else "Микрофон",
                            onClick = onToggleMute,
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
                            )
                        }

                        CallControlButton(
                            label = if (speakerEnabled) "Динамик" else "Телефон",
                            onClick = onToggleSpeaker,
                        ) {
                            Icon(
                                imageVector = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (speakerEnabled) "Выключить громкую связь" else "Включить громкую связь",
                            )
                        }

                        CallControlButton(
                            label = "Завершить",
                            onClick = onEnd,
                            destructive = true,
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
private fun CallControlButton(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (destructive) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(62.dp),
            ) {
                icon()
            }
        } else {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(62.dp),
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
private fun CallAvatar(name: String) {
    val initials = name
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "Z" }

    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun formatCallDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}
