package com.wafflehq.talktome.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun `migrate adds new columns while preserving existing rows`() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO partners (id, displayName, publicKey, pairedAt) VALUES (1, 'Alex', 'partner-pub-key', 1000)",
            )
            execSQL(
                "INSERT INTO outgoing_messages (id, draftText, status, createdAt, updatedAt) VALUES (1, 'Hallo', 'DRAFT', 1, 1)",
            )
            execSQL(
                "INSERT INTO inbox_messages (id, content, status, receivedAt) VALUES (1, 'Hi', 'PENDING_REVIEW', 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)

        migrated.query("SELECT displayName, remoteDeviceId, serverBaseUrl FROM partners WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alex", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }

        migrated.query(
            "SELECT draftText, rejectionCount, friendOpinionsJson, lastRejectionReason, serverMessageId FROM outgoing_messages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Hallo", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }

        migrated.query("SELECT content, serverMessageId, senderDeviceId FROM inbox_messages WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Hi", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }

        migrated.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'negotiation_turns'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migrated.close()
    }

    private companion object {
        const val TEST_DB_NAME = "migration-1-2-test"
    }
}
