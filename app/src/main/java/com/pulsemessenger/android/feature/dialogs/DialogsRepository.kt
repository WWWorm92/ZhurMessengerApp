package com.pulsemessenger.android.feature.dialogs

import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.offline.NetworkState
import com.pulsemessenger.android.core.offline.OfflineStore
import com.pulsemessenger.android.core.session.SessionStore
import com.pulsemessenger.android.core.share.ConversationShortcutPublisher

class DialogsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()
    private val context get() = PulseApp.instance.applicationContext
    private val offlineStore by lazy { OfflineStore.get(context) }

    suspend fun loadUsers(): Result<List<DialogUserDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            val cached = offlineStore.loadDialogs()
            return if (cached.isNotEmpty()) Result.success(cached)
            else Result.failure(IllegalStateException("No session token"))
        }

        if (!NetworkState.isOnline(context)) {
            val cached = offlineStore.loadDialogs()
            return if (cached.isNotEmpty()) Result.success(cached)
            else Result.failure(IllegalStateException("Нет соединения с сервером"))
        }

        return try {
            val response = networkProvider.api.users("Bearer $token")
            if (!response.isSuccessful) {
                val cached = offlineStore.loadDialogs()
                if (cached.isNotEmpty()) return Result.success(cached)
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load dialogs"))
            }
            val users = response.body()?.users.orEmpty()
            offlineStore.cacheDialogs(users)
            ConversationShortcutPublisher.publishDialogs(context, users)
            Result.success(users)
        } catch (error: Throwable) {
            val cached = offlineStore.loadDialogs()
            if (cached.isNotEmpty()) Result.success(cached)
            else Result.failure(IllegalStateException(error.message ?: "Failed to load dialogs"))
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
        offlineStore.clearDmMessages(peerUserId)
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
