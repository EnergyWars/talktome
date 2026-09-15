package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class InboxMessageStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
}

@Entity(tableName = "inbox_messages")
data class InboxMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val status: InboxMessageStatus,
    val mediatorFeedback: String? = null,
    val receivedAt: Long,
    val serverMessageId: String? = null,
    val senderDeviceId: String? = null,
)

@Dao
interface InboxMessageDao {

    @Query("SELECT * FROM inbox_messages WHERE status = :status ORDER BY receivedAt DESC")
    fun observeByStatus(status: InboxMessageStatus): Flow<List<InboxMessageEntity>>

    @Query("SELECT * FROM inbox_messages ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<InboxMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: InboxMessageEntity): Long

    @Delete
    suspend fun delete(message: InboxMessageEntity)
}
