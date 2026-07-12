package com.pulsemessenger.android.core.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import org.json.JSONArray

object PulseNotificationStore {
    private const val PREFS_NAME = "pulse_push_notifications"
    const val CHANNEL_ID = "pulse_messages"

    fun notificationId(chatKey: String): Int {
        return chatKey.hashCode()
    }

    fun addMessage(
        context: Context,
        chatKey: String,
        line: String,
        maxMessages: Int = 5,
    ): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(chatKey, "[]").orEmpty()

        val previous = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val lines = mutableListOf<String>()

        for (i in 0 until previous.length()) {
            val value = previous.optString(i)
            if (value.isNotBlank()) {
                lines += value
            }
        }

        val next = (lines + line).takeLast(maxMessages)

        val array = JSONArray()
        next.forEach { array.put(it) }

        prefs.edit {
            putString(chatKey, array.toString())
        }

        return next
    }

    fun clear(context: Context, chatKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(chatKey)
            }

        NotificationManagerCompat.from(context).cancel(notificationId(chatKey))
    }
}