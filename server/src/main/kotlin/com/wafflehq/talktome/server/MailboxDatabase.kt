package com.wafflehq.talktome.server

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

class MailboxDatabase(jdbcUrl: String) {

    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS devices (
                    id TEXT PRIMARY KEY,
                    token TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS mailbox (
                    id TEXT PRIMARY KEY,
                    recipient_device_id TEXT NOT NULL,
                    sender_device_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    ciphertext TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mailbox_recipient ON mailbox(recipient_device_id)")
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_devices_token ON devices(token)")
        }
    }

    @Synchronized
    fun registerDevice(): DeviceRegistrationResponse {
        val id = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        connection.prepareStatement("INSERT INTO devices (id, token, created_at) VALUES (?, ?, ?)").use { statement ->
            statement.setString(1, id)
            statement.setString(2, token)
            statement.setLong(3, Instant.now().toEpochMilli())
            statement.executeUpdate()
        }
        return DeviceRegistrationResponse(id, token)
    }

    @Synchronized
    fun deviceIdForToken(token: String): String? {
        connection.prepareStatement("SELECT id FROM devices WHERE token = ?").use { statement ->
            statement.setString(1, token)
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) resultSet.getString("id") else null
            }
        }
    }

    @Synchronized
    fun deviceExists(deviceId: String): Boolean {
        connection.prepareStatement("SELECT 1 FROM devices WHERE id = ?").use { statement ->
            statement.setString(1, deviceId)
            statement.executeQuery().use { resultSet -> return resultSet.next() }
        }
    }

    @Synchronized
    fun enqueue(recipientDeviceId: String, senderDeviceId: String, kind: String, ciphertext: String): String {
        val id = UUID.randomUUID().toString()
        connection.prepareStatement(
            "INSERT INTO mailbox (id, recipient_device_id, sender_device_id, kind, ciphertext, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, recipientDeviceId)
            statement.setString(3, senderDeviceId)
            statement.setString(4, kind)
            statement.setString(5, ciphertext)
            statement.setLong(6, Instant.now().toEpochMilli())
            statement.executeUpdate()
        }
        return id
    }

    @Synchronized
    fun pending(recipientDeviceId: String): List<MailboxItem> {
        connection.prepareStatement(
            "SELECT id, sender_device_id, kind, ciphertext, created_at FROM mailbox " +
                "WHERE recipient_device_id = ? ORDER BY created_at ASC",
        ).use { statement ->
            statement.setString(1, recipientDeviceId)
            statement.executeQuery().use { resultSet ->
                val items = mutableListOf<MailboxItem>()
                while (resultSet.next()) {
                    items += MailboxItem(
                        messageId = resultSet.getString("id"),
                        senderDeviceId = resultSet.getString("sender_device_id"),
                        kind = resultSet.getString("kind"),
                        ciphertext = resultSet.getString("ciphertext"),
                        createdAt = resultSet.getLong("created_at"),
                    )
                }
                return items
            }
        }
    }

    @Synchronized
    fun delete(messageId: String, recipientDeviceId: String): Boolean {
        connection.prepareStatement("DELETE FROM mailbox WHERE id = ? AND recipient_device_id = ?").use { statement ->
            statement.setString(1, messageId)
            statement.setString(2, recipientDeviceId)
            return statement.executeUpdate() > 0
        }
    }

    fun close() = connection.close()
}
