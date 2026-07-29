package com.pulsemessenger.android.core.sync

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ChatDraftDto
import com.pulsemessenger.android.core.network.ChatPreferencesDto
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.UpsertChatDraftRequest
import com.pulsemessenger.android.core.session.SessionStore

class ChatSyncRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    private fun authorization(): String {
        val token = sessionStore.currentToken().trim()
        require(token.isNotBlank()) { "Нет активной сессии" }
        return "Bearer $token"
    }

    private fun errorMessage(raw: String?, fallback: String): String {
        return runCatching {
            gson.fromJson(raw.orEmpty(), ErrorResponse::class.java)?.error
        }.getOrNull().orEmpty().ifBlank { fallback }
    }

    suspend fun loadDraft(scope: String, targetId: Long): Result<ChatDraftDto?> = runCatching {
        val response = networkProvider.api.chatDraft(authorization(), scope, targetId)
        if (!response.isSuccessful) {
            error(errorMessage(response.errorBody()?.string(), "Не удалось загрузить черновик"))
        }
        response.body()?.draft
    }

    suspend fun saveDraft(
        scope: String,
        targetId: Long,
        content: String,
        replyToMessageId: Long?,
    ): Result<ChatDraftDto?> = runCatching {
        val response = if (content.isBlank() && replyToMessageId == null) {
            networkProvider.api.deleteChatDraft(authorization(), scope, targetId)
        } else {
            networkProvider.api.saveChatDraft(
                authorization(),
                scope,
                targetId,
                UpsertChatDraftRequest(content = content, replyToMessageId = replyToMessageId),
            )
        }
        if (!response.isSuccessful) {
            error(errorMessage(response.errorBody()?.string(), "Не удалось сохранить черновик"))
        }
        response.body()?.draft
    }

    suspend fun loadPreferences(scope: String, targetId: Long): Result<ChatPreferencesDto> = runCatching {
        val response = networkProvider.api.chatPrefs(authorization(), scope, targetId)
        if (!response.isSuccessful) {
            error(errorMessage(response.errorBody()?.string(), "Не удалось загрузить настройки чата"))
        }
        response.body()?.preferences ?: ChatPreferencesDto(scope = scope, targetId = targetId)
    }

    suspend fun updatePreferences(request: ChatPrefsRequest): Result<ChatPreferencesDto> = runCatching {
        val response = networkProvider.api.updateChatPrefs(authorization(), request)
        if (!response.isSuccessful) {
            error(errorMessage(response.errorBody()?.string(), "Не удалось сохранить настройки чата"))
        }
        response.body()?.preferences ?: error("Пустой ответ сервера")
    }
}
