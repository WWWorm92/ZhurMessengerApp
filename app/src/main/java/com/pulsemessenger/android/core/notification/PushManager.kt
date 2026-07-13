package com.pulsemessenger.android.core.notification

import android.util.Log
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.RegisterFcmRequest
import com.pulsemessenger.android.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushManager(
    context: android.content.Context,
) {
    private val settings = PushSettings(context)
    private var lastToken: String = ""
    private var registeredSuccess: Boolean = false

    fun onTokenRefreshed(token: String, networkProvider: NetworkProvider, sessionStore: SessionStore) {
        Log.d("PushManager", "onTokenRefreshed, token=${token.take(20)}...")
        if (!settings.enabled) {
            Log.d("PushManager", "onTokenRefreshed skipped: push disabled")
            lastToken = token
            registeredSuccess = false
            return
        }
        lastToken = token
        registeredSuccess = false
        registerOnServer(token, networkProvider, sessionStore)
    }

    private fun registerOnServer(token: String, networkProvider: NetworkProvider, sessionStore: SessionStore) {
        if (!settings.enabled) {
            Log.d("PushManager", "registerOnServer skipped: push disabled")
            return
        }
        val auth = sessionStore.currentToken().trim()
        if (auth.isBlank()) {
            Log.w("PushManager", "registerOnServer: auth token is blank")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = networkProvider.api.registerFcmToken("Bearer $auth", RegisterFcmRequest(token))
                if (response.isSuccessful) {
                    Log.d("PushManager", "FCM token registered successfully")
                    registeredSuccess = true
                } else {
                    Log.w("PushManager", "Server returned ${response.code()} for FCM registration")
                }
            } catch (e: Exception) {
                Log.e("PushManager", "FCM registration network error", e)
            }
        }
    }

    fun retryRegistrationIfNeeded(token: String, networkProvider: NetworkProvider, sessionStore: SessionStore) {
        if (!settings.enabled) {
            Log.d("PushManager", "retryRegistrationIfNeeded skipped: push disabled")
            return
        }
        if (token.isNotBlank() && !registeredSuccess) {
            Log.d("PushManager", "retryRegistrationIfNeeded, token=${token.take(20)}...")
            registerOnServer(token, networkProvider, sessionStore)
        } else if (registeredSuccess) {
            Log.d("PushManager", "retryRegistrationIfNeeded skipped: already registered")
        }
    }
    fun isEnabled(): Boolean {
        return settings.enabled
    }

    fun setEnabled(enabled: Boolean) {
        settings.enabled = enabled
        if (!enabled) {
            registeredSuccess = false
        }
    }
}
