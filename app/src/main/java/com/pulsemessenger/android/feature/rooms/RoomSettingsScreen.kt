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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsemessenger.android.core.network.JoinRequestDto
import com.pulsemessenger.android.core.network.RoomMemberDto
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.GlassCard
import com.pulsemessenger.android.ui.OnlineDot
import com.pulsemessenger.android.ui.resolveBackendMediaUrl

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

    LaunchedEffect(roomId) { viewModel.load(roomId) }

    val detail = viewModel.roomDetail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Настройки комнаты", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("Готово") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (detail == null) {
            if (viewModel.error != null) {
                Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), radius = 30, padding = 20) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                                .clickable { avatarPicker.launch("image/*") }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (detail.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = resolveBackendMediaUrl(detail.avatarUrl),
                                    contentDescription = "Room avatar",
                                    modifier = Modifier.size(96.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                DialogAvatar(detail.name, modifier = Modifier.size(72.dp))
                            }
                            if (viewModel.isUploadingAvatar) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (detail.accessType == "private") "Закрытая комната" else "Публичная комната",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Нажмите, чтобы изменить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = tab == RoomSettingsTab.General, onClick = { tab = RoomSettingsTab.General }, shape = SegmentedButtonDefaults.itemShape(0, 4)) { Text("Общее") }
                        SegmentedButton(selected = tab == RoomSettingsTab.Members, onClick = { tab = RoomSettingsTab.Members }, shape = SegmentedButtonDefaults.itemShape(1, 4)) { Text("Участники") }
                        SegmentedButton(selected = tab == RoomSettingsTab.Requests, onClick = { tab = RoomSettingsTab.Requests }, shape = SegmentedButtonDefaults.itemShape(2, 4)) { Text("Заявки") }
                        SegmentedButton(selected = tab == RoomSettingsTab.Actions, onClick = { tab = RoomSettingsTab.Actions }, shape = SegmentedButtonDefaults.itemShape(3, 4)) { Text("Действия") }
                    }
                }
                item {
                    if (tab != RoomSettingsTab.General) return@item
                    GlassCard(modifier = Modifier.fillMaxWidth(), radius = 26, padding = 16) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Основные настройки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = viewModel.editName,
                                onValueChange = { viewModel.editName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Название") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = viewModel.editDescription,
                                onValueChange = { viewModel.editDescription = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Описание") },
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = viewModel.editSlug,
                                onValueChange = { viewModel.editSlug = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Ссылка (slug)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Тип доступа", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = viewModel.editAccessType == "public",
                                    onClick = { viewModel.editAccessType = "public" },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) { Text("Публичная") }
                                SegmentedButton(
                                    selected = viewModel.editAccessType == "private",
                                    onClick = { viewModel.editAccessType = "private" },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) { Text("Приватная") }
                            }
                            if (!viewModel.error.isNullOrBlank() && !viewModel.saveSuccess) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (viewModel.saveSuccess) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Сохранено", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.saveSettings(roomId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !viewModel.isSaving
                            ) {
                                if (viewModel.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else { Text("Сохранить") }
                            }
                        }
                    }
                }

                if (detail.canManage || detail.canOwn) {
                    item {
                        if (tab != RoomSettingsTab.Members) return@item
                        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 26, padding = 16) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Участники (${detail.members.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (detail.canInvite) {
                                        OutlinedButton(onClick = {
                                            viewModel.loadInviteCandidates(roomId)
                                            viewModel.showInviteSheet = true
                                        }) { Text("+ Пригласить") }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                detail.members.forEach { member ->
                                    MemberRow(
                                        member = member,
                                        canManageOthers = detail.canOwn || (detail.canManage && detail.createdBy != member.id),
                                        isRemoving = viewModel.removingMemberId == member.id,
                                        isUpdating = viewModel.updatingMemberId == member.id,
                                        onRemove = { viewModel.removeMember(roomId, member.id) },
                                        onToggleRole = { viewModel.toggleMemberRole(roomId, member) },
                                    )
                                    if (member != detail.members.last()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (detail.canManage || detail.canOwn) {
                    item {
                        if (tab != RoomSettingsTab.Requests) return@item
                        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 26, padding = 16) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Запросы на вступление (${viewModel.joinRequests.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    OutlinedButton(onClick = { viewModel.loadJoinRequests(roomId) }) {
                                        if (viewModel.isLoadingRequests) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else { Text("Обновить") }
                                    }
                                }
                                if (viewModel.joinRequests.isEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Нет активных запросов", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    viewModel.joinRequests.forEach { request ->
                                        JoinRequestRow(
                                            request = request,
                                            onApprove = { viewModel.approveRequest(roomId, request.userId) },
                                            onDecline = { viewModel.declineRequest(roomId, request.userId) },
                                        )
                                        if (request != viewModel.joinRequests.last()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    if (tab != RoomSettingsTab.Actions) return@item
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !viewModel.isLeaving
                        ) {
                            if (viewModel.isLeaving) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                            else { Text("Покинуть комнату") }
                        }
                        if (detail.canOwn) {
                            Button(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = !viewModel.isDeleting
                            ) {
                                if (viewModel.isDeleting) { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp) }
                                else { Text("Удалить") }
                            }
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
            text = { Text("Вы уверены? Вы сможете вернуться только по новому приглашению.") },
            confirmButton = {
                Button(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveRoom(roomId, onRoomLeft)
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Покинуть") }
            },
            dismissButton = { OutlinedButton(onClick = { showLeaveConfirm = false }) { Text("Отмена") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить комнату?") },
            text = { Text("Это действие необратимо. Вся история сообщений будет удалена.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteRoom(roomId, onRoomDeleted)
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") } }
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
private fun MemberRow(
    member: RoomMemberDto,
    canManageOthers: Boolean,
    isRemoving: Boolean,
    isUpdating: Boolean,
    onRemove: () -> Unit,
    onToggleRole: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(member.displayName, avatarUrl = member.avatarUrl)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.displayName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        if (member.online) OnlineDot()
                    }
                Text("@${member.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (member.role == "admin") {
                Text("Админ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (canManageOthers) {
                if (isUpdating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (member.role == "admin") "Снять" else "Назначить",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onToggleRole)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (isRemoving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Исключить",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable(onClick = onRemove)
                    )
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(request.displayName, avatarUrl = request.avatarUrl, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("@${request.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onApprove,
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) { Text("Принять") }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(12.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) { Text("Отклонить") }
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(candidates, key = { it.id }) { user ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isInviting) { onInvite(user.id) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DialogAvatar(user.displayName, avatarUrl = user.avatarUrl)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.displayName, fontWeight = FontWeight.Medium)
                                    Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isInviting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("Готово") } }
    )
}
