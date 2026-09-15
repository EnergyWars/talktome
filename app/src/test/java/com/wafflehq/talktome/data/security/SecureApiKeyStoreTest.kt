package com.wafflehq.talktome.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecureApiKeyStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newStore(cipher: FakeAesGcmBox = FakeAesGcmBox()): SecureApiKeyStore {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("api_key_${System.nanoTime()}.preferences_pb") },
        )
        return SecureApiKeyStore(dataStore, cipher)
    }

    @Test
    fun `hasApiKey is false when nothing stored`() = runTest {
        val store = newStore()

        assertFalse(store.hasApiKey.first())
    }

    @Test
    fun `getApiKey returns null when nothing stored`() = runTest {
        val store = newStore()

        assertNull(store.getApiKey())
    }

    @Test
    fun `setApiKey then getApiKey returns the original key`() = runTest {
        val store = newStore()

        store.setApiKey("AIzaSyExampleKey1234567890")

        assertEquals("AIzaSyExampleKey1234567890", store.getApiKey())
    }

    @Test
    fun `setApiKey trims surrounding whitespace`() = runTest {
        val store = newStore()

        store.setApiKey("  my-key-value  ")

        assertEquals("my-key-value", store.getApiKey())
    }

    @Test
    fun `hasApiKey becomes true after setApiKey`() = runTest {
        val store = newStore()

        store.setApiKey("some-key")

        assertTrue(store.hasApiKey.first())
    }

    @Test
    fun `setApiKey rejects blank input`() = runTest {
        val store = newStore()

        try {
            store.setApiKey("   ")
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `clearApiKey removes the stored key`() = runTest {
        val store = newStore()
        store.setApiKey("some-key")

        store.clearApiKey()

        assertNull(store.getApiKey())
        assertFalse(store.hasApiKey.first())
    }

    @Test
    fun `stored ciphertext is never the plaintext key`() = runTest {
        val cipher = FakeAesGcmBox()
        val plaintext = "super-secret-key"
        val payload = cipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))

        assertFalse(String(payload.ciphertext, Charsets.UTF_8).contains(plaintext))
    }

    @Test
    fun `a store cannot decrypt a key written by a store with a different cipher key`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("shared.preferences_pb") },
        )
        val storeA = SecureApiKeyStore(dataStore, FakeAesGcmBox())
        val storeB = SecureApiKeyStore(dataStore, FakeAesGcmBox())

        storeA.setApiKey("only-a-can-read-this")

        assertNull(storeB.getApiKey())
    }

    @Test
    fun `getApiKey clears the corrupted entry after a decryption failure`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("shared.preferences_pb") },
        )
        val storeA = SecureApiKeyStore(dataStore, FakeAesGcmBox())
        val storeB = SecureApiKeyStore(dataStore, FakeAesGcmBox())
        storeA.setApiKey("only-a-can-read-this")

        storeB.getApiKey()

        assertFalse(storeB.hasApiKey.first())
    }

    @Test
    fun `very long api key round-trips correctly`() = runTest {
        val store = newStore()
        val longKey = "k".repeat(4_000)

        store.setApiKey(longKey)

        assertEquals(longKey, store.getApiKey())
    }
}
