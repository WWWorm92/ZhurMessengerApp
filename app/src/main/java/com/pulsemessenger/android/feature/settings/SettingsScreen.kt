package com.pulsemessenger.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.BuildConfig
import com.pulsemessenger.android.core.session.DefaultTab
import com.pulsemessenger.android.core.session.ImageQuality
import com.pulsemessenger.android.core.session.ThemeMode
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.formatLastSeen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.pulsemessenger.android.PulseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    currentUserName: String,
    currentUserSubtitle: String,
    currentUserAvatarUrl: String,
    isAdmin: Boolean,
    onThemeChanged: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogoutCurrent: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (viewModel.isLoading && viewModel.sessions.isEmpty() && viewModel.devices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    SectionHeader("Аккаунт")
                }

                item {
                    AccountCard(
                        displayName = currentUserName,
                        subtitle = currentUserSubtitle,
                        avatarUrl = currentUserAvatarUrl,
                        onClick = onOpenProfile,
                    )
                }

                item {
                    SectionHeader("Внешний вид")
                }

                item {
                    AppearanceCard(
                        themeMode = viewModel.themeMode,
                        defaultTab = viewModel.defaultTab,
                        onThemeChange = { mode ->
                            viewModel.updateThemeMode(mode)
                            onThemeChanged(mode)
                        },
                        onDefaultTabChange = { viewModel.updateDefaultTab(it) },
                    )
                }

                item {
                    SectionHeader("Уведомления")
                }

                item {
                    NotificationCard(viewModel = viewModel)
                }

                item {
                    SectionHeader("Данные и хранилище")
                }

                item {
                    StorageCard(viewModel = viewModel)
                }

                item {
                    SectionHeader("Устройства")
                }

                item {
                    DevicesBox(
                        devices = viewModel.devices,
                        actingDeviceId = viewModel.actingDeviceId,
                        onRevoke = { deviceId ->
                            viewModel.revokeDevice(deviceId, onLogoutCurrent)
                        }
                    )
                }

                item {
                    SectionHeader("О приложении")
                }

                item {
                    AboutCard(
                        isAdmin = isAdmin,
                        adminStats = viewModel.adminStats,
                        onOpenAdmin = onOpenAdmin,
                    )
                }

                if (!viewModel.error.isNullOrBlank()) {
                    item {
                        Text(
                            text = viewModel.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun AccountCard(
    displayName: String,
    subtitle: String,
    avatarUrl: String,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(displayName, avatarUrl = avatarUrl, modifier = Modifier.size(52.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    defaultTab: DefaultTab,
    onThemeChange: (ThemeMode) -> Unit,
    onDefaultTabChange: (DefaultTab) -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTabDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsRow(
                icon = when (themeMode) {
                    ThemeMode.System -> Icons.Filled.PhoneIphone
                    ThemeMode.Dark -> Icons.Filled.DarkMode
                    ThemeMode.Light -> Icons.Filled.LightMode
                },
                title = "Тема",
                subtitle = when (themeMode) {
                    ThemeMode.System -> "Системная"
                    ThemeMode.Dark -> "Тёмная"
                    ThemeMode.Light -> "Светлая"
                },
                onClick = { showThemeDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsRow(
                icon = Icons.Filled.Tab,
                title = "Экран по умолчанию",
                subtitle = when (defaultTab) {
                    DefaultTab.Dialogs -> "Диалоги"
                    DefaultTab.Rooms -> "Комнаты"
                },
                onClick = { showTabDialog = true },
            )
        }
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "Тема",
            options = ThemeMode.entries.map { it to when (it) {
                ThemeMode.System -> "Системная"
                ThemeMode.Dark -> "Тёмная"
                ThemeMode.Light -> "Светлая"
            }},
            selected = themeMode,
            onSelect = { onThemeChange(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showTabDialog) {
        SingleChoiceDialog(
            title = "Экран по умолчанию",
            options = DefaultTab.entries.map { it to when (it) {
                DefaultTab.Dialogs -> "Диалоги"
                DefaultTab.Rooms -> "Комнаты"
            }},
            selected = defaultTab,
            onSelect = { onDefaultTabChange(it); showTabDialog = false },
            onDismiss = { showTabDialog = false },
        )
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        if (value == selected) {
                            Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NotificationCard(viewModel: SettingsViewModel) {
    val status = viewModel.notificationStatus
    val context = LocalContext.current
    val app = context.applicationContext as PulseApp
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember {
        mutableStateOf(app.pushManager.isEnabled())
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SwitchRow(
                icon = if (notificationsEnabled) {
                    Icons.Filled.NotificationsActive
                } else {
                    Icons.Filled.NotificationsOff
                },
                title = "Push-уведомления",
                subtitle = when {
                    !notificationsEnabled -> "Выключены на этом устройстве"
                    status == null -> "Загрузка..."
                    status.pushEnabled && status.subscriptions > 0 -> "Включены"
                    status.pushEnabled -> "Включены, регистрация устройства..."
                    else -> "Отключены на сервере"
                },
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    notificationsEnabled = enabled
                    app.pushManager.setEnabled(enabled)

                    if (enabled) {
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            app.pushManager.onTokenRefreshed(
                                token,
                                app.networkProvider,
                                app.sessionStore
                            )
                        }
                    } else {
                        scope.launch {
                            val token = app.sessionStore.currentToken().trim()

                            if (token.isNotBlank()) {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        app.networkProvider.api.unsubscribeAll("Bearer $token")
                                    }
                                }
                            }

                            NotificationManagerCompat.from(context).cancelAll()
                            viewModel.load()
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(modifier = Modifier.height(12.dp))
            SwitchRow(
                icon = Icons.Filled.Notifications,
                title = "Звук",
                subtitle = "Звук при получении сообщения",
                checked = viewModel.notifSound,
                onCheckedChange = { viewModel.updateNotifSound(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))
            SwitchRow(
                icon = Icons.Filled.Vibration,
                title = "Вибрация",
                subtitle = "Виброотклик при уведомлениях",
                checked = viewModel.notifVibration,
                onCheckedChange = { viewModel.updateNotifVibration(it) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun StorageCard(viewModel: SettingsViewModel) {
    var showQualityDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = "Кэш изображений",
                subtitle = viewModel.cacheSize,
                onClick = {},
                trailing = {
                    if (viewModel.isClearingCache) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.clearCache() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = "Очистить", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsRow(
                icon = when (viewModel.imageQuality) {
                    ImageQuality.AlwaysHD -> Icons.Filled.CloudSync
                    ImageQuality.WiFiOnly -> Icons.Filled.CloudSync
                    ImageQuality.SaveData -> Icons.Filled.CloudOff
                },
                title = "Качество изображений",
                subtitle = when (viewModel.imageQuality) {
                    ImageQuality.AlwaysHD -> "Всегда HD"
                    ImageQuality.WiFiOnly -> "Только по Wi-Fi"
                    ImageQuality.SaveData -> "Экономия трафика"
                },
                onClick = { showQualityDialog = true },
            )
        }
    }

    if (showQualityDialog) {
        SingleChoiceDialog(
            title = "Качество изображений",
            options = ImageQuality.entries.map { it to when (it) {
                ImageQuality.AlwaysHD -> "Всегда HD"
                ImageQuality.WiFiOnly -> "Только по Wi-Fi"
                ImageQuality.SaveData -> "Экономия трафика"
            }},
            selected = viewModel.imageQuality,
            onSelect = { viewModel.updateImageQuality(it); showQualityDialog = false },
            onDismiss = { showQualityDialog = false },
        )
    }
}

@Composable
private fun AboutCard(
    isAdmin: Boolean,
    adminStats: com.pulsemessenger.android.core.network.AdminStats?,
    onOpenAdmin: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Zhuravlik", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${BuildConfig.BASE_URL}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsRow(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = "Панель администратора",
                    subtitle = adminStats?.let { "${it.users} пользователей, ${it.rooms} комнат" } ?: "Загрузка...",
                    onClick = onOpenAdmin,
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
    ) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CurrentBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("текущее", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun DevicesBox(
    devices: List<com.pulsemessenger.android.core.network.DeviceDto>,
    actingDeviceId: Long?,
    onRevoke: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (devices.isEmpty()) {
            Text(
                text = "Нет активных устройств",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Card
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            devices.forEach { device ->
                DeviceCard(
                    device = device,
                    isActing = actingDeviceId == device.id,
                    onRevoke = { onRevoke(device.id) }
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: com.pulsemessenger.android.core.network.DeviceDto,
    isActing: Boolean,
    onRevoke: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Devices, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(device.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (device.isCurrent) CurrentBadge()
            }
            if (device.platform.isNotBlank() || device.browser.isNotBlank() || device.ip.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(device.platform, device.browser, device.ip).filter { it.isNotBlank() }.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Сессий: ${device.activeSessions}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            if (!device.isCurrent) {
                OutlinedButton(onClick = onRevoke, enabled = !isActing, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(if (isActing) "..." else "Отключить")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: com.pulsemessenger.android.core.network.SessionDto,
    isActing: Boolean,
    onRevoke: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (session.isCurrent) "Текущая сессия" else "Сессия", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (session.isCurrent) CurrentBadge()
            }
            if (session.userAgent.isNotBlank() || session.ip.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOf(session.userAgent, session.ip).filter { it.isNotBlank() }.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(if (session.isCurrent) "Текущая сессия" else formatLastSeen(session.lastSeenAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            if (!session.isCurrent) {
                OutlinedButton(onClick = onRevoke, enabled = !isActing, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(if (isActing) "..." else "Завершить")
                }
            }
        }
    }
}
