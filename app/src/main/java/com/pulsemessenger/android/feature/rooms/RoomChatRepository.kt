package com.pulsemessenger.android.feature.rooms

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.pulsemessenger.android.core.network.createFilePart
import com.pulsemessenger.android.core.network.createImagePart
import com.pulsemessenger.android.core.network.EditMessageRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.PinMessageRequest
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import com.pulsemessenger.android.core.network.PollCreationRequest
import com.pulsemessenger.android.core.network.SendMessageRequest
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import com.pulsemessenger.android.core.network.ToggleReactionRequest
import com.pulsemessenger.android.core.network.UnpinRequest
import com.pulsemessenger.android.core.network.VotePollRequest
import com.pulsemessenger.android.core.session.SessionStore

class RoomChatRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    data class RoomMessagesPayload(
        val messages: List<RoomMessageDto>,
        val hasMore: Boolean = false,
    )

    suspend fun loadMessages(
        roomId: Long,
        beforeId: Long? = null,
        limit: Int = 60,
    ): Result<RoomMessagesPayload> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.roomMessages(
                authorization = "Bearer $token",
                roomId = roomId,
                limit = limit,
                beforeId = beforeId,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load room messages"))
            }

            val body = response.body()

            Result.success(
                RoomMessagesPayload(
                    messages = body?.messages.orEmpty(),
                    hasMore = body?.hasMore == true,
                )
            )
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to load room messages"))
        }
    }

    suspend fun sendMessage(roomId: Long, content: String, replyToMessageId: Long? = null): Result<RoomMessageDto> {
        return sendMessage(roomId, content, imageUrl = null, replyToMessageId = replyToMessageId)
    }

    suspend fun sendMessage(roomId: Long, content: String, imageUrl: String?, replyToMessageId: Long? = null): Result<RoomMessageDto> {
        return sendMessage(roomId, content, imageUrl, null, null, null, replyToMessageId)
    }

    suspend fun sendMessage(
        roomId: Long,
        content: String,
        imageUrl: String?,
        fileUrl: String?,
        fileName: String?,
        fileSize: Long?,
        replyToMessageId: Long? = null,
        poll: PollCreationRequest? = null,
        forwardedFromName: String? = null,
        mediaGroupId: String? = null,
    ): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.sendRoomMessage(
            authorization = "Bearer $token",
            roomId = roomId,
            body = SendMessageRequest(
                content = content,
                imageUrl = imageUrl,
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize,
                mediaGroupId = mediaGroupId,
                replyToMessageId = replyToMessageId,
                poll = poll,
                forwardedFromName = forwardedFromName
            )
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to send room message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty room message response")))
    }

    suspend fun loadAttachments(roomId: Long): Result<List<SharedAttachmentDto>> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.roomAttachments(
                authorization = "Bearer $token",
                roomId = roomId,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load room attachments"))
            }

            Result.success(response.body()?.attachments.orEmpty())
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to load room attachments"))
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): Result<String> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val imagePart = createImagePart(context, uri)
            ?: return Result.failure(IllegalStateException("Failed to read image"))
        val response = networkProvider.api.uploadMessageImage("Bearer $token", imagePart)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to upload image"))
        }
        return Result.success(response.body()?.imageUrl ?: return Result.failure(IllegalStateException("Empty upload response")))
    }

    suspend fun uploadFile(context: Context, uri: Uri): Result<Triple<String, String, Long?>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val filePart = createFilePart(context, uri)
            ?: return Result.failure(IllegalStateException("Failed to read file"))
        val response = networkProvider.api.uploadMessageFile("Bearer $token", filePart)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to upload file"))
        }
        val body = response.body() ?: return Result.failure(IllegalStateException("Empty upload response"))
        return Result.success(Triple(body.fileUrl, body.fileName, body.fileSize))
    }

    suspend fun editMessage(roomId: Long, messageId: Long, content: String): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.editRoomMessage(
            authorization = "Bearer $token",
            roomId = roomId,
            messageId = messageId,
            body = EditMessageRequest(content = content)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to edit room message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty room message response")))
    }

    suspend fun deleteMessage(roomId: Long, messageId: Long): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.deleteRoomMessage(
            authorization = "Bearer $token",
            roomId = roomId,
            messageId = messageId,
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to delete room message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty room message response")))
    }

    suspend fun toggleReaction(roomId: Long, messageId: Long, emoji: String): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.toggleRoomReaction(
            authorization = "Bearer $token",
            roomId = roomId,
            messageId = messageId,
            body = ToggleReactionRequest(emoji = emoji)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to toggle reaction"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty room message response")))
    }

    suspend fun votePoll(pollId: Long, optionIds: List<Long>): Result<PollDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val normalized = optionIds.distinct().filter { it > 0 }
        if (normalized.isEmpty()) {
            return Result.failure(IllegalStateException("Choose at least one option"))
        }
        val response = networkProvider.api.votePoll(
            authorization = "Bearer $token",
            pollId = pollId,
            body = VotePollRequest(
                optionId = normalized.singleOrNull(),
                optionIds = if (normalized.size > 1) normalized else null,
            )
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to vote poll"))
        }
        return Result.success(response.body()?.poll ?: return Result.failure(IllegalStateException("Empty poll response")))
    }

    suspend fun closePoll(pollId: Long): Result<PollDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.closePoll(
            authorization = "Bearer $token",
            pollId = pollId,
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to close poll"))
        }
        return Result.success(response.body()?.poll ?: return Result.failure(IllegalStateException("Empty poll response")))
    }

    suspend fun pinMessage(roomId: Long, messageId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.pinMessage("Bearer $token", PinMessageRequest(scope = "room", targetId = roomId, messageId = messageId))
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to pin message"))
        }
        return Result.success(Unit)
    }

    suspend fun unpinMessage(roomId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.unpinMessage("Bearer $token", UnpinRequest(scope = "room", targetId = roomId))
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to unpin message"))
        }
        return Result.success(Unit)
    }
}
