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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.call.CallNotificationHelper
import com.pulsemessenger.android.core.call.ResolvedCallStore
import com.pulsemessenger.android.core.share.ConversationShortcutPublisher

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

        if (ActiveChatTracker.shouldSuppressNotification(scope, targetId)) {
            PulseNotificationStore.clear(this, chatKey)
            Log.d("PUSH", "Notification suppressed for active chat $chatKey")
            return
        }

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
            senderName = senderName,
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
            senderName = "",
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
        senderName: String,
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

        val bubblePendingIntent = PendingIntent.getActivity(
            this,
            notificationId + 300_000,
            Intent(openIntent),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
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

        val replyPendingIntent = if (scope in setOf("dm", "room") && targetId > 0L) {
            val replyIntent = Intent(this, MessageReplyReceiver::class.java).apply {
                putExtra(MessageReplyReceiver.EXTRA_SCOPE, scope)
                putExtra(MessageReplyReceiver.EXTRA_TARGET_ID, targetId)
                putExtra(MessageReplyReceiver.EXTRA_CHAT_KEY, chatKey)
                putExtra(MessageReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            PendingIntent.getBroadcast(
                this,
                notificationId + 200_000,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        } else null

        val remoteInput = RemoteInput.Builder(MessageReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Ответить")
            .build()

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.mipmap.ic_launcher,
        )

        val me = Person.Builder()
            .setName("Вы")
            .setKey("zhuravlik-me")
            .build()
        val peer = Person.Builder()
            .setName(senderName.ifBlank { title.ifBlank { "Собеседник" } })
            .setKey("$scope:$targetId")
            .setImportant(true)
            .build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (scope == "room") title else null)
            .setGroupConversation(scope == "room")
        lines.takeLast(8).forEach { line ->
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    line,
                    System.currentTimeMillis(),
                    peer,
                )
            )
        }

        if (scope in setOf("dm", "room") && targetId > 0L) {
            ConversationShortcutPublisher.ensureNotificationShortcut(
                context = this,
                scope = scope,
                targetId = targetId,
                title = title,
                senderName = senderName,
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
            .setStyle(messagingStyle)
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

        if (scope in setOf("dm", "room") && targetId > 0L) {
            builder
                .setShortcutId(ConversationShortcutPublisher.shortcutId(scope, targetId))
                .setLocusId(LocusIdCompat(chatKey.ifBlank { "$scope:$targetId" }))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setBubbleMetadata(
                    NotificationCompat.BubbleMetadata.Builder(
                        bubblePendingIntent,
                        IconCompat.createWithResource(this, R.mipmap.ic_launcher_round),
                    )
                        .setDesiredHeight(640)
                        .setAutoExpandBubble(false)
                        .setSuppressNotification(false)
                        .build()
                )
            }
        }

        if (replyPendingIntent != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher_round,
                    "Ответить",
                    replyPendingIntent,
                )
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build()
            )
        }

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
