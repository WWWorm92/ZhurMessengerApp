package com.pulsemessenger.android.core.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.SendMessageRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class OutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = PulseApp.instance
        val token = app.sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure()

        val store = OfflineStore.get(applicationContext)
        val items = store.pendingOutbox(20)
        if (items.isEmpty()) return Result.success()

        for (item in items) {
            val prepared = prepareAttachment(item, token)
            if (prepared is PreparedResult.Retry) {
                store.updateOutboxAttempt(item.id, item.attempts + 1, prepared.error)
                return Result.retry()
            }
            if (prepared is PreparedResult.PermanentFailure) {
                failPermanently(store, item, prepared.error)
                continue
            }
            prepared as PreparedResult.Ready

            val body = SendMessageRequest(
                content = item.text,
                replyToMessageId = item.replyToMessageId,
                imageUrl = prepared.imageUrl,
                fileUrl = prepared.fileUrl,
                fileName = prepared.fileName,
                fileSize = prepared.fileSize,
                notificationPreview = item.text.take(240).ifBlank { null },
                clientMessageId = item.clientMessageId,
            )

            try {
                if (item.scope == "room") {
                    val response = app.networkProvider.api.sendRoomMessage(
                        authorization = "Bearer $token",
                        roomId = item.targetId,
                        body = body,
                    )
                    if (!response.isSuccessful) {
                        if (isPermanent(response.code())) {
                            failPermanently(store, item, response.errorBody()?.string().orEmpty().ifBlank { "Не удалось отправить" })
                            continue
                        }
                        store.updateOutboxAttempt(item.id, item.attempts + 1, "HTTP ${response.code()}")
                        return Result.retry()
                    }
                    response.body()?.message?.let { store.upsertRoomMessages(item.targetId, listOf(it)) }
                } else {
                    val response = app.networkProvider.api.sendDmMessage(
                        authorization = "Bearer $token",
                        userId = item.targetId,
                        body = body,
                    )
                    if (!response.isSuccessful) {
                        if (isPermanent(response.code())) {
                            failPermanently(store, item, response.errorBody()?.string().orEmpty().ifBlank { "Не удалось отправить" })
                            continue
                        }
                        store.updateOutboxAttempt(item.id, item.attempts + 1, "HTTP ${response.code()}")
                        return Result.retry()
                    }
                    response.body()?.message?.let { message ->
                        item.localMessageId?.let { store.removeLocalDmMessage(item.targetId, it) }
                        store.upsertDmMessages(item.targetId, listOf(message))
                    }
                }

                cleanup(store, item)
            } catch (error: Throwable) {
                store.updateOutboxAttempt(item.id, item.attempts + 1, error.message)
                return Result.retry()
            }
        }

        if (store.pendingOutbox(1).isNotEmpty()) {
            OutboxScheduler.enqueueNow(applicationContext)
        }
        return Result.success()
    }

    private suspend fun prepareAttachment(item: PendingOutboxEntity, token: String): PreparedResult {
        val path = item.attachmentPath ?: return PreparedResult.Ready()
        val file = File(path)
        if (!file.isFile) return PreparedResult.PermanentFailure("Файл для отправки не найден")

        return try {
            val requestBody = file.asRequestBody(item.attachmentMimeType?.toMediaTypeOrNull())
            val fieldName = if (item.attachmentKind == "image") "image" else "file"
            val part = MultipartBody.Part.createFormData(
                fieldName,
                item.attachmentFileName ?: file.name,
                requestBody,
            )
            val api = PulseApp.instance.networkProvider.api
            if (item.attachmentKind == "image") {
                val response = api.uploadMessageImage("Bearer $token", part)
                if (!response.isSuccessful) {
                    if (isPermanent(response.code())) PreparedResult.PermanentFailure("Не удалось загрузить изображение")
                    else PreparedResult.Retry("HTTP ${response.code()}")
                } else {
                    val url = response.body()?.imageUrl.orEmpty()
                    if (url.isBlank()) PreparedResult.Retry("Пустой ответ загрузки")
                    else PreparedResult.Ready(imageUrl = url)
                }
            } else {
                val response = api.uploadMessageFile("Bearer $token", part)
                if (!response.isSuccessful) {
                    if (isPermanent(response.code())) PreparedResult.PermanentFailure("Не удалось загрузить файл")
                    else PreparedResult.Retry("HTTP ${response.code()}")
                } else {
                    val body = response.body()
                    if (body == null) PreparedResult.Retry("Пустой ответ загрузки")
                    else PreparedResult.Ready(
                        fileUrl = body.fileUrl,
                        fileName = body.fileName,
                        fileSize = body.fileSize,
                    )
                }
            }
        } catch (error: Throwable) {
            PreparedResult.Retry(error.message ?: "Ошибка загрузки")
        }
    }

    private suspend fun failPermanently(store: OfflineStore, item: PendingOutboxEntity, error: String) {
        if (item.scope == "dm" && item.localMessageId != null) {
            store.markLocalDmFailed(item.targetId, item.localMessageId, error)
        }
        cleanup(store, item)
    }

    private suspend fun cleanup(store: OfflineStore, item: PendingOutboxEntity) {
        store.deleteOutbox(item.id)
        item.attachmentPath?.let { runCatching { File(it).delete() } }
    }

    private fun isPermanent(code: Int): Boolean = code in 400..499 && code !in setOf(408, 425, 429)

    private sealed class PreparedResult {
        data class Ready(
            val imageUrl: String? = null,
            val fileUrl: String? = null,
            val fileName: String? = null,
            val fileSize: Long? = null,
        ) : PreparedResult()
        data class Retry(val error: String) : PreparedResult()
        data class PermanentFailure(val error: String) : PreparedResult()
    }
}
