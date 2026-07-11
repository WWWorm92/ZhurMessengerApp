package com.pulsemessenger.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.pulsemessenger.android.core.notification.FCMService
import com.pulsemessenger.android.core.notification.PushManager
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore

class PulseApp : Application() {
    lateinit var networkProvider: NetworkProvider
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var pushManager: PushManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStore = SessionStore(this)
        networkProvider = NetworkProvider(this, sessionStore)
        pushManager = PushManager()
        createNotificationChannel()
        FirebaseApp.initializeApp(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FCMService.CHANNEL_ID,
                "Zhuravlik",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Сообщения Zhuravlik" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: PulseApp
            private set
    }
}
