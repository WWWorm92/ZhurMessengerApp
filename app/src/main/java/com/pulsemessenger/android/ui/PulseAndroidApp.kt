package com.pulsemessenger.android.ui


import com.pulsemessenger.android.BuildConfig
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pulsemessenger.android.core.session.LocalSettings
import com.pulsemessenger.android.core.session.ThemeMode
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme
import com.pulsemessenger.android.feature.dialogs.DialogsRepository
import com.pulsemessenger.android.feature.dialogs.DialogsScreen
import com.pulsemessenger.android.feature.dialogs.DialogsViewModel
import com.pulsemessenger.android.feature.dialogs.DialogsViewModelFactory
import com.pulsemessenger.android.feature.invitations.InvitationsRepository
import com.pulsemessenger.android.feature.invitations.InvitationsScreen
import com.pulsemessenger.android.feature.invitations.InvitationsViewModel
import com.pulsemessenger.android.feature.invitations.InvitationsViewModelFactory
import com.pulsemessenger.android.feature.auth.AuthRepository
import com.pulsemessenger.android.feature.auth.AuthScreen
import com.pulsemessenger.android.feature.auth.AuthViewModel
import com.pulsemessenger.android.feature.auth.AuthViewModelFactory
import com.pulsemessenger.android.feature.chat.DmChatRepository
import com.pulsemessenger.android.feature.chat.DmChatScreen
import com.pulsemessenger.android.feature.chat.DmChatViewModel
import com.pulsemessenger.android.feature.chat.DmChatViewModelFactory
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.MeUserDto
import com.pulsemessenger.android.feature.rooms.RoomsRepository
import com.pulsemessenger.android.feature.rooms.RoomChatRepository
import com.pulsemessenger.android.feature.rooms.RoomChatScreen
import com.pulsemessenger.android.feature.rooms.RoomSettingsRepository
import com.pulsemessenger.android.feature.rooms.RoomSettingsScreen
import com.pulsemessenger.android.feature.rooms.RoomChatViewModel
import com.pulsemessenger.android.feature.rooms.RoomChatViewModelFactory
import com.pulsemessenger.android.feature.rooms.RoomsScreen
import com.pulsemessenger.android.feature.rooms.RoomsViewModel
import com.pulsemessenger.android.feature.rooms.RoomsViewModelFactory
import com.pulsemessenger.android.feature.settings.SettingsRepository
import com.pulsemessenger.android.feature.settings.AdminConsoleScreen
import com.pulsemessenger.android.feature.settings.SettingsScreen
import com.pulsemessenger.android.feature.settings.SettingsViewModel
import com.pulsemessenger.android.feature.settings.SettingsViewModelFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.pulsemessenger.android.feature.profile.ProfileRepository
import com.pulsemessenger.android.feature.profile.ProfileScreen
import com.pulsemessenger.android.feature.profile.ProfileViewModel
import com.pulsemessenger.android.feature.profile.ProfileViewModelFactory
import com.pulsemessenger.android.feature.dialogs.SearchUsersScreen
import com.pulsemessenger.android.feature.search.SearchRepository
import com.pulsemessenger.android.feature.search.SearchScreen
import com.pulsemessenger.android.feature.search.SearchViewModel
import com.pulsemessenger.android.feature.createroom.CreateRoomRepository
import com.pulsemessenger.android.feature.createroom.CreateRoomScreen
import com.pulsemessenger.android.feature.createroom.CreateRoomViewModel
import com.pulsemessenger.android.feature.createroom.CreateRoomViewModelFactory
import com.pulsemessenger.android.core.realtime.RealtimeSocketManager
import com.pulsemessenger.android.PulseApp
import androidx.compose.ui.platform.LocalContext
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.update.AppUpdateInfo
import com.pulsemessenger.android.core.update.AppUpdateManager
import com.pulsemessenger.android.ui.HomeTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private fun jwtExpiryMillis(token: String): Long? {
    return try {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val expSeconds = JSONObject(payload).optLong("exp", 0L)
        if (expSeconds <= 0L) null else expSeconds * 1000L
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PulseAndroidApp() {
    val context = LocalContext.current
    val app = PulseApp.instance
    val sessionStore = app.sessionStore
    val networkProvider = app.networkProvider
    val repository = remember {
        AuthRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val dialogsRepository = remember {
        DialogsRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val chatRepository = remember {
        DmChatRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val roomsRepository = remember {
        RoomsRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val invitationsRepository = remember {
        InvitationsRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val settingsRepository = remember {
        SettingsRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val roomChatRepository = remember {
        RoomChatRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val profileRepository = remember {
        ProfileRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val createRoomRepository = remember {
        CreateRoomRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val searchRepository = remember {
        SearchRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val roomSettingsRepository = remember {
        RoomSettingsRepository(
            networkProvider = networkProvider,
            sessionStore = sessionStore,
        )
    }
    val localSettings = remember { LocalSettings(context) }
    var appThemeMode by remember { mutableStateOf(localSettings.themeMode) }
    var realtimeConnected by remember { mutableStateOf(false) }
    val realtimeSocketManager = remember {
        RealtimeSocketManager(BuildConfig.BASE_URL)
    }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(repository))
    val dialogsViewModel: DialogsViewModel = viewModel(factory = DialogsViewModelFactory(dialogsRepository))
    val invitationsViewModel: InvitationsViewModel = viewModel(factory = InvitationsViewModelFactory(invitationsRepository))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepository, localSettings, context.applicationContext as android.app.Application))
    val chatViewModel: DmChatViewModel = viewModel(factory = DmChatViewModelFactory(chatRepository))
    val roomsViewModel: RoomsViewModel = viewModel(factory = RoomsViewModelFactory(roomsRepository))
    val roomChatViewModel: RoomChatViewModel = viewModel(factory = RoomChatViewModelFactory(roomChatRepository))
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(profileRepository))
    val createRoomViewModel: CreateRoomViewModel = viewModel(factory = CreateRoomViewModelFactory(createRoomRepository))
    val searchViewModel: SearchViewModel = viewModel(factory = com.pulsemessenger.android.feature.search.SearchViewModelFactory(searchRepository))
    var selectedDialog by remember { mutableStateOf<DialogUserDto?>(null) }
    var selectedRoom by remember { mutableStateOf<RoomDto?>(null) }
    LaunchedEffect(roomsViewModel.rooms, selectedRoom?.id) {
        val activeRoom = selectedRoom ?: return@LaunchedEffect
        val updatedRoom = roomsViewModel.rooms.firstOrNull { it.id == activeRoom.id }

        if (updatedRoom != null && updatedRoom != activeRoom) {
            selectedRoom = updatedRoom
            roomChatViewModel.openRoom(updatedRoom)
        }
    }
    var invitationsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var createRoomOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var messageSearchOpen by remember { mutableStateOf(false) }
    var roomSettingsOpen by remember { mutableStateOf(false) }
    var adminConsoleOpen by remember { mutableStateOf(false) }
    var forwardPayloads by remember { mutableStateOf<List<ForwardPayload>>(emptyList()) }
    var homeTab by remember { mutableStateOf(if (localSettings.defaultTab == com.pulsemessenger.android.core.session.DefaultTab.Rooms) HomeTab.Rooms else HomeTab.Dialogs) }
    val scope = rememberCoroutineScope()
    val sessionToken by sessionStore.token.collectAsState()
    val updateManager = remember { AppUpdateManager(context.applicationContext) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedUpdatePath by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    fun downloadUpdate(update: AppUpdateInfo) {
        if (isDownloadingUpdate) return

        scope.launch {
            isDownloadingUpdate = true
            updateDownloadProgress = null
            updateError = null

            val file = runCatching {
                updateManager.downloadApk(update) { progress ->
                    updateDownloadProgress = progress
                }
            }.onFailure { error ->
                Log.e("APP_UPDATE", "download failed", error)
            }.getOrNull()

            downloadedUpdatePath = file?.absolutePath
            isDownloadingUpdate = false

            if (file == null) {
                updateError = "Не удалось скачать APK. Нажмите, чтобы повторить."
            }

            Log.d("APP_UPDATE", "downloadedPath=$downloadedUpdatePath")
        }
    }

    fun buildDmForwardPayloads(ids: Set<Long>): List<ForwardPayload> {
        val currentPeer = selectedDialog ?: return emptyList()
        val me = authViewModel.currentUser
        return chatViewModel.messages.filter { ids.contains(it.id) && it.deletedAt == null }.mapNotNull { message ->
            val forwardedFromName = if (message.senderId == me?.id) me.displayName else currentPeer.displayName
            when {
                message.poll != null -> ForwardPayload(
                    content = buildString {
                        append(message.poll.question)
                        append("\n\n")
                        append(message.poll.options.joinToString("\n") { "- ${it.text}" })
                    },
                    forwardedFromName = forwardedFromName,
                )
                message.imageUrl.isNotBlank() -> ForwardPayload(
                    content = message.content,
                    imageUrl = message.imageUrl,
                    forwardedFromName = forwardedFromName,
                )
                message.fileUrl.isNotBlank() -> ForwardPayload(
                    content = "",
                    fileUrl = message.fileUrl,
                    fileName = message.fileName,
                    fileSize = message.fileSize,
                    forwardedFromName = forwardedFromName,
                )
                message.content.isNotBlank() -> ForwardPayload(
                    content = message.content,
                    forwardedFromName = forwardedFromName,
                )
                else -> null
            }
        }
    }

    fun buildRoomForwardPayloads(ids: Set<Long>): List<ForwardPayload> {
        return roomChatViewModel.messages.filter { ids.contains(it.id) && it.deletedAt == null }.mapNotNull { message ->
            val forwardedFromName = message.sender.displayName
            when {
                message.poll != null -> ForwardPayload(
                    content = buildString {
                        append(message.poll.question)
                        append("\n\n")
                        append(message.poll.options.joinToString("\n") { "- ${it.text}" })
                    },
                    forwardedFromName = forwardedFromName,
                )
                message.imageUrl.isNotBlank() -> ForwardPayload(
                    content = message.content,
                    imageUrl = message.imageUrl,
                    forwardedFromName = forwardedFromName,
                )
                message.fileUrl.isNotBlank() -> ForwardPayload(
                    content = "",
                    fileUrl = message.fileUrl,
                    fileName = message.fileName,
                    fileSize = message.fileSize,
                    forwardedFromName = forwardedFromName,
                )
                message.content.isNotBlank() -> ForwardPayload(
                    content = message.content,
                    forwardedFromName = forwardedFromName,
                )
                else -> null
            }
        }
    }

    val forwardTargets = remember(dialogsViewModel.users, roomsViewModel.rooms) {
        val dmTargets = dialogsViewModel.users.map {
            ForwardTarget("dm", it.id, it.displayName, "@${it.username}")
        }
        val roomTargets = roomsViewModel.rooms.filter { it.joined }.map {
            ForwardTarget("room", it.id, "# ${it.name}", it.description.ifBlank { if (it.accessType == "private") "Закрытая комната" else "Публичная комната" })
        }
        dmTargets + roomTargets
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(authViewModel.isAuthorized) {
        if (authViewModel.isAuthorized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(authViewModel.isAuthorized, authViewModel.currentUser?.id, sessionToken) {
        val token = sessionToken.trim()
        if (authViewModel.isAuthorized && token.isNotBlank()) {
            realtimeConnected = false
            realtimeSocketManager.connect(token, sessionStore.ensureDeviceKey())
            invitationsViewModel.load()
            val app = PulseApp.instance
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fcmToken = task.result
                        Log.d("FCM", "Token obtained: ${fcmToken?.take(20)}...")
                        if (fcmToken != null) {
                            app.pushManager.retryRegistrationIfNeeded(fcmToken, app.networkProvider, app.sessionStore)
                        }
                    } else {
                        Log.w("FCM", "Token task failed: ${task.exception?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FCM", "FirebaseMessaging.getInstance() failed", e)
            }
        } else {
            realtimeConnected = false
            realtimeSocketManager.disconnect()
        }
    }

    LaunchedEffect(authViewModel.isAuthorized, sessionToken) {
        if (!authViewModel.isAuthorized) return@LaunchedEffect

        while (authViewModel.isAuthorized) {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) break

            val expiryMillis = jwtExpiryMillis(token) ?: break
            val now = System.currentTimeMillis()
            val refreshIn = (expiryMillis - now - 120_000L).coerceAtLeast(15_000L)
            delay(refreshIn)

            val latestToken = sessionStore.currentToken().trim()
            if (latestToken.isBlank()) break

            val latestExpiry = jwtExpiryMillis(latestToken) ?: continue
            if (latestExpiry - System.currentTimeMillis() > 120_000L) {
                continue
            }

            val refreshed = networkProvider.refreshSessionToken()
            if (!refreshed) {
                delay(60_000L)
            }
        }
    }

    LaunchedEffect(profileViewModel.currentUser) {
        val profileUser = profileViewModel.currentUser ?: return@LaunchedEffect
        if (authViewModel.currentUser?.id == profileUser.id) {
            authViewModel.updateCurrentUser(profileUser)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            Log.d("APP_UPDATE", "checking local=${BuildConfig.VERSION_NAME}")

            val previousUpdateVersion = availableUpdate?.version

            val update = runCatching {
                updateManager.checkForUpdate(BuildConfig.VERSION_NAME)
            }.onFailure { error ->
                Log.e("APP_UPDATE", "check failed", error)
            }.getOrNull()

            if (update != null) {
                Log.d("APP_UPDATE", "update found remote=${update.version} url=${update.apkUrl}")

                if (previousUpdateVersion != update.version) {
                    downloadedUpdatePath = null
                    updateDownloadProgress = null
                    updateError = null
                    isDownloadingUpdate = false
                }

                availableUpdate = update
            } else {
                Log.d("APP_UPDATE", "no update")
                availableUpdate = null
                downloadedUpdatePath = null
                updateDownloadProgress = null
                updateError = null
                isDownloadingUpdate = false
            }

            delay(6 * 60 * 60 * 1000L)
        }
    }

    DisposableEffect(realtimeSocketManager, roomChatViewModel, chatViewModel, dialogsViewModel, roomsViewModel, invitationsViewModel) {
        realtimeSocketManager.setOnConnectionStateChanged { connected ->
            scope.launch {
                realtimeConnected = connected
            }
        }
        realtimeSocketManager.setOnConnectionError { message ->
            scope.launch {
                val text = message.orEmpty()

                if (authViewModel.isAuthorized && text.contains("Unauthorized", ignoreCase = true)) {
                    Log.w("AUTH", "Socket unauthorized, trying token refresh")

                    val refreshed = networkProvider.refreshSessionToken()

                    if (refreshed) {
                        val newToken = sessionStore.currentToken().trim()

                        if (newToken.isNotBlank()) {
                            Log.d("AUTH", "Token refreshed, reconnecting socket")

                            realtimeConnected = false
                            realtimeSocketManager.disconnect()

                            delay(500)

                            realtimeSocketManager.connect(
                                newToken,
                                sessionStore.ensureDeviceKey()
                            )
                        }
                    } else {
                        Log.w("AUTH", "Token refresh failed after socket unauthorized, keeping session")

                        realtimeConnected = false

                        // Важно:
                        // НЕ делаем authViewModel.logout()
                        // Иначе любое временное падение refresh/socket выбрасывает пользователя.
                    }
                }
            }
        }

        realtimeSocketManager.setOnDmMessageNew { payload ->
            chatViewModel.onRealtimeMessage(payload)
            val message = chatViewModel.parseRealtimeMessage(payload)
            dialogsViewModel.onDmMessage(
                message = message,
                currentUserId = authViewModel.currentUser?.id,
                activePeerId = selectedDialog?.id,
            )
        }
        realtimeSocketManager.setOnDmMessageUpdate { payload ->
            chatViewModel.onRealtimeMessageUpdate(payload)
            val message = chatViewModel.parseRealtimeMessage(payload)
            dialogsViewModel.onDmMessageUpdate(
                message = message,
                currentUserId = authViewModel.currentUser?.id,
            )
        }
        realtimeSocketManager.setOnDmRead { peerUserId, readAt ->
            Log.d("DM_READ", "peerUserId=$peerUserId readAt=$readAt currentPeer=${chatViewModel.peer?.id}")

            scope.launch {
                chatViewModel.onDmRead(peerUserId, readAt)
            }
        }
        realtimeSocketManager.setOnDialogCleared { peerUserId ->
            chatViewModel.clearDialog(peerUserId)
            dialogsViewModel.onDialogCleared(peerUserId)
        }
        realtimeSocketManager.setOnRoomMessageNew { roomId, payload ->
            roomChatViewModel.onRealtimeMessage(roomId, payload)
            val message = roomChatViewModel.parseRealtimeMessage(payload)
            roomsViewModel.onRoomMessage(
                message = message,
                currentUserId = authViewModel.currentUser?.id,
                activeRoomId = selectedRoom?.id,
            )
        }
        realtimeSocketManager.setOnRoomMessageUpdate { roomId, payload ->
            roomChatViewModel.onRealtimeMessageUpdate(roomId, payload)
            val message = roomChatViewModel.parseRealtimeMessage(payload)
            roomsViewModel.onRoomMessageUpdate(message)
        }
        realtimeSocketManager.setOnRoomsUpdate {
            roomsViewModel.load()
            invitationsViewModel.load()
        }
        realtimeSocketManager.setOnRoomDeleted { roomId ->
            roomsViewModel.removeRoom(roomId)
            if (selectedRoom?.id == roomId) {
                roomChatViewModel.closeRoom()
                selectedRoom = null
            }
        }
        realtimeSocketManager.setOnRoomMemberKicked { roomId, _ ->
            roomsViewModel.removeRoom(roomId)
            if (selectedRoom?.id == roomId) {
                roomChatViewModel.closeRoom()
                selectedRoom = null
            }
        }
        realtimeSocketManager.setOnPollUpdate { payload ->
            chatViewModel.onPollUpdate(payload)
            roomChatViewModel.onPollUpdate(payload)
        }
        realtimeSocketManager.setOnPresenceUpdate { onlineUserIds ->
            dialogsViewModel.onPresenceUpdate(onlineUserIds)
            chatViewModel.onPresenceUpdate(onlineUserIds)
            roomChatViewModel.onPresenceUpdate(onlineUserIds)
        }
        realtimeSocketManager.setOnTypingUpdate { scope, targetId, userId, isTyping ->
            chatViewModel.onTypingUpdate(scope, targetId, userId, isTyping)
            roomChatViewModel.onTypingUpdate(scope, targetId, userId, isTyping)
        }
        onDispose {
            realtimeSocketManager.setOnConnectionStateChanged(null)
            realtimeSocketManager.setOnConnectionError(null)
            realtimeSocketManager.setOnDmMessageNew(null)
            realtimeSocketManager.setOnDmMessageUpdate(null)
            realtimeSocketManager.setOnDmRead(null)
            realtimeSocketManager.setOnDialogCleared(null)
            realtimeSocketManager.setOnRoomMessageNew(null)
            realtimeSocketManager.setOnRoomMessageUpdate(null)
            realtimeSocketManager.setOnRoomsUpdate(null)
            realtimeSocketManager.setOnRoomDeleted(null)
            realtimeSocketManager.setOnRoomMemberKicked(null)
            realtimeSocketManager.setOnPollUpdate(null)
            realtimeSocketManager.setOnPresenceUpdate(null)
            realtimeSocketManager.setOnTypingUpdate(null)
        }
    }

    LaunchedEffect(settingsOpen) {
        if (!settingsOpen) {
            appThemeMode = localSettings.themeMode
        }
    }

    val useDarkTheme = when (appThemeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    PulseAndroidTheme(darkTheme = useDarkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AmbientBackdrop()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    if (authViewModel.isAuthorized) {
                        val peer = selectedDialog
                        val room = selectedRoom

                        if (messageSearchOpen) {
                            SearchScreen(
                                viewModel = searchViewModel,
                                onBack = { messageSearchOpen = false },
                                onOpenDm = { userId, userName ->
                                    messageSearchOpen = false
                                    selectedRoom?.id?.let {
                                        realtimeSocketManager.emitTypingUpdate("room", it, false)
                                    }
                                    selectedRoom = null
                                    roomChatViewModel.closeRoom()

                                    val user = DialogUserDto(
                                        id = userId,
                                        username = userName,
                                        displayName = userName
                                    )

                                    dialogsViewModel.markDialogOpened(user.id)
                                    selectedDialog = user
                                    chatViewModel.openDialog(user)
                                },
                                onOpenRoom = { roomId, roomName ->
                                    messageSearchOpen = false
                                    selectedDialog?.id?.let {
                                        realtimeSocketManager.emitTypingUpdate("dm", it, false)
                                    }
                                    selectedDialog = null
                                    chatViewModel.closeDialog()

                                    val targetRoom = RoomDto(id = roomId, name = roomName)

                                    roomsViewModel.markRoomOpened(roomId)
                                    selectedRoom = targetRoom
                                    roomChatViewModel.openRoom(targetRoom)
                                }
                            )
                        } else if (peer != null) {
                            DmChatScreen(
                                peer = peer,
                                currentUserId = authViewModel.currentUser?.id,
                                currentUserIsAdmin = authViewModel.currentUser?.isAdmin == true,
                                viewModel = chatViewModel,
                                onDraftChange = { value ->
                                    chatViewModel.onDraftChanged(value) { isTyping ->
                                        realtimeSocketManager.emitTypingUpdate(
                                            "dm",
                                            peer.id,
                                            isTyping
                                        )
                                    }
                                },
                                onImageSelected = { uri ->
                                    chatViewModel.selectImage(uri)
                                },
                                onSendPendingImage = {
                                    chatViewModel.sendPendingImage(context)
                                },
                                onFileSelected = { uri ->
                                    chatViewModel.selectFile(context, uri)
                                },
                                onSendPendingFile = {
                                    chatViewModel.sendPendingFile(context)
                                },
                                onBack = {
                                    realtimeSocketManager.emitTypingUpdate("dm", peer.id, false)
                                    chatViewModel.closeDialog()
                                    selectedDialog = null
                                },
                                onOpenSearch = { messageSearchOpen = true },
                                onForwardSelected = { ids ->
                                    forwardPayloads = buildDmForwardPayloads(ids)
                                }
                            )
                        } else if (room != null && roomSettingsOpen) {
                            RoomSettingsScreen(
                                roomId = room.id,
                                repository = roomSettingsRepository,
                                onBack = {
                                    roomSettingsOpen = false
                                    roomsViewModel.load()
                                },
                                onRoomDeleted = {
                                    roomSettingsOpen = false
                                    realtimeSocketManager.emitTypingUpdate("room", room.id, false)
                                    roomChatViewModel.closeRoom()
                                    selectedRoom = null
                                    roomsViewModel.load()
                                },
                                onRoomLeft = {
                                    roomSettingsOpen = false
                                    realtimeSocketManager.emitTypingUpdate("room", room.id, false)
                                    roomChatViewModel.closeRoom()
                                    selectedRoom = null
                                    roomsViewModel.load()
                                }
                            )
                        } else if (room != null) {
                            RoomChatScreen(
                                room = room,
                                currentUserId = authViewModel.currentUser?.id,
                                currentUserIsAdmin = authViewModel.currentUser?.isAdmin == true,
                                viewModel = roomChatViewModel,
                                onDraftChange = { value ->
                                    roomChatViewModel.onDraftChanged(value) { isTyping ->
                                        realtimeSocketManager.emitTypingUpdate(
                                            "room",
                                            room.id,
                                            isTyping
                                        )
                                    }
                                },
                                onImageSelected = { uri ->
                                    roomChatViewModel.selectImage(uri)
                                },
                                onSendPendingImage = {
                                    roomChatViewModel.sendPendingImage(context)
                                },
                                onFileSelected = { uri ->
                                    roomChatViewModel.selectFile(context, uri)
                                },
                                onSendPendingFile = {
                                    roomChatViewModel.sendPendingFile(context)
                                },
                                onBack = {
                                    realtimeSocketManager.emitTypingUpdate("room", room.id, false)
                                    roomChatViewModel.closeRoom()
                                    selectedRoom = null
                                },
                                onOpenSearch = { messageSearchOpen = true },
                                onForwardSelected = { ids ->
                                    forwardPayloads = buildRoomForwardPayloads(ids)
                                },
                                onOpenSettings = {
                                    roomSettingsOpen = true
                                }
                            )
                        } else if (invitationsOpen) {
                            InvitationsScreen(
                                viewModel = invitationsViewModel,
                                onBack = { invitationsOpen = false }
                            )
                        } else if (profileOpen) {
                            ProfileScreen(
                                viewModel = profileViewModel,
                                onBack = { profileOpen = false }
                            )
                        } else if (searchOpen) {
                            SearchUsersScreen(
                                repository = dialogsRepository,
                                onBack = { searchOpen = false },
                                onStartChat = { user ->
                                    selectedRoom?.id?.let {
                                        realtimeSocketManager.emitTypingUpdate(
                                            "room",
                                            it,
                                            false
                                        )
                                    }
                                    selectedRoom = null
                                    roomChatViewModel.closeRoom()
                                    dialogsViewModel.markDialogOpened(user.id)
                                    selectedDialog = user
                                    chatViewModel.openDialog(user)
                                    searchOpen = false
                                }
                            )
                        } else if (createRoomOpen) {
                            CreateRoomScreen(
                                viewModel = createRoomViewModel,
                                onBack = {
                                    createRoomOpen = false
                                    createRoomViewModel.reset()
                                },
                                onRoomCreated = {
                                    createRoomOpen = false
                                    roomsViewModel.load()
                                }
                            )
                        } else if (settingsOpen) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                currentUserName = authViewModel.currentUser?.displayName
                                    ?: authViewModel.currentUser?.username ?: "Pulse",
                                currentUserSubtitle = authViewModel.currentUser?.let { "@${it.username}" }
                                    ?: "",
                                currentUserAvatarUrl = authViewModel.currentUser?.avatarUrl ?: "",
                                isAdmin = authViewModel.currentUser?.isAdmin == true,
                                onThemeChanged = { mode ->
                                    appThemeMode = mode
                                },
                                onBack = {
                                    appThemeMode = localSettings.themeMode
                                    settingsOpen = false
                                },
                                onOpenProfile = {
                                    authViewModel.currentUser?.let { profileViewModel.init(it) }
                                    profileOpen = true
                                    settingsOpen = false
                                },
                                onOpenAdmin = {
                                    settingsOpen = false
                                    adminConsoleOpen = true
                                },
                                onLogoutCurrent = {
                                    settingsOpen = false
                                    realtimeSocketManager.disconnect()
                                    authViewModel.logout()
                                }
                            )
                        } else if (adminConsoleOpen) {
                            AdminConsoleScreen(
                                viewModel = settingsViewModel,
                                onBack = { adminConsoleOpen = false }
                            )
                        } else {
                            HomeScreen(
                                currentUserName = authViewModel.currentUser?.displayName
                                    ?: authViewModel.currentUser?.username ?: "Pulse",
                                currentUserSubtitle = authViewModel.currentUser?.let { "@${it.username}" }
                                    ?: "",
                                currentUserAvatarUrl = authViewModel.currentUser?.avatarUrl ?: "",
                                isAdmin = authViewModel.currentUser?.isAdmin == true,
                                currentTab = homeTab,
                                onTabChange = { homeTab = it },
                                onOpenProfile = {
                                    authViewModel.currentUser?.let { profileViewModel.init(it) }
                                    profileOpen = true
                                },
                                onOpenInvitations = {
                                    invitationsOpen = true
                                    invitationsViewModel.load()
                                },
                                onOpenSearchDialogs = {
                                    searchOpen = true
                                },
                                onOpenMessageSearch = {
                                    messageSearchOpen = true
                                },
                                onOpenCreateRoom = {
                                    createRoomOpen = true
                                    createRoomViewModel.reset()
                                },
                                onOpenSettings = {
                                    settingsOpen = true
                                    settingsViewModel.load()
                                },
                                onOpenAdmin = {
                                    settingsViewModel.load()
                                    adminConsoleOpen = true
                                },
                                onLogout = {
                                    selectedDialog?.id?.let {
                                        realtimeSocketManager.emitTypingUpdate(
                                            "dm",
                                            it,
                                            false
                                        )
                                    }
                                    selectedRoom?.id?.let {
                                        realtimeSocketManager.emitTypingUpdate(
                                            "room",
                                            it,
                                            false
                                        )
                                    }
                                    realtimeSocketManager.disconnect()
                                    chatViewModel.closeDialog()
                                    roomChatViewModel.closeRoom()
                                    selectedDialog = null
                                    selectedRoom = null
                                    invitationsOpen = false
                                    settingsOpen = false
                                    authViewModel.logout()
                                },
                                invitationsCount = invitationsViewModel.invitations.size,
                                dialogsContent = {
                                    DialogsScreen(
                                        viewModel = dialogsViewModel,
                                        onOpenDialog = { user ->
                                            selectedRoom?.id?.let {
                                                realtimeSocketManager.emitTypingUpdate(
                                                    "room",
                                                    it,
                                                    false
                                                )
                                            }
                                            selectedRoom = null
                                            roomChatViewModel.closeRoom()
                                            dialogsViewModel.markDialogOpened(user.id)
                                            selectedDialog = user
                                            chatViewModel.openDialog(user)
                                        }
                                    )
                                },
                                roomsContent = {
                                    RoomsScreen(
                                        viewModel = roomsViewModel,
                                        onOpenRoom = { targetRoom ->
                                            selectedDialog?.id?.let {
                                                realtimeSocketManager.emitTypingUpdate(
                                                    "dm",
                                                    it,
                                                    false
                                                )
                                            }
                                            selectedDialog = null
                                            chatViewModel.closeDialog()
                                            roomsViewModel.markRoomOpened(targetRoom.id)
                                            selectedRoom = targetRoom
                                            roomChatViewModel.openRoom(targetRoom)
                                        }
                                    )
                                }
                            )
                        }
                        if (forwardPayloads.isNotEmpty()) {
                            ForwardMessagesSheet(
                                payloads = forwardPayloads,
                                targets = forwardTargets,
                                onDismiss = { forwardPayloads = emptyList() },
                                onForwardTo = { target ->
                                    scope.launch {
                                        forwardPayloads.forEach { payload ->
                                            if (target.scope == "dm") {
                                                chatRepository.sendMessage(
                                                    peerUserId = target.id,
                                                    content = payload.content,
                                                    imageUrl = payload.imageUrl,
                                                    fileUrl = payload.fileUrl,
                                                    fileName = payload.fileName,
                                                    fileSize = payload.fileSize,
                                                    forwardedFromName = payload.forwardedFromName,
                                                )
                                            } else {
                                                roomChatRepository.sendMessage(
                                                    roomId = target.id,
                                                    content = payload.content,
                                                    imageUrl = payload.imageUrl,
                                                    fileUrl = payload.fileUrl,
                                                    fileName = payload.fileName,
                                                    fileSize = payload.fileSize,
                                                    forwardedFromName = payload.forwardedFromName,
                                                )
                                            }
                                        }
                                        chatViewModel.clearSelection()
                                        roomChatViewModel.clearSelection()
                                        forwardPayloads = emptyList()
                                    }
                                },
                            )
                        }

                    } else {
                        realtimeSocketManager.disconnect()
                        AuthScreen(authViewModel)
                    }
                }
                if (authViewModel.isAuthorized && !realtimeConnected) {
                    ConnectionBanner()
                }
                val update = availableUpdate
                if (update != null) {
                    UpdateBanner(
                        version = update.version,
                        isDownloading = isDownloadingUpdate,
                        progress = updateDownloadProgress,
                        downloaded = downloadedUpdatePath != null,
                        error = updateError,
                        onAction = {
                            val apkPath = downloadedUpdatePath

                            if (apkPath == null) {
                                downloadUpdate(update)
                                return@UpdateBanner
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } else {
                                context.startActivity(updateManager.createInstallIntent(File(apkPath)))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientBackdrop() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-40).dp, y = (-20).dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = 180.dp, y = 120.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 40.dp, y = 520.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}
@Composable
private fun ConnectionBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            shape = RoundedCornerShape(50.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )

                Text(
                    text = "Соединение...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    version: String,
    isDownloading: Boolean,
    progress: Float?,
    downloaded: Boolean,
    error: String?,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Доступна версия $version",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )

                    Text(
                        text = when {
                            isDownloading -> "Загрузка ${progress?.let { "${(it * 100).toInt()}%" } ?: "..."}"
                            downloaded -> "APK скачан, можно установить"
                            !error.isNullOrBlank() -> error
                            else -> "Нажмите, чтобы скачать обновление"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!error.isNullOrBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    FilledIconButton(onClick = onAction) {
                        Text(
                            text = when {
                                downloaded -> "OK"
                                !error.isNullOrBlank() -> "↻"
                                else -> "↓"
                            }
                        )
                    }
                }
            }
        }
    }
}