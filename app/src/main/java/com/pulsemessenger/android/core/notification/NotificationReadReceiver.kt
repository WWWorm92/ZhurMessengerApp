package com.pulsemessenger.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pulsemessenger.android.PulseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_READ) return

        val result = goAsync()

        val scope = intent.getStringExtra(EXTRA_SCOPE).orEmpty()
        val targetId = intent.getLongExtra(EXTRA_TARGET_ID, 0L)
        val chatKey = intent.getStringExtra(EXTRA_CHAT_KEY).orEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = PulseApp.instance
                val token = app.sessionStore.currentToken().trim()

                if (token.isBlank() || targetId <= 0 || chatKey.isBlank()) {
                    Log.w("PUSH", "mark read skipped: token/target/chatKey missing")
                    return@launch
                }

                val response = if (scope == "room") {
                    app.networkProvider.api.markRoomRead("Bearer $token", targetId)
                } else {
                    app.networkProvider.api.markDmRead("Bearer $token", targetId)
                }

                if (response.isSuccessful) {
                    PulseNotificationStore.clear(context, chatKey)
                    Log.d("PUSH", "notification marked as read: $chatKey")
                } else {
                    Log.w("PUSH", "mark read failed: ${response.code()}")
                }
            } catch (error: Exception) {
                Log.e("PUSH", "mark read error", error)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.pulsemessenger.android.action.MARK_READ"

        const val EXTRA_SCOPE = "scope"
        const val EXTRA_TARGET_ID = "targetId"
        const val EXTRA_CHAT_KEY = "chatKey"
    }
}