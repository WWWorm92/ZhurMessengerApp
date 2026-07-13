@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pulsemessenger.android.feature.rooms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsemessenger.android.core.network.JoinRequestDto
import com.pulsemessenger.android.core.network.RoomDetailResponse
import com.pulsemessenger.android.core.network.RoomMemberDto
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.OnlineDot
import com.pulsemessenger.android.ui.resolveBackendMediaUrl
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn

private enum class RoomSettingsTab {
    General,
    Members,
    Requests,
    Actions,
}

@Composable
fun RoomSettingsScreen(
    roomId: Long,
    repository: RoomSettingsRepository,
    onBack: () -> Unit,
    onRoomDeleted: () -> Unit,
    onRoomLeft: () -> Unit,
) {
    val viewModel: RoomSettingsViewModel = viewModel(factory = RoomSettingsViewModelFactory(repository))
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(RoomSettingsTab.General) }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(context, it) }
    }

    LaunchedEffect(roomId) {
        viewModel.load(roomId)
    }

    val detail = viewModel.roomDetail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
    ) {
        RoomSettingsHeader(onBack = onBack)

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = viewModel.error ?: "Не удалось загрузить настройки комнаты",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val canManage = detail.canManage || detail.canOwn

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    RoomHeroCard(
                        detail = detail,
                        isUploadingAvatar = viewModel.isUploadingAvatar,
                        onAvatarClick = { avatarPicker.launch("image/*") },
                    )
                }

                item {
                    RoomSettingsTabBar(
                        selected = tab,
                        membersCount = detail.members.size,
                        requestsCount = viewModel.joinRequests.size,
                        showManageTabs = canManage,
                        onSelected = { tab = it },
                    )
                }

                when (tab) {
                    RoomSettingsTab.General -> {
                        item {
                            GeneralSettingsCard(
                                viewModel = viewModel,
                                roomId = roomId,
                            )
                        }
                    }

                    RoomSettingsTab.Members -> {
                        item {
                            if (canManage) {
                                MembersCard(
                                    roomId = roomId,
                                    detail = detail,
                                    viewModel = viewModel,
                                )
                            } else {
                                EmptyModernCard("У вас нет доступа к управлению участниками")
                            }
                        }
                    }

                    RoomSettingsTab.Requests -> {
                        item {
                            if (canManage) {
                                RequestsCard(
                                    roomId = roomId,
                                    viewModel = viewModel,
                                )
                            } else {
                                EmptyModernCard("У вас нет доступа к заявкам")
                            }
                        }
                    }

                    RoomSettingsTab.Actions -> {
                        item {
                            ActionsCard(
                                canOwn = detail.canOwn,
                                isLeaving = viewModel.isLeaving,
                                isDeleting = viewModel.isDeleting,
                                onLeave = { showLeaveConfirm = true },
                                onDelete = { showDeleteConfirm = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Покинуть комнату?") },
            text = { Text("Вы сможете вернуться только по новому приглашению, если комната закрытая.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveRoom(roomId, onRoomLeft)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Покинуть")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить комнату?") },
            text = { Text("Это действие необратимо. История сообщений и настройки комнаты будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteRoom(roomId, onRoomDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (viewModel.showInviteSheet) {
        InviteCandidatesDialog(
            candidates = viewModel.inviteCandidates,
            isInviting = viewModel.isInviting,
            onDismiss = { viewModel.showInviteSheet = false },
            onInvite = { userId -> viewModel.inviteUser(roomId, userId) }
        )
    }
}

@Composable
private fun RoomSettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Настройки комнаты",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Профиль, доступ и участники",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Готово")
        }
    }
}

@Composable
private fun RoomHeroCard(
    detail: RoomDetailResponse,
    isUploadingAvatar: Boolean,
    onAvatarClick: () -> Unit,
) {
    ModernCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                if (detail.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = resolveBackendMediaUrl(detail.avatarUrl),
                        contentDescription = "Аватар комнаты",
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DialogAvatar(detail.name, modifier = Modifier.size(68.dp))
                }

                if (isUploadingAvatar) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = detail.description.ifBlank { "Описание не заполнено" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill(
                        text = if (detail.accessType == "private") "Закрытая" else "Публичная",
                        selected = true,
                    )
                    InfoPill(text = "${detail.members.size} участников")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (detail.slug.isNotBlank()) "#${detail.slug}" else "Ссылка не задана",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Нажмите на аватар, чтобы заменить изображение комнаты",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoomSettingsTabBar(
    selected: RoomSettingsTab,
    membersCount: Int,
    requestsCount: Int,
    showManageTabs: Boolean,
    onSelected: (RoomSettingsTab) -> Unit,
) {
    val tabs = buildList {
        add(RoomSettingsTab.General)
        if (showManageTabs) {
            add(RoomSettingsTab.Members)
            add(RoomSettingsTab.Requests)
        }
        add(RoomSettingsTab.Actions)
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { item ->
                TabPill(
                    title = when (item) {
                        RoomSettingsTab.General -> "Общее"
                        RoomSettingsTab.Members -> "Участники"
                        RoomSettingsTab.Requests -> "Заявки"
                        RoomSettingsTab.Actions -> "Действия"
                    },
                    badge = when (item) {
                        RoomSettingsTab.Members -> membersCount.takeIf { it > 0 }?.toString()
                        RoomSettingsTab.Requests -> requestsCount.takeIf { it > 0 }?.toString()
                        else -> null
                    },
                    selected = selected == item,
                    onClick = { onSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    title: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .widthIn(min = 94.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )

        if (!badge.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = badge,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun GeneralSettingsCard(
    viewModel: RoomSettingsViewModel,
    roomId: Long,
) {
    ModernCard {
        SectionTitle(
            title = "Основное",
            subtitle = "Название, описание и ссылка комнаты",
        )

        Spacer(modifier = Modifier.height(14.dp))

        SettingsTextField(
            value = viewModel.editName,
            onValueChange = { viewModel.editName = it },
            label = "Название",
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsTextField(
            value = viewModel.editDescription,
            onValueChange = { viewModel.editDescription = it },
            label = "Описание",
            maxLines = 3,
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsTextField(
            value = viewModel.editSlug,
            onValueChange = { viewModel.editSlug = it },
            label = "Короткая ссылка",
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(18.dp))

        SectionTitle(
            title = "Доступ",
            subtitle = "Кто сможет найти и открыть комнату",
        )

        Spacer(modifier = Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = viewModel.editAccessType == "public",
                onClick = { viewModel.editAccessType = "public" },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) {
                Text("Публичная")
            }
            SegmentedButton(
                selected = viewModel.editAccessType == "private",
                onClick = { viewModel.editAccessType = "private" },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) {
                Text("Закрытая")
            }
        }

        if (!viewModel.error.isNullOrBlank() && !viewModel.saveSuccess) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusText(text = viewModel.error ?: "", isError = true)
        }

        if (viewModel.saveSuccess) {
            Spacer(modifier = Modifier.height(12.dp))
            StatusText(text = "Сохранено", isError = false)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveSettings(roomId) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Сохранить изменения")
            }
        }
    }
}

@Composable
private fun MembersCard(
    roomId: Long,
    detail: RoomDetailResponse,
    viewModel: RoomSettingsViewModel,
) {
    ModernCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionTitle(
                title = "Участники",
                subtitle = "${detail.members.size} человек в комнате",
                modifier = Modifier.weight(1f),
            )

            if (detail.canInvite) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadInviteCandidates(roomId)
                        viewModel.showInviteSheet = true
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Пригласить")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (detail.members.isEmpty()) {
            EmptyInsideCard("Участников пока нет")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detail.members.forEach { member ->
                    MemberRow(
                        member = member,
                        canManageOthers = detail.canOwn || (detail.canManage && detail.createdBy != member.id),
                        isRemoving = viewModel.removingMemberId == member.id,
                        isUpdating = viewModel.updatingMemberId == member.id,
                        onRemove = { viewModel.removeMember(roomId, member.id) },
                        onToggleRole = { viewModel.toggleMemberRole(roomId, member) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestsCard(
    roomId: Long,
    viewModel: RoomSettingsViewModel,
) {
    ModernCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionTitle(
                title = "Заявки",
                subtitle = "Запросы на вступление в комнату",
                modifier = Modifier.weight(1f),
            )

            OutlinedButton(
                onClick = { viewModel.loadJoinRequests(roomId) },
                shape = RoundedCornerShape(16.dp),
            ) {
                if (viewModel.isLoadingRequests) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Обновить")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (viewModel.joinRequests.isEmpty()) {
            EmptyInsideCard("Активных заявок нет")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                viewModel.joinRequests.forEach { request ->
                    JoinRequestRow(
                        request = request,
                        onApprove = { viewModel.approveRequest(roomId, request.userId) },
                        onDecline = { viewModel.declineRequest(roomId, request.userId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsCard(
    canOwn: Boolean,
    isLeaving: Boolean,
    isDeleting: Boolean,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
) {
    ModernCard(
        containerAlpha = 0.30f,
    ) {
        SectionTitle(
            title = "Опасная зона",
            subtitle = "Действия, которые могут ограничить доступ к комнате",
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = !isLeaving,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            if (isLeaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Покинуть комнату")
            }
        }

        if (canOwn) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Удалить комнату")
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: RoomMemberDto,
    canManageOthers: Boolean,
    isRemoving: Boolean,
    isUpdating: Boolean,
    onRemove: () -> Unit,
    onToggleRole: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogAvatar(member.displayName, avatarUrl = member.avatarUrl, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.displayName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (member.online) {
                            OnlineDot()
                        }
                    }

                    Text(
                        text = "@${member.username}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(
                    text = when (member.role) {
                        "owner" -> "Владелец"
                        "admin" -> "Админ"
                        else -> "Участник"
                    },
                    selected = member.role == "owner" || member.role == "admin",
                )

                if (member.isMuted) {
                    InfoPill(text = "Mute", danger = true)
                }

                if (!member.canPostMedia) {
                    InfoPill(text = "Без медиа", danger = true)
                }
            }

            if (canManageOthers) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onToggleRole,
                        enabled = !isUpdating,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (member.role == "admin") "Снять админа" else "Сделать админом")
                        }
                    }

                    OutlinedButton(
                        onClick = onRemove,
                        enabled = !isRemoving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        if (isRemoving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Исключить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestRow(
    request: JoinRequestDto,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogAvatar(request.displayName, avatarUrl = request.avatarUrl, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${request.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text("Принять")
                }

                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Text("Отклонить")
                }
            }
        }
    }
}

@Composable
private fun InviteCandidatesDialog(
    candidates: List<com.pulsemessenger.android.core.network.DialogUserDto>,
    isInviting: Boolean,
    onDismiss: () -> Unit,
    onInvite: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить участника") },
        text = {
            if (candidates.isEmpty()) {
                Text("Нет доступных пользователей для приглашения")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(candidates, key = { it.id }) { user ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isInviting) { onInvite(user.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogAvatar(user.displayName, avatarUrl = user.avatarUrl, modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "@${user.username}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isInviting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "Пригласить",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
private fun ModernCard(
    modifier: Modifier = Modifier,
    containerAlpha: Float = 0.78f,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun InfoPill(
    text: String,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val containerColor = when {
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    }

    val textColor = when {
        danger -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text,
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

@Composable
private fun StatusText(text: String, isError: Boolean) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyModernCard(text: String) {
    ModernCard {
        EmptyInsideCard(text)
    }
}

@Composable
private fun EmptyInsideCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}