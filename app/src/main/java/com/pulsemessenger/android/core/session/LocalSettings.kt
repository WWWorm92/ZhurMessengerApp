package com.pulsemessenger.android.core.session

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { System, Dark, Light }
enum class DefaultTab { Dialogs, Rooms }
enum class ImageQuality { AlwaysHD, WiFiOnly, SaveData }

class LocalSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.System }
        set(value) { prefs.edit().putInt(KEY_THEME, value.ordinal).apply() }

    var defaultTab: DefaultTab
        get() = DefaultTab.entries.getOrElse(prefs.getInt(KEY_DEFAULT_TAB, 0)) { DefaultTab.Dialogs }
        set(value) { prefs.edit().putInt(KEY_DEFAULT_TAB, value.ordinal).apply() }

    var notificationSound: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_SOUND, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIF_SOUND, value).apply() }

    var notificationVibration: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_VIBRATION, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIF_VIBRATION, value).apply() }

    var imageQuality: ImageQuality
        get() = ImageQuality.entries.getOrElse(prefs.getInt(KEY_IMAGE_QUALITY, 0)) { ImageQuality.AlwaysHD }
        set(value) { prefs.edit().putInt(KEY_IMAGE_QUALITY, value.ordinal).apply() }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DEFAULT_TAB = "default_tab"
        private const val KEY_NOTIF_SOUND = "notif_sound"
        private const val KEY_NOTIF_VIBRATION = "notif_vibration"
        private const val KEY_IMAGE_QUALITY = "image_quality"
    }
}
