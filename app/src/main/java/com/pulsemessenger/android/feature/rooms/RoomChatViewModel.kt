package com.pulsemessenger.android.feature.rooms

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.queryDisplayName
import com.pulsemessenger.android.core.network.queryFileSize
import com.pulsemessenger.android.core.network.PollCreationRequest
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.core.network.PollOptionDto
import com.pulsemessenger.android.core.network.PollVoterDto
import com.pulsemessenger.android.core.network.PendingAttachment
import com.pulsemessenger.android.core.network.PendingAttachmentKind
import com.pulsemessenger.android.core.network.RoomMessageSenderDto
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import com.pulsemessenger.android.core.notification.ActiveChatTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class RoomChatViewModel(
    private val repository: RoomChatRepository,
) : ViewModel() {
    val quickReactions = listOf("😀", "😎", "😂", "😍", "🥳", "👍", "👎", "🔥", "❤️", "💔", "🤝", "👏")
    var room by mutableStateOf<RoomDto?>(null)
    var messages by mutableStateOf<List<RoomMessageDto>>(emptyList())
    var profileAttachments by mutableStateOf<List<SharedAttachmentDto>?>(null)
    var isProfileAttachmentsLoading by mutableStateOf(false)
    var profileAttachmentsError by mutableStateOf<String?>(null)
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
    var onlineMemberIds by mutableStateOf<Set<Long>>(emptySet())
    var typingMemberIds by mutableStateOf<Set<Long>>(emptySet())
    private var memberNamesById = emptyMap<Long, String>()
    private var stopTypingJob: Job? = null
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

    fun openRoom(targetRoom: RoomDto) {
        room?.id
            ?.takeIf { it != targetRoom.id }
            ?.let { ActiveChatTracker.leaveChat("room", it) }
        ActiveChatTracker.enterChat("room", targetRoom.id)
        room = targetRoom
        profileAttachments = null
        profileAttachmentsError = null
        isProfileAttachmentsLoading = false
        hasMoreMessages = false
        isLoadingOlder = false
        shouldScrollToBottom = true
        loadMessages()
    }

    fun closeRoom() {
        stopTypingJob?.cancel()
        room?.id?.let { ActiveChatTracker.leaveChat("room", it) }
        room = null
        messages = emptyList()
        draft = ""
        error = null
        editingMessageId = null
        replyToMessageId = null
        replyToMessageContent = ""
        isUploadingImage = false
        pendingAttachments = emptyList()
        onlineMemberIds = emptySet()
        typingMemberIds = emptySet()
        memberNamesById = emptyMap()
        profileAttachments = null
        profileAttachmentsError = null
        isProfileAttachmentsLoading = false
        clearSelection()
        hasMoreMessages = false
        isLoadingOlder = false
        shouldScrollToBottom = false
    }

    fun loadMessages() {
        val currentRoom = room ?: return
        if (isLoading) return

        isLoading = true
        error = null
        hasMoreMessages = false
        shouldScrollToBottom = true

        viewModelScope.launch {
            repository.loadMessages(
                roomId = currentRoom.id,
                beforeId = null,
                limit = 60,
            )
                .onSuccess { payload ->
                    messages = payload.messages.sortedBy { it.id }
                    memberNamesById = messages.associate { it.sender.id to it.sender.displayName }
                    hasMoreMessages = payload.hasMore
                }
                .onFailure {
                    error = it.message ?: "Failed to load room messages"
                }

            isLoading = false
        }
    }

    suspend fun loadOlderMessages(): Int {
        val currentRoom = room ?: return 0

        if (isLoading || isLoadingOlder || !hasMoreMessages || messages.isEmpty()) {
            return 0
        }

        val beforeId = messages.first().id
        isLoadingOlder = true
        error = null

        var addedCount = 0

        repository.loadMessages(
            roomId = currentRoom.id,
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

                    memberNamesById = messages.associate { it.sender.id to it.sender.displayName }
                    addedCount = older.size
                }

                hasMoreMessages = payload.hasMore
            }
            .onFailure {
                error = it.message ?: "Не удалось загрузить старые сообщения"
            }

        isLoadingOlder = false
        return addedCount
    }

    fun loadProfileAttachments() {
        val currentRoom = room ?: return
        if (isProfileAttachmentsLoading) return

        isProfileAttachmentsLoading = true
        profileAttachmentsError = null

        viewModelScope.launch {
            repository.loadAttachments(currentRoom.id)
                .onSuccess { attachments ->
                    profileAttachments = attachments
                }
                .onFailure {
                    profileAttachmentsError = it.message ?: "Не удалось загрузить вложения"
                }

            isProfileAttachmentsLoading = false
        }
    }

    fun consumeScrollToBottom() {
        shouldScrollToBottom = false
    }

    fun sendMessage(context: Context? = null) {
        val currentRoom = room ?: return
        val content = draft.trim()
        val attachments = pendingAttachments
        val editingId = editingMessageId

        if (editingId != null) {
            if (attachments.isNotEmpty()) {
                error = "Нельзя прикреплять файлы при редактировании сообщения"
                return
            }

            if (content.isNotBlank()) {
                saveEditedMessage(currentRoom.id, editingId, content)
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
                        roomId = currentRoom.id,
                        attachment = attachment,
                        caption = caption,
                        replyToMessageId = replyId,
                        mediaGroupId = mediaGroupId,
                    )

                    result
                        .onSuccess { message ->
                            upsertMessage(message, requestScroll = true)
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
            repository.sendMessage(currentRoom.id, content, replyToMessageId = replyingTo)
                .onSuccess { message ->
                    upsertMessage(message, requestScroll = true)
                }
                .onFailure {
                    draft = previousDraft
                    replyToMessageId = replyingTo
                    replyToMessageContent = messages.firstOrNull { it.id == replyingTo }?.content ?: ""
                    error = it.message ?: "Failed to send room message"
                }
        }
    }

    fun sendPoll(poll: PollCreationRequest) {
        val currentRoom = room ?: return
        pendingPoll = null
        error = null
        viewModelScope.launch {
            repository.sendMessage(
                roomId = currentRoom.id,
                content = "",
                imageUrl = null,
                fileUrl = null,
                fileName = null,
                fileSize = null,
                poll = poll,
            )
                .onSuccess { message ->
                    upsertMessage(message, requestScroll = true)
                }
                .onFailure {
                    error = it.message ?: "Failed to send poll"
                }
        }
    }

    fun beginEdit(message: RoomMessageDto) {
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

    fun beginReply(message: RoomMessageDto) {
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
        val currentRoom = room ?: return
        viewModelScope.launch {
            repository.pinMessage(currentRoom.id, messageId)
        }
    }

    fun unpinMessage() {
        val currentRoom = room ?: return
        viewModelScope.launch {
            repository.unpinMessage(currentRoom.id)
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
        val currentRoom = room ?: return
        error = null
        viewModelScope.launch {
            repository.deleteMessage(currentRoom.id, messageId)
                .onSuccess { message ->
                    upsertMessage(message)
                    if (editingMessageId == messageId) {
                        cancelEditing()
                    }
                }
                .onFailure {
                    error = it.message ?: "Failed to delete room message"
                }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        val currentRoom = room ?: return
        error = null
        viewModelScope.launch {
            repository.toggleReaction(currentRoom.id, messageId, emoji)
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

    private suspend fun sendAttachmentMessage(
        context: Context,
        roomId: Long,
        attachment: PendingAttachment,
        caption: String,
        replyToMessageId: Long?,
        mediaGroupId: String?,
    ): Result<RoomMessageDto> {
        return when (attachment.kind) {
            PendingAttachmentKind.Image -> {
                repository.uploadImage(context, attachment.uri).fold(
                    onSuccess = { imageUrl ->
                        repository.sendMessage(
                            roomId = roomId,
                            content = caption,
                            imageUrl = imageUrl,
                            fileUrl = null,
                            fileName = null,
                            fileSize = null,
                            replyToMessageId = replyToMessageId,
                            mediaGroupId = mediaGroupId,
                        )
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    }
                )
            }

            PendingAttachmentKind.File -> {
                repository.uploadFile(context, attachment.uri).fold(
                    onSuccess = { uploaded ->
                        val (fileUrl, fileName, fileSize) = uploaded

                        repository.sendMessage(
                            roomId = roomId,
                            content = caption,
                            imageUrl = null,
                            fileUrl = fileUrl,
                            fileName = fileName,
                            fileSize = fileSize,
                            replyToMessageId = replyToMessageId,
                        )
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    }
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
        onlineMemberIds = onlineUserIds
    }

    fun onTypingUpdate(scope: String, targetId: Long, userId: Long, isTyping: Boolean) {
        val currentRoomId = room?.id ?: return
        if (scope != "room" || currentRoomId != targetId) {
            return
        }
        typingMemberIds = if (isTyping) typingMemberIds + userId else typingMemberIds - userId
    }

    fun onRealtimeMessage(roomId: Long, payload: JSONObject) {
        val currentRoom = room ?: return
        if (currentRoom.id != roomId) {
            return
        }
        upsertMessage(parseRealtimeMessage(payload))
    }

    fun onRealtimeMessageUpdate(roomId: Long, payload: JSONObject) {
        val currentRoom = room ?: return
        if (currentRoom.id != roomId) {
            return
        }
        upsertMessage(parseRealtimeMessage(payload))
    }

    fun parseRealtimeMessage(payload: JSONObject): RoomMessageDto = payload.toRoomMessageDto()

    fun onPollUpdate(payload: JSONObject) {
        patchPoll(payload.toPollDto())
    }

    private fun upsertMessage(
        message: RoomMessageDto,
        requestScroll: Boolean = false,
    ) {
        val existed = messages.any { it.id == message.id }

        memberNamesById = memberNamesById + (message.sender.id to message.sender.displayName)

        messages = (messages.filterNot { it.id == message.id } + message)
            .distinctBy { it.id }
            .sortedBy { it.id }

        if (requestScroll && !existed) {
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

    fun roomStatusText(): String {
        val typingNames = typingMemberIds.mapNotNull(memberNamesById::get).distinct()
        if (typingNames.isNotEmpty()) {
            return if (typingNames.size == 1) {
                "${typingNames.first()} печатает..."
            } else {
                "Печатают: ${typingNames.take(2).joinToString()}"
            }
        }

        val onlineCount = messages.map { it.sender.id }.distinct().count { onlineMemberIds.contains(it) }
        return if (onlineCount > 0) "$onlineCount в сети" else buildRoomMembersText()
    }

    private fun buildRoomMembersText(): String {
        val currentRoom = room ?: return ""
        return if (currentRoom.membersCount > 0) "${currentRoom.membersCount} участников" else "Комната"
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

    private fun saveEditedMessage(roomId: Long, messageId: Long, content: String) {
        error = null
        viewModelScope.launch {
            repository.editMessage(roomId, messageId, content)
                .onSuccess { message ->
                    upsertMessage(message)
                    editingMessageId = null
                    draft = ""
                }
                .onFailure {
                    error = it.message ?: "Failed to edit room message"
                }
        }
    }

    override fun onCleared() {
        room?.id?.let { ActiveChatTracker.leaveChat("room", it) }
        super.onCleared()
    }

    private fun JSONObject.toRoomMessageDto(): RoomMessageDto {
        val senderJson = optJSONObject("sender") ?: JSONObject()
        return RoomMessageDto(
            id = optLong("id"),
            roomId = optLong("roomId"),
            senderId = optLong("senderId"),
            content = optString("content"),
            type = optString("type", "text"),
            imageUrl = optString("imageUrl"),
            fileUrl = optString("fileUrl"),
            fileName = optString("fileName"),
            fileSize = if (has("fileSize") && !isNull("fileSize")) optLong("fileSize") else null,
            mediaGroupId = optString("mediaGroupId"),
            forwardedFromName = optString("forwardedFromName"),
            replyToMessageId = if (has("replyToMessageId") && !isNull("replyToMessageId")) optLong("replyToMessageId") else null,
            editedAt = optNullableString("editedAt"),
            deletedAt = optNullableString("deletedAt"),
            poll = optPoll(),
            reactions = optReactions(),
            createdAt = optString("createdAt"),
            sender = RoomMessageSenderDto(
                id = senderJson.optLong("id"),
                username = senderJson.optString("username"),
                displayName = senderJson.optString("displayName"),
                avatarUrl = senderJson.optString("avatarUrl"),
                isAdmin = senderJson.optBoolean("isAdmin", false),
            )
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private fun JSONObject.optReactions(): List<com.pulsemessenger.android.core.network.MessageReactionDto> {
        val raw = optJSONArray("reactions") ?: return emptyList()
        return List(raw.length()) { index -> raw.optJSONObject(index) }
            .mapNotNull { item ->
                item ?: return@mapNotNull null
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
                .mapNotNull { option ->
                    option ?: return@mapNotNull null
                    val votersRaw = option.optJSONArray("voters")
                    val voters = if (votersRaw == null) {
                        emptyList()
                    } else {
                        List(votersRaw.length()) { idx -> votersRaw.optJSONObject(idx) }
                            .mapNotNull { voter ->
                                voter ?: return@mapNotNull null
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
