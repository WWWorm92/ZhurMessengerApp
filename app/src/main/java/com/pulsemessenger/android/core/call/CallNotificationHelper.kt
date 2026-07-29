package com.pulsemessenger.android.core.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.notification.PulseNotificationStore

object CallNotificationHelper {
    private val INCOMING_CALL_VIBRATION = longArrayOf(
        0L,
        750L,
        450L,
        750L,
        1_100L,
    )

    fun showIncomingCall(
        context: Context,
        callId: String,
        fromUserId: Long,
        fromName: String,
        fromAvatarUrl: String = "",
    ) {
        if (callId.isBlank() || fromUserId <= 0L) return
        if (ResolvedCallStore.isResolved(context, callId)) return

        createCallChannelIfNeeded(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = PulseNotificationStore.callNotificationId(callId)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CALL_OPEN
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putCallExtras(callId, fromUserId, fromName, fromAvatarUrl)
        }

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_CALL_ACCEPT
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putCallExtras(callId, fromUserId, fromName, fromAvatarUrl)
        }

        val rejectIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_REJECT
            putCallExtras(callId, fromUserId, fromName, fromAvatarUrl)
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // A direct activity PendingIntent is user initiated and is not blocked
        // like startActivity() called later from a background BroadcastReceiver.
        val acceptPendingIntent = PendingIntent.getActivity(
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

        val caller = Person.Builder()
            .setName(fromName.ifBlank { "Pulse" })
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            caller,
            rejectPendingIntent,
            acceptPendingIntent,
        )

        val notification = NotificationCompat.Builder(
            context,
            PulseNotificationStore.CALL_CHANNEL_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Входящий звонок")
            .setContentText("$fromName звонит")
            .setSubText("Zhuravlik")
            .setStyle(callStyle)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF55B6FF.toInt())
            .setColorized(true)
            .setVibrate(INCOMING_CALL_VIBRATION)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(60_000L)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        IncomingCallAlert.start(context.applicationContext, callId)
    }

    fun cancelCallNotification(context: Context, callId: String) {
        if (callId.isBlank()) return
        ResolvedCallStore.markResolved(context.applicationContext, callId)
        IncomingCallAlert.stop(context.applicationContext, callId)
        NotificationManagerCompat.from(context)
            .cancel(PulseNotificationStore.callNotificationId(callId))
    }

    fun createCallChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            PulseNotificationStore.CALL_CHANNEL_ID,
            "Входящие звонки",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о входящих звонках"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = INCOMING_CALL_VIBRATION
            setSound(ringtoneUri, audioAttributes)
        }

        manager.createNotificationChannel(channel)
    }

    fun createActiveCallChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            PulseNotificationStore.ACTIVE_CALL_CHANNEL_ID,
            "Активный звонок",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Текущее аудиосоединение Zhuravlik"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    private fun Intent.putCallExtras(
        callId: String,
        peerUserId: Long,
        peerName: String,
        peerAvatarUrl: String,
    ) {
        putExtra(MainActivity.EXTRA_CALL_ID, callId)
        putExtra(MainActivity.EXTRA_PEER_USER_ID, peerUserId)
        putExtra(MainActivity.EXTRA_PEER_NAME, peerName)
        putExtra(MainActivity.EXTRA_PEER_AVATAR_URL, peerAvatarUrl)
    }
}
