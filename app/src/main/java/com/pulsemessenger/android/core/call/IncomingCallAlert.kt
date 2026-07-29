package com.pulsemessenger.android.core.call

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object IncomingCallAlert {
    private const val MAX_RING_TIME_MS = 60_000L

    private val pattern = longArrayOf(
        0L,
        750L,
        450L,
        750L,
        1_100L,
    )

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeCallId: String = ""

    @Volatile
    private var vibrator: Vibrator? = null

    fun start(context: Context, callId: String) {
        if (callId.isBlank()) return
        if (activeCallId == callId) return

        stop(context)

        activeCallId = callId
        vibrator = resolveVibrator(context.applicationContext)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        pattern,
                        0,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }

        handler.postDelayed(
            {
                if (activeCallId == callId) {
                    stop(context.applicationContext, callId)
                }
            },
            MAX_RING_TIME_MS,
        )
    }

    fun stop(context: Context, callId: String? = null) {
        if (!callId.isNullOrBlank() && activeCallId.isNotBlank() && activeCallId != callId) {
            return
        }

        runCatching {
            (vibrator ?: resolveVibrator(context.applicationContext)).cancel()
        }

        activeCallId = ""
        vibrator = null
    }

    private fun resolveVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
