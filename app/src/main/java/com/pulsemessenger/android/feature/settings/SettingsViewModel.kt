package com.pulsemessenger.android.feature.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.pulsemessenger.android.core.network.AdminStats
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DeviceDto
import com.pulsemessenger.android.core.network.NotificationStatusResponse
import com.pulsemessenger.android.core.network.SessionDto
import com.pulsemessenger.android.core.session.DefaultTab
import com.pulsemessenger.android.core.session.ImageQuality
import com.pulsemessenger.android.core.session.LocalSettings
import com.pulsemessenger.android.core.session.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val localSettings: LocalSettings,
    private val app: Application,
) : ViewModel() {
    var sessions by mutableStateOf<List<SessionDto>>(emptyList())
    var devices by mutableStateOf<List<DeviceDto>>(emptyList())
    var notificationStatus by mutableStateOf<NotificationStatusResponse?>(null)
    var adminStats by mutableStateOf<AdminStats?>(null)
    var adminUsers by mutableStateOf<List<DialogUserDto>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var actingSessionId by mutableStateOf<String?>(null)
    var actingDeviceId by mutableStateOf<Long?>(null)

    var isUnsubscribing by mutableStateOf(false)
    var isTestingNotification by mutableStateOf(false)
    var testResult by mutableStateOf<String?>(null)

    var cacheSize by mutableStateOf("...")
    var isClearingCache by mutableStateOf(false)

    var themeMode by mutableStateOf(localSettings.themeMode)
    var defaultTab by mutableStateOf(localSettings.defaultTab)
    var notifSound by mutableStateOf(localSettings.notificationSound)
    var notifVibration by mutableStateOf(localSettings.notificationVibration)
    var imageQuality by mutableStateOf(localSettings.imageQuality)
    var adminUsername by mutableStateOf("")
    var adminDisplayName by mutableStateOf("")
    var adminPassword by mutableStateOf("")
    var adminCreateIsAdmin by mutableStateOf(false)
    var adminActingUserId by mutableStateOf<Long?>(null)

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            val sessionsResult = repository.loadSessions()
            val devicesResult = repository.loadDevices()
            val notifResult = repository.loadNotificationStatus()
            sessionsResult.onSuccess { sessions = it }
                .onFailure { error = it.message ?: "Failed to load sessions" }
            devicesResult.onSuccess { devices = it }
                .onFailure { if (error == null) error = it.message ?: "Failed to load devices" }
            notifResult.onSuccess { notificationStatus = it }
                .onFailure { if (error == null) error = it.message ?: "Failed to load notification status" }
            adminStats = repository.loadAdminOverview().getOrNull()?.stats
            adminUsers = repository.loadAdminUsers().getOrNull().orEmpty()
            computeCacheSize()
            isLoading = false
        }
    }

    fun createAdminUser() {
        if (adminUsername.isBlank() || adminDisplayName.isBlank() || adminPassword.length < 6) {
            error = "Заполните username, имя и пароль не короче 6 символов"
            return
        }
        error = null
        viewModelScope.launch {
            repository.createAdminUser(adminUsername.trim(), adminPassword, adminDisplayName.trim(), adminCreateIsAdmin)
                .onSuccess {
                    adminUsers = adminUsers + it
                    adminUsername = ""
                    adminDisplayName = ""
                    adminPassword = ""
                    adminCreateIsAdmin = false
                    adminStats = repository.loadAdminOverview().getOrNull()?.stats
                }
                .onFailure { error = it.message ?: "Failed to create user" }
        }
    }

    fun toggleAdminRole(user: DialogUserDto) {
        adminActingUserId = user.id
        error = null
        viewModelScope.launch {
            repository.updateAdminUserRole(user.id, !user.isAdmin)
                .onSuccess {
                    adminUsers = adminUsers.map { if (it.id == user.id) it.copy(isAdmin = !user.isAdmin) else it }
                }
                .onFailure { error = it.message ?: "Failed to update role" }
            adminActingUserId = null
        }
    }

    fun resetAdminPassword(userId: Long, newPassword: String) {
        if (newPassword.length < 6) {
            error = "Пароль должен быть не короче 6 символов"
            return
        }
        adminActingUserId = userId
        error = null
        viewModelScope.launch {
            repository.updateAdminUserPassword(userId, newPassword)
                .onFailure { error = it.message ?: "Failed to update password" }
            adminActingUserId = null
        }
    }

    fun deleteAdminUser(userId: Long) {
        adminActingUserId = userId
        error = null
        viewModelScope.launch {
            repository.deleteAdminUser(userId)
                .onSuccess {
                    adminUsers = adminUsers.filterNot { it.id == userId }
                    adminStats = repository.loadAdminOverview().getOrNull()?.stats
                }
                .onFailure { error = it.message ?: "Failed to delete user" }
            adminActingUserId = null
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        localSettings.themeMode = mode
    }

    fun updateDefaultTab(tab: DefaultTab) {
        defaultTab = tab
        localSettings.defaultTab = tab
    }

    fun updateNotifSound(enabled: Boolean) {
        notifSound = enabled
        localSettings.notificationSound = enabled
    }

    fun updateNotifVibration(enabled: Boolean) {
        notifVibration = enabled
        localSettings.notificationVibration = enabled
    }

    fun updateImageQuality(quality: ImageQuality) {
        imageQuality = quality
        localSettings.imageQuality = quality
    }

    private suspend fun computeCacheSize() {
        cacheSize = try {
            val cacheDir = app.cacheDir.resolve("image_cache")
            val size = withContext(Dispatchers.IO) { directorySize(cacheDir) }
            formatSize(size)
        } catch (_: Exception) { "0 B" }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        if (isClearingCache) return
        isClearingCache = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.imageLoader.diskCache?.clear()
                    val cacheDir = app.cacheDir.resolve("image_cache")
                    cacheDir.deleteRecursively()
                }
                cacheSize = "0 B"
            } catch (_: Exception) { error = "Не удалось очистить кэш" }
            isClearingCache = false
        }
    }

    fun unsubscribeAll() {
        if (isUnsubscribing) return
        isUnsubscribing = true
        error = null
        viewModelScope.launch {
            repository.unsubscribeAll()
                .onSuccess { notificationStatus = notificationStatus?.copy(subscriptions = 0) }
                .onFailure { error = it.message ?: "Failed to unsubscribe" }
            isUnsubscribing = false
        }
    }

    fun testNotification() {
        if (isTestingNotification) return
        isTestingNotification = true
        testResult = null
        error = null
        viewModelScope.launch {
            repository.testNotification()
                .onSuccess { testResult = "Тестовое уведомление отправлено!" }
                .onFailure { error = it.message ?: "Failed to send test notification" }
            isTestingNotification = false
        }
    }

    fun revokeSession(sessionId: String, onRevokedCurrent: () -> Unit) {
        actingSessionId = sessionId
        error = null
        viewModelScope.launch {
            repository.revokeSession(sessionId)
                .onSuccess { revokedCurrent ->
                    sessions = sessions.filterNot { it.id == sessionId }
                    if (revokedCurrent) onRevokedCurrent()
                }
                .onFailure { error = it.message ?: "Failed to revoke session" }
            actingSessionId = null
        }
    }

    fun revokeDevice(deviceId: Long, onRevokedCurrent: () -> Unit) {
        actingDeviceId = deviceId
        error = null
        viewModelScope.launch {
            repository.revokeDevice(deviceId)
                .onSuccess { revokedCurrent ->
                    devices = devices.filterNot { it.id == deviceId }
                    if (revokedCurrent) onRevokedCurrent()
                }
                .onFailure { error = it.message ?: "Failed to revoke device" }
            actingDeviceId = null
        }
    }

    private fun directorySize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) directorySize(file) else file.length()
            }
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024f / 1024f)
            else -> String.format("%.1f GB", bytes / 1024f / 1024f / 1024f)
        }
    }
}
