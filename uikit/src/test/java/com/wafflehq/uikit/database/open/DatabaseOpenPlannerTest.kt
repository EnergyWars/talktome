package com.wafflehq.uikit.database.open

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wafflehq.uikit.database.crypto.KeystoreKeyWrapper
import com.wafflehq.uikit.database.crypto.WrappedDek
import com.wafflehq.uikit.database.state.DatabaseKeyRecoveryState
import com.wafflehq.uikit.database.state.EncryptionStateStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseOpenPlannerTest {

    private class FakeKeystoreKeyWrapper : KeystoreKeyWrapper {
        override fun wrap(dek: ByteArray) = WrappedDek(iv = FIXED_IV, ciphertext = dek.copyOf())
        override fun unwrap(wrapped: WrappedDek): ByteArray = wrapped.ciphertext.copyOf()
        override fun deleteKey() = Unit
    }

    private class ThrowingKeystoreKeyWrapper : KeystoreKeyWrapper {
        override fun wrap(dek: ByteArray) = WrappedDek(iv = FIXED_IV, ciphertext = dek.copyOf())
        override fun unwrap(wrapped: WrappedDek): ByteArray = throw IllegalStateException("key lost")
        override fun deleteKey() = Unit
    }

    private class RecordingProbe(private val result: Boolean) : EncryptedDatabaseProbe {
        var lastFile: File? = null
        var lastDek: ByteArray? = null
        override fun canOpen(file: File, dek: ByteArray): Boolean {
            lastFile = file
            lastDek = dek
            return result
        }
    }

    private lateinit var context: Context
    private lateinit var stateStore: EncryptionStateStore
    private lateinit var dbFile: File
    private lateinit var legacyFile: File

    @Before
    fun setUp() {
        DatabaseKeyRecoveryState.keyUnavailable = false
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        stateStore = EncryptionStateStore(context, PREFS_NAME)
        dbFile = context.getDatabasePath(DB_NAME)
        legacyFile = context.getDatabasePath(LEGACY_DB_NAME)
        dbFile.parentFile?.mkdirs()
        deleteFiles()
    }

    @After
    fun tearDown() {
        DatabaseKeyRecoveryState.keyUnavailable = false
        deleteFiles()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun deleteFiles() {
        listOf(dbFile, legacyFile).forEach { base ->
            listOf("", "-wal", "-shm", "-journal").forEach { File(base.path + it).delete() }
        }
    }

    private fun planner(
        keyWrapper: KeystoreKeyWrapper = FakeKeystoreKeyWrapper(),
        probe: EncryptedDatabaseProbe = RecordingProbe(true),
        legacyName: String? = LEGACY_DB_NAME,
    ) = DatabaseOpenPlanner(
        context = context,
        databaseFileName = DB_NAME,
        legacyDatabaseFileName = legacyName,
        recoveryPlaceholderFileName = PLACEHOLDER_NAME,
        stateStore = stateStore,
        keyWrapper = keyWrapper,
        probe = probe,
    )

    private fun storeEncryptedWith(wrapper: KeystoreKeyWrapper, dek: ByteArray = ByteArray(32) { it.toByte() }) {
        stateStore.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrapper.wrap(dek))
        stateStore.commitSwapComplete()
    }

    @Test
    fun `plain database yields no factory and canonical file name`() {
        val plan = planner().plan()

        assertNull(plan.openHelperFactory)
        assertEquals(DB_NAME, plan.fileName)
        assertFalse(plan.keyUnavailable)
        assertFalse(DatabaseKeyRecoveryState.keyUnavailable)
    }

    @Test
    fun `encrypted database with valid wrap and passing probe yields factory`() {
        val wrapper = FakeKeystoreKeyWrapper()
        val dek = ByteArray(32) { (it * 3).toByte() }
        storeEncryptedWith(wrapper, dek)
        val probe = RecordingProbe(true)

        val plan = planner(keyWrapper = wrapper, probe = probe).plan()

        assertNotNull(plan.openHelperFactory)
        assertEquals(DB_NAME, plan.fileName)
        assertFalse(plan.keyUnavailable)
        assertFalse(DatabaseKeyRecoveryState.keyUnavailable)
        assertEquals(dbFile, probe.lastFile)
        assertArrayEquals(dek, probe.lastDek)
    }

    @Test
    fun `encrypted database without stored wrap marks key unavailable and uses placeholder`() {
        stateStore.commitSwapIntent(newIsEncrypted = true, newWrappedDek = null)
        stateStore.commitSwapComplete()

        val plan = planner().plan()

        assertNull(plan.openHelperFactory)
        assertTrue(plan.keyUnavailable)
        assertEquals(PLACEHOLDER_NAME, plan.fileName)
        assertTrue(DatabaseKeyRecoveryState.keyUnavailable)
    }

    @Test
    fun `encrypted database with failing probe marks key unavailable`() {
        val wrapper = FakeKeystoreKeyWrapper()
        storeEncryptedWith(wrapper)

        val plan = planner(keyWrapper = wrapper, probe = RecordingProbe(false)).plan()

        assertNull(plan.openHelperFactory)
        assertTrue(plan.keyUnavailable)
        assertEquals(PLACEHOLDER_NAME, plan.fileName)
    }

    @Test
    fun `encrypted database with throwing wrapper marks key unavailable`() {
        storeEncryptedWith(FakeKeystoreKeyWrapper())
        val probe = RecordingProbe(true)

        val plan = planner(keyWrapper = ThrowingKeystoreKeyWrapper(), probe = probe).plan()

        assertNull(plan.openHelperFactory)
        assertTrue(plan.keyUnavailable)
        assertEquals(PLACEHOLDER_NAME, plan.fileName)
        assertNull(probe.lastFile)
    }

    @Test
    fun `plain database never consults probe or wrapper`() {
        val probe = RecordingProbe(false)

        val plan = planner(keyWrapper = ThrowingKeystoreKeyWrapper(), probe = probe).plan()

        assertNull(plan.openHelperFactory)
        assertFalse(plan.keyUnavailable)
        assertNull(probe.lastFile)
    }

    @Test
    fun `legacy database file is renamed together with its sidecars`() {
        legacyFile.writeText("legacy")
        File(legacyFile.path + "-wal").writeText("wal")

        planner().plan()

        assertFalse(legacyFile.exists())
        assertFalse(File(legacyFile.path + "-wal").exists())
        assertEquals("legacy", dbFile.readText())
        assertEquals("wal", File(dbFile.path + "-wal").readText())
    }

    @Test
    fun `legacy database file is kept when the new file already exists`() {
        legacyFile.writeText("legacy")
        dbFile.writeText("current")

        planner().plan()

        assertTrue(legacyFile.exists())
        assertEquals("current", dbFile.readText())
    }

    @Test
    fun `no legacy file name means nothing is renamed`() {
        legacyFile.writeText("legacy")

        planner(legacyName = null).plan()

        assertTrue(legacyFile.exists())
        assertFalse(dbFile.exists())
    }

    @Test
    fun `missing legacy file is a no op`() {
        planner().plan()

        assertFalse(legacyFile.exists())
        assertFalse(dbFile.exists())
    }

    private companion object {
        val FIXED_IV = ByteArray(12) { 1 }
        const val DB_NAME = "planner_test.db"
        const val LEGACY_DB_NAME = "planner_legacy.db"
        const val PLACEHOLDER_NAME = "planner_placeholder.db"
        const val PREFS_NAME = "test_planner_encryption_state"
    }
}
