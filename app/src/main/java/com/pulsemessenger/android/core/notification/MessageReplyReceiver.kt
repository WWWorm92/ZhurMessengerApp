package com.pulsemessenger.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.pulsemessenger.android.core.offline.OutboxQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val scope = intent.getStringExtra(EXTRA_SCOPE).orEmpty()
        val targetId = intent.getLongExtra(EXTRA_TARGET_ID, 0L)
        val chatKey = intent.getStringExtra(EXTRA_CHAT_KEY).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (reply.isBlank() || targetId <= 0L || scope !in setOf("dm", "room")) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                OutboxQueue.enqueueText(
                    context = context.applicationContext,
                    scope = scope,
                    targetId = targetId,
                    text = reply,
                )
                if (chatKey.isNotBlank()) PulseNotificationStore.clear(context, chatKey)
                if (notificationId != 0) NotificationManagerCompat.from(context).cancel(notificationId)
            }
            result.finish()
        }
    }

    companion object {
        const val KEY_TEXT_REPLY = "message_reply_text"
        const val EXTRA_SCOPE = "reply_scope"
        const val EXTRA_TARGET_ID = "reply_target_id"
        const val EXTRA_CHAT_KEY = "reply_chat_key"
        const val EXTRA_NOTIFICATION_ID = "reply_notification_id"
    }
}
