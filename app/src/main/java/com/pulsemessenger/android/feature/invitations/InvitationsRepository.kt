package com.pulsemessenger.android.feature.invitations

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.InvitationDto
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore

class InvitationsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun loadInvitations(): Result<List<InvitationDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.invitations("Bearer $token")
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to load invitations"))
        }
        return Result.success(response.body()?.invitations.orEmpty())
    }

    suspend fun acceptInvitation(invitationId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.acceptInvitation("Bearer $token", invitationId)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to accept invitation"))
        }
        return Result.success(Unit)
    }

    suspend fun declineInvitation(invitationId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("No session token"))
        }
        val response = networkProvider.api.declineInvitation("Bearer $token", invitationId)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Failed to decline invitation"))
        }
        return Result.success(Unit)
    }
}
