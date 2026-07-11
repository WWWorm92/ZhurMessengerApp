package com.pulsemessenger.android.feature.profile

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ChangePasswordRequest
import com.pulsemessenger.android.core.network.createImagePart
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.MeUserDto
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.UpdateProfileRequest
import com.pulsemessenger.android.core.session.SessionStore

class ProfileRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun updateDisplayName(displayName: String): Result<MeUserDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateProfile("Bearer $token", UpdateProfileRequest(displayName))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to update display name"))
        }
        return Result.success(response.body()?.user ?: return Result.failure(IllegalStateException("Empty response")))
    }

    suspend fun uploadAvatar(context: Context, uri: Uri): Result<MeUserDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val part = createImagePart(context, uri, "avatar")
            ?: return Result.failure(IllegalStateException("Cannot open image"))
        val response = networkProvider.api.uploadAvatar("Bearer $token", part)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to upload avatar"))
        }
        return Result.success(response.body()?.user ?: return Result.failure(IllegalStateException("Empty response")))
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.changePassword(
            "Bearer $token",
            ChangePasswordRequest(currentPassword, newPassword)
        )
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to change password"))
        }
        return Result.success(Unit)
    }

    private fun parseError(errorBody: String, fallback: String): IllegalStateException {
        val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
        return IllegalStateException(parsed?.error ?: fallback)
    }
}
