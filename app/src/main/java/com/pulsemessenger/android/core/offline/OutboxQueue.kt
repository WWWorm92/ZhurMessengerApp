package com.pulsemessenger.android.core.offline

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.DmMessageDto
import java.io.File
import java.time.Instant
import java.util.UUID

object OutboxQueue {
    suspend fun enqueueText(
        context: Context,
        scope: String,
        targetId: Long,
        text: String,
        replyToMessageId: Long? = null,
        createLocalDmMessage: Boolean = false,
        clientMessageId: String = UUID.randomUUID().toString(),
    ): DmMessageDto? {
        val normalizedScope = if (scope == "room") "room" else "dm"
        val cleanText = text.trim()
        require(targetId > 0L) { "Invalid target" }
        require(cleanText.isNotBlank()) { "Empty message" }

        val id = clientMessageId.ifBlank { UUID.randomUUID().toString() }
        val localId = if (createLocalDmMessage && normalizedScope == "dm") newLocalMessageId() else null
        val entity = PendingOutboxEntity(
            id = id,
            scope = normalizedScope,
            targetId = targetId,
            text = cleanText,
            replyToMessageId = replyToMessageId,
            attachmentPath = null,
            attachmentMimeType = null,
            attachmentFileName = null,
            attachmentKind = "none",
            clientMessageId = id,
            localMessageId = localId,
            createdAt = System.currentTimeMillis(),
        )
        val store = OfflineStore.get(context)
        store.addOutbox(entity)

        val localMessage = if (localId != null) {
            val senderId = OfflineStore.userIdFromToken(PulseApp.instance.sessionStore.currentToken())
            DmMessageDto(
                id = localId,
                senderId = senderId,
                receiverId = targetId,
                content = cleanText,
                type = "text",
                replyToMessageId = replyToMessageId,
                createdAt = Instant.now().toString(),
                localSendState = "sending",
                localError = "queued",
            ).also { store.saveLocalDmMessage(targetId, it) }
        } else null

        OutboxScheduler.enqueueNow(context)
        return localMessage
    }

    suspend fun enqueueSharedUri(
        context: Context,
        scope: String,
        targetId: Long,
        uri: Uri,
        caption: String = "",
    ) {
        val id = UUID.randomUUID().toString()
        val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val displayName = displayName(context, uri).ifBlank {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
            if (extension.isBlank()) "attachment" else "attachment.$extension"
        }
        val kind = if (mime.startsWith("image/")) "image" else "file"
        val outboxDir = File(context.filesDir, "outbox").apply { mkdirs() }
        val destination = File(outboxDir, "$id-${sanitizeFileName(displayName)}")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read shared file" }
            destination.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        }

        OfflineStore.get(context).addOutbox(
            PendingOutboxEntity(
                id = id,
                scope = if (scope == "room") "room" else "dm",
                targetId = targetId,
                text = caption.trim(),
                replyToMessageId = null,
                attachmentPath = destination.absolutePath,
                attachmentMimeType = mime,
                attachmentFileName = displayName,
                attachmentKind = kind,
                clientMessageId = id,
                localMessageId = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        OutboxScheduler.enqueueNow(context)
    }

    private fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use ""
                    cursor.getString(0).orEmpty()
                }.orEmpty()
        }.getOrDefault("")
    }

    private fun sanitizeFileName(value: String): String {
        return value.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_").take(160).ifBlank { "attachment" }
    }

    private fun newLocalMessageId(): Long {
        val positive = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        return -positive.coerceAtLeast(1L)
    }
}
