package com.pulsemessenger.android.feature.rooms

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.InviteCandidatesResponse
import com.pulsemessenger.android.core.network.InviteUserRequest
import com.pulsemessenger.android.core.network.JoinRequestDto
import com.pulsemessenger.android.core.network.MemberActionRequest
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RoomDetailResponse
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.network.UpdateRoomRequest
import android.content.Context
import android.net.Uri
import com.pulsemessenger.android.core.network.createImagePart
import com.pulsemessenger.android.core.session.SessionStore

class RoomSettingsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun loadRoomDetail(roomId: Long): Result<RoomDetailResponse> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.roomDetail("Bearer $token", roomId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load room"))
        }
        return Result.success(response.body() ?: return Result.failure(IllegalStateException("Empty response")))
    }

    suspend fun updateRoom(roomId: Long, request: UpdateRoomRequest): Result<RoomDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateRoom("Bearer $token", roomId, request)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to update room"))
        }
        return Result.success(response.body()?.room ?: return Result.failure(IllegalStateException("Empty response")))
    }

    suspend fun loadInviteCandidates(roomId: Long): Result<List<com.pulsemessenger.android.core.network.DialogUserDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.inviteCandidates("Bearer $token", roomId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load candidates"))
        }
        return Result.success(response.body()?.users.orEmpty())
    }

    suspend fun inviteUser(roomId: Long, userId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.inviteRoomUser("Bearer $token", roomId, InviteUserRequest(userId))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to invite user"))
        }
        return Result.success(Unit)
    }

    suspend fun removeMember(roomId: Long, userId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.removeRoomMember("Bearer $token", roomId, userId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to remove member"))
        }
        return Result.success(Unit)
    }

    suspend fun updateMember(roomId: Long, userId: Long, role: String?): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateRoomMember("Bearer $token", roomId, userId, MemberActionRequest(role = role))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to update member"))
        }
        return Result.success(Unit)
    }

    suspend fun leaveRoom(roomId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.leaveRoom("Bearer $token", roomId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to leave room"))
        }
        return Result.success(Unit)
    }

    suspend fun deleteRoom(roomId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.deleteRoom("Bearer $token", roomId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to delete room"))
        }
        return Result.success(Unit)
    }

    suspend fun loadJoinRequests(roomId: Long): Result<List<JoinRequestDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.joinRequests("Bearer $token", roomId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load requests"))
        }
        return Result.success(response.body()?.requests.orEmpty())
    }

    suspend fun approveJoinRequest(roomId: Long, userId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.approveJoinRequest("Bearer $token", roomId, userId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to approve request"))
        }
        return Result.success(Unit)
    }

    suspend fun declineJoinRequest(roomId: Long, userId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.declineJoinRequest("Bearer $token", roomId, userId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to decline request"))
        }
        return Result.success(Unit)
    }

    suspend fun uploadRoomAvatar(context: Context, uri: Uri): Result<String> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val imagePart = createImagePart(context, uri, "avatar")
            ?: return Result.failure(IllegalStateException("Failed to read image"))
        val response = networkProvider.api.uploadRoomAvatar("Bearer $token", imagePart)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to upload avatar"))
        }
        return Result.success(response.body()?.avatarUrl ?: return Result.failure(IllegalStateException("Empty upload response")))
    }

    private fun parseError(errorBody: String, fallback: String): IllegalStateException {
        val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
        return IllegalStateException(parsed?.error ?: fallback)
    }
}
