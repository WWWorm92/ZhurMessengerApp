package com.pulsemessenger.android.core.offline

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.pulsemessenger.android.PulseApp
import com.pulsemessenger.android.core.network.DialogUserDto
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.RoomDto
import com.pulsemessenger.android.core.network.RoomMessageDto
import org.json.JSONObject

class OfflineStore private constructor(
    context: Context,
    ownerUserId: Long,
) {
    private val dao = ZhuravlikOfflineDatabase.get(context, ownerUserId).offlineDao()
    private val gson = Gson()

    suspend fun cacheDialogs(users: List<DialogUserDto>) {
        dao.clearDialogs()
        if (users.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertDialogs(users.map { user ->
            CachedDialogEntity(
                peerUserId = user.id,
                payloadJson = gson.toJson(user),
                sortAt = user.lastMessageAt,
                updatedAt = now,
            )
        })
    }

    suspend fun loadDialogs(): List<DialogUserDto> {
        return dao.dialogs().mapNotNull { entity ->
            runCatching { gson.fromJson(entity.payloadJson, DialogUserDto::class.java) }.getOrNull()
        }.sortedWith(
            compareByDescending<DialogUserDto> { it.pinned || it.isSaved }
                .thenByDescending { it.lastMessageAt.orEmpty() }
                .thenBy { it.displayName.lowercase() }
        )
    }

    suspend fun cacheRooms(rooms: List<RoomDto>) {
        dao.clearRooms()
        if (rooms.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertRooms(rooms.map { room ->
            CachedRoomEntity(
                roomId = room.id,
                payloadJson = gson.toJson(room),
                sortAt = room.lastMessageAt,
                updatedAt = now,
            )
        })
    }

    suspend fun loadRooms(): List<RoomDto> {
        return dao.rooms().mapNotNull { entity ->
            runCatching { gson.fromJson(entity.payloadJson, RoomDto::class.java) }.getOrNull()
        }.sortedWith(
            compareByDescending<RoomDto> { it.pinned }
                .thenByDescending { it.lastMessageAt.orEmpty() }
                .thenBy { it.name.lowercase() }
        )
    }

    suspend fun replaceDmMessages(peerUserId: Long, messages: List<DmMessageDto>) {
        dao.clearServerDmMessages(peerUserId)
        upsertDmMessages(peerUserId, messages)
    }

    suspend fun upsertDmMessages(peerUserId: Long, messages: List<DmMessageDto>) {
        if (messages.isEmpty()) return
        dao.upsertDmMessages(messages.map { message ->
            CachedDmMessageEntity(
                peerUserId = peerUserId,
                messageId = message.id,
                payloadJson = gson.toJson(message),
                createdAt = message.createdAt,
            )
        })
    }

    suspend fun loadDmMessages(
        peerUserId: Long,
        beforeId: Long? = null,
        limit: Int = 60,
    ): List<DmMessageDto> {
        val all = dao.dmMessages(peerUserId).mapNotNull { entity ->
            runCatching { gson.fromJson(entity.payloadJson, DmMessageDto::class.java) }.getOrNull()
        }
        val filtered = if (beforeId != null) all.filter { it.id < beforeId && it.id >= 0 } else all
        return filtered.sortedBy { it.id }.takeLast(limit)
    }

    suspend fun pendingDmMessages(peerUserId: Long): List<DmMessageDto> {
        return dao.dmMessages(peerUserId).mapNotNull { entity ->
            if (entity.messageId >= 0) return@mapNotNull null
            runCatching { gson.fromJson(entity.payloadJson, DmMessageDto::class.java) }.getOrNull()
        }.sortedBy { it.id }
    }

    suspend fun saveLocalDmMessage(peerUserId: Long, message: DmMessageDto) {
        upsertDmMessages(peerUserId, listOf(message))
    }

    suspend fun removeLocalDmMessage(peerUserId: Long, messageId: Long) {
        dao.deleteDmMessage(peerUserId, messageId)
    }

    suspend fun clearDmMessages(peerUserId: Long) {
        dao.clearDmMessages(peerUserId)
    }

    suspend fun markLocalDmFailed(peerUserId: Long, messageId: Long, error: String) {
        val current = dao.dmMessages(peerUserId)
            .firstOrNull { it.messageId == messageId }
            ?: return
        val parsed = runCatching { gson.fromJson(current.payloadJson, DmMessageDto::class.java) }.getOrNull()
            ?: return
        saveLocalDmMessage(
            peerUserId,
            parsed.copy(localSendState = "failed", localError = error),
        )
    }

    suspend fun replaceRoomMessages(roomId: Long, messages: List<RoomMessageDto>) {
        dao.clearServerRoomMessages(roomId)
        upsertRoomMessages(roomId, messages)
    }

    suspend fun upsertRoomMessages(roomId: Long, messages: List<RoomMessageDto>) {
        if (messages.isEmpty()) return
        dao.upsertRoomMessages(messages.map { message ->
            CachedRoomMessageEntity(
                roomId = roomId,
                messageId = message.id,
                payloadJson = gson.toJson(message),
                createdAt = message.createdAt,
            )
        })
    }

    suspend fun loadRoomMessages(roomId: Long, beforeId: Long? = null, limit: Int = 60): List<RoomMessageDto> {
        val all = dao.roomMessages(roomId).mapNotNull { entity ->
            runCatching { gson.fromJson(entity.payloadJson, RoomMessageDto::class.java) }.getOrNull()
        }
        val filtered = if (beforeId != null) all.filter { it.id < beforeId } else all
        return filtered.sortedBy { it.id }.takeLast(limit)
    }

    suspend fun addOutbox(item: PendingOutboxEntity) = dao.upsertOutbox(item)
    suspend fun pendingOutbox(limit: Int = 20): List<PendingOutboxEntity> = dao.pendingOutbox(limit)
    suspend fun deleteOutbox(id: String) = dao.deleteOutbox(id)
    suspend fun updateOutboxAttempt(id: String, attempts: Int, error: String?) =
        dao.updateOutboxAttempt(id, attempts, error)

    companion object {
        private val instances = mutableMapOf<Long, OfflineStore>()

        fun get(context: Context): OfflineStore {
            val ownerUserId = userIdFromToken(
                runCatching { PulseApp.instance.sessionStore.currentToken() }.getOrDefault("")
            ).coerceAtLeast(0L)
            return synchronized(this) {
                instances[ownerUserId] ?: OfflineStore(
                    context.applicationContext,
                    ownerUserId,
                ).also { instances[ownerUserId] = it }
            }
        }

        fun userIdFromToken(token: String): Long {
            if (token.isBlank()) return 0L
            return runCatching {
                val payload = token.split('.').getOrNull(1).orEmpty()
                if (payload.isBlank()) return@runCatching 0L
                val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
                val json = JSONObject(decoded)
                when {
                    json.has("id") -> json.optLong("id")
                    json.has("userId") -> json.optLong("userId")
                    json.has("user_id") -> json.optLong("user_id")
                    else -> json.optString("sub").toLongOrNull() ?: 0L
                }
            }.getOrDefault(0L)
        }
    }
}
