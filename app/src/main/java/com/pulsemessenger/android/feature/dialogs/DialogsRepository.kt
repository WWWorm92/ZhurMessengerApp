package com.pulsemessenger.android.feature.dialogs

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore

class DialogsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

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
        return Result.success(response.body()?.users.orEmpty())
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
