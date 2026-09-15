package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class MediatorAgentRole {
    NEUTRAL_MEDIATOR,
    RECEIVER_GATEKEEPER,
    SENDER_COACH,
    VENTING_COMPANION,
}

@Entity(tableName = "mediator_notes")
data class MediatorNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: MediatorAgentRole,
    val noteText: String,
    val createdAt: Long,
)

@Dao
interface MediatorNoteDao {

    @Query("SELECT * FROM mediator_notes WHERE role = :role ORDER BY createdAt DESC")
    fun observeByRole(role: MediatorAgentRole): Flow<List<MediatorNoteEntity>>

    @Query("SELECT * FROM mediator_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediatorNoteEntity>>

    @Insert
    suspend fun insert(note: MediatorNoteEntity): Long

    @Query("DELETE FROM mediator_notes")
    suspend fun deleteAll()

    @Query("DELETE FROM mediator_notes WHERE role = :role")
    suspend fun deleteByRole(role: MediatorAgentRole)
}
