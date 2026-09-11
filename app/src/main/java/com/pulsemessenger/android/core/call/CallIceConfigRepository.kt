package com.pulsemessenger.android.core.call

import android.util.Log
import com.pulsemessenger.android.core.network.CallIceServerDto
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.PeerConnection

/**
 * Loads the same ICE/TURN configuration that the web client uses.
 * Credentials are never logged. Static TURN config is refreshed periodically;
 * time-limited credentials follow the server-provided TTL with a safety margin.
 */
class CallIceConfigRepository(
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    companion object {
        private const val TAG = "WEBRTC_ICE_CONFIG"
        private const val STATIC_CONFIG_CACHE_MS = 10 * 60 * 1000L
        private const val MIN_CACHE_MS = 60 * 1000L
        private const val EXPIRY_SAFETY_MS = 60 * 1000L
    }

    private val mutex = Mutex()
    private var cachedServers: List<PeerConnection.IceServer> = emptyList()
    private var expiresAtMs: Long = 0L

    suspend fun load(force: Boolean = false): List<PeerConnection.IceServer> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && cachedServers.isNotEmpty() && now < expiresAtMs - EXPIRY_SAFETY_MS) {
            return@withLock cachedServers
        }

        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) {
            return@withLock cachedServers.ifEmpty { fallbackServers() }
        }

        val fetched = runCatching {
            val response = networkProvider.api.callIceConfig("Bearer $token")
            if (!response.isSuccessful) {
                error("ICE config HTTP ${response.code()}")
            }
            val body = response.body() ?: error("Empty ICE config")
            val servers = body.iceServers.flatMap(::toWebRtcServers)
            if (servers.isEmpty()) error("Server returned no ICE servers")

            val ttlMs = body.credentialTtlSeconds
                ?.takeIf { it > 0L }
                ?.times(1000L)
                ?: STATIC_CONFIG_CACHE_MS

            cachedServers = servers
            expiresAtMs = now + ttlMs.coerceAtLeast(MIN_CACHE_MS)
            Log.d(TAG, "ICE config loaded servers=${servers.size} ttlSeconds=${body.credentialTtlSeconds ?: 0}")
            servers
        }.onFailure { error ->
            Log.w(TAG, "ICE config refresh failed: ${error.message}")
        }.getOrNull()

        fetched ?: cachedServers.ifEmpty { fallbackServers() }
    }

    private fun toWebRtcServers(server: CallIceServerDto): List<PeerConnection.IceServer> {
        val urls = when (val raw = server.urls) {
            is String -> listOf(raw)
            is Collection<*> -> raw.mapNotNull { it?.toString() }
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotBlank() }

        return urls.map { url ->
            PeerConnection.IceServer.builder(url).apply {
                if (server.username.isNotBlank()) setUsername(server.username)
                if (server.credential.isNotBlank()) setPassword(server.credential)
            }.createIceServer()
        }
    }

    private fun fallbackServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
}
