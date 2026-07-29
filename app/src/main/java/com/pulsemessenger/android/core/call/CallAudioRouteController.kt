package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class CallAudioRouteController(context: Context) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = chooseBestRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = chooseBestRoute()
    }

    private val monitor = object : Runnable {
        override fun run() {
            if (!started) return
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) chooseBestRoute()
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

    private fun chooseBestRoute() {
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION || audioManager.isSpeakerphoneOn) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val best = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } 
                ?: devices.firstOrNull { Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            if (best != null && audioManager.communicationDevice?.id != best.id) {
                runCatching { audioManager.setCommunicationDevice(best) }
            }
        } else {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasBluetooth = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            @Suppress("DEPRECATION")
            if (hasBluetooth && !audioManager.isBluetoothScoOn) {
                runCatching { audioManager.startBluetoothSco() }
                audioManager.isBluetoothScoOn = true
            }
            audioManager.isSpeakerphoneOn = false
        }
    }
}
