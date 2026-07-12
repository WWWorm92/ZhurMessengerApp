package com.pulsemessenger.android.feature.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.MessageReactionDto
import com.pulsemessenger.android.core.network.PendingAttachment
import com.pulsemessenger.android.core.network.PendingAttachmentKind
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.ui.CreatePollDialog
import com.pulsemessenger.android.ui.EmojiPickerSheet
import com.pulsemessenger.android.ui.FullscreenImageViewer
import com.pulsemessenger.android.ui.AudioAttachmentCard
import com.pulsemessenger.android.ui.DmProfileSheet
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.ReactionChip
import com.pulsemessenger.android.ui.ChatBubbleSkeleton
import com.pulsemessenger.android.ui.VideoAttachmentCard
import com.pulsemessenger.android.ui.VideoPlayerDialog
import com.pulsemessenger.android.ui.formatDateTime
import com.pulsemessenger.android.ui.formatFileSize
import com.pulsemessenger.android.ui.formatLastSeen
import com.pulsemessenger.android.ui.isAudioAttachment
import com.pulsemessenger.android.ui.isVideoAttachment
import com.pulsemessenger.android.ui.parseUtcMillis
import com.pulsemessenger.android.ui.resolveBackendMediaUrl
import androidx.compose.ui.unit.sp
import com.pulsemessenger.android.ui.formatMessageTime
import com.pulsemessenger.android.ui.formatDayLabel
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmChatScreen(
    peer: DialogUserDto,
    currentUserId: Long?,
    currentUserIsAdmin: Boolean,
    viewModel: DmChatViewModel,
    onDraftChange: (String) -> Unit,
    onImageSelected: (Uri) -> Unit,
    onSendPendingImage: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    onSendPendingFile: () -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onForwardSelected: (Set<Long>) -> Unit,
) {
    val listState = rememberLazyListState()
    var fullscreenImageModel by remember { mutableStateOf<Any?>(null) }
    var fullscreenImageModels by remember { mutableStateOf<List<Any>>(emptyList()) }
    var fullscreenImageStartIndex by remember { mutableStateOf(0) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            onImageSelected(uri)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            onFileSelected(uri)
        }
    }

    LaunchedEffect(viewModel.shouldScrollToBottom, viewModel.messages.size, viewModel.isLoading) {
        if (
            viewModel.shouldScrollToBottom &&
            !viewModel.isLoading &&
            viewModel.messages.isNotEmpty()
        ) {
            listState.animateScrollToItem(viewModel.messages.lastIndex)
            viewModel.consumeScrollToBottom()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                if (
                    firstIndex <= 1 &&
                    viewModel.hasMoreMessages &&
                    !viewModel.isLoading &&
                    !viewModel.isLoadingOlder &&
                    viewModel.messages.isNotEmpty()
                ) {
                    val restoreIndex = listState.firstVisibleItemIndex
                    val restoreOffset = listState.firstVisibleItemScrollOffset

                    val added = viewModel.loadOlderMessages()

                    if (added > 0) {
                        listState.scrollToItem(
                            index = restoreIndex + added,
                            scrollOffset = restoreOffset
                        )
                    }
                }
            }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onBack) {
                val edgeWidth = with(density) { 72.dp.toPx() }
                val trigger = with(density) { 96.dp.toPx() }
                var fromEdge = false
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        fromEdge = start.x <= edgeWidth
                        total = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (fromEdge) {
                            total += dragAmount
                            if (total >= trigger) {
                                fromEdge = false
                                onBack()
                            }
                        }
                    },
                )
            }
            .imePadding()
            .navigationBarsPadding()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        if (viewModel.selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Выбрано: ${viewModel.selectedMessageIds.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onForwardSelected(viewModel.selectedMessageIds) }) {
                        Icon(Icons.Default.Share, contentDescription = "Переслать")
                    }
                    IconButton(onClick = { viewModel.deleteSelected() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }

                    DialogAvatar(
                        peer.displayName,
                        avatarUrl = peer.avatarUrl,
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                showProfileSheet = true
                                viewModel.loadProfileAttachments()
                            }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showProfileSheet = true
                                viewModel.loadProfileAttachments()
                            }
                    ) {
                        Text(
                            text = peer.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = when {
                                viewModel.isPeerTyping -> "печатает..."
                                viewModel.isPeerOnline -> "в сети"
                                else -> formatLastSeen(peer.lastSeenAt).ifBlank { "не в сети" }
                            },
                            color = if (viewModel.isPeerTyping || viewModel.isPeerOnline) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.isLoading && viewModel.messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChatBubbleSkeleton(isMine = false)
                ChatBubbleSkeleton(isMine = true)
                ChatBubbleSkeleton(isMine = false)
                ChatBubbleSkeleton(isMine = true)
            }
        } else if (!viewModel.error.isNullOrBlank() && viewModel.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Не удалось загрузить диалог", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(onClick = viewModel::loadMessages) {
                            Text("Повторить")
                        }
                    }
                }
            }
        } else if (viewModel.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Пока сообщений нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (viewModel.isLoadingOlder) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Загружаем старые сообщения...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = viewModel.messages,
                    key = { _, item -> item.id }

                ) { index, message ->

                    val previousMessage = viewModel.messages.getOrNull(index - 1)

                    val currentDay = formatDayLabel(message.createdAt)
                    val previousDay = formatDayLabel(previousMessage?.createdAt)

                    val showDayLabel = currentDay.isNotBlank() && currentDay != previousDay

                    if (showDayLabel) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(50.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                            ) {
                                Text(
                                    text = currentDay,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(modifier = Modifier.animateItem()) {
                        DmMessageBubble(
                            message = message,
                            isMine = currentUserId != null && message.senderId == currentUserId,
                            isEditing = viewModel.editingMessageId == message.id,
                            onOpenImage = {
                                val images = viewModel.messages
                                    .filter { it.deletedAt == null && it.imageUrl.isNotBlank() }
                                    .map { resolveBackendMediaUrl(it.imageUrl) as Any }

                                val currentImage = resolveBackendMediaUrl(message.imageUrl)
                                val currentIndex = images.indexOf(currentImage).takeIf { it >= 0 } ?: 0

                                fullscreenImageModels = images
                                fullscreenImageStartIndex = currentIndex
                                fullscreenImageModel = currentImage
                            },
                            onOpenVideo = { url -> fullscreenVideoUrl = url },
                            onOpenFile = { fileUrl -> uriHandler.openUri(resolveBackendMediaUrl(fileUrl)) },
                            quickReactions = viewModel.quickReactions,
                            onToggleReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                            currentUserId = currentUserId,
                            currentUserIsAdmin = currentUserIsAdmin,
                            onVotePoll = { pollId, optionIds -> viewModel.votePoll(pollId, optionIds) },
                            onClosePoll = viewModel::closePoll,
                            peerLastReadAt = viewModel.peerLastReadAt,
                            onEdit = { viewModel.beginEdit(message) },
                            onDelete = { viewModel.deleteMessage(message.id) },
                            onPin = { viewModel.pinMessage(message.id) },
                            onUnpin = { viewModel.unpinMessage() },
                            onReply = { viewModel.beginReply(message) },
                            onSelect = { viewModel.beginSelect(message.id) },
                            selectionMode = viewModel.selectionMode,
                            isSelected = viewModel.selectedMessageIds.contains(message.id),
                            onToggleSelect = { viewModel.toggleSelection(message.id) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.editingMessageId != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Редактирование", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = viewModel::cancelEditing) {
                        Text("Отмена")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (viewModel.replyToMessageId != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ответ: ${viewModel.replyToMessageContent.take(50)}",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = viewModel::cancelReply) {
                        Text("Отмена")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (viewModel.pendingAttachments.isNotEmpty()) {


            PendingAttachmentsPreview(
                attachments = viewModel.pendingAttachments,
                isUploading = viewModel.isUploadingImage,
                onRemove = viewModel::removePendingAttachment,
                onOpenImage = { uri ->
                    val images = viewModel.pendingAttachments
                        .filter { it.kind == PendingAttachmentKind.Image }
                        .map { it.uri as Any }

                    fullscreenImageModels = images
                    fullscreenImageStartIndex = images.indexOf(uri).takeIf { it >= 0 } ?: 0
                    fullscreenImageModel = uri
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { attachMenuExpanded = true },
                enabled = !viewModel.isUploadingImage && viewModel.editingMessageId == null,
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить")
            }
            OutlinedTextField(
                value = viewModel.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = { Text("Сообщение") },
                maxLines = 5,
                shape = RoundedCornerShape(26.dp),
            )
            IconButton(onClick = { showEmojiPicker = true }) {
                Icon(Icons.Default.TagFaces, contentDescription = "Emoji")
            }
            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendMessage(context)
                },
                enabled = !viewModel.isUploadingImage &&
                    (
                        viewModel.draft.isNotBlank() ||
                            viewModel.pendingAttachments.isNotEmpty() ||
                            viewModel.editingMessageId != null
                        ),
            ) {
                Icon(
                    imageVector = if (viewModel.editingMessageId != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (viewModel.editingMessageId != null) "Сохранить" else "Отправить",
                )
            }
        }

        if (!viewModel.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val imageModel = fullscreenImageModel
        if (imageModel != null) {
            FullscreenImageViewer(
                imageModel = imageModel,
                imageModels = fullscreenImageModels.ifEmpty { listOf(imageModel) },
                initialIndex = fullscreenImageStartIndex,
                onDismiss = {
                    fullscreenImageModel = null
                    fullscreenImageModels = emptyList()
                    fullscreenImageStartIndex = 0
                }
            )
        }

        val videoUrl = fullscreenVideoUrl
        if (videoUrl != null) {
            VideoPlayerDialog(url = videoUrl, onDismiss = { fullscreenVideoUrl = null })
        }

        if (showEmojiPicker) {
            EmojiPickerSheet(
                onEmojiPicked = { emoji ->
                    onDraftChange(viewModel.draft + emoji)
                    showEmojiPicker = false
                },
                onDismiss = { showEmojiPicker = false },
            )
        }

        if (showPollDialog) {
            CreatePollDialog(
                onDismiss = { showPollDialog = false },
                onCreate = { poll ->
                    showPollDialog = false
                    viewModel.sendPoll(poll)
                },
            )
        }

        if (attachMenuExpanded) {
            ModalBottomSheet(
                onDismissRequest = { attachMenuExpanded = false }
            ) {
                AttachmentPickerSheetContent(
                    onImageClick = {
                        attachMenuExpanded = false
                        imagePicker.launch("image/*")
                    },
                    onFileClick = {
                        attachMenuExpanded = false
                        filePicker.launch("*/*")
                    },
                    onPollClick = {
                        attachMenuExpanded = false
                        showPollDialog = true
                    }
                )
            }
        }

        if (showProfileSheet) {
            DmProfileSheet(
                peer = peer,
                messages = viewModel.messages,
                attachments = viewModel.profileAttachments,
                isOnline = viewModel.isPeerOnline,
                onDismiss = { showProfileSheet = false },
                onOpenSearch = onOpenSearch,
                onClearDialog = { viewModel.clearDialog(peer.id) },
                onOpenImage = { url ->
                    showProfileSheet = false
                    fullscreenImageModels = listOf(url)
                    fullscreenImageStartIndex = 0
                    fullscreenImageModel = url
                },
                onOpenVideo = { url ->
                    showProfileSheet = false
                    fullscreenVideoUrl = url
                },
                onOpenFile = { url -> uriHandler.openUri(url) },
            )
        }
    }
}

@Composable
private fun AttachmentPickerSheetContent(
    onImageClick: () -> Unit,
    onFileClick: () -> Unit,
    onPollClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Прикрепить",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        AttachmentOption(
            icon = "🖼️",
            title = "Фото",
            subtitle = "Выбрать одно или несколько фото",
            onClick = onImageClick
        )

        AttachmentOption(
            icon = "📎",
            title = "Файл",
            subtitle = "Документ, архив, видео или другой файл",
            onClick = onFileClick
        )

        AttachmentOption(
            icon = "📊",
            title = "Опрос",
            subtitle = "Создать опрос в чате",
            onClick = onPollClick
        )
    }
}

@Composable
private fun AttachmentOption(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
@Composable
private fun PendingAttachmentsPreview(
    attachments: List<PendingAttachment>,
    isUploading: Boolean,
    onRemove: (Long) -> Unit,
    onOpenImage: (Uri) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(attachments, key = { it.localId }) { attachment ->
                    Box(
                        modifier = Modifier.size(86.dp)
                    ) {
                        when (attachment.kind) {
                            PendingAttachmentKind.Image -> {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = "Фото",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable(enabled = !isUploading) {
                                            onOpenImage(attachment.uri)
                                        }
                                )
                            }

                            PendingAttachmentKind.File -> {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = attachment.fileName.ifBlank { "Файл" },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (attachment.fileSize != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatFileSize(attachment.fileSize),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { onRemove(attachment.localId) },
                            enabled = !isUploading,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Убрать",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmMessageBubble(
    message: DmMessageDto,
    isMine: Boolean,
    isEditing: Boolean,
    onOpenImage: (Any) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    quickReactions: List<String>,
    onToggleReaction: (String) -> Unit,
    currentUserId: Long?,
    currentUserIsAdmin: Boolean,
    onVotePoll: (Long, List<Long>) -> Unit,
    onClosePoll: (Long) -> Unit,
    peerLastReadAt: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit = {},
    onUnpin: () -> Unit = {},
    onReply: () -> Unit = {},
    onSelect: () -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    var confirmDelete by remember(message.id) { mutableStateOf(false) }
    val resolvedFileUrl = if (message.fileUrl.isNotBlank()) resolveBackendMediaUrl(message.fileUrl) else ""
    var replySwipeOffset by remember(message.id) { mutableStateOf(0f) }
    val maxReplySwipe = with(LocalDensity.current) { 56.dp.toPx() }
    val replyTrigger = with(LocalDensity.current) { 34.dp.toPx() }

    val bubbleModifier = Modifier
        .widthIn(max = 292.dp)
        .offset {
            IntOffset(
                x = replySwipeOffset.roundToInt(),
                y = 0
            )
        }
        .pointerInput(message.id, selectionMode) {
            if (!selectionMode && message.deletedAt == null) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f) {
                            replySwipeOffset = (replySwipeOffset + dragAmount)
                                .coerceIn(0f, maxReplySwipe)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (replySwipeOffset >= replyTrigger) {
                            onReply()
                        }
                        replySwipeOffset = 0f
                    },
                    onDragCancel = {
                        replySwipeOffset = 0f
                    }
                )
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode && !isMine) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
        }
        Box {
            if (replySwipeOffset > 4f && message.deletedAt == null && !selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↩",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Card(
                    modifier = if (message.deletedAt == null && !selectionMode) {
                        bubbleModifier.combinedClickable(
                            onClick = { menuExpanded = true },
                            onLongClick = { onSelect() }
                        )
                    } else if (message.deletedAt == null && selectionMode) {
                        bubbleModifier.combinedClickable(
                            onClick = { onToggleSelect() },
                            onLongClick = {}
                        )
                    } else {
                        bubbleModifier
                    },
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isMine) 20.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 20.dp,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                        } else if (isMine) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        val mainText = when {
                            message.poll != null -> message.poll.question
                            message.deletedAt != null -> "Сообщение удалено"
                            message.content.isNotBlank() -> message.content
                            message.fileUrl.isNotBlank() || message.imageUrl.isNotBlank() -> ""
                            else -> "[${message.type}]"
                        }
                        if (mainText.isNotBlank()) {
                            Text(mainText)
                        }
                        if (message.imageUrl.isNotBlank() && message.deletedAt == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = resolveBackendMediaUrl(message.imageUrl),
                                contentDescription = "image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 280.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = !selectionMode) {
                                        onOpenImage(resolveBackendMediaUrl(message.imageUrl))
                                    }
                            )
                        }
                        if (message.poll != null && message.deletedAt == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PollCard(
                                poll = message.poll,
                                currentUserId = currentUserId,
                                currentUserIsAdmin = currentUserIsAdmin,
                                onVote = { optionIds -> onVotePoll(message.poll.id, optionIds) },
                                onClose = { onClosePoll(message.poll.id) },
                            )
                        }
                        if (message.fileUrl.isNotBlank() && message.deletedAt == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            when {
                                isVideoAttachment(message.fileName, resolvedFileUrl) -> VideoAttachmentCard(
                                    fileName = message.fileName,
                                    fileSize = message.fileSize,
                                    url = resolvedFileUrl,
                                    enabled = !selectionMode,
                                    onOpen = { onOpenVideo(resolvedFileUrl) },
                                )
                                isAudioAttachment(message.fileName, resolvedFileUrl) -> AudioAttachmentCard(
                                    fileName = message.fileName,
                                    fileSize = message.fileSize,
                                    url = resolvedFileUrl,
                                    enabled = !selectionMode,
                                )
                                else -> Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable(enabled = !selectionMode) { onOpenFile(message.fileUrl) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(message.fileName.ifBlank { "Файл" }, fontWeight = FontWeight.SemiBold)
                                        if (message.fileSize != null) {
                                            Text(
                                                formatFileSize(message.fileSize),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Нажмите, чтобы открыть",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append(formatMessageTime(message.createdAt))
                                    if (message.editedAt != null && message.deletedAt == null) {
                                        append(" • изменено")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isMine && message.deletedAt == null) {
                                val readMillis = parseUtcMillis(peerLastReadAt)
                                val sentMillis = parseUtcMillis(message.createdAt)
                                val checks = if (readMillis != null && sentMillis != null && readMillis >= sentMillis) {
                                    "✓✓"
                                } else {
                                    "✓"
                                }

                                Text(
                                    text = checks,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        letterSpacing = (-4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        message.reactions.forEach { reaction ->
                            ReactionChip(reaction = reaction, onClick = { onToggleReaction(reaction.emoji) })
                        }
                    }
                }
            }
            if (message.deletedAt == null && !selectionMode) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickReactions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        menuExpanded = false
                                        onToggleReaction(emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 22.sp,
                                    lineHeight = 22.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Закрепить") },
                        onClick = {
                            menuExpanded = false
                            onPin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ответить") },
                        onClick = {
                            menuExpanded = false
                            onReply()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Выбрать") },
                        onClick = {
                            menuExpanded = false
                            onSelect()
                        }
                    )
                    if (isMine) {
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }
            }
        }
        if (selectionMode && isMine) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено для участников диалога.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun PollCard(
    poll: PollDto,
    currentUserId: Long?,
    currentUserIsAdmin: Boolean,
    onVote: (List<Long>) -> Unit,
    onClose: () -> Unit,
) {
    val totalVotes = poll.options.sumOf { it.votes }
    var selectedOptionIds by remember(poll.id, poll.options) {
        mutableStateOf(poll.options.filter { it.votedByMe }.map { it.id }.toSet())
    }
    val canClose = !poll.isClosed && (currentUserIsAdmin || currentUserId == poll.creatorId)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Голосов: $totalVotes${if (poll.isClosed) " • закрыт" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        poll.options.forEach { option ->
            val fraction = if (totalVotes > 0) option.votes.toFloat() / totalVotes else 0f
            Card(
                onClick = {
                    if (poll.isClosed) return@Card
                    if (poll.allowMultiple) {
                        selectedOptionIds = if (selectedOptionIds.contains(option.id)) {
                            selectedOptionIds - option.id
                        } else {
                            selectedOptionIds + option.id
                        }
                    } else {
                        onVote(listOf(option.id))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedOptionIds.contains(option.id) || option.votedByMe) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    }
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option.text, modifier = Modifier.weight(1f))
                        Text(
                            text = "${option.votes}",
                            color = if (selectedOptionIds.contains(option.id) || option.votedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (poll.isClosed) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            { fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                }
            }
        }
        if (poll.allowMultiple) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { selectedOptionIds = emptySet() },
                    enabled = !poll.isClosed && selectedOptionIds.isNotEmpty()
                ) {
                    Text("Сбросить")
                }
                Button(
                    onClick = { onVote(selectedOptionIds.toList()) },
                    enabled = !poll.isClosed && selectedOptionIds.isNotEmpty()
                ) {
                    Text("Голосовать")
                }
            }
        }
        if (canClose) {
            OutlinedButton(onClick = onClose) {
                Text("Закрыть опрос")
            }
        }
    }
}
