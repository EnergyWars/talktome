package com.wafflehq.talktome.server

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailboxDatabaseTest {

    private lateinit var database: MailboxDatabase

    @Before
    fun setUp() {
        database = MailboxDatabase("jdbc:sqlite::memory:")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `registerDevice returns a resolvable token`() {
        val device = database.registerDevice()

        assertEquals(device.deviceId, database.deviceIdForToken(device.token))
    }

    @Test
    fun `deviceIdForToken returns null for an unknown token`() {
        assertNull(database.deviceIdForToken("unknown-token"))
    }

    @Test
    fun `deviceExists is true only for registered devices`() {
        val device = database.registerDevice()

        assertTrue(database.deviceExists(device.deviceId))
        assertFalse(database.deviceExists("unknown-device"))
    }

    @Test
    fun `enqueue then pending returns the item for the recipient`() {
        val sender = database.registerDevice()
        val recipient = database.registerDevice()

        database.enqueue(recipient.deviceId, sender.deviceId, "MESSAGE", "ciphertext")

        val pending = database.pending(recipient.deviceId)
        assertEquals(1, pending.size)
        assertEquals("ciphertext", pending.first().ciphertext)
        assertEquals(sender.deviceId, pending.first().senderDeviceId)
    }

    @Test
    fun `pending is scoped to the recipient`() {
        val sender = database.registerDevice()
        val recipientA = database.registerDevice()
        val recipientB = database.registerDevice()
        database.enqueue(recipientA.deviceId, sender.deviceId, "MESSAGE", "for-a")

        assertTrue(database.pending(recipientB.deviceId).isEmpty())
    }

    @Test
    fun `pending is ordered oldest first`() {
        val sender = database.registerDevice()
        val recipient = database.registerDevice()
        database.enqueue(recipient.deviceId, sender.deviceId, "MESSAGE", "first")
        database.enqueue(recipient.deviceId, sender.deviceId, "MESSAGE", "second")

        val pending = database.pending(recipient.deviceId)

        assertEquals("first", pending[0].ciphertext)
        assertEquals("second", pending[1].ciphertext)
    }

    @Test
    fun `delete removes the item only for the matching recipient`() {
        val sender = database.registerDevice()
        val recipient = database.registerDevice()
        val impostor = database.registerDevice()
        val messageId = database.enqueue(recipient.deviceId, sender.deviceId, "MESSAGE", "ciphertext")

        val removedByImpostor = database.delete(messageId, impostor.deviceId)
        val removedByRecipient = database.delete(messageId, recipient.deviceId)

        assertFalse(removedByImpostor)
        assertTrue(removedByRecipient)
        assertTrue(database.pending(recipient.deviceId).isEmpty())
    }

    @Test
    fun `delete returns false for an unknown message id`() {
        val recipient = database.registerDevice()

        assertFalse(database.delete("unknown-id", recipient.deviceId))
    }
}
