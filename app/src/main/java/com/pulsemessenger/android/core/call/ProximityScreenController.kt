package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

class ProximityScreenController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val proximityWakeLock =
        if (
            powerManager.isWakeLockLevelSupported(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            )
        ) {
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Zhuravlik:ProximityCall",
            ).apply {
                setReferenceCounted(false)
            }
        } else {
            null
        }

    private var running = false

    private val monitor = object : Runnable {
        override fun run() {
            if (!running) return

            val shouldUseProximity =
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION &&
                    !audioManager.isSpeakerphoneOn

            if (shouldUseProximity) {
                acquire()
            } else {
                release()
            }

            handler.postDelayed(this, 250L)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(monitor)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(monitor)
        release()
    }

    private fun acquire() {
        val lock = proximityWakeLock ?: return
        if (!lock.isHeld) {
            runCatching { lock.acquire() }
        }
    }

    private fun release() {
        val lock = proximityWakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
        }
    }
}
