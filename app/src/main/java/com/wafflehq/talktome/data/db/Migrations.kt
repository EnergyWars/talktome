package com.wafflehq.talktome.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE partners ADD COLUMN remoteDeviceId TEXT")
        database.execSQL("ALTER TABLE partners ADD COLUMN serverBaseUrl TEXT")

        database.execSQL("ALTER TABLE outgoing_messages ADD COLUMN friendOpinionsJson TEXT")
        database.execSQL("ALTER TABLE outgoing_messages ADD COLUMN rejectionCount INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE outgoing_messages ADD COLUMN lastRejectionReason TEXT")
        database.execSQL("ALTER TABLE outgoing_messages ADD COLUMN serverMessageId TEXT")

        database.execSQL("ALTER TABLE inbox_messages ADD COLUMN serverMessageId TEXT")
        database.execSQL("ALTER TABLE inbox_messages ADD COLUMN senderDeviceId TEXT")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS negotiation_turns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                outgoingMessageId INTEGER NOT NULL,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_negotiation_turns_outgoingMessageId ON negotiation_turns(outgoingMessageId)",
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
