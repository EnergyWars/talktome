package com.wafflehq.talktome.data.db

import android.content.Context
import androidx.room.Room
import com.wafflehq.uikit.database.DatabaseEncryptionDefaults
import com.wafflehq.uikit.database.SqlCipherNativeLibrary
import com.wafflehq.uikit.database.conversion.DatabaseEncryptionConverter
import com.wafflehq.uikit.database.conversion.EncryptionConversionRecovery
import com.wafflehq.uikit.database.crypto.AndroidKeystoreKeyWrapper
import com.wafflehq.uikit.database.crypto.PasswordKeyWrapper
import com.wafflehq.uikit.database.open.DatabaseOpenPlanner
import com.wafflehq.uikit.database.state.EncryptionStateStore
import kotlinx.coroutines.runBlocking

class EncryptedAppDatabaseFactory(
    private val context: Context,
    private val databaseFileName: String = DATABASE_FILE_NAME,
    private val stateStore: EncryptionStateStore = EncryptionStateStore(context),
    private val keyWrapper: AndroidKeystoreKeyWrapper = AndroidKeystoreKeyWrapper(DatabaseEncryptionDefaults.KEK_ALIAS),
) {

    fun create(): AppDatabase {
        SqlCipherNativeLibrary.load()
        EncryptionConversionRecovery(context, databaseFileName, stateStore).recoverIfNeeded()
        ensureEncrypted()

        val plan = DatabaseOpenPlanner(
            context = context,
            databaseFileName = databaseFileName,
            legacyDatabaseFileName = null,
            recoveryPlaceholderFileName = RECOVERY_PLACEHOLDER_FILE_NAME,
            stateStore = stateStore,
            keyWrapper = keyWrapper,
        ).plan()

        val builder = Room.databaseBuilder(context, AppDatabase::class.java, plan.fileName)
        plan.openHelperFactory?.let { builder.openHelperFactory(it) }
        builder.addMigrations(*ALL_MIGRATIONS)
        return builder.build()
    }

    private fun ensureEncrypted() {
        if (stateStore.isEncrypted) return
        val converter = DatabaseEncryptionConverter(
            context = context,
            databaseFileName = databaseFileName,
            stateStore = stateStore,
            keyWrapper = keyWrapper,
            passwordWrapper = PasswordKeyWrapper(),
            closeOpenDatabase = {},
        )
        runBlocking { converter.convert(targetEncrypted = true) }
    }

    private companion object {
        const val DATABASE_FILE_NAME = "talktome.db"
        const val RECOVERY_PLACEHOLDER_FILE_NAME = "talktome_recovered.db"
    }
}
