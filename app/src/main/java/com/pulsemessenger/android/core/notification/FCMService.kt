package com.pulsemessenger.android.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.call.CallNotificationHelper
import com.pulsemessenger.android.core.call.ResolvedCallStore

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = PulseApp.instance
        app.pushManager.onTokenRefreshed(
            token,
            app.networkProvider,
            app.sessionStore,
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        when (data["type"]) {
            "call" -> {
                showIncomingCallNotification(data)
                return
            }

            "call_resolved" -> {
                resolveIncomingCall(data)
                return
            }

            "message" -> {
                showGroupedMessageNotification(data)
                return
            }
        }

        val title =
            message.notification?.title ?: data["title"] ?: "Zhuravlik"
        val body =
            message.notification?.body ?: data["body"] ?: ""
        val url = data["url"] ?: "/"

        showFallbackNotification(title, body, url)
    }

    private fun resolveIncomingCall(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        if (callId.isBlank()) return

        ResolvedCallStore.markResolved(this, callId)
        CallNotificationHelper.cancelCallNotification(this, callId)

        Log.d(
            "CALL_NOTIFICATION",
            "Resolved incoming call from push callId=$callId reason=${data["reason"].orEmpty()}",
        )
    }

    private fun showIncomingCallNotification(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        val fromUserId =
            data["fromUserId"]?.toLongOrNull() ?: return

        if (ResolvedCallStore.isResolved(this, callId)) {
            Log.d(
                "CALL_NOTIFICATION",
                "Ignoring late incoming-call push callId=$callId",
            )
            return
        }

        val fromName = data["fromName"]
            .orEmpty()
            .ifBlank { data["senderName"].orEmpty() }
            .ifBlank { "Pulse" }

        val fromAvatarUrl = data["fromAvatarUrl"]
            .orEmpty()
            .ifBlank { data["avatarUrl"].orEmpty() }

        CallNotificationHelper.showIncomingCall(
            context = this,
            callId = callId,
            fromUserId = fromUserId,
            fromName = fromName,
            fromAvatarUrl = fromAvatarUrl,
        )
    }

    private fun showGroupedMessageNotification(
        data: Map<String, String>,
    ) {
        val scope = data["scope"].orEmpty()
        val targetId =
            data["targetId"]?.toLongOrNull() ?: return
        val chatKey =
            data["chatKey"].takeUnless { it.isNullOrBlank() }
                ?: "$scope:$targetId"

        val title =
            data["title"].orEmpty().ifBlank { "Zhuravlik" }
        val senderName = data["senderName"].orEmpty()
        val body =
            data["body"].orEmpty().ifBlank { "Новое сообщение" }
        val url = data["url"] ?: "/"

        val line =
            if (scope == "room" && senderName.isNotBlank()) {
                "$senderName: $body"
            } else {
                body
            }

        val lines = PulseNotificationStore.addMessage(
            context = this,
            chatKey = chatKey,
            line = line,
            maxMessages = 5,
        )

        showNotification(
            notificationId =
                PulseNotificationStore.notificationId(chatKey),
            title = title,
            body = lines.lastOrNull().orEmpty(),
            url = url,
            lines = lines,
            scope = scope,
            targetId = targetId,
            chatKey = chatKey,
        )
    }

    private fun showFallbackNotification(
        title: String,
        body: String,
        url: String,
    ) {
        showNotification(
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body,
            url = url,
            lines = listOf(body).filter { it.isNotBlank() },
            scope = "",
            targetId = 0L,
            chatKey = "",
        )
    }

    private fun showNotification(
        notificationId: Int,
        title: String,
        body: String,
        url: String,
        lines: List<String>,
        scope: String,
        targetId: Long,
        chatKey: String,
    ) {
        createChannelIfNeeded()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "PUSH",
                "POST_NOTIFICATIONS permission is not granted",
            )
            return
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("url", url)
        }

        val openPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        val readPendingIntent =
            if (
                scope.isNotBlank() &&
                targetId > 0 &&
                chatKey.isNotBlank()
            ) {
                val readIntent =
                    Intent(this, NotificationReadReceiver::class.java).apply {
                        action =
                            NotificationReadReceiver.ACTION_MARK_READ
                        putExtra(
                            NotificationReadReceiver.EXTRA_SCOPE,
                            scope,
                        )
                        putExtra(
                            NotificationReadReceiver.EXTRA_TARGET_ID,
                            targetId,
                        )
                        putExtra(
                            NotificationReadReceiver.EXTRA_CHAT_KEY,
                            chatKey,
                        )
                    }

                PendingIntent.getBroadcast(
                    this,
                    notificationId + 100_000,
                    readIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                null
            }

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.mipmap.ic_launcher,
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)

        lines.takeLast(5).forEach(inboxStyle::addLine)

        if (lines.size > 1) {
            inboxStyle.setSummaryText(
                "${lines.size} новых сообщений",
            )
        }

        val builder = NotificationCompat.Builder(
            this,
            PulseNotificationStore.CHANNEL_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(inboxStyle)
            .setSubText("Zhuravlik")
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFF55B6FF.toInt())
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(lines.size > 1)
            .setNumber(lines.size)
            .setGroup("pulse_messages")

        if (readPendingIntent != null) {
            builder.addAction(
                R.mipmap.ic_launcher_round,
                "Прочитать",
                readPendingIntent,
            )
        }

        NotificationManagerCompat.from(this)
            .notify(notificationId, builder.build())
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            PulseNotificationStore.CHANNEL_ID,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о новых сообщениях"
        }

        manager.createNotificationChannel(channel)
    }
}
