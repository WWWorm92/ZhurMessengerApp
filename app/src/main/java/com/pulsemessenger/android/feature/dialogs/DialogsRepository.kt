package com.pulsemessenger.android.feature.dialogs

import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.e2ee.E2EEMessageCrypto
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore

class DialogsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    private val e2eeCrypto by lazy {
        E2EEMessageCrypto(
            context = PulseApp.instance.applicationContext,
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }

    suspend fun loadUsers(): Result<List<DialogUserDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.users("Bearer $token")
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load dialogs"))
        }
        val users = response.body()?.users.orEmpty().map { user ->
            decryptLastMessagePreview(user)
        }
        return Result.success(users)
    }

    private fun decryptLastMessagePreview(user: DialogUserDto): DialogUserDto {
        if (user.lastMessageType != "encrypted" || user.lastEncryptionVersion <= 0) {
            return user
        }
        if (user.lastEncryptedPayload.isBlank() || user.lastEncryptedHeader.isBlank()) {
            return user
        }

        val decrypted = e2eeCrypto.tryDecryptDmMessage(
            DmMessageDto(
                id = user.lastMessageId ?: 0L,
                senderId = user.lastSenderId ?: 0L,
                receiverId = user.lastReceiverId ?: 0L,
                content = "",
                type = "encrypted",
                encryptedPayload = user.lastEncryptedPayload,
                encryptedHeader = user.lastEncryptedHeader,
                encryptionVersion = user.lastEncryptionVersion,
                senderDeviceId = user.lastSenderDeviceId,
                recipientDeviceId = user.lastRecipientDeviceId,
                createdAt = user.lastMessageAt.orEmpty(),
            )
        )

        val preview = dialogPreviewText(decrypted)
        return if (preview.isBlank()) {
            user
        } else {
            user.copy(
                lastMessage = preview,
                lastFileName = decrypted.fileName,
                lastMessageType = decrypted.type,
            )
        }
    }

    private fun dialogPreviewText(message: DmMessageDto): String {
        val content = message.content.trim()
        val type = message.type
        val fileName = message.fileName.trim()
        val encryptedKind = message.encryptedAttachmentKind.orEmpty().lowercase()
        val encryptedMime = message.encryptedAttachmentMimeType.orEmpty().lowercase()

        return when {
            content.isNotBlank() && content != "Сообщение недоступно на этом устройстве" -> content
            encryptedKind == "image" || encryptedMime.startsWith("image/") -> "Изображение"
            encryptedKind == "file" && fileName.isNotBlank() -> "Файл: $fileName"
            type == "image" -> "Изображение"
            type == "file" && fileName.isNotBlank() -> "Файл: $fileName"
            content == "Сообщение недоступно на этом устройстве" -> content
            else -> ""
        }
    }

    suspend fun clearDialog(peerUserId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.deleteDialog("Bearer $token", peerUserId)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to clear dialog"))
        }
        return Result.success(Unit)
    }

    suspend fun updateDmChatPrefs(targetId: Long, pinned: Boolean? = null, muted: Boolean? = null, archived: Boolean? = null): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateChatPrefs(
            "Bearer $token",
            ChatPrefsRequest(scope = "dm", targetId = targetId, pinned = pinned, muted = muted, archived = archived)
        )
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to update chat prefs"))
        }
        return Result.success(Unit)
    }
}
