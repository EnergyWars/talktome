package com.wafflehq.uikit.database.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wafflehq.uikit.database.crypto.PasswordWrappedDek
import com.wafflehq.uikit.database.crypto.WrappedDek
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptionStateStoreTest {

    private lateinit var context: Context
    private lateinit var store: EncryptionStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        store = EncryptionStateStore(context, PREFS_NAME)
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun wrappedDek() = WrappedDek(iv = byteArrayOf(1, 2, 3), ciphertext = byteArrayOf(4, 5, 6, 7))

    private fun passwordWrappedDek() = PasswordWrappedDek(
        salt = byteArrayOf(10, 11, 12),
        iv = byteArrayOf(13, 14, 15),
        ciphertext = byteArrayOf(16, 17, 18, 19),
        iterations = 600_000,
    )

    @Test
    fun `fresh store defaults to unencrypted with no conversion and no keys`() {
        assertFalse(store.isEncrypted)
        assertEquals(ConversionState.NONE, store.conversionState)
        assertNull(store.readWrappedDek())
        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `commitConversionStart sets exporting state without touching encryption flag`() {
        store.commitConversionStart()

        assertEquals(ConversionState.EXPORTING, store.conversionState)
        assertFalse(store.isEncrypted)
    }

    @Test
    fun `commitSwapIntent to encrypted persists flag and keystore wrap`() {
        val dek = wrappedDek()

        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = dek)

        assertEquals(ConversionState.SWAPPING, store.conversionState)
        assertTrue(store.isEncrypted)
        val readBack = store.readWrappedDek()
        assertArrayEquals(dek.iv, readBack?.iv)
        assertArrayEquals(dek.ciphertext, readBack?.ciphertext)
        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `commitSwapIntent with password wrap round trips both wraps`() {
        val dek = wrappedDek()
        val pw = passwordWrappedDek()

        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = dek, newPasswordWrappedDek = pw)

        val readBack = store.readPasswordWrappedDek()
        assertArrayEquals(pw.salt, readBack?.salt)
        assertArrayEquals(pw.iv, readBack?.iv)
        assertArrayEquals(pw.ciphertext, readBack?.ciphertext)
        assertEquals(pw.iterations, readBack?.iterations)
        assertArrayEquals(dek.iv, store.readWrappedDek()?.iv)
    }

    @Test
    fun `commitSwapIntent to plain clears both wraps`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek(), newPasswordWrappedDek = passwordWrappedDek())

        store.commitSwapIntent(newIsEncrypted = false, newWrappedDek = null)

        assertEquals(ConversionState.SWAPPING, store.conversionState)
        assertFalse(store.isEncrypted)
        assertNull(store.readWrappedDek())
        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `commitRollbackIntent persists rolling back state and old wraps`() {
        val dek = WrappedDek(iv = byteArrayOf(9), ciphertext = byteArrayOf(9, 9))
        val pw = passwordWrappedDek()

        store.commitRollbackIntent(oldIsEncrypted = true, oldWrappedDek = dek, oldPasswordWrappedDek = pw)

        assertEquals(ConversionState.ROLLING_BACK, store.conversionState)
        assertTrue(store.isEncrypted)
        val readBack = store.readWrappedDek()
        assertArrayEquals(dek.iv, readBack?.iv)
        assertArrayEquals(dek.ciphertext, readBack?.ciphertext)
        assertEquals(pw.iterations, store.readPasswordWrappedDek()?.iterations)
    }

    @Test
    fun `commitRollbackIntent to plain clears wraps`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek(), newPasswordWrappedDek = passwordWrappedDek())

        store.commitRollbackIntent(oldIsEncrypted = false, oldWrappedDek = null)

        assertEquals(ConversionState.ROLLING_BACK, store.conversionState)
        assertFalse(store.isEncrypted)
        assertNull(store.readWrappedDek())
        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `commitSwapComplete resets conversion state but keeps encryption flag and wrap`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek())

        store.commitSwapComplete()

        assertEquals(ConversionState.NONE, store.conversionState)
        assertTrue(store.isEncrypted)
        assertArrayEquals(wrappedDek().ciphertext, store.readWrappedDek()?.ciphertext)
    }

    @Test
    fun `writePasswordWrappedDek then read round trips`() {
        val pw = passwordWrappedDek()

        store.writePasswordWrappedDek(pw)

        val readBack = store.readPasswordWrappedDek()
        assertArrayEquals(pw.salt, readBack?.salt)
        assertArrayEquals(pw.iv, readBack?.iv)
        assertArrayEquals(pw.ciphertext, readBack?.ciphertext)
        assertEquals(pw.iterations, readBack?.iterations)
    }

    @Test
    fun `writePasswordWrappedDek with null removes stored values`() {
        store.writePasswordWrappedDek(passwordWrappedDek())

        store.writePasswordWrappedDek(null)

        assertNull(store.readPasswordWrappedDek())
        assertFalse(prefs().contains("pw_dek_salt"))
        assertFalse(prefs().contains("pw_dek_iv"))
        assertFalse(prefs().contains("pw_dek_ciphertext"))
        assertFalse(prefs().contains("pw_dek_iterations"))
    }

    @Test
    fun `writePasswordWrappedDek does not touch keystore wrap or flags`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek())
        store.commitSwapComplete()

        store.writePasswordWrappedDek(passwordWrappedDek())

        assertTrue(store.isEncrypted)
        assertEquals(ConversionState.NONE, store.conversionState)
        assertArrayEquals(wrappedDek().iv, store.readWrappedDek()?.iv)
    }

    @Test
    fun `readPasswordWrappedDek returns null when iterations are missing`() {
        store.writePasswordWrappedDek(passwordWrappedDek())
        prefs().edit().remove("pw_dek_iterations").commit()

        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `readPasswordWrappedDek returns null when iterations are not positive`() {
        store.writePasswordWrappedDek(passwordWrappedDek())
        prefs().edit().putInt("pw_dek_iterations", 0).commit()

        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `readWrappedDek returns null when only one half is present`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek())
        prefs().edit().remove("dek_ciphertext").commit()

        assertNull(store.readWrappedDek())
    }

    @Test
    fun `resetAfterUnrecoverableKeyLoss clears everything`() {
        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek(), newPasswordWrappedDek = passwordWrappedDek())

        store.resetAfterUnrecoverableKeyLoss()

        assertFalse(store.isEncrypted)
        assertEquals(ConversionState.NONE, store.conversionState)
        assertNull(store.readWrappedDek())
        assertNull(store.readPasswordWrappedDek())
    }

    @Test
    fun `invalid conversion state string falls back to NONE`() {
        prefs().edit().putString("conversion_state", "NOT_A_STATE").commit()

        assertEquals(ConversionState.NONE, store.conversionState)
    }

    @Test
    fun `stores with different prefs names are isolated`() {
        val other = EncryptionStateStore(context, "other_encryption_state")

        store.commitSwapIntent(newIsEncrypted = true, newWrappedDek = wrappedDek())

        assertFalse(other.isEncrypted)
        assertNull(other.readWrappedDek())
        context.getSharedPreferences("other_encryption_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "test_encryption_state"
    }
}
