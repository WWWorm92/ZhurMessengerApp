package com.pulsemessenger.android.core.offline

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_dialogs")
data class CachedDialogEntity(
    @androidx.room.PrimaryKey val peerUserId: Long,
    val payloadJson: String,
    val sortAt: String?,
    val updatedAt: Long,
)

@Entity(tableName = "cached_rooms")
data class CachedRoomEntity(
    @androidx.room.PrimaryKey val roomId: Long,
    val payloadJson: String,
    val sortAt: String?,
    val updatedAt: Long,
)

@Entity(tableName = "cached_dm_messages", primaryKeys = ["peerUserId", "messageId"])
data class CachedDmMessageEntity(
    val peerUserId: Long,
    val messageId: Long,
    val payloadJson: String,
    val createdAt: String,
)

@Entity(tableName = "cached_room_messages", primaryKeys = ["roomId", "messageId"])
data class CachedRoomMessageEntity(
    val roomId: Long,
    val messageId: Long,
    val payloadJson: String,
    val createdAt: String,
)

@Entity(tableName = "pending_outbox")
data class PendingOutboxEntity(
    @androidx.room.PrimaryKey val id: String,
    val scope: String,
    val targetId: Long,
    val text: String,
    val replyToMessageId: Long?,
    val attachmentPath: String?,
    val attachmentMimeType: String?,
    val attachmentFileName: String?,
    val attachmentKind: String,
    val clientMessageId: String,
    val localMessageId: Long?,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Dao
interface OfflineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDialogs(items: List<CachedDialogEntity>)

    @Query("SELECT * FROM cached_dialogs ORDER BY sortAt DESC")
    suspend fun dialogs(): List<CachedDialogEntity>

    @Query("DELETE FROM cached_dialogs")
    suspend fun clearDialogs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRooms(items: List<CachedRoomEntity>)

    @Query("SELECT * FROM cached_rooms ORDER BY sortAt DESC")
    suspend fun rooms(): List<CachedRoomEntity>

    @Query("DELETE FROM cached_rooms")
    suspend fun clearRooms()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDmMessages(items: List<CachedDmMessageEntity>)

    @Query("SELECT * FROM cached_dm_messages WHERE peerUserId = :peerUserId ORDER BY messageId ASC")
    suspend fun dmMessages(peerUserId: Long): List<CachedDmMessageEntity>

    @Query("DELETE FROM cached_dm_messages WHERE peerUserId = :peerUserId AND messageId >= 0")
    suspend fun clearServerDmMessages(peerUserId: Long)

    @Query("DELETE FROM cached_dm_messages WHERE peerUserId = :peerUserId")
    suspend fun clearDmMessages(peerUserId: Long)

    @Query("DELETE FROM cached_dm_messages WHERE peerUserId = :peerUserId AND messageId = :messageId")
    suspend fun deleteDmMessage(peerUserId: Long, messageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoomMessages(items: List<CachedRoomMessageEntity>)

    @Query("SELECT * FROM cached_room_messages WHERE roomId = :roomId ORDER BY messageId ASC")
    suspend fun roomMessages(roomId: Long): List<CachedRoomMessageEntity>

    @Query("DELETE FROM cached_room_messages WHERE roomId = :roomId AND messageId >= 0")
    suspend fun clearServerRoomMessages(roomId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(item: PendingOutboxEntity)

    @Query("SELECT * FROM pending_outbox ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pendingOutbox(limit: Int = 20): List<PendingOutboxEntity>

    @Query("DELETE FROM pending_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String)

    @Query("UPDATE pending_outbox SET attempts = :attempts, lastError = :lastError WHERE id = :id")
    suspend fun updateOutboxAttempt(id: String, attempts: Int, lastError: String?)
}

@Database(
    entities = [
        CachedDialogEntity::class,
        CachedRoomEntity::class,
        CachedDmMessageEntity::class,
        CachedRoomMessageEntity::class,
        PendingOutboxEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ZhuravlikOfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        private val instances = mutableMapOf<Long, ZhuravlikOfflineDatabase>()

        fun get(context: Context, ownerUserId: Long): ZhuravlikOfflineDatabase {
            val safeOwnerId = ownerUserId.coerceAtLeast(0L)
            return synchronized(this) {
                instances[safeOwnerId] ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZhuravlikOfflineDatabase::class.java,
                    "zhuravlik-offline-$safeOwnerId.db",
                ).fallbackToDestructiveMigration().build().also {
                    instances[safeOwnerId] = it
                }
            }
        }
    }
}
