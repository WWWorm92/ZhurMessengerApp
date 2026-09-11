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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.pulsemessenger.android.BuildConfig
import com.pulsemessenger.android.core.call.WebRtcCallManager
import org.webrtc.SurfaceViewRenderer

private val PulseCallGreen = Color(0xFF16A34A)
private val PulseCallRed = Color(0xFFEF4444)
private val PulseCallBlue = Color(0xFF0EA5E9)
private val PulseCallDeepBlue = Color(0xFF075985)
private val PulseCallDark = Color(0xFF07111F)

@Composable
fun CallTypeChooserDialog(
    peerName: String,
    onDismiss: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Позвонить",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CallTypeChoice(
                        modifier = Modifier.weight(1f),
                        label = "Аудио",
                        color = PulseCallGreen,
                        onClick = onAudioCall,
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                    }
                    CallTypeChoice(
                        modifier = Modifier.weight(1f),
                        label = "Видео",
                        color = PulseCallBlue,
                        onClick = onVideoCall,
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTypeChoice(
    modifier: Modifier,
    label: String,
    color: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(62.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = color,
                    contentColor = Color.White,
                ),
            ) {
                icon()
            }
            Spacer(Modifier.height(10.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun IncomingCallOverlay(
    callerName: String,
    callerAvatarUrl: String = "",
    videoCall: Boolean = false,
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
                    text = if (videoCall) "Входящий видеозвонок" else "Входящий звонок",
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
                        Icon(
                            imageVector = if (videoCall) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Ответить",
                        )
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
    videoCall: Boolean = false,
    cameraEnabled: Boolean = true,
    callManager: WebRtcCallManager? = null,
    onBackToDialog: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
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
                    .padding(horizontal = if (videoCall) 12.dp else 22.dp, vertical = 18.dp),
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

                if (videoCall && callManager != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VideoCallStage(
                        manager = callManager,
                        peerName = peerName,
                        peerAvatarUrl = peerAvatarUrl,
                        statusText = statusText,
                        cameraEnabled = cameraEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                } else {
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
                }

                CallControlsCard(
                    videoCall = videoCall,
                    muted = muted,
                    speakerEnabled = speakerEnabled,
                    cameraEnabled = cameraEnabled,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera,
                    onEnd = onEnd,
                )
            }
        }
    }
}

@Composable
private fun VideoCallStage(
    manager: WebRtcCallManager,
    peerName: String,
    peerAvatarUrl: String,
    statusText: String,
    cameraEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black),
    ) {
        WebRtcVideoView(
            manager = manager,
            local = false,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Text(
                text = peerName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .width(112.dp)
                .height(158.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF101820)),
        ) {
            if (cameraEnabled) {
                WebRtcVideoView(
                    manager = manager,
                    local = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        if (!manager.isMediaConnected()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CallAvatar(name = peerName, avatarUrl = peerAvatarUrl, size = 96)
            }
        }
    }
}

@Composable
private fun WebRtcVideoView(
    manager: WebRtcCallManager,
    local: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember(manager, local) {
        SurfaceViewRenderer(context).also { manager.bindVideoRenderer(it, local) }
    }

    DisposableEffect(renderer, manager, local) {
        onDispose {
            manager.unbindVideoRenderer(renderer, local)
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}

@Composable
private fun CallControlsCard(
    videoCall: Boolean,
    muted: Boolean,
    speakerEnabled: Boolean,
    cameraEnabled: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEnd: () -> Unit,
) {
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
                .padding(horizontal = if (videoCall) 8.dp else 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallControlButton(
                label = if (muted) "Включить" else "Микрофон",
                active = muted,
                compact = videoCall,
                onClick = onToggleMute,
            ) {
                Icon(
                    imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
                )
            }

            if (videoCall) {
                CallControlButton(
                    label = if (cameraEnabled) "Камера" else "Включить",
                    active = !cameraEnabled,
                    compact = true,
                    onClick = onToggleCamera,
                ) {
                    Icon(
                        imageVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Камера",
                    )
                }

                CallControlButton(
                    label = "Сменить",
                    compact = true,
                    onClick = onSwitchCamera,
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Сменить камеру")
                }
            }

            CallControlButton(
                label = if (speakerEnabled) "Динамик" else "Телефон",
                active = speakerEnabled,
                compact = videoCall,
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
                compact = videoCall,
                onClick = onEnd,
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Завершить")
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
    videoCall: Boolean = false,
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
                            imageVector = if (videoCall) Icons.Default.Videocam else Icons.Default.Call,
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
    compact: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val buttonSize = if (compact) 54.dp else 64.dp
        if (destructive) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(buttonSize),
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
                modifier = Modifier.size(buttonSize),
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
