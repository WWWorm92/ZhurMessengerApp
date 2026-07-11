package com.pulsemessenger.android.feature.dialogs

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.ui.DialogAvatar
import com.pulsemessenger.android.ui.GlassCard
import com.pulsemessenger.android.ui.ListSkeletonCard
import com.pulsemessenger.android.ui.OnlineDot
import com.pulsemessenger.android.ui.UnreadBadge
import com.pulsemessenger.android.ui.formatDateTime
import com.pulsemessenger.android.ui.formatChatListTime
import com.pulsemessenger.android.ui.formatMessageTime
import com.pulsemessenger.android.ui.formatDayLabel
import androidx.compose.foundation.lazy.itemsIndexed

private enum class DialogListFilter {
    All,
    Unread,
    Archived,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogsScreen(
    viewModel: DialogsViewModel,


    onOpenDialog: (DialogUserDto) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(DialogListFilter.All) }
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val filteredUsers = when (selectedFilter) {
        DialogListFilter.All -> viewModel.users.filterNot { it.archived }
        DialogListFilter.Unread -> viewModel.users.filter { !it.archived && it.unreadCount > 0 }
        DialogListFilter.Archived -> viewModel.users.filter { it.archived }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedFilter == DialogListFilter.All,
                onClick = { selectedFilter = DialogListFilter.All },
                label = { Text("Все") },
            )
            FilterChip(
                selected = selectedFilter == DialogListFilter.Unread,
                onClick = { selectedFilter = DialogListFilter.Unread },
                label = { Text("Непрочитанные") },
            )
            FilterChip(
                selected = selectedFilter == DialogListFilter.Archived,
                onClick = { selectedFilter = DialogListFilter.Archived },
                label = { Text("Архив") },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(6) {
                        ListSkeletonCard()
                    }
                }
            }

            !viewModel.error.isNullOrBlank() -> {
                GlassCard(radius = 28, padding = 18) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Ошибка", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(viewModel.error ?: "")
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.Button(onClick = viewModel::load) {
                            Text("Повторить")
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
                    if (filteredUsers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlassCard(radius = 28, padding = 22) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Пусто", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when (selectedFilter) {
                                            DialogListFilter.All -> "Диалогов пока нет"
                                            DialogListFilter.Unread -> "Непрочитанных диалогов нет"
                                            DialogListFilter.Archived -> "Архив пуст"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filteredUsers, key = { it.id }) { user ->
                                DialogUserCard(
                                    user = user,
                                    onClick = { onOpenDialog(user) },
                                    onTogglePin = { viewModel.togglePin(user.id) },
                                    onToggleMute = { viewModel.toggleMute(user.id) },
                                    onToggleArchive = { viewModel.toggleArchive(user.id) },
                                    onClearDialog = { viewModel.clearDialog(user.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogUserCard(
    user: DialogUserDto,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
    onClearDialog: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "dialogCardScale")
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
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialogAvatar(user.displayName, avatarUrl = user.avatarUrl)
            Spacer(modifier = Modifier.padding(horizontal = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buildString {
                            if (user.pinned) append("📌 ")
                            if (user.muted) append("🔇 ")
                            append(user.displayName)
                        },
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (user.online) OnlineDot()
                }
                Text(
                    if (user.lastMessage.isNotBlank()) user.lastMessage else "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!user.lastMessageAt.isNullOrBlank()) {
                    Text(
                        formatChatListTime(user.lastMessageAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            UnreadBadge(user.unreadCount)
            }
        }
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(if (user.pinned) "Открепить" else "Закрепить") },
            onClick = { menuExpanded = false; onTogglePin() }
        )
        DropdownMenuItem(
            text = { Text(if (user.muted) "Включить звук" else "Отключить звук") },
            onClick = { menuExpanded = false; onToggleMute() }
        )
        DropdownMenuItem(
            text = { Text(if (user.archived) "Разархивировать" else "Архивировать") },
            onClick = { menuExpanded = false; onToggleArchive() }
        )
        DropdownMenuItem(
            text = { Text("Очистить диалог", color = MaterialTheme.colorScheme.error) },
            onClick = { menuExpanded = false; onClearDialog() }
        )
    }
    }
}
