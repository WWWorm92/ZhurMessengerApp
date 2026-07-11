package com.pulsemessenger.android.core.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("pulse_session", Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, "") ?: "")
    val token: StateFlow<String> = _token.asStateFlow()

    fun currentToken(): String = _token.value

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _token.value = token
    }

    fun ensureDeviceKey(): String {
        val existing = prefs.getString(KEY_DEVICE_KEY, "").orEmpty().trim()
        if (existing.isNotBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_KEY, generated).apply()
        return generated
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _token.value = ""
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_KEY = "device_key"
    }
}
