package com.wafflehq.uikit.database.conversion

import android.content.Context
import android.util.Log
import com.wafflehq.uikit.database.state.ConversionState
import com.wafflehq.uikit.database.state.EncryptionStateStore
import java.io.File

enum class SwapRecoveryAction {
    CLEANUP_ONLY,
    COMPLETE_WITH_NEW,
    RESTORE_BACKUP,
    NOTHING_TO_DO
}

fun decideSwapRecovery(bakExists: Boolean, canonicalExists: Boolean, newExists: Boolean): SwapRecoveryAction =
    when {
        bakExists && !canonicalExists && newExists -> SwapRecoveryAction.COMPLETE_WITH_NEW
        bakExists && !canonicalExists -> SwapRecoveryAction.RESTORE_BACKUP
        bakExists && canonicalExists -> SwapRecoveryAction.CLEANUP_ONLY
        !bakExists && canonicalExists && newExists -> SwapRecoveryAction.COMPLETE_WITH_NEW
        else -> SwapRecoveryAction.NOTHING_TO_DO
    }

class EncryptionConversionRecovery(
    context: Context,
    databaseFileName: String,
    private val stateStore: EncryptionStateStore,
) {

    private val canonicalFile: File = context.getDatabasePath(databaseFileName)

    fun recoverIfNeeded() {
        when (stateStore.conversionState) {
            ConversionState.EXPORTING -> {
                deleteFileSet(newFile())
                stateStore.commitSwapComplete()
            }
            ConversionState.SWAPPING -> {
                finishSwap()
                stateStore.commitSwapComplete()
            }
            ConversionState.ROLLING_BACK -> {
                finishRollback()
                stateStore.commitSwapComplete()
            }
            ConversionState.NONE -> {
                deleteFileSet(newFile())
            }
        }
    }

    private fun finishSwap() {
        val new = newFile()
        val bak = bakFile()
        when (decideSwapRecovery(bakExists = bak.exists(), canonicalExists = canonicalFile.exists(), newExists = new.exists())) {
            SwapRecoveryAction.COMPLETE_WITH_NEW -> {
                renameFileSet(new, canonicalFile)
                deleteFileSet(bak)
            }
            SwapRecoveryAction.RESTORE_BACKUP -> {
                Log.w(TAG, "Swap crashed with no new file to finish with, restoring backup")
                renameFileSet(bak, canonicalFile)
                deleteFileSet(bak)
            }
            SwapRecoveryAction.CLEANUP_ONLY -> deleteFileSet(bak)
            SwapRecoveryAction.NOTHING_TO_DO -> deleteFileSet(new)
        }
    }

    private fun finishRollback() {
        val bak = bakFile()
        if (bak.exists()) {
            deleteFileSet(canonicalFile)
            renameFileSet(bak, canonicalFile)
        }
        deleteFileSet(newFile())
    }

    private fun newFile() = File(canonicalFile.path + NEW_SUFFIX)
    private fun bakFile() = File(canonicalFile.path + BAK_SUFFIX)

    companion object {
        private const val TAG = "EncryptionRecovery"
        const val NEW_SUFFIX = ".new"
        const val BAK_SUFFIX = ".bak"
        val AUX_SUFFIXES = listOf("-wal", "-shm", "-journal")

        fun renameFileSet(from: File, to: File) {
            if (!from.renameTo(to)) {
                Log.w(TAG, "Failed to rename ${from.name} to ${to.name}")
            }
            AUX_SUFFIXES.forEach { suffix ->
                val src = File(from.path + suffix)
                if (src.exists()) src.renameTo(File(to.path + suffix))
            }
        }

        fun deleteFileSet(file: File) {
            if (file.exists()) file.delete()
            AUX_SUFFIXES.forEach { suffix ->
                val aux = File(file.path + suffix)
                if (aux.exists()) aux.delete()
            }
        }
    }
}
