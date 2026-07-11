package com.pulsemessenger.android.feature.rooms

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.session.SessionStore
import java.io.IOException

class RoomsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun loadRooms(): Result<List<RoomDto>> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.rooms("Bearer $token")

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load rooms"))
            }

            Result.success(response.body()?.rooms.orEmpty())
        } catch (error: Throwable) {
            Result.failure(networkError(error))
        }
    }

    suspend fun joinRoom(roomId: Long): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.joinRoom("Bearer $token", roomId)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to join room"))
            }

            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(networkError(error))
        }
    }

    suspend fun updateRoomChatPrefs(
        roomId: Long,
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
                    scope = "room",
                    targetId = roomId,
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

    suspend fun requestJoinRoom(roomId: Long): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.requestJoinRoom("Bearer $token", roomId)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to request join"))
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