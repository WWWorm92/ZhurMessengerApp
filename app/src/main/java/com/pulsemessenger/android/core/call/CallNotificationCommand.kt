package com.pulsemessenger.android.core.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pulsemessenger.android.BuildConfig
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.realtime.RealtimeSocketManager
import java.util.concurrent.atomic.AtomicBoolean

object CallNotificationCommand {
    private const val COMMAND_TIMEOUT_MS = 8_000L
    private const val DISCONNECT_AFTER_EMIT_MS = 900L

    fun reject(
        context: Context,
        callId: String,
        peerUserId: Long,
        onComplete: () -> Unit = {},
    ) {
        if (callId.isBlank() || peerUserId <= 0L) {
            onComplete()
            return
        }

        val app = context.applicationContext as? PulseApp
        if (app == null) {
            onComplete()
            return
        }

        val token = app.sessionStore.currentToken().trim()
        if (token.isBlank()) {
            onComplete()
            return
        }

        val manager = RealtimeSocketManager(BuildConfig.BASE_URL)
        val handler = Handler(Looper.getMainLooper())
        val emitted = AtomicBoolean(false)
        val finished = AtomicBoolean(false)

        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            runCatching { manager.disconnect() }
            onComplete()
        }

        manager.setOnConnectionStateChanged { connected ->
            if (connected && emitted.compareAndSet(false, true)) {
                Log.d("CALL_NOTIFICATION", "Rejecting call from notification callId=$callId")
                manager.emitCallReject(callId, peerUserId)
                handler.postDelayed({ finish() }, DISCONNECT_AFTER_EMIT_MS)
            }
        }

        manager.setOnConnectionError { error ->
            Log.w("CALL_NOTIFICATION", "Unable to reject call: ${error.orEmpty()}")
            finish()
        }

        manager.connect(
            token = token,
            deviceKey = app.sessionStore.ensureDeviceKey(),
        )

        handler.postDelayed({ finish() }, COMMAND_TIMEOUT_MS)
    }
}
