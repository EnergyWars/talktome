package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class OutgoingMessageStatus {
    DRAFT,
    NEGOTIATING,
    SENT,
    REJECTED,
}

@Entity(tableName = "outgoing_messages")
data class OutgoingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftText: String,
    val status: OutgoingMessageStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val friendOpinionsJson: String? = null,
    val rejectionCount: Int = 0,
    val lastRejectionReason: String? = null,
    val serverMessageId: String? = null,
)

@Dao
interface OutgoingMessageDao {

    @Query("SELECT * FROM outgoing_messages ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<OutgoingMessageEntity>>

    @Query("SELECT * FROM outgoing_messages WHERE id = :id")
    suspend fun getById(id: Long): OutgoingMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: OutgoingMessageEntity): Long

    @Delete
    suspend fun delete(message: OutgoingMessageEntity)
}
