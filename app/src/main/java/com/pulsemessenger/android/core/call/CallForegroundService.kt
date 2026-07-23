package com.pulsemessenger.android.core.call

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.notification.PulseNotificationStore

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Pulse" }
        val status = intent?.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "Звонок активен" }

        CallNotificationHelper.createActiveCallChannelIfNeeded(this)

        startForeground(
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID,
            buildNotification(peerName, status),
        )

        return START_STICKY
    }

    private fun buildNotification(peerName: String, status: String): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CALL_SHOW_CURRENT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val endIntent = Intent(this, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_END
        }

        val openPendingIntent = android.app.PendingIntent.getActivity(
            this,
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val endPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID + 1,
            endIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, PulseNotificationStore.ACTIVE_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(largeIcon)
            .setContentTitle("Звонок с $peerName")
            .setContentText(status)
            .setSubText("Zhuravlik")
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVibrate(null)
            .addAction(R.mipmap.ic_launcher_round, "Завершить", endPendingIntent)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val ACTION_START = "com.pulsemessenger.android.action.CALL_FOREGROUND_START"
        private const val ACTION_STOP = "com.pulsemessenger.android.action.CALL_FOREGROUND_STOP"
        private const val EXTRA_PEER_NAME = "peer_name"
        private const val EXTRA_STATUS = "status"

        fun start(context: Context, peerName: String, status: String) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, CallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_STATUS, status)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, CallForegroundService::class.java))
            NotificationManagerCompat.from(appContext).cancel(PulseNotificationStore.ACTIVE_CALL_NOTIFICATION_ID)
        }
    }
}
