package com.wafflehq.uikit.database.open

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

fun interface EncryptedDatabaseProbe {
    fun canOpen(file: File, dek: ByteArray): Boolean
}

object SqlCipherDatabaseProbe : EncryptedDatabaseProbe {
    override fun canOpen(file: File, dek: ByteArray): Boolean {
        if (!file.exists()) return true
        val db = try {
            SQLiteDatabase.openDatabase(file.path, dek, null, SQLiteDatabase.OPEN_READONLY, null, null)
        } catch (e: Throwable) {
            return false
        }
        return try {
            val cursor = db.rawQuery("PRAGMA cipher_integrity_check", null)
            try {
                !cursor.moveToFirst()
            } finally {
                cursor.close()
            }
        } catch (e: Throwable) {
            false
        } finally {
            db.close()
        }
    }
}
