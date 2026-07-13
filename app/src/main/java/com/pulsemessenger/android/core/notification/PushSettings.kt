package com.pulsemessenger.android.core.notification

import android.content.Context
import androidx.core.content.edit

class PushSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pulse_push_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) {
            prefs.edit {
                putBoolean("enabled", value)
            }
        }
}