package com.pulsemessenger.android.feature.auth

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.LoginRequest
import com.pulsemessenger.android.core.network.MeUserDto
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RegisterRequest
import com.pulsemessenger.android.core.session.SessionStore
import java.io.IOException

class AuthRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun register(username: String, password: String, displayName: String): Result<MeUserDto> {
        val response = networkProvider.api.register(RegisterRequest(username = username, password = password, displayName = displayName))
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Registration failed"))
        }
        val body = response.body() ?: return Result.failure(IllegalStateException("Empty register response"))
        sessionStore.saveToken(body.token)
        return Result.success(body.user)
    }

    suspend fun login(username: String, password: String): Result<MeUserDto> {
        return try {
            val response = networkProvider.api.login(LoginRequest(username = username, password = password))

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
                return Result.failure(IllegalStateException(parsed?.error ?: "Login failed"))
            }

            val body = response.body()
                ?: return Result.failure(IllegalStateException("Empty login response"))

            sessionStore.saveToken(body.token)
            Result.success(body.user)
        } catch (error: Exception) {
            Result.failure(networkError(error))
        }
    }

    suspend fun restoreSession(): Result<MeUserDto> {
        return try {
            var token = sessionStore.currentToken().trim()

            if (token.isBlank()) {
                return Result.failure(IllegalStateException("No saved token"))
            }

            var response = networkProvider.api.me("Bearer $token")

            if (!response.isSuccessful && (response.code() == 401 || response.code() == 403)) {
                val refreshed = networkProvider.refreshSessionToken()

                if (!refreshed) {
                    if (networkProvider.lastRefreshNetworkError) {
                        return Result.failure(
                            IllegalStateException("Нет соединения с сервером")
                        )
                    }

                    sessionStore.clear()

                    return Result.failure(
                        IllegalStateException("Session refresh failed")
                    )
                }

                token = sessionStore.currentToken().trim()

                if (token.isBlank()) {
                    sessionStore.clear()
                    return Result.failure(IllegalStateException("Empty refreshed token"))
                }

                response = networkProvider.api.me("Bearer $token")
            }

            if (!response.isSuccessful) {
                if (response.code() == 401 || response.code() == 403) {
                    sessionStore.clear()
                }

                val errorBody = response.errorBody()?.string().orEmpty()
                val parsed = runCatching {
                    gson.fromJson(errorBody, ErrorResponse::class.java)
                }.getOrNull()

                return Result.failure(
                    IllegalStateException(parsed?.error ?: "Session restore failed")
                )
            }

            Result.success(
                response.body()?.user
                    ?: return Result.failure(IllegalStateException("Empty session response"))
            )
        } catch (error: Exception) {
            Result.failure(networkError(error))
        }
    }

    fun logout() {
        try {
            val token = sessionStore.currentToken().trim()
            if (token.isNotBlank()) {
                kotlinx.coroutines.runBlocking {
                    networkProvider.api.logout("Bearer $token")
                }
            }
        } catch (_: Exception) { }
        sessionStore.clear()
    }

    fun hasSession(): Boolean = sessionStore.currentToken().isNotBlank()
    private fun networkError(error: Exception): IllegalStateException {
        return when (error) {
            is IOException -> IllegalStateException("Нет соединения с сервером")
            else -> IllegalStateException(error.message ?: "Ошибка соединения")
        }
    }
}
