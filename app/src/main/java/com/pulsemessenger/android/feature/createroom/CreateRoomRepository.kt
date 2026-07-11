package com.pulsemessenger.android.feature.createroom

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.CreateRoomRequest
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.session.SessionStore

class CreateRoomRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun createRoom(
        name: String,
        accessType: String,
        description: String,
        slug: String,
    ): Result<RoomDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val body = CreateRoomRequest(
            name = name,
            accessType = accessType,
            description = description,
            slug = slug,
        )
        val response = networkProvider.api.createRoom("Bearer $token", body)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to create room"))
        }
        return Result.success(response.body()?.room ?: return Result.failure(IllegalStateException("Empty response")))
    }

    private fun parseError(errorBody: String, fallback: String): IllegalStateException {
        val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
        return IllegalStateException(parsed?.error ?: fallback)
    }
}
