package com.wafflehq.uikit.database.state

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.wafflehq.uikit.database.DatabaseEncryptionDefaults
import com.wafflehq.uikit.database.crypto.PasswordWrappedDek
import com.wafflehq.uikit.database.crypto.WrappedDek

enum class ConversionState { NONE, EXPORTING, SWAPPING, ROLLING_BACK }

class EncryptionStateStore(
    context: Context,
    prefsName: String = DatabaseEncryptionDefaults.STATE_PREFS_NAME,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    val isEncrypted: Boolean
        get() = prefs.getBoolean(KEY_IS_ENCRYPTED, false)

    val conversionState: ConversionState
        get() = prefs.getString(KEY_CONVERSION_STATE, null)
            ?.let { runCatching { ConversionState.valueOf(it) }.getOrNull() }
            ?: ConversionState.NONE

    fun readWrappedDek(): WrappedDek? {
        val iv = prefs.getString(KEY_DEK_IV, null) ?: return null
        val ciphertext = prefs.getString(KEY_DEK_CIPHERTEXT, null) ?: return null
        return WrappedDek(
            iv = Base64.decode(iv, Base64.NO_WRAP),
            ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
        )
    }

    fun readPasswordWrappedDek(): PasswordWrappedDek? {
        val salt = prefs.getString(KEY_PW_DEK_SALT, null) ?: return null
        val iv = prefs.getString(KEY_PW_DEK_IV, null) ?: return null
        val ciphertext = prefs.getString(KEY_PW_DEK_CIPHERTEXT, null) ?: return null
        val iterations = prefs.getInt(KEY_PW_DEK_ITERATIONS, -1).takeIf { it > 0 } ?: return null
        return PasswordWrappedDek(
            salt = Base64.decode(salt, Base64.NO_WRAP),
            iv = Base64.decode(iv, Base64.NO_WRAP),
            ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
            iterations = iterations,
        )
    }

    fun writePasswordWrappedDek(wrapped: PasswordWrappedDek?) {
        prefs.edit().apply {
            if (wrapped != null) {
                putString(KEY_PW_DEK_SALT, Base64.encodeToString(wrapped.salt, Base64.NO_WRAP))
                putString(KEY_PW_DEK_IV, Base64.encodeToString(wrapped.iv, Base64.NO_WRAP))
                putString(KEY_PW_DEK_CIPHERTEXT, Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
                putInt(KEY_PW_DEK_ITERATIONS, wrapped.iterations)
            } else {
                remove(KEY_PW_DEK_SALT)
                remove(KEY_PW_DEK_IV)
                remove(KEY_PW_DEK_CIPHERTEXT)
                remove(KEY_PW_DEK_ITERATIONS)
            }
        }.commit()
    }

    fun commitConversionStart() {
        prefs.edit().putString(KEY_CONVERSION_STATE, ConversionState.EXPORTING.name).commit()
    }

    fun commitSwapIntent(newIsEncrypted: Boolean, newWrappedDek: WrappedDek?, newPasswordWrappedDek: PasswordWrappedDek? = null) =
        commitDekState(ConversionState.SWAPPING, newIsEncrypted, newWrappedDek, newPasswordWrappedDek)

    fun commitRollbackIntent(oldIsEncrypted: Boolean, oldWrappedDek: WrappedDek?, oldPasswordWrappedDek: PasswordWrappedDek? = null) =
        commitDekState(ConversionState.ROLLING_BACK, oldIsEncrypted, oldWrappedDek, oldPasswordWrappedDek)

    private fun commitDekState(
        state: ConversionState,
        isEncrypted: Boolean,
        wrappedDek: WrappedDek?,
        passwordWrappedDek: PasswordWrappedDek?,
    ) {
        prefs.edit().apply {
            putString(KEY_CONVERSION_STATE, state.name)
            putBoolean(KEY_IS_ENCRYPTED, isEncrypted)
            if (wrappedDek != null) {
                putString(KEY_DEK_IV, Base64.encodeToString(wrappedDek.iv, Base64.NO_WRAP))
                putString(KEY_DEK_CIPHERTEXT, Base64.encodeToString(wrappedDek.ciphertext, Base64.NO_WRAP))
            } else {
                remove(KEY_DEK_IV)
                remove(KEY_DEK_CIPHERTEXT)
            }
            if (passwordWrappedDek != null) {
                putString(KEY_PW_DEK_SALT, Base64.encodeToString(passwordWrappedDek.salt, Base64.NO_WRAP))
                putString(KEY_PW_DEK_IV, Base64.encodeToString(passwordWrappedDek.iv, Base64.NO_WRAP))
                putString(KEY_PW_DEK_CIPHERTEXT, Base64.encodeToString(passwordWrappedDek.ciphertext, Base64.NO_WRAP))
                putInt(KEY_PW_DEK_ITERATIONS, passwordWrappedDek.iterations)
            } else {
                remove(KEY_PW_DEK_SALT)
                remove(KEY_PW_DEK_IV)
                remove(KEY_PW_DEK_CIPHERTEXT)
                remove(KEY_PW_DEK_ITERATIONS)
            }
        }.commit()
    }

    fun commitSwapComplete() {
        prefs.edit().putString(KEY_CONVERSION_STATE, ConversionState.NONE.name).commit()
    }

    fun resetAfterUnrecoverableKeyLoss() {
        prefs.edit()
            .putBoolean(KEY_IS_ENCRYPTED, false)
            .putString(KEY_CONVERSION_STATE, ConversionState.NONE.name)
            .remove(KEY_DEK_IV)
            .remove(KEY_DEK_CIPHERTEXT)
            .remove(KEY_PW_DEK_SALT)
            .remove(KEY_PW_DEK_IV)
            .remove(KEY_PW_DEK_CIPHERTEXT)
            .remove(KEY_PW_DEK_ITERATIONS)
            .commit()
    }

    private companion object {
        const val KEY_IS_ENCRYPTED = "is_encrypted"
        const val KEY_CONVERSION_STATE = "conversion_state"
        const val KEY_DEK_IV = "dek_iv"
        const val KEY_DEK_CIPHERTEXT = "dek_ciphertext"
        const val KEY_PW_DEK_SALT = "pw_dek_salt"
        const val KEY_PW_DEK_IV = "pw_dek_iv"
        const val KEY_PW_DEK_CIPHERTEXT = "pw_dek_ciphertext"
        const val KEY_PW_DEK_ITERATIONS = "pw_dek_iterations"
    }
}
