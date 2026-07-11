package com.pulsemessenger.android.feature.search

import com.google.gson.Gson
import com.pulsemessenger.android.core.network.ErrorResponse
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.SearchResultDto
import com.pulsemessenger.android.core.session.SessionStore

class SearchRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val gson = Gson()

    suspend fun searchMessages(query: String): Result<List<SearchResultDto>> {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return Result.failure(IllegalStateException("No session token"))
        if (query.isBlank()) return Result.success(emptyList())
        val response = networkProvider.api.searchMessages("Bearer $token", query = query, scope = "all")
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { gson.fromJson(errorBody, ErrorResponse::class.java) }.getOrNull()
            return Result.failure(IllegalStateException(parsed?.error ?: "Search failed"))
        }
        return Result.success(response.body()?.results.orEmpty())
    }
}
