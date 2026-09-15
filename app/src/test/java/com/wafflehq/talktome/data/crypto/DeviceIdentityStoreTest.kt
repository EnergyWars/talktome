package com.wafflehq.talktome.data.crypto

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.wafflehq.talktome.data.security.FakeAesGcmBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceIdentityStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newStore(cipher: FakeAesGcmBox = FakeAesGcmBox()): DeviceIdentityStore {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("device_identity_${System.nanoTime()}.preferences_pb") },
        )
        return DeviceIdentityStore(dataStore, cipher)
    }

    @Test
    fun `hasIdentity is false when nothing stored`() = runTest {
        assertFalse(newStore().hasIdentity.first())
    }

    @Test
    fun `getIdentity returns null when nothing stored`() = runTest {
        assertNull(newStore().getIdentity())
    }

    @Test
    fun `saveIdentity then getIdentity returns the original values`() = runTest {
        val store = newStore()

        store.saveIdentity(DeviceIdentity(deviceId = "device-123", token = "secret-token", serverBaseUrl = "https://server.example"))

        val loaded = store.getIdentity()
        assertEquals("device-123", loaded?.deviceId)
        assertEquals("secret-token", loaded?.token)
        assertEquals("https://server.example", loaded?.serverBaseUrl)
    }

    @Test
    fun `hasIdentity becomes true after saveIdentity`() = runTest {
        val store = newStore()

        store.saveIdentity(DeviceIdentity(deviceId = "device-123", token = "secret-token", serverBaseUrl = "https://server.example"))

        assertTrue(store.hasIdentity.first())
    }

    @Test
    fun `clear removes the stored identity`() = runTest {
        val store = newStore()
        store.saveIdentity(DeviceIdentity(deviceId = "device-123", token = "secret-token", serverBaseUrl = "https://server.example"))

        store.clear()

        assertNull(store.getIdentity())
        assertFalse(store.hasIdentity.first())
    }
}
