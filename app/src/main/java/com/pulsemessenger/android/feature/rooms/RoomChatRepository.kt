package com.pulsemessenger.android.feature.rooms

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.createFilePart
import com.pulsemessenger.android.core.network.createImagePart
import com.pulsemessenger.android.core.network.EditMessageRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.PinMessageRequest
import com.pulsemessenger.android.core.network.PollCreationRequest
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import com.pulsemessenger.android.core.network.SendMessageRequest
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import com.pulsemessenger.android.core.network.ToggleReactionRequest
import com.pulsemessenger.android.core.network.UnpinRequest
import com.pulsemessenger.android.core.network.VotePollRequest
import com.pulsemessenger.android.core.offline.NetworkState
import com.pulsemessenger.android.core.offline.OfflineStore
import com.pulsemessenger.android.core.session.SessionStore
import java.util.UUID

class RoomChatRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()
    private val context get() = PulseApp.instance.applicationContext
    private val offlineStore by lazy { OfflineStore.get(context) }

    data class RoomMessagesPayload(
        val messages: List<RoomMessageDto>,
        val hasMore: Boolean = false,
    )

    suspend fun loadMessages(
        roomId: Long,
        beforeId: Long? = null,
        limit: Int = 60,
    ): Result<RoomMessagesPayload> {
        suspend fun cached(): List<RoomMessageDto> =
            offlineStore.loadRoomMessages(roomId, beforeId, limit)

        val token = sessionStore.currentToken().trim()
        if (token.isBlank() || !NetworkState.isOnline(context)) {
            val local = cached()
            return if (local.isNotEmpty()) {
                Result.success(RoomMessagesPayload(local, hasMore = false))
            } else {
                Result.failure(
                    IllegalStateException(
                        if (token.isBlank()) "No session token" else "Нет соединения с сервером"
                    )
                )
            }
        }

        return try {
            val response = networkProvider.api.roomMessages(
                authorization = "Bearer $token",
                roomId = roomId,
                limit = limit,
                beforeId = beforeId,
            )
            if (!response.isSuccessful) {
                val local = cached()
                if (local.isNotEmpty()) {
                    return Result.success(RoomMessagesPayload(local, hasMore = false))
                }
                return Result.failure(apiError(response.errorBody()?.string(), "Failed to load room messages"))
            }
            val body = response.body()
            val messages = body?.messages.orEmpty()
            if (beforeId == null) {
                offlineStore.replaceRoomMessages(roomId, messages)
            } else {
                offlineStore.upsertRoomMessages(roomId, messages)
            }
            Result.success(RoomMessagesPayload(messages, body?.hasMore == true))
        } catch (error: Throwable) {
            val local = cached()
            if (local.isNotEmpty()) Result.success(RoomMessagesPayload(local, false))
            else Result.failure(IllegalStateException(error.message ?: "Failed to load room messages"))
        }
    }

    suspend fun sendMessage(
        roomId: Long,
        content: String,
        replyToMessageId: Long? = null,
    ): Result<RoomMessageDto> = sendMessage(
        roomId = roomId,
        content = content,
        imageUrl = null,
        replyToMessageId = replyToMessageId,
    )

    suspend fun sendMessage(
        roomId: Long,
        content: String,
        imageUrl: String?,
        replyToMessageId: Long? = null,
    ): Result<RoomMessageDto> = sendMessage(
        roomId = roomId,
        content = content,
        imageUrl = imageUrl,
        fileUrl = null,
        fileName = null,
        fileSize = null,
        replyToMessageId = replyToMessageId,
    )

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
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        return try {
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
                    forwardedFromName = forwardedFromName,
                    clientMessageId = UUID.randomUUID().toString(),
                ),
            )
            if (!response.isSuccessful) {
                return Result.failure(apiError(response.errorBody()?.string(), "Failed to send room message"))
            }
            val message = response.body()?.message
                ?: return Result.failure(IllegalStateException("Empty room message response"))
            offlineStore.upsertRoomMessages(roomId, listOf(message))
            Result.success(message)
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to send room message"))
        }
    }

    suspend fun loadAttachments(roomId: Long): Result<List<SharedAttachmentDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        return try {
            val response = networkProvider.api.roomAttachments("Bearer $token", roomId)
            if (!response.isSuccessful) {
                Result.failure(apiError(response.errorBody()?.string(), "Failed to load room attachments"))
            } else {
                Result.success(response.body()?.attachments.orEmpty())
            }
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to load room attachments"))
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): Result<String> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val part = createImagePart(context, uri)
            ?: return Result.failure(IllegalStateException("Failed to read image"))
        val response = networkProvider.api.uploadMessageImage("Bearer $token", part)
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to upload image"))
        }
        return Result.success(
            response.body()?.imageUrl
                ?: return Result.failure(IllegalStateException("Empty upload response"))
        )
    }

    suspend fun uploadFile(context: Context, uri: Uri): Result<Triple<String, String, Long?>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val part = createFilePart(context, uri)
            ?: return Result.failure(IllegalStateException("Failed to read file"))
        val response = networkProvider.api.uploadMessageFile("Bearer $token", part)
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to upload file"))
        }
        val body = response.body()
            ?: return Result.failure(IllegalStateException("Empty upload response"))
        return Result.success(Triple(body.fileUrl, body.fileName, body.fileSize))
    }

    suspend fun editMessage(roomId: Long, messageId: Long, content: String): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.editRoomMessage(
            "Bearer $token", roomId, messageId, EditMessageRequest(content)
        )
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to update room message"))
        }
        val message = response.body()?.message
            ?: return Result.failure(IllegalStateException("Empty room message response"))
        offlineStore.upsertRoomMessages(roomId, listOf(message))
        return Result.success(message)
    }

    suspend fun deleteMessage(roomId: Long, messageId: Long): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.deleteRoomMessage("Bearer $token", roomId, messageId)
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to delete room message"))
        }
        val message = response.body()?.message
            ?: return Result.failure(IllegalStateException("Empty room message response"))
        offlineStore.upsertRoomMessages(roomId, listOf(message))
        return Result.success(message)
    }

    suspend fun toggleReaction(roomId: Long, messageId: Long, emoji: String): Result<RoomMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.toggleRoomReaction(
            "Bearer $token", roomId, messageId, ToggleReactionRequest(emoji)
        )
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to toggle reaction"))
        }
        val message = response.body()?.message
            ?: return Result.failure(IllegalStateException("Empty room message response"))
        offlineStore.upsertRoomMessages(roomId, listOf(message))
        return Result.success(message)
    }

    suspend fun votePoll(pollId: Long, optionIds: List<Long>): Result<PollDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val normalized = optionIds.distinct().filter { it > 0L }
        if (normalized.isEmpty()) return Result.failure(IllegalStateException("Choose at least one option"))
        val response = networkProvider.api.votePoll(
            "Bearer $token",
            pollId,
            VotePollRequest(
                optionId = normalized.singleOrNull(),
                optionIds = normalized.takeIf { it.size > 1 },
            ),
        )
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to vote poll"))
        }
        return Result.success(
            response.body()?.poll
                ?: return Result.failure(IllegalStateException("Empty poll response"))
        )
    }

    suspend fun closePoll(pollId: Long): Result<PollDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.closePoll("Bearer $token", pollId)
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to close poll"))
        }
        return Result.success(
            response.body()?.poll
                ?: return Result.failure(IllegalStateException("Empty poll response"))
        )
    }

    suspend fun pinMessage(roomId: Long, messageId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.pinMessage(
            "Bearer $token",
            PinMessageRequest(scope = "room", targetId = roomId, messageId = messageId),
        )
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to pin message"))
        }
        return Result.success(Unit)
    }

    suspend fun unpinMessage(roomId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.unpinMessage(
            "Bearer $token",
            UnpinRequest(scope = "room", targetId = roomId),
        )
        if (!response.isSuccessful) {
            return Result.failure(apiError(response.errorBody()?.string(), "Failed to unpin message"))
        }
        return Result.success(Unit)
    }

    private fun apiError(raw: String?, fallback: String): IllegalStateException {
        val parsed = runCatching { gson.fromJson(raw.orEmpty(), ErrorResponse::class.java) }.getOrNull()
        return IllegalStateException(parsed?.error ?: fallback)
    }
}
