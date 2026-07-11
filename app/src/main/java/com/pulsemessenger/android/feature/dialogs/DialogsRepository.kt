package com.pulsemessenger.android.feature.dialogs

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore
import java.io.IOException

class DialogsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun loadUsers(): Result<List<DialogUserDto>> {
        return try {
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

            Result.success(response.body()?.users.orEmpty())
        } catch (error: Throwable) {
            Result.failure(networkError(error))
        }
    }

    suspend fun clearDialog(peerUserId: Long): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.deleteDialog("Bearer $token", peerUserId)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to clear dialog"))
            }

            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(networkError(error))
        }
    }

    suspend fun updateDmChatPrefs(
        targetId: Long,
        pinned: Boolean? = null,
        muted: Boolean? = null,
        archived: Boolean? = null,
    ): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.updateChatPrefs(
                "Bearer $token",
                ChatPrefsRequest(
                    scope = "dm",
                    targetId = targetId,
                    pinned = pinned,
                    muted = muted,
                    archived = archived
                )
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to update chat prefs"))
            }

            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(networkError(error))
        }
    }

    private fun networkError(error: Throwable): IllegalStateException {
        return when (error) {
            is IOException -> IllegalStateException("Нет соединения с сервером")
            else -> IllegalStateException(error.message ?: "Ошибка соединения")
        }
    }
}