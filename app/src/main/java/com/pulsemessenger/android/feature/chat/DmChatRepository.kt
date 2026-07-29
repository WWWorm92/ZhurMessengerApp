package com.pulsemessenger.android.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.offline.NetworkState
import com.pulsemessenger.android.core.offline.OfflineStore
import com.pulsemessenger.android.core.offline.OutboxQueue
import com.pulsemessenger.android.core.e2ee.E2EEAttachmentCrypto
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
import androidx.core.content.FileProvider
import com.pulsemessenger.android.core.session.SessionStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DmChatRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()
    private val appContext get() = PulseApp.instance.applicationContext
    private val offlineStore by lazy { OfflineStore.get(appContext) }

    private fun safeString(value: String?): String = value.orEmpty()

    private fun safeLower(value: String?): String = value.orEmpty().lowercase()


    data class DmMessagesPayload(
        val messages: List<DmMessageDto>,
        val peerLastReadAt: String?,
        val hasMore: Boolean = false,
    )

    data class OpenedAttachment(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val kind: String,
    )

    suspend fun loadMessages(
        peerUserId: Long,
        beforeId: Long? = null,
        limit: Int = 60,
    ): Result<DmMessagesPayload> {
        val cached = suspend {
            offlineStore.loadDmMessages(peerUserId, beforeId, limit)
        }

        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            val local = cached()
            return if (local.isNotEmpty()) {
                Result.success(DmMessagesPayload(local, peerLastReadAt = null, hasMore = false))
            } else {
                Result.failure(IllegalStateException("No session token"))
            }
        }

        if (!NetworkState.isOnline(appContext)) {
            val local = cached()
            return if (local.isNotEmpty()) {
                Result.success(DmMessagesPayload(local, peerLastReadAt = null, hasMore = false))
            } else {
                Result.failure(IllegalStateException("Нет соединения с сервером"))
            }
        }

        return try {
            val response = networkProvider.api.dmMessages(
                authorization = "Bearer $token",
                userId = peerUserId,
                limit = limit,
                beforeId = beforeId,
            )

            if (!response.isSuccessful) {
                val local = cached()
                if (local.isNotEmpty()) {
                    return Result.success(DmMessagesPayload(local, peerLastReadAt = null, hasMore = false))
                }
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load messages"))
            }

            val body = response.body()
            val serverMessages = body?.messages.orEmpty()
            if (beforeId == null) {
                offlineStore.replaceDmMessages(peerUserId, serverMessages)
            } else {
                offlineStore.upsertDmMessages(peerUserId, serverMessages)
            }
            Result.success(
                DmMessagesPayload(
                    messages = serverMessages,
                    peerLastReadAt = body?.peerLastReadAt,
                    hasMore = body?.hasMore == true,
                )
            )
        } catch (error: Throwable) {
            val local = cached()
            if (local.isNotEmpty()) {
                Result.success(DmMessagesPayload(local, peerLastReadAt = null, hasMore = false))
            } else {
                Result.failure(IllegalStateException(error.message ?: "Failed to load messages"))
            }
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
        mediaGroupId: String? = null,
    ): Result<DmMessageDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }

        val canQueueOffline = content.isNotBlank() &&
            imageUrl.isNullOrBlank() &&
            fileUrl.isNullOrBlank() &&
            poll == null &&
            forwardedFromName.isNullOrBlank()
        val clientMessageId = UUID.randomUUID().toString()

        suspend fun queueInstead(): Result<DmMessageDto> {
            if (!canQueueOffline) {
                return Result.failure(IllegalStateException("Не удалось отправить сообщение"))
            }
            val queued = OutboxQueue.enqueueText(
                context = appContext,
                scope = "dm",
                targetId = peerUserId,
                text = content,
                replyToMessageId = replyToMessageId,
                createLocalDmMessage = true,
                clientMessageId = clientMessageId,
            ) ?: return Result.failure(IllegalStateException("Не удалось добавить сообщение в очередь"))
            return Result.success(queued)
        }

        if (!NetworkState.isOnline(appContext) && canQueueOffline) {
            return queueInstead()
        }

        return try {
            val response = networkProvider.api.sendDmMessage(
                authorization = "Bearer $token",
                userId = peerUserId,
                body = SendMessageRequest(
                    content = content,
                    imageUrl = imageUrl,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    fileSize = fileSize,
                    mediaGroupId = mediaGroupId,
                    notificationPreview = content.trim().take(240).ifBlank { null },
                    replyToMessageId = replyToMessageId,
                    poll = poll,
                    forwardedFromName = forwardedFromName,
                    clientMessageId = clientMessageId,
                )
            )
            if (!response.isSuccessful) {
                if (canQueueOffline && (response.code() >= 500 || response.code() in setOf(408, 425, 429))) {
                    return queueInstead()
                }
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to send message"))
            }
            val message = response.body()?.message
                ?: return Result.failure(IllegalStateException("Empty message response"))
            offlineStore.upsertDmMessages(peerUserId, listOf(message))
            Result.success(message)
        } catch (error: Throwable) {
            if (canQueueOffline) queueInstead()
            else Result.failure(IllegalStateException(error.message ?: "Failed to send message"))
        }
    }

    fun decryptMessage(message: DmMessageDto): DmMessageDto {
        val peerUserId = if (message.senderId == OfflineStore.userIdFromToken(sessionStore.currentToken())) {
            message.receiverId
        } else {
            message.senderId
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (peerUserId > 0L) offlineStore.upsertDmMessages(peerUserId, listOf(message))
        }
        return message
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
                val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to upload file"))
        }
        val body = response.body() ?: return Result.failure(IllegalStateException("Empty upload response"))
        return Result.success(Triple(body.fileUrl, body.fileName, body.fileSize))
    }


    private suspend fun sendLegacyAttachment(
        peerUserId: Long,
        context: Context,
        uri: Uri,
        attachmentKind: String,
        caption: String,
        replyToMessageId: Long?,
        mediaGroupId: String?,
    ): Result<DmMessageDto> {
        return if (attachmentKind == "image") {
            val imageUrl = uploadImage(context, uri).getOrElse { error ->
                return Result.failure(IllegalStateException(error.message ?: "Failed to upload image"))
            }
            sendMessage(
                peerUserId = peerUserId,
                content = caption,
                imageUrl = imageUrl,
                fileUrl = null,
                fileName = null,
                fileSize = null,
                replyToMessageId = replyToMessageId,
                mediaGroupId = mediaGroupId,
            )
        } else {
            val uploaded = uploadFile(context, uri).getOrElse { error ->
                return Result.failure(IllegalStateException(error.message ?: "Failed to upload file"))
            }
            sendMessage(
                peerUserId = peerUserId,
                content = caption,
                imageUrl = null,
                fileUrl = uploaded.first,
                fileName = uploaded.second,
                fileSize = uploaded.third,
                replyToMessageId = replyToMessageId,
                mediaGroupId = mediaGroupId,
            )
        }
    }

    suspend fun sendEncryptedAttachment(
        peerUserId: Long,
        context: Context,
        uri: Uri,
        attachmentKind: String,
        caption: String,
        replyToMessageId: Long? = null,
        mediaGroupId: String? = null,
    ): Result<DmMessageDto> {
        return sendLegacyAttachment(
            peerUserId = peerUserId,
            context = context,
            uri = uri,
            attachmentKind = attachmentKind,
            caption = caption,
            replyToMessageId = replyToMessageId,
            mediaGroupId = mediaGroupId,
        )
    }

    fun isDecryptedAttachmentCached(context: Context, message: DmMessageDto): Boolean {
        return cachedDecryptedAttachment(context, message) != null
    }

    fun cachedDecryptedAttachment(context: Context, message: DmMessageDto): OpenedAttachment? {
        if (!isEncryptedAttachment(message)) {
            return null
        }

        val cached = E2EEAttachmentCrypto(context).cachedDecryptedAttachment(
            originalFileName = originalEncryptedFileName(message),
            originalMimeType = safeString(message.encryptedAttachmentMimeType),
            kind = safeString(message.encryptedAttachmentKind).ifBlank { "file" },
            cacheKey = encryptedAttachmentCacheKey(message),
        ) ?: return null

        return OpenedAttachment(
            uri = cached.uri,
            fileName = cached.fileName,
            mimeType = cached.mimeType,
            kind = cached.kind,
        )
    }

    suspend fun prefetchEncryptedAttachment(context: Context, message: DmMessageDto): Result<Unit> {
        if (!isEncryptedAttachment(message)) {
            return Result.success(Unit)
        }

        if (isDecryptedAttachmentCached(context, message)) {
            return Result.success(Unit)
        }

        return downloadAndDecryptAttachment(context, message).map { Unit }
    }

    suspend fun downloadAndDecryptAttachment(context: Context, message: DmMessageDto): Result<OpenedAttachment> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }

        cachedDecryptedAttachment(context, message)?.let { cached ->
            return Result.success(cached)
        }

        val encryptedUrl = safeString(message.encryptedAttachmentUrl).ifBlank { message.fileUrl }
        val fileKey = safeString(message.encryptedAttachmentKey)
        val fileIv = safeString(message.encryptedAttachmentIv)

        if (encryptedUrl.isBlank() || fileKey.isBlank() || fileIv.isBlank()) {
            return Result.failure(IllegalStateException("Attachment is not encrypted or key is missing"))
        }

        val encryptedTemp = File(context.cacheDir, "e2ee_downloads/${System.currentTimeMillis()}-${message.id}.bin")
        encryptedTemp.parentFile?.mkdirs()

        return try {
            val response = networkProvider.api.downloadMedia(
                authorization = "Bearer $token",
                url = resolveMediaUrl(encryptedUrl),
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to download encrypted attachment"))
            }

            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty attachment response"))

            encryptedTemp.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output, FAST_COPY_BUFFER_SIZE) }
            }

            val decrypted = E2EEAttachmentCrypto(context).decryptToCache(
                encryptedFile = encryptedTemp,
                originalFileName = originalEncryptedFileName(message),
                originalMimeType = safeString(message.encryptedAttachmentMimeType),
                kind = safeString(message.encryptedAttachmentKind).ifBlank { "file" },
                fileKeyBase64 = fileKey,
                fileIvBase64 = fileIv,
                cacheKey = encryptedAttachmentCacheKey(message),
            )

            Result.success(
                OpenedAttachment(
                    uri = decrypted.uri,
                    fileName = decrypted.fileName,
                    mimeType = decrypted.mimeType,
                    kind = decrypted.kind,
                )
            )
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message ?: "Failed to decrypt attachment"))
        } finally {
            encryptedTemp.delete()
        }
    }


    suspend fun createEncryptedImagePreview(context: Context, message: DmMessageDto): Result<String> {
        if (!isEncryptedImageAttachment(message)) {
            return Result.failure(IllegalStateException("Attachment is not an encrypted image"))
        }

        val directory = File(context.cacheDir, "e2ee_thumbnails")
        directory.mkdirs()

        val cacheKey = listOf(
            message.id.toString(),
            (message.encryptedAttachmentFileSize ?: message.fileSize ?: 0L).toString(),
            safeString(message.encryptedAttachmentIv).takeLast(8),
        ).joinToString("-").replace(Regex("[^a-zA-Z0-9._-]+"), "_")

        val previewFile = File(directory, "$cacheKey.jpg")
        val previewUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            previewFile,
        )

        if (previewFile.isFile && previewFile.length() > 0L) {
            return Result.success(previewUri.toString())
        }

        val embeddedPreviewData = safeString(message.encryptedAttachmentPreviewData)
        if (embeddedPreviewData.isNotBlank()) {
            val embedded = E2EEAttachmentCrypto(context).embeddedImagePreviewToCache(
                previewDataBase64 = embeddedPreviewData,
                previewMimeType = safeString(message.encryptedAttachmentPreviewMimeType),
                cacheKey = "embedded-$cacheKey",
            )

            if (embedded != null) {
                return Result.success(embedded.uri.toString())
            }
        }

        val opened = downloadAndDecryptAttachment(context, message).getOrElse { error ->
            return Result.failure(IllegalStateException(error.message ?: "Failed to decrypt image preview"))
        }

        return try {
            val bitmap = context.contentResolver.openInputStream(opened.uri).use { input ->
                requireNotNull(input) { "Cannot open decrypted image" }
                BitmapFactory.decodeStream(input)
            } ?: return Result.failure(IllegalStateException("Failed to decode image preview"))

            val scaled = scaleBitmapForPreview(bitmap, maxSide = 720)
            previewFile.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 84, output)
            }

            if (scaled !== bitmap) {
                scaled.recycle()
            }
            bitmap.recycle()

            Result.success(previewUri.toString())
        } catch (error: Throwable) {
            previewFile.delete()
            Result.failure(IllegalStateException(error.message ?: "Failed to create image preview"))
        }
    }

    private fun encryptedAttachmentCacheKey(message: DmMessageDto): String {
        val urlPart = safeString(message.encryptedAttachmentUrl).ifBlank { message.fileUrl }
            .substringAfterLast('/')
            .ifBlank { "attachment" }

        return listOf(
            message.id.toString(),
            urlPart,
            (message.encryptedAttachmentFileSize ?: message.fileSize ?: 0L).toString(),
            safeString(message.encryptedAttachmentIv).takeLast(16),
        ).joinToString("-")
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .take(140)
    }

    private fun originalEncryptedFileName(message: DmMessageDto): String {
        return safeString(message.encryptedAttachmentFileName).ifBlank {
            message.fileName
                .removePrefix("🔐 Зашифрованный файл: ")
                .removePrefix("🔐 Зашифрованное изображение: ")
                .removePrefix("🔐 Зашифрованное вложение: ")
                .ifBlank { "attachment" }
        }
    }

    fun isEncryptedImageAttachment(message: DmMessageDto): Boolean {
        val kind = safeLower(message.encryptedAttachmentKind)
        val mime = safeLower(message.encryptedAttachmentMimeType)
        val name = safeLower(originalEncryptedFileName(message))
        return isEncryptedAttachment(message) && (
            kind == "image" ||
                mime.startsWith("image/") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".png") ||
                name.endsWith(".webp") ||
                name.endsWith(".gif")
        )
    }

    private fun scaleBitmapForPreview(bitmap: Bitmap, maxSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= 0 || height <= 0 || (width <= maxSide && height <= maxSide)) {
            return bitmap
        }

        val scale = maxSide.toFloat() / maxOf(width, height).toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun isEncryptedAttachment(message: DmMessageDto): Boolean {
        return safeString(message.encryptedAttachmentKey).isNotBlank() &&
            safeString(message.encryptedAttachmentIv).isNotBlank() &&
            (safeString(message.encryptedAttachmentUrl).isNotBlank() || message.fileUrl.isNotBlank())
    }

    private fun resolveMediaUrl(url: String): String {
        val original = url.trim()
        val legacyEncryptedMatch = Regex("^/uploads/files/([^/?]+-encrypted\\.bin)(?:\\?.*)?$", RegexOption.IGNORE_CASE)
            .find(original)
        val value = if (legacyEncryptedMatch != null) {
            "/api/e2ee/files/${legacyEncryptedMatch.groupValues[1]}"
        } else {
            original
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value
        }
        val base = com.pulsemessenger.android.BuildConfig.BASE_URL.trimEnd('/')
        return if (value.startsWith("/")) base + value else "$base/$value"
    }

    private companion object {
        const val FAST_COPY_BUFFER_SIZE = 1024 * 1024
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
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
            val parsed: ErrorResponse? = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to mark read"))
        }

        return Result.success(response.body()?.readAt)
    }
}
