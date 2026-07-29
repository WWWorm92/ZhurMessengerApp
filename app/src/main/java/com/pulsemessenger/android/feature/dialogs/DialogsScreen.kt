package com.pulsemessenger.android.feature.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.ui.ChatListFilterBar
import com.pulsemessenger.android.ui.ChatListFilterItem
import com.pulsemessenger.android.ui.ChatListRowCard
import com.pulsemessenger.android.ui.GlassCard
import com.pulsemessenger.android.ui.ListSkeletonCard
import com.pulsemessenger.android.ui.formatChatListTime

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
            .padding(20.dp),
    ) {
        ChatListFilterBar(
            selected = selectedFilter,
            onSelected = { selectedFilter = it },
            items = listOf(
                ChatListFilterItem(DialogListFilter.All, "Все"),
                ChatListFilterItem(
                    DialogListFilter.Unread,
                    "Новые",
                    viewModel.users.count { !it.archived && it.unreadCount > 0 },
                ),
                ChatListFilterItem(
                    DialogListFilter.Archived,
                    "Архив",
                    viewModel.users.count { it.archived },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading && viewModel.users.isEmpty() -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(6) { ListSkeletonCard() }
                }
            }

            !viewModel.error.isNullOrBlank() && viewModel.users.isEmpty() -> {
                GlassCard(radius = 28, padding = 18) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Ошибка", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(viewModel.error.orEmpty())
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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (filteredUsers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlassCard(radius = 28, padding = 22) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Пусто",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
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
    val hasDraft = user.draftText.isNotBlank() || user.draftReplyToMessageId != null
    val subtitle = when {
        hasDraft && user.draftText.isNotBlank() -> "Черновик: ${user.draftText}"
        hasDraft -> "Черновик: ответ на сообщение"
        user.lastMessage.isNotBlank() -> user.lastMessage
        user.isSaved -> "Заметки, ссылки и файлы"
        else -> "@${user.username}"
    }
    val displayTimestamp = user.lastMessageAt

    Box {
        ChatListRowCard(
            title = user.displayName,
            subtitle = subtitle,
            avatarUrl = user.avatarUrl,
            time = displayTimestamp
                ?.takeIf { it.isNotBlank() }
                ?.let(::formatChatListTime)
                .orEmpty(),
            unreadCount = user.unreadCount,
            online = user.online,
            pinned = user.pinned,
            muted = user.muted,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (!user.isSaved) {
                DropdownMenuItem(
                    text = { Text(if (user.pinned) "Открепить" else "Закрепить") },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (user.muted) "Включить звук" else "Отключить звук") },
                    onClick = {
                        menuExpanded = false
                        onToggleMute()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (user.archived) "Разархивировать" else "Архивировать") },
                    onClick = {
                        menuExpanded = false
                        onToggleArchive()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Очистить диалог", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onClearDialog()
                },
            )
        }
    }
}
