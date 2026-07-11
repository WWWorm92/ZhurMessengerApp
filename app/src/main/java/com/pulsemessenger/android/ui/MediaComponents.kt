package com.pulsemessenger.android.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.request.videoFrameMillis

private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "avi", "m4v")
private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")

fun isVideoAttachment(fileName: String, path: String): Boolean =
    extractExtension(fileName, path) in videoExtensions

fun isAudioAttachment(fileName: String, path: String): Boolean =
    extractExtension(fileName, path) in audioExtensions

private fun extractExtension(fileName: String, path: String): String {
    val source = fileName.ifBlank { path }
    val clean = source.substringBefore('?').substringBefore('#')
    return clean.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}

@Composable
fun VideoAttachmentCard(
    fileName: String,
    fileSize: Long?,
    url: String,
    enabled: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        onClick = onOpen,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp, 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .videoFrameMillis(1_000)
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName.ifBlank { "Видео" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append("Нажмите, чтобы открыть")
                        if (fileSize != null) append(" • ${formatFileSize(fileSize)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AudioAttachmentCard(
    fileName: String,
    fileSize: Long?,
    url: String,
    enabled: Boolean,
) {
    var mediaPlayer by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isPreparing by remember(url) { mutableStateOf(false) }

    DisposableEffect(url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                enabled = enabled,
                onClick = {
                    val player = mediaPlayer
                    if (player != null) {
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                        return@IconButton
                    }

                    isPreparing = true
                    val created = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(url)
                        setOnPreparedListener {
                            isPreparing = false
                            isPlaying = true
                            it.start()
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        prepareAsync()
                    }
                    mediaPlayer = created
                },
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName.ifBlank { "Аудио" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append(
                            when {
                                isPreparing -> "Подготовка..."
                                isPlaying -> "Идёт воспроизведение"
                                else -> "Нажмите, чтобы прослушать"
                            }
                        )
                        if (fileSize != null) append(" • ${formatFileSize(fileSize)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
            }
        }
    }
}
