package com.pulsemessenger.android.core.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.notification.PulseNotificationStore

class CallForegroundService : Service() {
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private lateinit var audioRouteController: CallAudioRouteController

    override fun onCreate() {
        super.onCreate()
        audioRouteController = CallAudioRouteController(this)
        audioRouteController.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val peerName = intent
            ?.getStringExtra(EXTRA_PEER_NAME)
            .orEmpty()
            .ifBlank { "Zhuravlik" }

        val status = intent
            ?.getStringExtra(EXTRA_STATUS)
            .orEmpty()
            .ifBlank { "Звонок активен" }

        CallNotificationHelper.createActiveCallChannelIfNeeded(this)

        return try {
            startForeground(
                PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID,
                buildNotification(peerName, status),
            )
            acquireCpuWakeLock()
            running = true
            START_STICKY
        } catch (error: SecurityException) {
            // Android 14 rejects a microphone FGS created after the app has
            // already entered the background. Never let this kill the app.
            Log.e(
                TAG,
                "Microphone foreground service was started too late",
                error,
            )
            releaseCpuWakeLock()
            running = false
            stopSelf()
            START_NOT_STICKY
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to start call foreground service", error)
            releaseCpuWakeLock()
            running = false
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        if (::audioRouteController.isInitialized) {
            audioRouteController.stop()
        }
        releaseCpuWakeLock()
        running = false
        super.onDestroy()
    }

    private fun buildNotification(
        peerName: String,
        status: String,
    ): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CALL_SHOW_CURRENT
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val endIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_END
        }

        val openPendingIntent = android.app.PendingIntent.getActivity(
            this,
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val endPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID + 1,
            endIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val audioRoutePendingIntent = android.app.PendingIntent.getActivity(
            this,
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID + 2,
            Intent(this, CallAudioRouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.mipmap.ic_launcher,
        )

        val caller = Person.Builder()
            .setName(peerName)
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forOngoingCall(
            caller,
            endPendingIntent,
        )

        return NotificationCompat.Builder(
            this,
            PulseNotificationStore.ACTIVE_CALL_CHANNEL_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(largeIcon)
            .setContentTitle("Текущий звонок")
            .setContentText("$peerName · $status")
            .setSubText("Zhuravlik")
            .setStyle(callStyle)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE,
            )
            .setColor(0xFF55B6FF.toInt())
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVibrate(null)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .addAction(
                R.mipmap.ic_launcher_round,
                "Аудио",
                audioRoutePendingIntent,
            )
            .build()
    }

    private fun acquireCpuWakeLock() {
        val existing = cpuWakeLock
        if (existing?.isHeld == true) return

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as PowerManager

        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Zhuravlik:ActiveCallCpu",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        val lock = cpuWakeLock
        if (lock?.isHeld == true) {
            runCatching { lock.release() }
        }
        cpuWakeLock = null
    }

    private fun stopForegroundCompat() {
        releaseCpuWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "CALL_FOREGROUND"

        private const val ACTION_START =
            "com.pulsemessenger.android.action.CALL_FOREGROUND_START"
        private const val ACTION_STOP =
            "com.pulsemessenger.android.action.CALL_FOREGROUND_STOP"
        private const val EXTRA_PEER_NAME = "peer_name"
        private const val EXTRA_STATUS = "status"

        @Volatile
        private var running = false

        fun start(
            context: Context,
            peerName: String,
            status: String,
        ) {
            val appContext = context.applicationContext
            val intent = Intent(
                appContext,
                CallForegroundService::class.java,
            ).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_STATUS, status)
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to request call foreground service", error)
                running = false
            }
        }

        fun startIfNeeded(
            context: Context,
            peerName: String = "Zhuravlik",
            status: String = "Звонок активен",
        ) {
            if (!running) {
                start(context, peerName, status)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            val audioManager =
                appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

            // PulseAndroidApp previously stopped the service whenever the UI
            // was foregrounded. Keep it alive until WebRTC actually leaves
            // MODE_IN_COMMUNICATION.
            if (audioManager.mode == android.media.AudioManager.MODE_IN_COMMUNICATION) {
                return
            }

            running = false
            appContext.stopService(
                Intent(appContext, CallForegroundService::class.java),
            )
            NotificationManagerCompat.from(appContext)
                .cancel(PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID)
        }
    }
}
