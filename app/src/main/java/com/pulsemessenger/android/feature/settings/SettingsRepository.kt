package com.pulsemessenger.android.feature.settings

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.AdminOverviewResponse
import com.pulsemessenger.android.core.network.AdminCreateUserRequest
import com.pulsemessenger.android.core.network.AdminCreateUserResponse
import com.pulsemessenger.android.core.network.AdminPasswordRequest
import com.pulsemessenger.android.core.network.AdminToggleRoleRequest
import com.pulsemessenger.android.core.network.AdminUsersResponse
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DeviceDto
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.NotificationStatusResponse
import com.pulsemessenger.android.core.network.SessionDto
import com.pulsemessenger.android.core.session.SessionStore

class SettingsRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun loadSessions(): Result<List<SessionDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.sessions("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load sessions"))
        }
        return Result.success(response.body()?.sessions.orEmpty())
    }

    suspend fun loadDevices(): Result<List<DeviceDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.devices("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load devices"))
        }
        return Result.success(response.body()?.devices.orEmpty())
    }

    suspend fun loadNotificationStatus(): Result<NotificationStatusResponse> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.notificationStatus("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load notification status"))
        }
        return Result.success(response.body() ?: NotificationStatusResponse())
    }

    suspend fun unsubscribeAll(): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.unsubscribeAll("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to unsubscribe"))
        }
        return Result.success(Unit)
    }

    suspend fun testNotification(): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.testNotification("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to send test notification"))
        }
        return Result.success(Unit)
    }

    suspend fun revokeSession(sessionId: String): Result<Boolean> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.revokeSession("Bearer $token", sessionId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to revoke session"))
        }
        return Result.success(response.body()?.revokedCurrent == true)
    }

    suspend fun revokeDevice(deviceId: Long): Result<Boolean> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.revokeDevice("Bearer $token", deviceId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to revoke device"))
        }
        return Result.success(response.body()?.revokedCurrent == true)
    }

    suspend fun loadAdminOverview(): Result<AdminOverviewResponse> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.adminOverview("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load admin overview"))
        }
        return Result.success(response.body() ?: AdminOverviewResponse())
    }

    suspend fun loadAdminUsers(): Result<List<DialogUserDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.adminUsers("Bearer $token")
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to load admin users"))
        }
        return Result.success(response.body()?.users.orEmpty())
    }

    suspend fun createAdminUser(username: String, password: String, displayName: String, isAdmin: Boolean): Result<DialogUserDto> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.createAdminUser("Bearer $token", AdminCreateUserRequest(username, password, displayName, isAdmin))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to create user"))
        }
        return Result.success(response.body()?.user ?: return Result.failure(IllegalStateException("Empty response")))
    }

    suspend fun updateAdminUserRole(userId: Long, isAdmin: Boolean): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateAdminUserRole("Bearer $token", userId, AdminToggleRoleRequest(isAdmin))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to update role"))
        }
        return Result.success(Unit)
    }

    suspend fun updateAdminUserPassword(userId: Long, newPassword: String): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.updateAdminUserPassword("Bearer $token", userId, AdminPasswordRequest(newPassword))
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to update password"))
        }
        return Result.success(Unit)
    }

    suspend fun deleteAdminUser(userId: Long): Result<Unit> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        val response = networkProvider.api.deleteAdminUser("Bearer $token", userId)
        if (!response.isSuccessful) {
            return Result.failure(parseError(response.errorBody()?.string().orEmpty(), "Failed to delete user"))
        }
        return Result.success(Unit)
    }

    private fun parseError(errorBody: String, fallback: String): IllegalStateException {
        val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
        return IllegalStateException(parsed?.error ?: fallback)
    }
}
