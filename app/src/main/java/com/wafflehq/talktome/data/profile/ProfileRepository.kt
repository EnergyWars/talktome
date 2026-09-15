package com.wafflehq.talktome.data.profile

import com.wafflehq.talktome.data.db.ProfileDao
import com.wafflehq.talktome.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Profile(
    val filterText: String,
    val selfDescription: String,
    val partnerDescription: String,
) {
    companion object {
        val Empty = Profile(filterText = "", selfDescription = "", partnerDescription = "")
    }
}

@Singleton
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val clock: () -> Long,
) {

    @Inject
    constructor(profileDao: ProfileDao) : this(profileDao, System::currentTimeMillis)

    val profile: Flow<Profile> = profileDao.observe(ProfileEntity.SINGLETON_ID).map { entity ->
        entity?.let {
            Profile(
                filterText = it.filterText,
                selfDescription = it.selfDescription,
                partnerDescription = it.partnerDescription,
            )
        } ?: Profile.Empty
    }

    suspend fun setFilterText(text: String) = update { it.copy(filterText = text) }

    suspend fun setSelfDescription(text: String) = update { it.copy(selfDescription = text) }

    suspend fun setPartnerDescription(text: String) = update { it.copy(partnerDescription = text) }

    private suspend fun update(transform: (ProfileEntity) -> ProfileEntity) {
        val current = profileDao.get(ProfileEntity.SINGLETON_ID) ?: ProfileEntity()
        val updated = transform(current).copy(updatedAt = clock())
        profileDao.upsert(updated)
    }
}
