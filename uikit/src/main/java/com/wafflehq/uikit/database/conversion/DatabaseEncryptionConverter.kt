package com.wafflehq.uikit.database.conversion

import android.content.Context
import android.database.Cursor
import com.wafflehq.uikit.database.crypto.KeystoreKeyWrapper
import com.wafflehq.uikit.database.crypto.PasswordKeyWrapper
import com.wafflehq.uikit.database.crypto.PasswordWrappedDek
import com.wafflehq.uikit.database.crypto.WrappedDek
import com.wafflehq.uikit.database.state.EncryptionStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.IOException
import java.security.SecureRandom

sealed interface ConversionResult {
    data object Success : ConversionResult
    data class Failure(val reason: ConversionFailureReason) : ConversionResult
}

enum class ConversionFailureReason { EXPORT_ERROR, ROW_COUNT_MISMATCH, INTEGRITY_CHECK_FAILED, IO_ERROR, KEYSTORE_ERROR }

class DatabaseEncryptionConverter(
    private val context: Context,
    private val databaseFileName: String,
    private val stateStore: EncryptionStateStore,
    private val keyWrapper: KeystoreKeyWrapper,
    private val passwordWrapper: PasswordKeyWrapper,
    private val closeOpenDatabase: () -> Unit,
) {

    suspend fun convert(
        targetEncrypted: Boolean,
        backupPassword: CharArray? = null,
        externalDek: ByteArray? = null,
    ): ConversionResult = withContext(Dispatchers.IO) {
        val wasEncrypted = stateStore.isEncrypted
        if (targetEncrypted == wasEncrypted) return@withContext ConversionResult.Success

        val canonicalFile = context.getDatabasePath(databaseFileName)
        val newFile = File(canonicalFile.path + EncryptionConversionRecovery.NEW_SUFFIX)
        EncryptionConversionRecovery.deleteFileSet(newFile)
        stateStore.commitConversionStart()

        val originalWrappedDek = stateStore.readWrappedDek()
        val originalPasswordWrappedDek = stateStore.readPasswordWrappedDek()
        val sourceKey: ByteArray? = if (wasEncrypted) {
            originalWrappedDek?.let { keyWrapper.unwrap(it) }
        } else {
            null
        }
        val newDek: ByteArray? = if (targetEncrypted) (externalDek ?: generateDek()) else null

        val result = try {
            runExportAndVerify(canonicalFile, newFile, sourceKey, newDek)
        } catch (e: IOException) {
            ConversionResult.Failure(ConversionFailureReason.IO_ERROR)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ConversionResult.Failure(ConversionFailureReason.EXPORT_ERROR)
        }

        if (result != ConversionResult.Success) {
            EncryptionConversionRecovery.deleteFileSet(newFile)
            stateStore.commitSwapComplete()
            return@withContext result
        }

        try {
            performSwap(
                canonicalFile, newFile, targetEncrypted, newDek,
                wasEncrypted, originalWrappedDek, originalPasswordWrappedDek, backupPassword,
            )
        } catch (e: KeystoreWrapException) {
            EncryptionConversionRecovery.deleteFileSet(newFile)
            stateStore.commitSwapComplete()
            ConversionResult.Failure(ConversionFailureReason.KEYSTORE_ERROR)
        }
    }

    private class KeystoreWrapException(cause: Throwable) : Exception(cause)

    private fun performSwap(
        canonicalFile: File,
        newFile: File,
        targetEncrypted: Boolean,
        newDek: ByteArray?,
        wasEncrypted: Boolean,
        originalWrappedDek: WrappedDek?,
        originalPasswordWrappedDek: PasswordWrappedDek?,
        backupPassword: CharArray?,
    ): ConversionResult {
        val wrappedNewDek = try {
            newDek?.let { keyWrapper.wrap(it) }
        } catch (e: Exception) {
            throw KeystoreWrapException(e)
        }
        val passwordWrappedDek = if (newDek != null && backupPassword != null) {
            try {
                passwordWrapper.wrap(newDek, backupPassword)
            } catch (e: Exception) {
                throw KeystoreWrapException(e)
            }
        } else {
            null
        }
        stateStore.commitSwapIntent(
            newIsEncrypted = targetEncrypted,
            newWrappedDek = wrappedNewDek,
            newPasswordWrappedDek = passwordWrappedDek,
        )

        closeOpenDatabase()

        val bakFile = File(canonicalFile.path + EncryptionConversionRecovery.BAK_SUFFIX)
        EncryptionConversionRecovery.renameFileSet(canonicalFile, bakFile)
        EncryptionConversionRecovery.renameFileSet(newFile, canonicalFile)

        if (!checkIntegrity(canonicalFile, newDek)) {
            stateStore.commitRollbackIntent(wasEncrypted, originalWrappedDek, originalPasswordWrappedDek)
            EncryptionConversionRecovery.deleteFileSet(canonicalFile)
            EncryptionConversionRecovery.renameFileSet(bakFile, canonicalFile)
            stateStore.commitSwapComplete()
            return ConversionResult.Failure(ConversionFailureReason.INTEGRITY_CHECK_FAILED)
        }

        EncryptionConversionRecovery.deleteFileSet(bakFile)

        if (!targetEncrypted) {
            keyWrapper.deleteKey()
        }
        stateStore.commitSwapComplete()
        return ConversionResult.Success
    }

    private fun runExportAndVerify(
        canonicalFile: File,
        newFile: File,
        sourceKey: ByteArray?,
        newDek: ByteArray?,
    ): ConversionResult {
        val sourceDb = SQLiteDatabase.openDatabase(
            canonicalFile.path,
            sourceKey,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            null,
            null,
        )
        val exportOutcome = try {
            if (newDek != null) {
                sourceDb.execSQL("ATTACH DATABASE ? AS target KEY ?", arrayOf(newFile.path, newDek))
            } else {
                sourceDb.execSQL("ATTACH DATABASE ? AS target KEY ''", arrayOf(newFile.path))
            }
            try {
                withCursor(sourceDb.rawQuery("SELECT sqlcipher_export('target')", NO_ARGS)) { it.moveToFirst() }

                val userVersion = withCursor(sourceDb.rawQuery("PRAGMA user_version", NO_ARGS)) { c ->
                    c.moveToFirst()
                    c.getInt(0)
                }
                sourceDb.execSQL("PRAGMA target.user_version = $userVersion")

                if (rowCountsMatch(sourceDb)) {
                    ConversionResult.Success
                } else {
                    ConversionResult.Failure(ConversionFailureReason.ROW_COUNT_MISMATCH)
                }
            } finally {
                sourceDb.execSQL("DETACH DATABASE target")
            }
        } finally {
            sourceDb.close()
        }

        if (exportOutcome != ConversionResult.Success) return exportOutcome
        return verifyStandalone(newFile, newDek)
    }

    private fun rowCountsMatch(sourceDb: SQLiteDatabase): Boolean {
        val tables = withCursor(
            sourceDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                NO_ARGS,
            ),
        ) { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        return tables.all { table ->
            val mainCount = withCursor(sourceDb.rawQuery("SELECT count(*) FROM main.\"$table\"", NO_ARGS)) {
                it.moveToFirst(); it.getInt(0)
            }
            val targetCount = withCursor(sourceDb.rawQuery("SELECT count(*) FROM target.\"$table\"", NO_ARGS)) {
                it.moveToFirst(); it.getInt(0)
            }
            mainCount == targetCount
        }
    }

    private fun verifyStandalone(newFile: File, newDek: ByteArray?): ConversionResult =
        if (checkIntegrity(newFile, newDek)) {
            ConversionResult.Success
        } else {
            ConversionResult.Failure(ConversionFailureReason.INTEGRITY_CHECK_FAILED)
        }

    private fun checkIntegrity(file: File, dek: ByteArray?): Boolean {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, dek, null, SQLiteDatabase.OPEN_READONLY, null, null)
        } catch (e: Throwable) {
            return false
        }
        return try {
            if (dek != null) {
                withCursor(db.rawQuery("PRAGMA cipher_integrity_check", NO_ARGS)) { !it.moveToFirst() }
            } else {
                withCursor(db.rawQuery("PRAGMA integrity_check", NO_ARGS)) { it.moveToFirst() && it.getString(0) == "ok" }
            }
        } catch (e: Throwable) {
            false
        } finally {
            db.close()
        }
    }

    private fun generateDek(): ByteArray = ByteArray(DEK_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

    private inline fun <T> withCursor(cursor: Cursor, block: (Cursor) -> T): T {
        try {
            return block(cursor)
        } finally {
            cursor.close()
        }
    }

    private companion object {
        const val DEK_SIZE_BYTES = 32
        val NO_ARGS = emptyArray<String>()
    }
}
