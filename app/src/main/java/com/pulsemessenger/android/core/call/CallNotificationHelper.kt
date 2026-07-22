package com.pulsemessenger.android.core.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.notification.PulseNotificationStore

object CallNotificationHelper {

    fun showIncomingCall(
        context: Context,
        callId: String,
        fromUserId: Long,
        fromName: String,
    ) {
        if (callId.isBlank() || fromUserId <= 0L) return

        createCallChannelIfNeeded(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = PulseNotificationStore.callNotificationId(callId)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CALL_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_PEER_USER_ID, fromUserId)
            putExtra(MainActivity.EXTRA_PEER_NAME, fromName)
        }

        val acceptIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_ACCEPT
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_PEER_USER_ID, fromUserId)
            putExtra(MainActivity.EXTRA_PEER_NAME, fromName)
        }

        val rejectIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_REJECT
            putExtra(MainActivity.EXTRA_CALL_ID, callId)
            putExtra(MainActivity.EXTRA_PEER_USER_ID, fromUserId)
            putExtra(MainActivity.EXTRA_PEER_NAME, fromName)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(context, PulseNotificationStore.CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(largeIcon)
            .setContentTitle("Входящий звонок")
            .setContentText("$fromName звонит")
            .setSubText("Zhuravlik")
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(60_000L)
            .addAction(R.mipmap.ic_launcher_round, "Отклонить", rejectPendingIntent)
            .addAction(R.mipmap.ic_launcher_round, "Принять", acceptPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun cancelCallNotification(context: Context, callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(context).cancel(PulseNotificationStore.callNotificationId(callId))
    }

    fun createCallChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PulseNotificationStore.CALL_CHANNEL_ID,
            "Звонки",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о входящих и активных звонках"
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }
}
