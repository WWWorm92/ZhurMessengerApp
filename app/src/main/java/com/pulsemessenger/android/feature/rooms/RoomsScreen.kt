package com.pulsemessenger.android.feature.rooms

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.GlassCard
import com.pulsemessenger.android.ui.ListSkeletonCard
import com.pulsemessenger.android.ui.UnreadBadge
import com.pulsemessenger.android.ui.formatDateTime
import com.pulsemessenger.android.ui.formatChatListTime

private enum class RoomListFilter {
    All,
    Unread,
    Archived,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    onOpenRoom: (RoomDto) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(RoomListFilter.All) }
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val filteredRooms = when (selectedFilter) {
        RoomListFilter.All -> viewModel.rooms.filterNot { it.archived }
        RoomListFilter.Unread -> viewModel.rooms.filter { !it.archived && it.unreadCount > 0 }
        RoomListFilter.Archived -> viewModel.rooms.filter { it.archived }
    }

    viewModel.joinError?.let { errorMsg ->
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                androidx.compose.material3.TextButton(onClick = { viewModel.joinError = null }) {
                    Text("OK")
                }
            }
        ) {
            Text(errorMsg)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedFilter == RoomListFilter.All,
                onClick = { selectedFilter = RoomListFilter.All },
                label = { Text("Все") },
            )
            FilterChip(
                selected = selectedFilter == RoomListFilter.Unread,
                onClick = { selectedFilter = RoomListFilter.Unread },
                label = { Text("Непрочитанные") },
            )
            FilterChip(
                selected = selectedFilter == RoomListFilter.Archived,
                onClick = { selectedFilter = RoomListFilter.Archived },
                label = { Text("Архив") },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading && viewModel.rooms.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(6) {
                        ListSkeletonCard()
                    }
                }
            }

            !viewModel.error.isNullOrBlank() && viewModel.rooms.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassCard(radius = 28, padding = 18) {
                        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Не удалось загрузить комнаты", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.Button(onClick = viewModel::load) {
                                Text("Повторить")
                            }
                        }
                    }
                }
            }

            filteredRooms.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlassCard(radius = 28, padding = 22) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Пусто", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (selectedFilter) {
                                    RoomListFilter.All -> "Комнат пока нет"
                                    RoomListFilter.Unread -> "Непрочитанных комнат нет"
                                    RoomListFilter.Archived -> "Архив пуст"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = viewModel::load,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!viewModel.error.isNullOrBlank()) {
                            item {
                                Text(
                                    text = viewModel.error ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }

                        items(filteredRooms, key = { it.id }) { room ->
                            RoomCard(
                                room = room,
                                onClick = { if (room.joined) onOpenRoom(room) },
                                isJoining = viewModel.joiningRoomId == room.id,
                                onJoin = { viewModel.joinRoom(room.id) },
                                onRequestJoin = { viewModel.requestJoinRoom(room.id) },
                                onTogglePin = { viewModel.togglePin(room.id) },
                                onToggleMute = { viewModel.toggleMute(room.id) },
                                onToggleArchive = { viewModel.toggleArchive(room.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomCard(
    room: RoomDto,
    onClick: () -> Unit,
    isJoining: Boolean = false,
    onJoin: () -> Unit = {},
    onRequestJoin: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "roomCardScale")
    Box {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (room.joined) onClick() },
                onLongClick = { if (room.joined) menuExpanded = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(room.name, avatarUrl = room.avatarUrl)
            Spacer(modifier = Modifier.padding(horizontal = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buildString {
                            if (room.pinned) append("📌 ")
                            if (room.muted) append("🔇 ")
                            append(room.name)
                        },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (room.hasJoinRequest) {
                        Text(
                            "запрос отправлен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(
                    room.description.ifBlank {
                        if (room.joined) "Участников: ${room.membersCount}" else room.accessType
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (!room.lastMessageAt.isNullOrBlank()) {
                    Text(
                        formatChatListTime(room.lastMessageAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            if (!room.joined && !room.hasJoinRequest && isJoining) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (!room.joined && !room.hasJoinRequest) {
                if (room.accessType == "public" || room.hasInvitation) {
                    Button(
                        onClick = onJoin,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Вступить", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRequestJoin,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Запросить", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                UnreadBadge(room.unreadCount)
            }
            }
        }
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(if (room.pinned) "Открепить" else "Закрепить") },
            onClick = { menuExpanded = false; onTogglePin() }
        )
        DropdownMenuItem(
            text = { Text(if (room.muted) "Включить звук" else "Отключить звук") },
            onClick = { menuExpanded = false; onToggleMute() }
        )
        DropdownMenuItem(
            text = { Text(if (room.archived) "Разархивировать" else "Архивировать") },
            onClick = { menuExpanded = false; onToggleArchive() }
        )
    }
    }
}
