package com.pulsemessenger.android.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.pulsemessenger.android.core.sync.ChatSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DmDraftSyncEffect(
    peerId: Long,
    viewModel: DmChatViewModel,
    repository: ChatSyncRepository,
) {
    var loaded by remember(peerId) { mutableStateOf(false) }
    var remoteUpdatedAt by remember(peerId) { mutableStateOf<String?>(null) }
    var lastSyncedContent by remember(peerId) { mutableStateOf("") }
    var lastSyncedReplyId by remember(peerId) { mutableStateOf<Long?>(null) }
    val latestContent by rememberUpdatedState(viewModel.draft)
    val latestReplyId by rememberUpdatedState(viewModel.replyToMessageId)

    fun applyRemote(content: String, replyId: Long?) {
        viewModel.draft = content
        viewModel.replyToMessageId = replyId
        viewModel.replyToMessageContent = replyId
            ?.let { id -> viewModel.messages.firstOrNull { it.id == id }?.content }
            .orEmpty()
        lastSyncedContent = content
        lastSyncedReplyId = replyId
    }

    LaunchedEffect(peerId) {
        loaded = false
        val contentAtRequestStart = viewModel.draft
        val replyAtRequestStart = viewModel.replyToMessageId

        repository.loadDraft("dm", peerId)
            .onSuccess { draft ->
                val content = draft?.content.orEmpty()
                val replyId = draft?.replyToMessageId
                remoteUpdatedAt = draft?.updatedAt

                val composerWasNotChanged =
                    viewModel.draft == contentAtRequestStart &&
                        viewModel.replyToMessageId == replyAtRequestStart

                if (composerWasNotChanged) {
                    applyRemote(content, replyId)
                } else {
                    // The user started typing before the server replied.
                    // Remember the remote revision, then let the debounce block
                    // upload the newer local text instead of erasing it.
                    lastSyncedContent = content
                    lastSyncedReplyId = replyId
                }
            }
        loaded = true
    }

    LaunchedEffect(peerId, viewModel.messages.size, viewModel.replyToMessageId) {
        val replyId = viewModel.replyToMessageId ?: return@LaunchedEffect
        if (viewModel.replyToMessageContent.isBlank()) {
            viewModel.replyToMessageContent = viewModel.messages
                .firstOrNull { it.id == replyId }
                ?.content
                .orEmpty()
        }
    }

    LaunchedEffect(peerId, viewModel.draft, viewModel.replyToMessageId, loaded) {
        if (!loaded) return@LaunchedEffect
        val content = viewModel.draft
        val replyId = viewModel.replyToMessageId
        if (content == lastSyncedContent && replyId == lastSyncedReplyId) return@LaunchedEffect

        delay(700)
        repository.saveDraft("dm", peerId, content, replyId)
            .onSuccess { saved ->
                lastSyncedContent = saved?.content.orEmpty()
                lastSyncedReplyId = saved?.replyToMessageId
                remoteUpdatedAt = saved?.updatedAt
            }
    }

    LaunchedEffect(peerId, loaded) {
        if (!loaded) return@LaunchedEffect
        while (true) {
            delay(4_000)
            repository.loadDraft("dm", peerId)
                .onSuccess { remote ->
                    val nextContent = remote?.content.orEmpty()
                    val nextReply = remote?.replyToMessageId
                    val nextUpdatedAt = remote?.updatedAt
                    val localUnchanged =
                        viewModel.draft == lastSyncedContent &&
                            viewModel.replyToMessageId == lastSyncedReplyId

                    if (nextUpdatedAt != remoteUpdatedAt && localUnchanged) {
                        remoteUpdatedAt = nextUpdatedAt
                        applyRemote(nextContent, nextReply)
                    }
                }
        }
    }

    DisposableEffect(peerId) {
        onDispose {
            val content = latestContent
            val replyId = latestReplyId
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                repository.saveDraft("dm", peerId, content, replyId)
            }
        }
    }
}
