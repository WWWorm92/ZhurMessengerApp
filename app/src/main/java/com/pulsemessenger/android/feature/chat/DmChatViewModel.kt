package com.pulsemessenger.android.feature.chat

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.PollCreationRequest
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.core.network.PollOptionDto
import com.pulsemessenger.android.core.network.PollVoterDto
import com.pulsemessenger.android.core.network.PendingAttachment
import com.pulsemessenger.android.core.network.PendingAttachmentKind
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import com.pulsemessenger.android.core.network.queryDisplayName
import com.pulsemessenger.android.core.network.queryFileSize
import com.pulsemessenger.android.ui.resolveBackendMediaUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class DmChatViewModel(
    private val repository: DmChatRepository,
) : ViewModel() {
    val quickReactions = listOf("😀", "😎", "😂", "😍", "🥳", "👍", "👎", "🔥", "❤️", "💔", "🤝", "👏")
    var peer by mutableStateOf<DialogUserDto?>(null)
    var messages by mutableStateOf<List<DmMessageDto>>(emptyList())
    var profileAttachments by mutableStateOf<List<SharedAttachmentDto>?>(null)
    var isProfileAttachmentsLoading by mutableStateOf(false)
    var profileAttachmentsError by mutableStateOf<String?>(null)
    var encryptedImagePreviews by mutableStateOf<Map<Long, String>>(emptyMap())
    var cachedEncryptedAttachments by mutableStateOf<Set<Long>>(emptySet())
    private val encryptedImagePreviewJobs = mutableSetOf<Long>()
    private val encryptedAttachmentPrefetchJobs = mutableSetOf<Long>()
    var isLoading by mutableStateOf(false)
    var isLoadingOlder by mutableStateOf(false)
    var hasMoreMessages by mutableStateOf(false)
    var shouldScrollToBottom by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var draft by mutableStateOf("")
    var editingMessageId by mutableStateOf<Long?>(null)
    var replyToMessageId by mutableStateOf<Long?>(null)
    var replyToMessageContent by mutableStateOf("")
    var isUploadingImage by mutableStateOf(false)
    var pendingAttachments by mutableStateOf<List<PendingAttachment>>(emptyList())
    var pendingPoll by mutableStateOf<PollCreationRequest?>(null)
    var isPeerOnline by mutableStateOf(false)
    var isPeerTyping by mutableStateOf(false)
    var peerLastReadAt by mutableStateOf<String?>(null)
    private var stopTypingJob: Job? = null

    private var markReadJob: Job? = null
    var selectionMode by mutableStateOf(false)
    var selectedMessageIds by mutableStateOf<Set<Long>>(emptySet())

    fun beginSelect(messageId: Long) {
        selectionMode = true
        selectedMessageIds = setOf(messageId)
    }

    fun toggleSelection(messageId: Long) {
        selectedMessageIds = if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds - messageId
        } else {
            selectedMessageIds + messageId
        }
        if (selectedMessageIds.isEmpty()) {
            selectionMode = false
        }
    }

    fun clearSelection() {
        selectionMode = false
        selectedMessageIds = emptySet()
    }

    fun deleteSelected() {
        val idsToDelete = selectedMessageIds.toList()
        clearSelection()
        idsToDelete.forEach { id -> deleteMessage(id) }
    }

    fun openDialog(user: DialogUserDto) {
        peer = user
        isPeerOnline = user.online
        isPeerTyping = false
        peerLastReadAt = null
        profileAttachments = null
        profileAttachmentsError = null
        isProfileAttachmentsLoading = false
        encryptedImagePreviews = emptyMap()
        cachedEncryptedAttachments = emptySet()
        encryptedImagePreviewJobs.clear()
        encryptedAttachmentPrefetchJobs.clear()
        hasMoreMessages = false
        isLoadingOlder = false
        shouldScrollToBottom = true
        loadMessages()
    }

    fun closeDialog() {
        stopTypingJob?.cancel()
        markReadJob?.cancel()
        peer = null
        messages = emptyList()
        draft = ""
        error = null
        editingMessageId = null
        replyToMessageId = null
        replyToMessageContent = ""
        isUploadingImage = false
        pendingAttachments = emptyList()
        isPeerOnline = false
        isPeerTyping = false
        peerLastReadAt = null
        profileAttachments = null
        profileAttachmentsError = null
        isProfileAttachmentsLoading = false
        encryptedImagePreviews = emptyMap()
        cachedEncryptedAttachments = emptySet()
        encryptedImagePreviewJobs.clear()
        encryptedAttachmentPrefetchJobs.clear()
        clearSelection()
        hasMoreMessages = false
        isLoadingOlder = false
        shouldScrollToBottom = false
    }

    fun loadMessages() {
        val currentPeer = peer ?: return
        if (isLoading) return

        isLoading = true
        error = null
        hasMoreMessages = false
        shouldScrollToBottom = true

        viewModelScope.launch {
            repository.loadMessages(
                peerUserId = currentPeer.id,
                beforeId = null,
                limit = 60,
            )
                .onSuccess { payload ->
                    messages = payload.messages.sortedBy { it.id }
                    peerLastReadAt = payload.peerLastReadAt
                    hasMoreMessages = payload.hasMore
                }
                .onFailure {
                    error = it.message ?: "Failed to load messages"
                }

            isLoading = false
        }
    }

    suspend fun loadOlderMessages(): Int {
        val currentPeer = peer ?: return 0

        if (isLoading || isLoadingOlder || !hasMoreMessages || messages.isEmpty()) {
            return 0
        }

        val beforeId = messages.first().id
        isLoadingOlder = true
        error = null

        var addedCount = 0

        repository.loadMessages(
            peerUserId = currentPeer.id,
            beforeId = beforeId,
            limit = 60,
        )
            .onSuccess { payload ->
                val existingIds = messages.map { it.id }.toSet()
                val older = payload.messages
                    .filterNot { existingIds.contains(it.id) }
                    .sortedBy { it.id }

                if (older.isNotEmpty()) {
                    messages = (older + messages)
                        .distinctBy { it.id }
                        .sortedBy { it.id }

                    addedCount = older.size
                }

                if (peerLastReadAt == null) {
                    peerLastReadAt = payload.peerLastReadAt
                }

                hasMoreMessages = payload.hasMore
            }
            .onFailure {
                error = it.message ?: "Не удалось загрузить старые сообщения"
            }

        isLoadingOlder = false
        return addedCount
    }

    fun consumeScrollToBottom() {
        shouldScrollToBottom = false
    }

    fun updatePeerReadAt(readAt: String?) {
        peerLastReadAt = readAt
    }

    fun loadProfileAttachments() {
        val currentPeer = peer ?: return
        if (isProfileAttachmentsLoading) return

        isProfileAttachmentsLoading = true
        profileAttachmentsError = null

        viewModelScope.launch {
            repository.loadAttachments(currentPeer.id)
                .onSuccess { attachments ->
                    profileAttachments = attachments
                }
                .onFailure {
                    profileAttachmentsError = it.message ?: "Не удалось загрузить вложения"
                }

            isProfileAttachmentsLoading = false
        }
    }

    fun onDmRead(peerUserId: Long, readAt: String?) {
        val currentPeer = peer ?: return

        if (currentPeer.id == peerUserId) {
            peerLastReadAt = readAt
        }
    }

    private fun markCurrentDialogRead() {
        val currentPeer = peer ?: return

        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            repository.markRead(currentPeer.id)
        }
    }

    fun sendMessage(context: Context? = null) {
        val currentPeer = peer ?: return
        val content = draft.trim()
        val attachments = pendingAttachments
        val editingId = editingMessageId

        if (editingId != null) {
            if (attachments.isNotEmpty()) {
                error = "Нельзя прикреплять файлы при редактировании сообщения"
                return
            }

            if (content.isNotBlank()) {
                saveEditedMessage(editingId, content)
            }
            return
        }

        if (attachments.isNotEmpty()) {
            if (context == null || isUploadingImage) {
                return
            }

            val previousDraft = draft
            val replyingTo = replyToMessageId
            val toSend = attachments

            val imageAttachments = toSend.filter { it.kind == PendingAttachmentKind.Image }

            val mediaGroupId = if (imageAttachments.size > 1 && imageAttachments.size == toSend.size) {
                UUID.randomUUID().toString()
            } else {
                null
            }

            error = null
            isUploadingImage = true

            viewModelScope.launch {
                for ((index, attachment) in toSend.withIndex()) {
                    val caption = if (index == 0) previousDraft else ""
                    val replyId = if (index == 0) replyingTo else null

                    val result = sendAttachmentMessage(
                        context = context,
                        peerUserId = currentPeer.id,
                        attachment = attachment,
                        caption = caption,
                        replyToMessageId = replyId,
                        mediaGroupId = mediaGroupId,
                    )

                    result
                        .onSuccess { message ->
                            upsertMessage(message)
                        }
                        .onFailure {
                            error = it.message ?: "Не удалось отправить вложение"
                            isUploadingImage = false
                            return@launch
                        }
                }

                draft = ""
                replyToMessageId = null
                replyToMessageContent = ""
                pendingAttachments = emptyList()
                isUploadingImage = false
            }

            return
        }

        if (content.isBlank()) return

        val replyingTo = replyToMessageId
        val previousDraft = draft
        draft = ""
        replyToMessageId = null
        replyToMessageContent = ""
        error = null

        viewModelScope.launch {
            repository.sendMessage(currentPeer.id, content, replyToMessageId = replyingTo)
                .onSuccess { message ->
                    upsertMessage(message)
                }
                .onFailure {
                    draft = previousDraft
                    replyToMessageId = replyingTo
                    replyToMessageContent = messages.firstOrNull { it.id == replyingTo }?.content ?: ""
                    error = it.message ?: "Failed to send message"
                }
        }
    }

    fun sendPoll(poll: PollCreationRequest) {
        val currentPeer = peer ?: return
        pendingPoll = null
        error = null
        viewModelScope.launch {
            repository.sendMessage(
                peerUserId = currentPeer.id,
                content = "",
                imageUrl = null,
                fileUrl = null,
                fileName = null,
                fileSize = null,
                poll = poll,
            )
                .onSuccess { message ->
                    upsertMessage(message)
                }
                .onFailure {
                    error = it.message ?: "Failed to send poll"
                }
        }
    }

    fun beginEdit(message: DmMessageDto) {
        if (message.deletedAt != null) {
            return
        }
        editingMessageId = message.id
        draft = message.content
        error = null
    }

    fun cancelEditing() {
        editingMessageId = null
        draft = ""
    }

    fun beginReply(message: DmMessageDto) {
        if (message.deletedAt != null) return
        cancelEditing()
        replyToMessageId = message.id
        replyToMessageContent = message.content
    }

    fun cancelReply() {
        replyToMessageId = null
        replyToMessageContent = ""
    }

    fun selectImage(uri: Uri) {
        if (editingMessageId != null || isUploadingImage) {
            return
        }

        pendingAttachments = pendingAttachments + PendingAttachment(
            uri = uri,
            kind = PendingAttachmentKind.Image,
        )
        error = null
    }

    fun pinMessage(messageId: Long) {
        val currentPeer = peer ?: return
        viewModelScope.launch {
            repository.pinMessage(currentPeer.id, messageId)
        }
    }

    fun unpinMessage() {
        val currentPeer = peer ?: return
        viewModelScope.launch {
            repository.unpinMessage(currentPeer.id)
        }
    }

    fun selectFile(context: Context, uri: Uri) {
        if (editingMessageId != null || isUploadingImage) {
            return
        }

        pendingAttachments = pendingAttachments + PendingAttachment(
            uri = uri,
            kind = PendingAttachmentKind.File,
            fileName = queryDisplayName(context, uri).orEmpty(),
            fileSize = queryFileSize(context, uri),
        )
        error = null
    }

    fun removePendingAttachment(localId: Long) {
        pendingAttachments = pendingAttachments.filterNot { it.localId == localId }
    }

    fun clearPendingAttachments() {
        pendingAttachments = emptyList()
    }

    fun deleteMessage(messageId: Long) {
        error = null
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onSuccess { message ->
                    upsertMessage(message)
                    if (editingMessageId == messageId) {
                        cancelEditing()
                    }
                }
                .onFailure {
                    error = it.message ?: "Failed to delete message"
                }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        error = null
        viewModelScope.launch {
            repository.toggleReaction(messageId, emoji)
                .onSuccess { message ->
                    upsertMessage(message)
                }
                .onFailure {
                    error = it.message ?: "Failed to toggle reaction"
                }
        }
    }

    fun votePoll(pollId: Long, optionIds: List<Long>) {
        error = null
        viewModelScope.launch {
            repository.votePoll(pollId, optionIds)
                .onSuccess { poll -> patchPoll(poll) }
                .onFailure {
                    error = it.message ?: "Failed to vote poll"
                }
        }
    }

    fun closePoll(pollId: Long) {
        error = null
        viewModelScope.launch {
            repository.closePoll(pollId)
                .onSuccess { poll -> patchPoll(poll) }
                .onFailure {
                    error = it.message ?: "Failed to close poll"
                }
        }
    }

    fun sendPendingImage(context: Context) {
        sendMessage(context)
    }

    fun sendPendingFile(context: Context) {
        sendMessage(context)
    }



    fun preloadEncryptedImagePreviews(context: Context) {
        messages
            .filter { it.deletedAt == null }
            .filter { repository.isEncryptedAttachment(it) }
            .forEach { message -> ensureEncryptedAttachmentPrefetched(context, message) }
    }

    fun ensureEncryptedAttachmentPrefetched(context: Context, message: DmMessageDto) {
        if (!repository.isEncryptedAttachment(message)) return

        if (cachedEncryptedAttachments.contains(message.id) || repository.isDecryptedAttachmentCached(context, message)) {
            cachedEncryptedAttachments = cachedEncryptedAttachments + message.id
            if (repository.isEncryptedImageAttachment(message)) {
                ensureEncryptedImagePreview(context, message)
            }
            return
        }

        if (!encryptedAttachmentPrefetchJobs.add(message.id)) return

        viewModelScope.launch {
            repository.prefetchEncryptedAttachment(context, message)
                .onSuccess {
                    cachedEncryptedAttachments = cachedEncryptedAttachments + message.id
                    if (repository.isEncryptedImageAttachment(message)) {
                        ensureEncryptedImagePreview(context, message)
                    }
                }
                .onFailure {
                    // Тихо игнорируем: вложение всё равно можно будет скачать по нажатию.
                }

            encryptedAttachmentPrefetchJobs.remove(message.id)
        }
    }

    fun ensureEncryptedImagePreview(context: Context, message: DmMessageDto) {
        if (!repository.isEncryptedImageAttachment(message)) return
        if (encryptedImagePreviews.containsKey(message.id)) return
        if (!encryptedImagePreviewJobs.add(message.id)) return

        viewModelScope.launch {
            repository.createEncryptedImagePreview(context, message)
                .onSuccess { previewUri ->
                    encryptedImagePreviews = encryptedImagePreviews + (message.id to previewUri)
                }
                .onFailure {
                    // Не показываем ошибку в шапке чата: превью не критично, файл всё равно откроется по нажатию.
                }

            encryptedImagePreviewJobs.remove(message.id)
        }
    }

    fun encryptedPreviewFor(messageId: Long): String? = encryptedImagePreviews[messageId]

    fun isEncryptedImageAttachment(message: DmMessageDto): Boolean {
        return repository.isEncryptedImageAttachment(message)
    }

    fun openAttachment(
        context: Context,
        message: DmMessageDto,
        onOpenImage: (String) -> Unit,
        onOpenUrl: (String) -> Unit,
    ) {
        if (!repository.isEncryptedAttachment(message)) {
            if (message.fileUrl.isNotBlank()) {
                onOpenUrl(resolveBackendMediaUrl(message.fileUrl))
            }
            return
        }

        repository.cachedDecryptedAttachment(context, message)?.let { opened ->
            if (opened.kind == "image" || opened.mimeType.startsWith("image/")) {
                onOpenImage(opened.uri.toString())
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(opened.uri, opened.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(Intent.createChooser(intent, opened.fileName))
            } catch (error: ActivityNotFoundException) {
                this@DmChatViewModel.error = "Нет приложения для открытия этого файла"
            }
            return
        }

        error = null
        viewModelScope.launch {
            repository.downloadAndDecryptAttachment(context, message)
                .onSuccess { opened ->
                    if (opened.kind == "image" || opened.mimeType.startsWith("image/")) {
                        onOpenImage(opened.uri.toString())
                        return@onSuccess
                    }

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(opened.uri, opened.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    try {
                        context.startActivity(Intent.createChooser(intent, opened.fileName))
                    } catch (error: ActivityNotFoundException) {
                        this@DmChatViewModel.error = "Нет приложения для открытия этого файла"
                    }
                }
                .onFailure {
                    error = it.message ?: "Не удалось открыть зашифрованное вложение"
                }
        }
    }


    private suspend fun sendAttachmentMessage(
        context: Context,
        peerUserId: Long,
        attachment: PendingAttachment,
        caption: String,
        replyToMessageId: Long?,
        mediaGroupId: String?,
    ): Result<DmMessageDto> {
        return when (attachment.kind) {
            PendingAttachmentKind.Image -> {
                repository.sendEncryptedAttachment(
                    peerUserId = peerUserId,
                    context = context,
                    uri = attachment.uri,
                    attachmentKind = "image",
                    caption = caption,
                    replyToMessageId = replyToMessageId,
                    mediaGroupId = mediaGroupId,
                )
            }

            PendingAttachmentKind.File -> {
                repository.sendEncryptedAttachment(
                    peerUserId = peerUserId,
                    context = context,
                    uri = attachment.uri,
                    attachmentKind = "file",
                    caption = caption,
                    replyToMessageId = replyToMessageId,
                    mediaGroupId = mediaGroupId,
                )
            }
        }
    }

    fun onDraftChanged(value: String, emitTyping: (Boolean) -> Unit) {
        val wasBlank = draft.isBlank()
        draft = value
        val isBlank = value.isBlank()
        if (wasBlank && !isBlank) {
            emitTyping(true)
        } else if (!wasBlank && isBlank) {
            emitTyping(false)
        }
        scheduleTypingStop(isBlank, emitTyping)
    }

    fun onPresenceUpdate(onlineUserIds: Set<Long>) {
        val currentPeerId = peer?.id ?: return
        isPeerOnline = onlineUserIds.contains(currentPeerId)
    }

    fun onTypingUpdate(scope: String, targetId: Long, userId: Long, isTyping: Boolean) {
        val currentPeer = peer ?: return
        if (scope != "dm" || currentPeer.id != targetId || currentPeer.id != userId) {
            return
        }
        isPeerTyping = isTyping
    }

    fun onRealtimeMessage(payload: JSONObject) {
        val currentPeer = peer ?: return
        val message = parseRealtimeMessage(payload)

        val belongsToCurrentDialog =
            message.senderId == currentPeer.id || message.receiverId == currentPeer.id

        if (!belongsToCurrentDialog) {
            return
        }

        upsertMessage(message)

        if (message.senderId == currentPeer.id) {
            isPeerTyping = false
            markCurrentDialogRead()
        }
    }

    fun onRealtimeMessageUpdate(payload: JSONObject) {
        val currentPeer = peer ?: return
        val message = parseRealtimeMessage(payload)
        val belongsToCurrentDialog = message.senderId == currentPeer.id || message.receiverId == currentPeer.id
        if (!belongsToCurrentDialog) {
            return
        }
        upsertMessage(message)
    }

    fun clearDialog(peerUserId: Long) {
        if (peer?.id != peerUserId) {
            return
        }
        messages = emptyList()
        error = null
        editingMessageId = null
        pendingAttachments = emptyList()
        encryptedImagePreviews = emptyMap()
        encryptedImagePreviewJobs.clear()
        isPeerTyping = false
    }

    fun onPollUpdate(payload: JSONObject) {
        patchPoll(payload.toPollDto())
    }

    private fun saveEditedMessage(messageId: Long, content: String) {
        error = null
        viewModelScope.launch {
            repository.editMessage(messageId, content)
                .onSuccess { message ->
                    upsertMessage(message)
                    editingMessageId = null
                    draft = ""
                }
                .onFailure {
                    error = it.message ?: "Failed to edit message"
                }
        }
    }

    fun parseRealtimeMessage(payload: JSONObject): DmMessageDto = repository.decryptMessage(payload.toDmMessageDto())

    private fun upsertMessage(message: DmMessageDto) {
        val existed = messages.any { it.id == message.id }

        messages = (messages.filterNot { it.id == message.id } + message)
            .distinctBy { it.id }
            .sortedBy { it.id }

        if (!existed) {
            shouldScrollToBottom = true
        }
    }

    private fun patchPoll(poll: PollDto) {
        messages = messages.map { message ->
            if (message.poll?.id == poll.id) {
                message.copy(poll = poll)
            } else {
                message
            }
        }
    }

    private fun scheduleTypingStop(isBlank: Boolean, emitTyping: (Boolean) -> Unit) {
        stopTypingJob?.cancel()
        if (isBlank) {
            return
        }
        stopTypingJob = viewModelScope.launch {
            delay(1600)
            emitTyping(false)
        }
    }

    private fun JSONObject.toDmMessageDto(): DmMessageDto {
        return DmMessageDto(
            id = optLong("id"),
            senderId = optLong("senderId"),
            receiverId = optLong("receiverId"),
            content = optString("content"),
            type = optString("type", "text"),
            imageUrl = optString("imageUrl"),
            fileUrl = optString("fileUrl"),
            fileName = optString("fileName"),
            fileSize = if (has("fileSize") && !isNull("fileSize")) optLong("fileSize") else null,
            mediaGroupId = optString("mediaGroupId"),
            encryptedPayload = optString("encryptedPayload"),
            encryptedHeader = optString("encryptedHeader"),
            encryptionVersion = optInt("encryptionVersion", 0),
            senderDeviceId = if (has("senderDeviceId") && !isNull("senderDeviceId")) optLong("senderDeviceId") else null,
            recipientDeviceId = if (has("recipientDeviceId") && !isNull("recipientDeviceId")) optLong("recipientDeviceId") else null,
            encryptedAttachmentUrl = optString("encryptedAttachmentUrl"),
            encryptedAttachmentFileName = optString("encryptedAttachmentFileName"),
            encryptedAttachmentFileSize = if (has("encryptedAttachmentFileSize") && !isNull("encryptedAttachmentFileSize")) optLong("encryptedAttachmentFileSize") else null,
            encryptedAttachmentMimeType = optString("encryptedAttachmentMimeType"),
            encryptedAttachmentKey = optString("encryptedAttachmentKey"),
            encryptedAttachmentIv = optString("encryptedAttachmentIv"),
            encryptedAttachmentKind = optString("encryptedAttachmentKind"),
            forwardedFromName = optString("forwardedFromName"),
            replyToMessageId = if (has("replyToMessageId") && !isNull("replyToMessageId")) optLong("replyToMessageId") else null,
            editedAt = optNullableString("editedAt"),
            deletedAt = optNullableString("deletedAt"),
            poll = optPoll(),
            reactions = optReactions(),
            createdAt = optString("createdAt"),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private fun JSONObject.optReactions(): List<com.pulsemessenger.android.core.network.MessageReactionDto> {
        val raw = optJSONArray("reactions") ?: return emptyList()
        return List(raw.length()) { index -> raw.optJSONObject(index) }
            .mapNotNull reactionsMap@{ item ->
                item ?: return@reactionsMap null
                com.pulsemessenger.android.core.network.MessageReactionDto(
                    emoji = item.optString("emoji"),
                    count = item.optInt("count", 0),
                    reactedByMe = item.optBoolean("reactedByMe", false),
                )
            }
    }

    private fun JSONObject.optPoll(): PollDto? {
        val poll = optJSONObject("poll") ?: return null
        return poll.toPollDto()
    }

    private fun JSONObject.toPollDto(): PollDto {
        val optionsRaw = optJSONArray("options")
        val options = if (optionsRaw == null) {
            emptyList()
        } else {
            List(optionsRaw.length()) { index -> optionsRaw.optJSONObject(index) }
                .mapNotNull optionsMap@{ option ->
                    option ?: return@optionsMap null
                    val votersRaw = option.optJSONArray("voters")
                    val voters = if (votersRaw == null) {
                        emptyList()
                    } else {
                        List(votersRaw.length()) { idx -> votersRaw.optJSONObject(idx) }
                            .mapNotNull votersMap@{ voter ->
                                voter ?: return@votersMap null
                                PollVoterDto(
                                    id = voter.optLong("id"),
                                    username = voter.optString("username"),
                                    displayName = voter.optString("displayName"),
                                )
                            }
                    }
                    PollOptionDto(
                        id = option.optLong("id"),
                        text = option.optString("text"),
                        votes = option.optInt("votes", 0),
                        votedByMe = option.optBoolean("votedByMe", false),
                        voters = voters,
                    )
                }
        }
        return PollDto(
            id = optLong("id"),
            question = optString("question"),
            creatorId = optLong("creatorId"),
            isClosed = optBoolean("isClosed", false),
            allowMultiple = optBoolean("allowMultiple", false),
            createdAt = optString("createdAt"),
            options = options,
        )
    }
}