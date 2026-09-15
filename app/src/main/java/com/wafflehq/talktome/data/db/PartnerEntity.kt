package com.wafflehq.talktome.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val publicKey: String? = null,
    val pairedAt: Long? = null,
    val remoteDeviceId: String? = null,
    val serverBaseUrl: String? = null,
)

@Dao
interface PartnerDao {

    @Query("SELECT * FROM partners LIMIT 1")
    fun observe(): Flow<PartnerEntity?>

    @Query("SELECT * FROM partners LIMIT 1")
    suspend fun get(): PartnerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(partner: PartnerEntity): Long

    @Query("DELETE FROM partners")
    suspend fun deleteAll()
}
