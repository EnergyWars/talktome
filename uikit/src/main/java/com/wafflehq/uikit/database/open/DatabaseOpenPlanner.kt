package com.wafflehq.uikit.database.open

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.wafflehq.uikit.database.crypto.KeystoreKeyWrapper
import com.wafflehq.uikit.database.state.DatabaseKeyRecoveryState
import com.wafflehq.uikit.database.state.EncryptionStateStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

data class DatabaseOpenPlan(
    val fileName: String,
    val openHelperFactory: SupportSQLiteOpenHelper.Factory?,
    val keyUnavailable: Boolean,
)

class DatabaseOpenPlanner(
    private val context: Context,
    private val databaseFileName: String,
    private val legacyDatabaseFileName: String?,
    private val recoveryPlaceholderFileName: String,
    private val stateStore: EncryptionStateStore,
    private val keyWrapper: KeystoreKeyWrapper,
    private val probe: EncryptedDatabaseProbe = SqlCipherDatabaseProbe,
) {

    fun plan(): DatabaseOpenPlan {
        renameLegacyDatabaseFileIfNeeded()
        val dek = resolveDek()
        val keyUnavailable = DatabaseKeyRecoveryState.keyUnavailable
        val fileName = if (keyUnavailable) recoveryPlaceholderFileName else databaseFileName
        return DatabaseOpenPlan(
            fileName = fileName,
            openHelperFactory = dek?.let { SupportOpenHelperFactory(it) },
            keyUnavailable = keyUnavailable,
        )
    }

    private fun resolveDek(): ByteArray? {
        if (!stateStore.isEncrypted) return null
        val wrappedDek = stateStore.readWrappedDek()
        if (wrappedDek == null) {
            DatabaseKeyRecoveryState.keyUnavailable = true
            return null
        }
        return try {
            val unwrapped = keyWrapper.unwrap(wrappedDek)
            if (probe.canOpen(context.getDatabasePath(databaseFileName), unwrapped)) {
                unwrapped
            } else {
                DatabaseKeyRecoveryState.keyUnavailable = true
                null
            }
        } catch (e: Exception) {
            DatabaseKeyRecoveryState.keyUnavailable = true
            null
        }
    }

    private fun renameLegacyDatabaseFileIfNeeded() {
        val legacyName = legacyDatabaseFileName ?: return
        val legacyFile = context.getDatabasePath(legacyName)
        val newFile = context.getDatabasePath(databaseFileName)
        if (!legacyFile.exists() || newFile.exists()) return

        legacyFile.renameTo(newFile)
        LEGACY_AUX_SUFFIXES.forEach { suffix ->
            val legacyAuxFile = File(legacyFile.path + suffix)
            if (legacyAuxFile.exists()) {
                legacyAuxFile.renameTo(File(newFile.path + suffix))
            }
        }
    }

    private companion object {
        val LEGACY_AUX_SUFFIXES = listOf("-wal", "-shm", "-journal")
    }
}
