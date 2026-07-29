package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class CallAudioRouteController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            applyConfiguredRoute(appContext)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            applyConfiguredRoute(appContext)
        }
    }

    private val monitor = object : Runnable {
        override fun run() {
            if (!started) return
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                applyConfiguredRoute(appContext)
            }
            handler.postDelayed(this, 700L)
        }
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        handler.post(monitor)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(monitor)
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
    }

    data class Route(
        val id: String,
        val label: String,
        val selected: Boolean,
    )

    companion object {
        private const val PREFS = "call_audio_route"
        private const val KEY_ROUTE = "route"
        const val ROUTE_AUTO = "auto"

        fun availableRoutes(context: Context): List<Route> {
            val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val selectedId = selectedRouteId(context)
            val routes = mutableListOf(Route(ROUTE_AUTO, "Автоматически", selectedId == ROUTE_AUTO))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.availableCommunicationDevices
                    .distinctBy { it.id }
                    .forEach { device ->
                        val id = "device:${device.id}"
                        routes += Route(id, labelFor(device), selectedId == id)
                    }
            } else {
                val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                routes += Route("legacy:earpiece", "Телефон", selectedId == "legacy:earpiece")
                routes += Route("legacy:speaker", "Громкая связь", selectedId == "legacy:speaker")
                if (outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
                    routes += Route("legacy:bluetooth", "Bluetooth", selectedId == "legacy:bluetooth")
                }
                if (outputs.any { it.type in wiredTypes }) {
                    routes += Route("legacy:wired", "Проводная / USB-гарнитура", selectedId == "legacy:wired")
                }
            }
            return routes.distinctBy { it.id }
        }

        fun selectRoute(context: Context, routeId: String): Boolean {
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ROUTE, routeId.ifBlank { ROUTE_AUTO }).apply()
            return applyConfiguredRoute(appContext, force = true)
        }

        fun selectedRouteId(context: Context): String {
            return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROUTE, ROUTE_AUTO).orEmpty().ifBlank { ROUTE_AUTO }
        }

        fun applyConfiguredRoute(context: Context, force: Boolean = false): Boolean {
            val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!force && manager.mode != AudioManager.MODE_IN_COMMUNICATION) return false
            val selected = selectedRouteId(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (selected == ROUTE_AUTO) {
                    if (manager.isSpeakerphoneOn) return true
                    val devices = manager.availableCommunicationDevices
                    val best = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_HEARING_AID }
                        ?: devices.firstOrNull { it.type in wiredTypes }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    return if (best != null) runCatching { manager.setCommunicationDevice(best) }.getOrDefault(false) else false
                }
                val deviceId = selected.removePrefix("device:").toIntOrNull() ?: return false
                val device = manager.availableCommunicationDevices.firstOrNull { it.id == deviceId } ?: return false
                return runCatching { manager.setCommunicationDevice(device) }.getOrDefault(false)
            }

            @Suppress("DEPRECATION")
            when (selected) {
                "legacy:speaker" -> {
                    runCatching { manager.stopBluetoothSco() }
                    manager.isBluetoothScoOn = false
                    manager.isSpeakerphoneOn = true
                }
                "legacy:bluetooth" -> {
                    manager.isSpeakerphoneOn = false
                    runCatching { manager.startBluetoothSco() }
                    manager.isBluetoothScoOn = true
                }
                "legacy:earpiece", "legacy:wired" -> {
                    runCatching { manager.stopBluetoothSco() }
                    manager.isBluetoothScoOn = false
                    manager.isSpeakerphoneOn = false
                }
                else -> {
                    if (!manager.isSpeakerphoneOn) {
                        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        if (outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
                            runCatching { manager.startBluetoothSco() }
                            manager.isBluetoothScoOn = true
                        }
                    }
                }
            }
            return true
        }

        private val wiredTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )

        private fun labelFor(device: AudioDeviceInfo): String = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Телефон"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Громкая связь"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Слуховой аппарат"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводная гарнитура"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-гарнитура"
            else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Аудиоустройство"
        }
    }
}
