package com.wafflehq.talktome.data.crypto

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

class E2eIdentityTest {

    @Before
    fun setUp() {
        HybridConfig.register()
    }

    private fun newIdentity(): E2eIdentity =
        E2eIdentity(KeysetHandle.generateNew(KeyTemplates.get(E2eIdentity.HYBRID_KEY_TEMPLATE_NAME)))

    @Test
    fun `encryptFor then decryptOwn returns the original plaintext`() {
        val recipient = newIdentity()
        val sender = newIdentity()
        val plaintext = "Ich vermisse dich".toByteArray(Charsets.UTF_8)

        val ciphertext = sender.encryptFor(recipient.publicKeyBase64, plaintext)
        val decrypted = recipient.decryptOwn(ciphertext)

        assertEquals("Ich vermisse dich", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `ciphertext never contains the plaintext`() {
        val recipient = newIdentity()
        val sender = newIdentity()
        val plaintext = "geheime-nachricht"

        val ciphertext = sender.encryptFor(recipient.publicKeyBase64, plaintext.toByteArray(Charsets.UTF_8))

        assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains(plaintext))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `a different recipient cannot decrypt the message`() {
        val recipient = newIdentity()
        val impostor = newIdentity()
        val sender = newIdentity()

        val ciphertext = sender.encryptFor(recipient.publicKeyBase64, "top secret".toByteArray(Charsets.UTF_8))

        impostor.decryptOwn(ciphertext)
    }

    @Test
    fun `publicKeyBase64 is stable across repeated reads`() {
        val identity = newIdentity()

        assertEquals(identity.publicKeyBase64, identity.publicKeyBase64)
    }

    @Test
    fun `two identities have different public keys`() {
        val a = newIdentity()
        val b = newIdentity()

        assertNotEquals(a.publicKeyBase64, b.publicKeyBase64)
    }
}
