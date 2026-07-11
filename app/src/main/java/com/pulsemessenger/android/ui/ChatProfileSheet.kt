package com.pulsemessenger.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ProfilePanel {
    Info,
    Photos,
    Videos,
    Files,
    Links,
}

data class SharedPhotoItem(val id: Long, val imageUrl: String, val createdAt: String)
data class SharedVideoItem(val id: Long, val fileUrl: String, val fileName: String, val fileSize: Long?, val createdAt: String)
data class SharedFileItem(val id: Long, val fileUrl: String, val fileName: String, val fileSize: Long?, val createdAt: String)
data class SharedLinkItem(val id: Long, val url: String, val createdAt: String)
private data class MediaMonthGroup<T>(val label: String, val items: List<T>)

private val urlRegex = Regex("https?://[^\\s]+")

fun collectDmPhotos(messages: List<DmMessageDto>): List<SharedPhotoItem> = messages
    .filter { it.deletedAt == null && it.imageUrl.isNotBlank() }
    .map { SharedPhotoItem(it.id, resolveBackendMediaUrl(it.imageUrl), it.createdAt) }

fun collectDmVideos(messages: List<DmMessageDto>): List<SharedVideoItem> = messages
    .filter { it.deletedAt == null && it.fileUrl.isNotBlank() && isVideoAttachment(it.fileName, it.fileUrl) }
    .map { SharedVideoItem(it.id, resolveBackendMediaUrl(it.fileUrl), it.fileName, it.fileSize, it.createdAt) }

fun collectDmFiles(messages: List<DmMessageDto>): List<SharedFileItem> = messages
    .filter { it.deletedAt == null && it.fileUrl.isNotBlank() && !isVideoAttachment(it.fileName, it.fileUrl) }
    .map { SharedFileItem(it.id, resolveBackendMediaUrl(it.fileUrl), it.fileName, it.fileSize, it.createdAt) }

fun collectDmLinks(messages: List<DmMessageDto>): List<SharedLinkItem> = messages
    .filter { it.deletedAt == null && it.content.isNotBlank() }
    .flatMap { message -> urlRegex.findAll(message.content).map { SharedLinkItem(message.id, it.value, message.createdAt) }.toList() }

fun collectRoomPhotos(messages: List<RoomMessageDto>): List<SharedPhotoItem> = messages
    .filter { it.deletedAt == null && it.imageUrl.isNotBlank() }
    .map { SharedPhotoItem(it.id, resolveBackendMediaUrl(it.imageUrl), it.createdAt) }

fun collectRoomVideos(messages: List<RoomMessageDto>): List<SharedVideoItem> = messages
    .filter { it.deletedAt == null && it.fileUrl.isNotBlank() && isVideoAttachment(it.fileName, it.fileUrl) }
    .map { SharedVideoItem(it.id, resolveBackendMediaUrl(it.fileUrl), it.fileName, it.fileSize, it.createdAt) }

fun collectRoomFiles(messages: List<RoomMessageDto>): List<SharedFileItem> = messages
    .filter { it.deletedAt == null && it.fileUrl.isNotBlank() && !isVideoAttachment(it.fileName, it.fileUrl) }
    .map { SharedFileItem(it.id, resolveBackendMediaUrl(it.fileUrl), it.fileName, it.fileSize, it.createdAt) }

fun collectRoomLinks(messages: List<RoomMessageDto>): List<SharedLinkItem> = messages
    .filter { it.deletedAt == null && it.content.isNotBlank() }
    .flatMap { message -> urlRegex.findAll(message.content).map { SharedLinkItem(message.id, it.value, message.createdAt) }.toList() }

fun collectAttachmentPhotos(attachments: List<SharedAttachmentDto>): List<SharedPhotoItem> = attachments
    .filter { it.kind == "photo" || it.kind == "image" }
    .filter { it.url.isNotBlank() }
    .map { SharedPhotoItem(it.id, resolveBackendMediaUrl(it.url), it.createdAt) }

