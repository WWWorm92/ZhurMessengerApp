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
import com.pulsemessenger.android.ui.ChatListRowCard
import com.pulsemessenger.android.ui.ChatListFilterBar
import com.pulsemessenger.android.ui.ChatListFilterItem

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
        ChatListFilterBar(
            selected = selectedFilter,
            onSelected = { selectedFilter = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            items = listOf(
                ChatListFilterItem(RoomListFilter.All, "Все"),
                ChatListFilterItem(
                    RoomListFilter.Unread,
                    "Новые",
                    viewModel.rooms.count { !it.archived && it.unreadCount > 0 }
                ),
                ChatListFilterItem(
                    RoomListFilter.Archived,
                    "Архив",
                    viewModel.rooms.count { it.archived }
                ),
            )
        )

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
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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

    Box {
        ChatListRowCard(
            title = room.name,
            subtitle = room.description.ifBlank {
                when {
                    room.joined -> "Участников: ${room.membersCount}"
                    room.accessType == "private" -> "Закрытая комната"
                    else -> "Публичная комната"
                }
            },
            avatarUrl = room.avatarUrl,
            time = room.lastMessageAt
                ?.takeIf { it.isNotBlank() }
                ?.let { formatChatListTime(it) }
                ?: "",
            unreadCount = room.unreadCount,
            pinned = room.pinned,
            muted = room.muted,
            enabled = room.joined,
            onClick = onClick,
            onLongClick = {
                if (room.joined) {
                    menuExpanded = true
                }
            },
            extraTitleContent = {
                if (room.hasJoinRequest) {
                    Text(
                        text = "запрос отправлен",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1
                    )
                }
            },
            trailingContent = {
                if (!room.lastMessageAt.isNullOrBlank()) {
                    Text(
                        text = formatChatListTime(room.lastMessageAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }

                when {
                    !room.joined && !room.hasJoinRequest && isJoining -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    !room.joined && !room.hasJoinRequest && (room.accessType == "public" || room.hasInvitation) -> {
                        Button(
                            onClick = onJoin,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Text("Вступить", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    !room.joined && !room.hasJoinRequest -> {
                        OutlinedButton(
                            onClick = onRequestJoin,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Text("Запросить", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    else -> {
                        UnreadBadge(room.unreadCount)
                    }
                }
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (room.pinned) "Открепить" else "Закрепить") },
                onClick = {
                    menuExpanded = false
                    onTogglePin()
                }
            )

            DropdownMenuItem(
                text = { Text(if (room.muted) "Включить звук" else "Отключить звук") },
                onClick = {
                    menuExpanded = false
                    onToggleMute()
                }
            )

            DropdownMenuItem(
                text = { Text(if (room.archived) "Разархивировать" else "Архивировать") },
                onClick = {
                    menuExpanded = false
                    onToggleArchive()
                }
            )
        }
    }
}
