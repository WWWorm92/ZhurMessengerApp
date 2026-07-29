package com.pulsemessenger.android.core.call

import android.content.Context
import androidx.core.content.edit

object ResolvedCallStore {
    private const val PREFS_NAME = "pulse_resolved_calls"
    private const val KEEP_MS = 24L * 60L * 60L * 1000L

    fun markResolved(context: Context, callId: String) {
        if (callId.isBlank()) return

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(callId, now)
        }

        cleanup(prefs, now)
    }

    fun isResolved(context: Context, callId: String): Boolean {
        if (callId.isBlank()) return false

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val resolvedAt = prefs.getLong(callId, 0L)
        if (resolvedAt <= 0L) return false

        if (System.currentTimeMillis() - resolvedAt > KEEP_MS) {
            prefs.edit { remove(callId) }
            return false
        }

        return true
    }

    private fun cleanup(
        prefs: android.content.SharedPreferences,
        now: Long,
    ) {
        val staleKeys = prefs.all
            .mapNotNull { (key, value) ->
                val timestamp = value as? Long ?: return@mapNotNull key
                key.takeIf { now - timestamp > KEEP_MS }
            }

        if (staleKeys.isNotEmpty()) {
            prefs.edit {
                staleKeys.forEach(::remove)
            }
        }
    }
}
