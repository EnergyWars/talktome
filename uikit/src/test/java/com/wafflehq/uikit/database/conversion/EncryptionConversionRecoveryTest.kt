package com.wafflehq.uikit.database.conversion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wafflehq.uikit.database.crypto.WrappedDek
import com.wafflehq.uikit.database.state.ConversionState
import com.wafflehq.uikit.database.state.EncryptionStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptionConversionRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var stateStore: EncryptionStateStore
    private lateinit var canonicalFile: File
    private lateinit var newFile: File
    private lateinit var bakFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        canonicalFile = context.getDatabasePath(DB_FILE_NAME)
        canonicalFile.parentFile?.mkdirs()
        newFile = File(canonicalFile.path + EncryptionConversionRecovery.NEW_SUFFIX)
        bakFile = File(canonicalFile.path + EncryptionConversionRecovery.BAK_SUFFIX)
        deleteAllDbFiles()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        stateStore = EncryptionStateStore(context, PREFS_NAME)
    }

    @After
    fun tearDown() {
        deleteAllDbFiles()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun deleteAllDbFiles() {
        listOf(canonicalFile, newFile, bakFile).forEach { EncryptionConversionRecovery.deleteFileSet(it) }
    }

    private fun recovery() = EncryptionConversionRecovery(context, DB_FILE_NAME, stateStore)

    @Test
    fun decideSwapRecovery_crashedBeforeOldRenamed_completesForward() {
        val action = decideSwapRecovery(bakExists = false, canonicalExists = true, newExists = true)

        assertEquals(SwapRecoveryAction.COMPLETE_WITH_NEW, action)
    }

    @Test
    fun decideSwapRecovery_crashedBetweenOldRenamedAndNewRenamed_withNewFile_completesForward() {
        val action = decideSwapRecovery(bakExists = true, canonicalExists = false, newExists = true)

        assertEquals(SwapRecoveryAction.COMPLETE_WITH_NEW, action)
    }

    @Test
    fun decideSwapRecovery_crashedWithNoNewFile_restoresBackup() {
        val action = decideSwapRecovery(bakExists = true, canonicalExists = false, newExists = false)

        assertEquals(SwapRecoveryAction.RESTORE_BACKUP, action)
    }

    @Test
    fun decideSwapRecovery_crashedAfterBothRenames_onlyCleansUpBackup() {
        val action = decideSwapRecovery(bakExists = true, canonicalExists = true, newExists = false)

        assertEquals(SwapRecoveryAction.CLEANUP_ONLY, action)
    }

    @Test
    fun decideSwapRecovery_crashedAfterBothRenames_withStrayNewFile_onlyCleansUpBackup() {
        val action = decideSwapRecovery(bakExists = true, canonicalExists = true, newExists = true)

        assertEquals(SwapRecoveryAction.CLEANUP_ONLY, action)
    }

    @Test
    fun decideSwapRecovery_swapAlreadyComplete_isNothingToDo() {
        val action = decideSwapRecovery(bakExists = false, canonicalExists = true, newExists = false)

        assertEquals(SwapRecoveryAction.NOTHING_TO_DO, action)
    }

    @Test
    fun decideSwapRecovery_noFilesAtAll_isNothingToDo() {
        val action = decideSwapRecovery(bakExists = false, canonicalExists = false, newExists = false)

        assertEquals(SwapRecoveryAction.NOTHING_TO_DO, action)
    }

    @Test
    fun renameFileSet_movesMainFileAndAuxSidecars() {
        val from = tempFolder.newFile("source.db")
        EncryptionConversionRecovery.AUX_SUFFIXES.forEach { File(from.path + it).writeText("aux") }
        val to = File(tempFolder.root, "target.db")

        EncryptionConversionRecovery.renameFileSet(from, to)

        assertTrue(to.exists())
        assertFalse(from.exists())
        EncryptionConversionRecovery.AUX_SUFFIXES.forEach { suffix ->
            assertTrue(File(to.path + suffix).exists())
            assertFalse(File(from.path + suffix).exists())
        }
    }

    @Test
    fun renameFileSet_withoutSidecars_movesOnlyMainFile() {
        val from = tempFolder.newFile("source.db")
        val to = File(tempFolder.root, "target.db")

        EncryptionConversionRecovery.renameFileSet(from, to)

        assertTrue(to.exists())
        assertFalse(from.exists())
        EncryptionConversionRecovery.AUX_SUFFIXES.forEach { suffix ->
            assertFalse(File(to.path + suffix).exists())
        }
    }

    @Test
    fun deleteFileSet_removesMainFileAndAuxSidecars() {
        val file = tempFolder.newFile("stray.db.new")
        EncryptionConversionRecovery.AUX_SUFFIXES.forEach { File(file.path + it).writeText("aux") }

        EncryptionConversionRecovery.deleteFileSet(file)

        assertFalse(file.exists())
        EncryptionConversionRecovery.AUX_SUFFIXES.forEach { suffix ->
            assertFalse(File(file.path + suffix).exists())
        }
    }

    @Test
    fun deleteFileSet_onMissingFile_isNoOp() {
        val file = File(tempFolder.root, "does_not_exist.db")

        EncryptionConversionRecovery.deleteFileSet(file)

        assertFalse(file.exists())
    }

    @Test
    fun recoverIfNeeded_stateNone_deletesStrayNewFileAndKeepsCanonical() {
        canonicalFile.writeText("current")
        newFile.writeText("stray")
        File(newFile.path + "-wal").writeText("aux")

        recovery().recoverIfNeeded()

        assertEquals("current", canonicalFile.readText())
        assertFalse(newFile.exists())
        assertFalse(File(newFile.path + "-wal").exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    @Test
    fun recoverIfNeeded_stateExporting_deletesNewFileAndResetsState() {
        canonicalFile.writeText("current")
        newFile.writeText("half-written")
        stateStore.commitConversionStart()

        recovery().recoverIfNeeded()

        assertEquals("current", canonicalFile.readText())
        assertFalse(newFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
        assertFalse(stateStore.isEncrypted)
    }

    @Test
    fun recoverIfNeeded_stateSwapping_withBakAndNewButNoCanonical_completesSwap() {
        bakFile.writeText("old")
        newFile.writeText("new")
        File(newFile.path + "-wal").writeText("aux")
        val dek = WrappedDek(iv = byteArrayOf(1), ciphertext = byteArrayOf(2))
        stateStore.commitSwapIntent(newIsEncrypted = true, newWrappedDek = dek)

        recovery().recoverIfNeeded()

        assertEquals("new", canonicalFile.readText())
        assertTrue(File(canonicalFile.path + "-wal").exists())
        assertFalse(bakFile.exists())
        assertFalse(newFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
        assertTrue(stateStore.isEncrypted)
        assertTrue(dek.ciphertext.contentEquals(stateStore.readWrappedDek()?.ciphertext))
    }

    @Test
    fun recoverIfNeeded_stateSwapping_withCanonicalAndNew_completesSwap() {
        canonicalFile.writeText("old")
        newFile.writeText("new")
        stateStore.commitSwapIntent(newIsEncrypted = false, newWrappedDek = null)

        recovery().recoverIfNeeded()

        assertEquals("new", canonicalFile.readText())
        assertFalse(newFile.exists())
        assertFalse(bakFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    @Test
    fun recoverIfNeeded_stateSwapping_withBakOnly_restoresBackup() {
        bakFile.writeText("old")
        stateStore.commitSwapIntent(newIsEncrypted = true, newWrappedDek = WrappedDek(byteArrayOf(1), byteArrayOf(2)))

        recovery().recoverIfNeeded()

        assertEquals("old", canonicalFile.readText())
        assertFalse(bakFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    @Test
    fun recoverIfNeeded_stateSwapping_withBakAndCanonical_cleansUpBackup() {
        canonicalFile.writeText("new")
        bakFile.writeText("old")
        stateStore.commitSwapIntent(newIsEncrypted = false, newWrappedDek = null)

        recovery().recoverIfNeeded()

        assertEquals("new", canonicalFile.readText())
        assertFalse(bakFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    @Test
    fun recoverIfNeeded_stateRollingBack_withBak_restoresOldDatabase() {
        canonicalFile.writeText("broken-new-content")
        bakFile.writeText("good-old-content")
        newFile.writeText("leftover")
        stateStore.commitRollbackIntent(oldIsEncrypted = false, oldWrappedDek = null)

        recovery().recoverIfNeeded()

        assertEquals("good-old-content", canonicalFile.readText())
        assertFalse(bakFile.exists())
        assertFalse(newFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
        assertFalse(stateStore.isEncrypted)
    }

    @Test
    fun recoverIfNeeded_stateRollingBack_alreadyFinished_isIdempotent() {
        canonicalFile.writeText("good-old-content")
        stateStore.commitRollbackIntent(oldIsEncrypted = false, oldWrappedDek = null)

        recovery().recoverIfNeeded()

        assertEquals("good-old-content", canonicalFile.readText())
        assertFalse(bakFile.exists())
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    @Test
    fun recoverIfNeeded_stateRollingBack_restoresEncryptionState() {
        val originalDek = WrappedDek(iv = byteArrayOf(1, 2, 3), ciphertext = byteArrayOf(4, 5, 6))
        canonicalFile.writeText("broken-new-content")
        bakFile.writeText("good-old-content")
        stateStore.commitRollbackIntent(oldIsEncrypted = true, oldWrappedDek = originalDek)

        recovery().recoverIfNeeded()

        assertTrue(stateStore.isEncrypted)
        val restoredDek = stateStore.readWrappedDek()!!
        assertTrue(originalDek.iv.contentEquals(restoredDek.iv))
        assertTrue(originalDek.ciphertext.contentEquals(restoredDek.ciphertext))
        assertEquals(ConversionState.NONE, stateStore.conversionState)
    }

    private companion object {
        const val DB_FILE_NAME = "recovery_test.db"
        const val PREFS_NAME = "test_recovery_encryption_state"
    }
}
