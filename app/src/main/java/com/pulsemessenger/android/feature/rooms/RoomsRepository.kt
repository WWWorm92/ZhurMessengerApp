package com.pulsemessenger.android.feature.rooms

import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.offline.NetworkState
import com.pulsemessenger.android.core.offline.OfflineStore
import com.pulsemessenger.android.core.session.SessionStore
import com.pulsemessenger.android.core.share.ConversationShortcutPublisher
import java.io.IOException

class RoomsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()
    private val context get() = PulseApp.instance.applicationContext
    private val offlineStore by lazy { OfflineStore.get(context) }

    suspend fun loadRooms(): Result<List<RoomDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            val cached = offlineStore.loadRooms()
            return if (cached.isNotEmpty()) Result.success(cached)
            else Result.failure(IllegalStateException("No session token"))
        }
        if (!NetworkState.isOnline(context)) {
            val cached = offlineStore.loadRooms()
            return if (cached.isNotEmpty()) Result.success(cached)
            else Result.failure(IllegalStateException("Нет соединения с сервером"))
        }
        return try {
            val response = networkProvider.api.rooms("Bearer $token")
            if (!response.isSuccessful) {
                val cached = offlineStore.loadRooms()
                if (cached.isNotEmpty()) return Result.success(cached)
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load rooms"))
            }
            val rooms = response.body()?.rooms.orEmpty()
            offlineStore.cacheRooms(rooms)
            ConversationShortcutPublisher.publishRooms(context, rooms)
            Result.success(rooms)
        } catch (error: Throwable) {
            val cached = offlineStore.loadRooms()
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(networkError(error))
        }
    }

    suspend fun joinRoom(roomId: Long): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
            val response = networkProvider.api.joinRoom("Bearer $token", roomId)
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to join room"))
            }
            Result.success(Unit)
        } catch (error: Throwable) { Result.failure(networkError(error)) }
    }

    suspend fun updateRoomChatPrefs(
        roomId: Long,
        pinned: Boolean? = null,
        muted: Boolean? = null,
        archived: Boolean? = null,
    ): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
            val response = networkProvider.api.updateChatPrefs(
                "Bearer $token",
                ChatPrefsRequest(scope = "room", targetId = roomId, pinned = pinned, muted = muted, archived = archived)
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to update chat prefs"))
            }
            Result.success(Unit)
        } catch (error: Throwable) { Result.failure(networkError(error)) }
    }

    suspend fun requestJoinRoom(roomId: Long): Result<Unit> {
        return try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
            val response = networkProvider.api.requestJoinRoom("Bearer $token", roomId)
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Failed to request join"))
            }
            Result.success(Unit)
        } catch (error: Throwable) { Result.failure(networkError(error)) }
    }

    private fun networkError(error: Throwable): IllegalStateException = when (error) {
        is IOException -> IllegalStateException("Нет соединения с сервером")
        else -> IllegalStateException(error.message ?: "Ошибка соединения")
    }
}
