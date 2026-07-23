package com.pulsemessenger.android.core.session

import android.content.Context
import com.pulsemessenger.android.core.privacy.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SessionStore(context: Context) {
    private val securePrefs = SecurePrefs(context, "pulse_session_secure")
    private val legacyPrefs = context.getSharedPreferences("pulse_session", Context.MODE_PRIVATE)

    init {
        migrateLegacyValue(KEY_TOKEN)
        migrateLegacyValue(KEY_DEVICE_KEY)
    }

    private val _token = MutableStateFlow(securePrefs.getString(KEY_TOKEN, ""))
    val token: StateFlow<String> = _token.asStateFlow()

    fun currentToken(): String = _token.value

    fun saveToken(token: String) {
        securePrefs.putString(KEY_TOKEN, token)
        _token.value = token
    }

    fun ensureDeviceKey(): String {
        val existing = securePrefs.getString(KEY_DEVICE_KEY, "").trim()
        if (existing.isNotBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        securePrefs.putString(KEY_DEVICE_KEY, generated)
        return generated
    }

    fun clear() {
        securePrefs.remove(KEY_TOKEN)
        _token.value = ""
    }

    private fun migrateLegacyValue(key: String) {
        val legacyValue = legacyPrefs.getString(key, "").orEmpty()
        if (legacyValue.isNotBlank() && securePrefs.getString(key, "").isBlank()) {
            securePrefs.putString(key, legacyValue)
        }
        legacyPrefs.edit().remove(key).apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_KEY = "device_key"
    }
}
