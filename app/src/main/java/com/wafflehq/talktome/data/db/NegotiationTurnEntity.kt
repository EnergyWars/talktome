package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class NegotiationTurnSender {
    USER,
    MEDIATOR,
}

@Entity(
    tableName = "negotiation_turns",
    indices = [Index(value = ["outgoingMessageId"])],
)
data class NegotiationTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outgoingMessageId: Long,
    val sender: NegotiationTurnSender,
    val text: String,
    val createdAt: Long,
)

@Dao
interface NegotiationTurnDao {

    @Query("SELECT * FROM negotiation_turns WHERE outgoingMessageId = :outgoingMessageId ORDER BY createdAt ASC")
    fun observeForMessage(outgoingMessageId: Long): Flow<List<NegotiationTurnEntity>>

    @Query("SELECT * FROM negotiation_turns WHERE outgoingMessageId = :outgoingMessageId ORDER BY createdAt ASC")
    suspend fun getForMessage(outgoingMessageId: Long): List<NegotiationTurnEntity>

    @Insert
    suspend fun insert(turn: NegotiationTurnEntity): Long

    @Query("DELETE FROM negotiation_turns WHERE outgoingMessageId = :outgoingMessageId")
    suspend fun deleteForMessage(outgoingMessageId: Long)
}
