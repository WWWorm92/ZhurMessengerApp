package com.pulsemessenger.android.feature.chat

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.pulsemessenger.android.core.network.createFilePart
import com.pulsemessenger.android.core.network.createImagePart
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.EditMessageRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.PinMessageRequest
import com.pulsemessenger.android.core.network.PollDto
import com.pulsemessenger.android.core.network.PollCreationRequest
import com.pulsemessenger.android.core.network.SendMessageRequest
import com.pulsemessenger.android.core.network.SharedAttachmentDto
import com.pulsemessenger.android.core.network.ToggleReactionRequest
import com.pulsemessenger.android.core.network.UnpinRequest
import com.pulsemessenger.android.core.network.VotePollRequest
import com.pulsemessenger.android.core.session.SessionStore

class DmChatRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    data class DmMessagesPayload(
        val messages: List<DmMessageDto>,
        val peerLastReadAt: String?,
        val hasMore: Boolean = false,
    )

    suspend fun loadMessages(
        peerUserId: Long,
        beforeId: Long? = null,
        limit: Int = 60,
    ): Result<DmMessagesPayload> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.dmMessages(
                authorization = "Bearer $token",
                userId = peerUserId,
                limit = limit,
                beforeId = beforeId,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load messages"))
            }

            val body = response.body()

            Result.success(
                DmMessagesPayload(
                    messages = body?.messages.orEmpty(),
                    peerLastReadAt = body?.peerLastReadAt,
                    hasMore = body?.hasMore == true,
                )
            )
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to load messages"))
        }
    }

    suspend fun sendMessage(peerUserId: Long, content: String, replyToMessageId: Long? = null): Result<DmMessageDto> {
        return sendMessage(peerUserId, content, imageUrl = null, replyToMessageId = replyToMessageId)
    }

    suspend fun sendMessage(peerUserId: Long, content: String, imageUrl: String?, replyToMessageId: Long? = null): Result<DmMessageDto> {
        return sendMessage(peerUserId, content, imageUrl, null, null, null, replyToMessageId)
    }

    suspend fun sendMessage(
        peerUserId: Long,
        content: String,
        imageUrl: String?,
        fileUrl: String?,
        fileName: String?,
        fileSize: Long?,
        replyToMessageId: Long? = null,
        poll: PollCreationRequest? = null,
        forwardedFromName: String? = null,
    ): Result<DmMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.sendDmMessage(
            authorization = "Bearer $token",
            userId = peerUserId,
            body = SendMessageRequest(content = content, imageUrl = imageUrl, fileUrl = fileUrl, fileName = fileName, fileSize = fileSize, replyToMessageId = replyToMessageId, poll = poll, forwardedFromName = forwardedFromName)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to send message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty message response")))
    }

    suspend fun loadAttachments(peerUserId: Long): Result<List<SharedAttachmentDto>> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.dmAttachments(
                authorization = "Bearer $token",
                userId = peerUserId,
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load attachments"))
            }

            Result.success(response.body()?.attachments.orEmpty())
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to load attachments"))
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

    suspend fun editMessage(messageId: Long, content: String): Result<DmMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.editDmMessage(
            authorization = "Bearer $token",
            messageId = messageId,
            body = EditMessageRequest(content = content)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to edit message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty message response")))
    }

    suspend fun deleteMessage(messageId: Long): Result<DmMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.deleteDmMessage(
            authorization = "Bearer $token",
            messageId = messageId,
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to delete message"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty message response")))
    }

    suspend fun toggleReaction(messageId: Long, emoji: String): Result<DmMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.toggleDmReaction(
            authorization = "Bearer $token",
            messageId = messageId,
            body = ToggleReactionRequest(emoji = emoji)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to toggle reaction"))
        }
        return Result.success(response.body()?.message ?: return Result.failure(IllegalStateException("Empty message response")))
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

    suspend fun pinMessage(peerUserId: Long, messageId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.pinMessage("Bearer $token", PinMessageRequest(scope = "dm", targetId = peerUserId, messageId = messageId))
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to pin message"))
        }
        return Result.success(Unit)
    }

    suspend fun unpinMessage(peerUserId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.unpinMessage("Bearer $token", UnpinRequest(scope = "dm", targetId = peerUserId))
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to unpin message"))
        }
        return Result.success(Unit)
    }

    suspend fun markRead(peerUserId: Long): Result<String?> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }

        val response = networkProvider.api.markDmRead("Bearer $token", peerUserId)

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to mark read"))
        }

        return Result.success(response.body()?.readAt)
    }
}