fun collectAttachmentVideos(attachments: List<SharedAttachmentDto>): List<SharedVideoItem> = attachments
    .filter { it.kind == "file" && it.url.isNotBlank() && isVideoAttachment(it.fileName, it.url) }
    .map { SharedVideoItem(it.id, resolveBackendMediaUrl(it.url), it.fileName, it.fileSize, it.createdAt) }

fun collectAttachmentFiles(attachments: List<SharedAttachmentDto>): List<SharedFileItem> = attachments
    .filter { it.kind == "file" && it.url.isNotBlank() && !isVideoAttachment(it.fileName, it.url) }
    .map { SharedFileItem(it.id, resolveBackendMediaUrl(it.url), it.fileName, it.fileSize, it.createdAt) }

fun collectAttachmentLinks(attachments: List<SharedAttachmentDto>): List<SharedLinkItem> = attachments
    .filter { it.kind == "link" && it.url.isNotBlank() }
    .map { SharedLinkItem(it.id, it.url, it.createdAt) }

private fun <T> groupByMonth(items: List<T>, dateSelector: (T) -> String): List<MediaMonthGroup<T>> {
    val formatter = SimpleDateFormat("LLLL yyyy", Locale("ru"))
    return items.groupBy {
        val raw = dateSelector(it).replace(' ', 'T')
        val millis = runCatching { java.time.Instant.parse(if (raw.endsWith("Z")) raw else "${raw}Z").toEpochMilli() }.getOrDefault(0L)
        formatter.format(java.util.Date(millis)).replaceFirstChar { c -> c.uppercase() }
    }.entries.map { MediaMonthGroup(it.key, it.value) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmProfileSheet(
    peer: DialogUserDto,
    messages: List<DmMessageDto>,
    attachments: List<SharedAttachmentDto>? = null,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    onClearDialog: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val photos = remember(messages, attachments) {
        attachments?.let { collectAttachmentPhotos(it) } ?: collectDmPhotos(messages)
    }
    val videos = remember(messages, attachments) {
        attachments?.let { collectAttachmentVideos(it) } ?: collectDmVideos(messages)
    }
    val files = remember(messages, attachments) {
        attachments?.let { collectAttachmentFiles(it) } ?: collectDmFiles(messages)
    }
    val links = remember(messages, attachments) {
        attachments?.let { collectAttachmentLinks(it) } ?: collectDmLinks(messages)
    }
    var panel by remember { mutableStateOf(ProfilePanel.Info) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (panel != ProfilePanel.Info) {
                    IconButton(onClick = { panel = ProfilePanel.Info }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            }
            when (panel) {
                ProfilePanel.Info -> {
                    item {
                        GlassCard(modifier = Modifier.padding(horizontal = 20.dp), radius = 28, padding = 20) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                DialogAvatar(
                                    peer.displayName,
                                    avatarUrl = peer.avatarUrl,
                                    modifier = Modifier
                                        .width(124.dp)
                                        .height(124.dp)
                                        .clickable(enabled = peer.avatarUrl.isNotBlank()) {
                                            onOpenImage(resolveBackendMediaUrl(peer.avatarUrl))
                                        }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(peer.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(if (isOnline) "в сети" else formatLastSeen(peer.lastSeenAt).ifBlank { "не в сети" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile("Сообщение", Icons.Default.Send, Modifier.weight(1f), onClick = onDismiss)
                            ActionTile("Поиск", Icons.Default.Search, Modifier.weight(1f), onClick = { onDismiss(); onOpenSearch() })
                            ActionTile("Очистить", Icons.Default.Delete, Modifier.weight(1f), danger = true, onClick = { onDismiss(); onClearDialog() })
                        }
                    }
                    item {
                        InfoBlock(listOf(
                            "Username" to "@${peer.username}",
                            "Статус" to if (isOnline) "в сети" else formatLastSeen(peer.lastSeenAt).ifBlank { "не в сети" },
                        ))
                    }
                    item {
                        StatsBlock(
                            photoCount = photos.size,
                            videoCount = videos.size,
                            fileCount = files.size,
                            linkCount = links.size,
                            onOpenPhotos = { panel = ProfilePanel.Photos },
                            onOpenVideos = { panel = ProfilePanel.Videos },
                            onOpenFiles = { panel = ProfilePanel.Files },
                            onOpenLinks = { panel = ProfilePanel.Links },
                        )
                    }
                }
                ProfilePanel.Photos -> {
                    item { SectionHeader("Фото", "Все изображения из этого диалога.") }
                    if (photos.isEmpty()) item { EmptyPanel("Фото пока нет") }
                    items(groupByMonth(photos) { it.createdAt }, key = { it.label }) { group ->
                        MediaGridSection(
                            label = group.label,
                            photos = group.items,
                            videos = emptyList(),
                            onOpenImage = onOpenImage,
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
                ProfilePanel.Videos -> {
                    item { SectionHeader("Видео", "Все видео из этого диалога.") }
                    if (videos.isEmpty()) item { EmptyPanel("Видео пока нет") }
                    items(groupByMonth(videos) { it.createdAt }, key = { it.label }) { group ->
                        MediaGridSection(
                            label = group.label,
                            photos = emptyList(),
                            videos = group.items,
                            onOpenImage = onOpenImage,
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
                ProfilePanel.Files -> {
                    item { SectionHeader("Файлы", "Все документы и вложения из этого диалога.") }
                    if (files.isEmpty()) item { EmptyPanel("Файлов пока нет") }
                    items(files, key = { it.id }) { item -> FileRow(item, onClick = { onOpenFile(item.fileUrl) }) }
                }
                ProfilePanel.Links -> {
                    item { SectionHeader("Ссылки", "Все ссылки, отправленные в этом диалоге.") }
                    if (links.isEmpty()) item { EmptyPanel("Ссылок пока нет") }
                    items(links, key = { "${it.id}:${it.url}" }) { item -> LinkRow(item, onClick = { onOpenFile(item.url) }) }
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomProfileSheet(
    room: RoomDto,
    messages: List<RoomMessageDto>,
    attachments: List<SharedAttachmentDto>? = null,
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val photos = remember(messages, attachments) {
        attachments?.let { collectAttachmentPhotos(it) } ?: collectRoomPhotos(messages)
    }
    val videos = remember(messages, attachments) {
        attachments?.let { collectAttachmentVideos(it) } ?: collectRoomVideos(messages)
    }
    val files = remember(messages, attachments) {
        attachments?.let { collectAttachmentFiles(it) } ?: collectRoomFiles(messages)
    }
    val links = remember(messages, attachments) {
        attachments?.let { collectAttachmentLinks(it) } ?: collectRoomLinks(messages)
    }
    var panel by remember { mutableStateOf(ProfilePanel.Info) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (panel != ProfilePanel.Info) {
                    IconButton(onClick = { panel = ProfilePanel.Info }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            }
            when (panel) {
                ProfilePanel.Info -> {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            DialogAvatar(room.name, avatarUrl = room.avatarUrl, modifier = Modifier.width(120.dp).height(120.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(room.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(if (room.accessType == "private") "Закрытая комната" else "Публичная комната", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile("Сообщения", Icons.Default.Send, Modifier.weight(1f), onClick = onDismiss)
                            ActionTile("Поиск", Icons.Default.Search, Modifier.weight(1f), onClick = { onDismiss(); onOpenSearch() })
                            if (onOpenSettings != null) {
                                ActionTile("Настройки", Icons.Default.VideoLibrary, Modifier.weight(1f), onClick = { onDismiss(); onOpenSettings() })
                            }
                        }
                    }
                    item {
                        InfoBlock(listOf(
                            "Тип" to if (room.accessType == "private") "Закрытая комната" else "Публичная комната",
                            "Ссылка" to "#${room.slug.ifBlank { room.name }}",
                            "Описание" to room.description.ifBlank { "Описание не заполнено" },
                        ))
                    }
                    item {
                        StatsBlock(
                            photoCount = photos.size,
                            videoCount = videos.size,
                            fileCount = files.size,
                            linkCount = links.size,
                            extraLabel = "Участники",
                            extraCount = room.membersCount,
                            onOpenPhotos = { panel = ProfilePanel.Photos },
                            onOpenVideos = { panel = ProfilePanel.Videos },
                            onOpenFiles = { panel = ProfilePanel.Files },
                            onOpenLinks = { panel = ProfilePanel.Links },
                        )
                    }
                }
                ProfilePanel.Photos -> {
                    item { SectionHeader("Фото", "Все изображения комнаты.") }
                    if (photos.isEmpty()) item { EmptyPanel("Фото пока нет") }
                    items(groupByMonth(photos) { it.createdAt }, key = { it.label }) { group ->
                        MediaGridSection(
                            label = group.label,
                            photos = group.items,
                            videos = emptyList(),
                            onOpenImage = onOpenImage,
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
                ProfilePanel.Videos -> {
                    item { SectionHeader("Видео", "Все видео комнаты.") }
                    if (videos.isEmpty()) item { EmptyPanel("Видео пока нет") }
                    items(groupByMonth(videos) { it.createdAt }, key = { it.label }) { group ->
                        MediaGridSection(
                            label = group.label,
                            photos = emptyList(),
                            videos = group.items,
                            onOpenImage = onOpenImage,
                            onOpenVideo = onOpenVideo,
                        )
                    }
                }
                ProfilePanel.Files -> {
                    item { SectionHeader("Файлы", "Все документы и вложения комнаты.") }
                    if (files.isEmpty()) item { EmptyPanel("Файлов пока нет") }
                    items(files, key = { it.id }) { item -> FileRow(item, onClick = { onOpenFile(item.fileUrl) }) }
                }
                ProfilePanel.Links -> {
                    item { SectionHeader("Ссылки", "Все ссылки, найденные в сообщениях.") }
                    if (links.isEmpty()) item { EmptyPanel("Ссылок пока нет") }
                    items(links, key = { "${it.id}:${it.url}" }) { item -> LinkRow(item, onClick = { onOpenFile(item.url) }) }
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ActionTile(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(text, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InfoBlock(rows: List<Pair<String, String>>) {
    GlassCard(modifier = Modifier.padding(horizontal = 20.dp), radius = 22, padding = 12) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(value, fontWeight = FontWeight.SemiBold)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsBlock(
    photoCount: Int,
    videoCount: Int,
    fileCount: Int,
    linkCount: Int,
    extraLabel: String? = null,
    extraCount: Int? = null,
    onOpenPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenLinks: () -> Unit,
) {
    GlassCard(modifier = Modifier.padding(horizontal = 20.dp), radius = 22, padding = 12) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatRow(Icons.Default.Image, "Фото", photoCount, onClick = onOpenPhotos)
            StatRow(Icons.Default.VideoLibrary, "Видео", videoCount, onClick = onOpenVideos)
            StatRow(Icons.Default.InsertDriveFile, "Файлы", fileCount, onClick = onOpenFiles)
            StatRow(Icons.Default.Link, "Ссылки", linkCount, onClick = onOpenLinks)
            if (extraLabel != null && extraCount != null) {
                StatRow(Icons.Default.Send, extraLabel, extraCount, onClick = null)
            }
        }
    }
}

@Composable
private fun StatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(count.toString(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PhotoRow(item: SharedPhotoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(formatDateTime(item.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaGridSection(
    label: String,
    photos: List<SharedPhotoItem>,
    videos: List<SharedVideoItem>,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val entries = photos.map { it.id to it } + videos.map { it.id to it }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        entries.chunked(3).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { (_, item) ->
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = RoundedCornerShape(18.dp),
                        onClick = {
                            when (item) {
                                is SharedPhotoItem -> onOpenImage(item.imageUrl)
                                is SharedVideoItem -> onOpenVideo(item.fileUrl)
                            }
                        },
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            when (item) {
                                is SharedPhotoItem -> AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Crop,
                                )
                                is SharedVideoItem -> AsyncImage(
                                    model = item.fileUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            if (item is SharedVideoItem) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Видео", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun FileRow(item: SharedFileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.fileName.ifBlank { "Файл" }, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(formatDateTime(item.createdAt))
                    if (item.fileSize != null) append(" • ${formatFileSize(item.fileSize)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(item: SharedLinkItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                "Сообщение #${item.id} • ${formatDateTime(item.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
