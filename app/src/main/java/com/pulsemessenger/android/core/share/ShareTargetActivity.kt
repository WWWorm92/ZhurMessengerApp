package com.pulsemessenger.android.core.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.offline.OfflineStore
import com.pulsemessenger.android.core.offline.OutboxQueue
import com.pulsemessenger.android.ui.theme.PulseAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class ShareTargetActivity : ComponentActivity() {
    private val payload by lazy { SharedPayload.fromIntent(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseAndroidTheme {
                ShareTargetContent(
                    payload = payload,
                    shortcutTarget = ConversationShortcutPublisher.parseShortcutId(
                        intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
                            ?: intent.getStringExtra("shortcut_id")
                    ),
                    onSelected = { scope, targetId -> enqueueAndFinish(scope, targetId) },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun enqueueAndFinish(scope: String, targetId: Long) {
        lifecycleScopeCompat {
            runCatching {
                if (payload.uris.isEmpty()) {
                    OutboxQueue.enqueueText(
                        context = this@ShareTargetActivity,
                        scope = scope,
                        targetId = targetId,
                        text = payload.text.ifBlank { "Поделиться" },
                    )
                } else {
                    payload.uris.forEachIndexed { index, uri ->
                        OutboxQueue.enqueueSharedUri(
                            context = this@ShareTargetActivity,
                            scope = scope,
                            targetId = targetId,
                            uri = uri,
                            caption = if (index == 0) payload.text else "",
                        )
                    }
                }
            }.onSuccess {
                Toast.makeText(this@ShareTargetActivity, "Добавлено в очередь отправки", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this@ShareTargetActivity, error.message ?: "Не удалось поделиться", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun lifecycleScopeCompat(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }
}

private data class SharedPayload(
    val text: String,
    val uris: List<Uri>,
) {
    companion object {
        @Suppress("DEPRECATION")
        fun fromIntent(intent: Intent): SharedPayload {
            val text = listOf(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
            ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString("\n")

            val uris = buildList {
                if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                    addAll(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
                }
                val clip = intent.clipData
                if (clip != null) {
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index).uri?.let(::add)
                    }
                }
            }.distinctBy { it.toString() }
            return SharedPayload(text = text, uris = uris)
        }
    }
}

private data class ShareDestination(
    val scope: String,
    val targetId: Long,
    val title: String,
    val subtitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareTargetContent(
    payload: SharedPayload,
    shortcutTarget: Pair<String, Long>?,
    onSelected: (String, Long) -> Unit,
    onClose: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var destinations by remember { mutableStateOf<List<ShareDestination>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shortcutTarget) {
        if (shortcutTarget != null) {
            onSelected(shortcutTarget.first, shortcutTarget.second)
            return@LaunchedEffect
        }
        val app = PulseApp.instance
        val store = OfflineStore.get(app.applicationContext)
        val loaded = withContext(Dispatchers.IO) {
            val token = app.sessionStore.currentToken().trim()
            val users = if (token.isBlank()) {
                store.loadDialogs()
            } else {
                try {
                    val response = app.networkProvider.api.users("Bearer $token")
                    if (response.isSuccessful) response.body()?.users.orEmpty()
                    else store.loadDialogs()
                } catch (_: Throwable) {
                    store.loadDialogs()
                }
            }
            val rooms = if (token.isBlank()) {
                store.loadRooms()
            } else {
                try {
                    val response = app.networkProvider.api.rooms("Bearer $token")
                    if (response.isSuccessful) response.body()?.rooms.orEmpty()
                    else store.loadRooms()
                } catch (_: Throwable) {
                    store.loadRooms()
                }
            }
            buildList {
                users.forEach { user ->
                    add(ShareDestination("dm", user.id, user.displayName, if (user.isSaved) "Личные заметки" else "@${user.username}"))
                }
                rooms.filter { it.joined }.forEach { room ->
                    add(ShareDestination("room", room.id, "# ${room.name}", "Комната"))
                }
            }
        }
        destinations = loaded
        loading = false
        if (loaded.isEmpty()) error = "Не удалось загрузить чаты"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Поделиться в Zhuravlik") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Text(
                text = when {
                    payload.uris.size > 1 -> "Файлов: ${payload.uris.size}"
                    payload.uris.size == 1 -> "Один файл"
                    else -> payload.text.take(120).ifBlank { "Сообщение" }
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Найти чат") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text("Закрыть", modifier = Modifier.clickable(onClick = onClose), color = MaterialTheme.colorScheme.primary)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val filtered = destinations.filter {
                        query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true)
                    }
                    items(filtered, key = { "${it.scope}:${it.targetId}" }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(item.scope, item.targetId) }.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Отправить", color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
