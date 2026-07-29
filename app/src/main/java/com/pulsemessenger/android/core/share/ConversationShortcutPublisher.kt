package com.pulsemessenger.android.core.share

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.pulsemessenger.android.MainActivity
import com.pulsemessenger.android.R
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.RoomDto

object ConversationShortcutPublisher {
    const val SHARE_CATEGORY = "com.pulsemessenger.android.category.SHARE_TARGET"

    fun shortcutId(scope: String, targetId: Long): String = "share-${if (scope == "room") "room" else "dm"}-$targetId"

    fun parseShortcutId(value: String?): Pair<String, Long>? {
        val match = Regex("^share-(dm|room)-(\\d+)$").matchEntire(value.orEmpty()) ?: return null
        val targetId = match.groupValues[2].toLongOrNull() ?: return null
        return match.groupValues[1] to targetId
    }

    fun publishDialogs(context: Context, users: List<DialogUserDto>) {
        val shortcuts = users
            .sortedWith(compareByDescending<DialogUserDto> { it.isSaved || it.pinned }.thenByDescending { it.lastMessageAt.orEmpty() })
            .take(4)
            .mapIndexed { index, user ->
                buildShortcut(
                    context = context,
                    id = shortcutId("dm", user.id),
                    label = user.displayName.ifBlank { user.username },
                    rank = index,
                    person = Person.Builder()
                        .setName(user.displayName.ifBlank { user.username })
                        .setKey("user:${user.id}")
                        .setImportant(user.pinned || user.isSaved)
                        .build(),
                )
            }
        shortcuts.forEach { shortcut ->
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        }
    }

    fun publishRooms(context: Context, rooms: List<RoomDto>) {
        val shortcuts = rooms
            .filter { it.joined }
            .sortedWith(compareByDescending<RoomDto> { it.pinned }.thenByDescending { it.lastMessageAt.orEmpty() })
            .take(3)
            .mapIndexed { index, room ->
                buildShortcut(
                    context = context,
                    id = shortcutId("room", room.id),
                    label = "# ${room.name}",
                    rank = index + 4,
                    person = null,
                )
            }
        shortcuts.forEach { shortcut ->
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        }
    }

    fun ensureNotificationShortcut(
        context: Context,
        scope: String,
        targetId: Long,
        title: String,
        senderName: String,
    ) {
        val shortcut = buildShortcut(
            context = context,
            id = shortcutId(scope, targetId),
            label = title.ifBlank { senderName.ifBlank { "Zhuravlik" } },
            rank = 0,
            person = if (scope == "dm") {
                Person.Builder()
                    .setName(senderName.ifBlank { title.ifBlank { "Собеседник" } })
                    .setKey("user:$targetId")
                    .build()
            } else null,
        )
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }

    private fun buildShortcut(
        context: Context,
        id: String,
        label: String,
        rank: Int,
        person: Person?,
    ): ShortcutInfoCompat {
        val target = parseShortcutId(id)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            target?.let { (scope, targetId) ->
                putExtra("url", if (scope == "room") "/?room=$targetId" else "/?dm=$targetId")
            }
        }
        val builder = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label.take(40))
            .setLongLabel(label.take(80))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher_round))
            .setIntent(intent)
            .setCategories(setOf(SHARE_CATEGORY))
            .setLongLived(true)
            .setRank(rank)
        if (person != null) builder.setPerson(person)
        return builder.build()
    }
}
