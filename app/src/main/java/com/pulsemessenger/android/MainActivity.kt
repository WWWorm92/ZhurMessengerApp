package com.pulsemessenger.android

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import com.pulsemessenger.android.core.call.CallActionBus
import com.pulsemessenger.android.core.call.CallNotificationAction
import com.pulsemessenger.android.core.call.IncomingCallAlert
import com.pulsemessenger.android.core.call.CallForegroundService
import com.pulsemessenger.android.core.call.ProximityScreenController
import com.pulsemessenger.android.core.notification.ActiveChatTracker
import com.pulsemessenger.android.core.notification.PulseNotificationStore
import com.pulsemessenger.android.ui.PulseAndroidApp
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme

class MainActivity : ComponentActivity() {
    private lateinit var proximityScreenController: ProximityScreenController

    private val callServiceHandler = Handler(Looper.getMainLooper())
    private var callServiceMonitorRunning = false

    private val callServiceMonitor = object : Runnable {
        override fun run() {
            if (!callServiceMonitorRunning) return

            ensureCallForegroundServiceStarted()
            callServiceHandler.postDelayed(this, 150L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proximityScreenController = ProximityScreenController(this)
        proximityScreenController.start()
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


    override fun onResume() {
        super.onResume()
        ActiveChatTracker.setAppVisible(true)
        startCallServiceMonitor()
    }

    override fun onPause() {
        ActiveChatTracker.setAppVisible(false)
        // Last synchronous attempt while the Activity is still in the
        // foreground-eligible lifecycle state. Starting a microphone FGS
        // from onStop is too late on Android 14+.
        ensureCallForegroundServiceStarted()
        stopCallServiceMonitor()
        super.onPause()
    }

    private fun startCallServiceMonitor() {
        if (callServiceMonitorRunning) return
        callServiceMonitorRunning = true
        callServiceHandler.post(callServiceMonitor)
    }

    private fun stopCallServiceMonitor() {
        callServiceMonitorRunning = false
        callServiceHandler.removeCallbacks(callServiceMonitor)
    }

    private fun ensureCallForegroundServiceStarted() {
        val audioManager =
            getSystemService(AUDIO_SERVICE) as AudioManager

        if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
            CallForegroundService.startIfNeeded(
                context = applicationContext,
                status = "Звонок активен",
            )
        }
    }

    override fun onDestroy() {
        stopCallServiceMonitor()
        proximityScreenController.stop()
        super.onDestroy()
    }

    private fun handleCallIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val peerUserId = intent.getLongExtra(EXTRA_PEER_USER_ID, 0L)
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME)
            .orEmpty()
            .ifBlank { "Pulse" }
        val peerAvatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR_URL).orEmpty()

        if (
            action == ACTION_CALL_ACCEPT ||
            action == ACTION_CALL_REJECT ||
            action == ACTION_CALL_END
        ) {
            IncomingCallAlert.stop(this, callId)
            if (callId.isNotBlank()) {
                NotificationManagerCompat.from(this)
                    .cancel(PulseNotificationStore.callNotificationId(callId))
            }
        }

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
