package com.pulsemessenger.android.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.core.notification.PulseNotificationStore

class CallNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(MainActivity.EXTRA_CALL_ID).orEmpty()
        val peerUserId = intent.getLongExtra(MainActivity.EXTRA_PEER_USER_ID, 0L)
        val peerName = intent.getStringExtra(MainActivity.EXTRA_PEER_NAME).orEmpty().ifBlank { "Pulse" }

        if (callId.isNotBlank()) {
            NotificationManagerCompat.from(context).cancel(PulseNotificationStore.callNotificationId(callId))
        }

        val action = when (intent.action) {
            ACTION_ACCEPT -> MainActivity.ACTION_CALL_ACCEPT
            ACTION_REJECT -> MainActivity.ACTION_CALL_REJECT
            ACTION_END -> MainActivity.ACTION_CALL_END
            else -> MainActivity.ACTION_CALL_OPEN
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_PEER_USER_ID, peerUserId)
            putExtra(MainActivity.EXTRA_PEER_NAME, peerName)
        }

        context.startActivity(openIntent)
    }

    companion object {
        const val ACTION_ACCEPT = "com.pulsemessenger.android.action.CALL_ACCEPT"
        const val ACTION_REJECT = "com.pulsemessenger.android.action.CALL_REJECT"
        const val ACTION_END = "com.pulsemessenger.android.action.CALL_END"
    }
}
