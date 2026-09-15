package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val filterText: String = "",
    val selfDescription: String = "",
    val partnerDescription: String = "",
    val updatedAt: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = :id")
    fun observe(id: Int): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: Int): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}
