package com.pulsemessenger.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pulsemessenger.android.core.call.CallActionBus
import com.pulsemessenger.android.core.call.CallNotificationAction
import com.pulsemessenger.android.ui.PulseAndroidApp
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)

        setContent {
            PulseAndroidTheme {
                PulseAndroidApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val peerUserId = intent.getLongExtra(EXTRA_PEER_USER_ID, 0L)
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Pulse" }
        val peerAvatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR_URL).orEmpty()

        when (action) {
            ACTION_CALL_OPEN -> {
                if (callId.isNotBlank() && peerUserId > 0L) {
                    CallActionBus.publish(
                        CallNotificationAction.Incoming(
                            callId = callId,
                            peerUserId = peerUserId,
                            peerName = peerName,
                            peerAvatarUrl = peerAvatarUrl,
                        )
                    )
                }
            }

            ACTION_CALL_ACCEPT -> {
                if (callId.isNotBlank() && peerUserId > 0L) {
                    CallActionBus.publish(
                        CallNotificationAction.Accept(
                            callId = callId,
                            peerUserId = peerUserId,
                            peerName = peerName,
                            peerAvatarUrl = peerAvatarUrl,
                        )
                    )
                }
            }

            ACTION_CALL_REJECT -> {
                if (callId.isNotBlank() && peerUserId > 0L) {
                    CallActionBus.publish(
                        CallNotificationAction.Reject(
                            callId = callId,
                            peerUserId = peerUserId,
                        )
                    )
                }
            }

            ACTION_CALL_END -> {
                CallActionBus.publish(CallNotificationAction.EndCurrent)
            }

            ACTION_CALL_SHOW_CURRENT -> {
                CallActionBus.publish(CallNotificationAction.OpenCurrent)
            }
        }
    }

    companion object {
        const val ACTION_CALL_OPEN = "com.pulsemessenger.android.action.CALL_OPEN"
        const val ACTION_CALL_ACCEPT = "com.pulsemessenger.android.action.CALL_ACCEPT"
        const val ACTION_CALL_REJECT = "com.pulsemessenger.android.action.CALL_REJECT"
        const val ACTION_CALL_END = "com.pulsemessenger.android.action.CALL_END"
        const val ACTION_CALL_SHOW_CURRENT = "com.pulsemessenger.android.action.CALL_SHOW_CURRENT"

        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PEER_USER_ID = "peer_user_id"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_PEER_AVATAR_URL = "peer_avatar_url"
    }
}
