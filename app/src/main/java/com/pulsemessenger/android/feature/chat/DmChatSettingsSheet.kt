package com.pulsemessenger.android.feature.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsemessenger.android.core.network.ChatPreferencesDto
import com.pulsemessenger.android.core.network.ChatPrefsRequest
import com.pulsemessenger.android.core.sync.ChatSyncRepository
import com.pulsemessenger.android.core.sync.ChatPreferencesBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private data class Choice(val value: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DmChatSettingsSheet(
    peerName: String,
    peerId: Long,
    initial: ChatPreferencesDto,
    context: Context,
    repository: ChatSyncRepository,
    onSaved: (ChatPreferencesDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var pinned by remember(initial) { mutableStateOf(initial.pinned) }
    var archived by remember(initial) { mutableStateOf(initial.archived) }
    var notificationPreview by remember(initial) { mutableStateOf(initial.notificationPreview) }
    var muteMode by remember(initial) {
        mutableStateOf(
            when {
                initial.mutedForever -> "forever"
                !initial.muteUntil.isNullOrBlank() -> "keep"
                else -> "off"
            }
        )
    }
    var bubbleColor by remember(initial) {
        mutableStateOf(
            initial.bubbleColor
                .takeUnless { it.isBlank() || it == "default" }
                ?: "blue"
        )
    }
    var wallpaper by remember(initial) { mutableStateOf(initial.wallpaper) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun muteUntil(mode: String): String? {
        val seconds = when (mode) {
            "1h" -> 3_600L
            "8h" -> 28_800L
            "1d" -> 86_400L
            else -> return if (mode == "keep") initial.muteUntil else null
        }
        return Instant.now().plusSeconds(seconds).toString()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Настройки чата", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(peerName, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SettingSwitch("Закрепить чат", pinned) { pinned = it }
            SettingSwitch("Архивировать", archived) { archived = it }
            SettingSwitch("Показывать текст уведомлений", notificationPreview) { notificationPreview = it }

            Text("Уведомления", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                choices = listOf(
                    Choice("off", "Включены"),
                    Choice("1h", "1 час"),
                    Choice("8h", "8 часов"),
                    Choice("1d", "Сутки"),
                    Choice("forever", "Навсегда"),
                ) + if (muteMode == "keep") listOf(Choice("keep", "До выбранного времени")) else emptyList(),
                selected = muteMode,
                onSelect = { muteMode = it },
            )

            Text("Цвет исходящих сообщений", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                choices = listOf(
                    Choice("blue", "Синий · по умолчанию"),
                    Choice("green", "Зелёный"), Choice("purple", "Фиолетовый"),
                    Choice("orange", "Оранжевый"), Choice("graphite", "Графит"),
                ),
                selected = bubbleColor,
                onSelect = { bubbleColor = it },
            )

            Text("Фон чата", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                choices = listOf(
                    Choice("default", "Обычный"), Choice("clean", "Чистый"),
                    Choice("dots", "Точки"), Choice("gradient", "Градиент"),
                    Choice("night", "Ночной"),
                ),
                selected = wallpaper,
                onSelect = { wallpaper = it },
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    saving = true
                    error = null
                    coroutineScope.launch {
                        repository.updatePreferences(
                            ChatPrefsRequest(
                                scope = "dm",
                                targetId = peerId,
                                pinned = pinned,
                                archived = archived,
                                muted = muteMode == "forever",
                                muteUntil = muteUntil(muteMode),
                                notificationPreview = notificationPreview,
                                wallpaper = wallpaper,
                                bubbleColor = bubbleColor,
                            )
                        ).onSuccess { saved ->
                            ChatPreferencesBus.publish(saved)
                            onSaved(saved)
                            onDismiss()
                        }.onFailure { failure ->
                            error = failure.message ?: "Не удалось сохранить настройки"
                        }
                        saving = false
                    }
                },
            ) {
                Text(if (saving) "Сохраняем…" else "Сохранить")
            }

            HorizontalDivider()

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            context.cacheDir.listFiles()?.forEach { file -> runCatching { file.deleteRecursively() } }
                        }
                    }
                },
            ) {
                Text("Очистить локальный кэш")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(
    choices: List<Choice>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices, key = { it.value }) { choice ->
            FilterChip(
                selected = selected == choice.value,
                onClick = { onSelect(choice.value) },
                label = { Text(choice.label) },
            )
        }
    }
}
