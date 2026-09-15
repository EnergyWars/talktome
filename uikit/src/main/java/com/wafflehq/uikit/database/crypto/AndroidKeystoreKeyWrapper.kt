package com.wafflehq.uikit.database.crypto

import com.wafflehq.uikit.security.AndroidKeystoreAesGcmBox

class AndroidKeystoreKeyWrapper(alias: String) : KeystoreKeyWrapper {

    private val box = AndroidKeystoreAesGcmBox(alias)

    override fun wrap(dek: ByteArray): WrappedDek =
        box.encrypt(dek).let { WrappedDek(iv = it.iv, ciphertext = it.ciphertext) }

    override fun unwrap(wrapped: WrappedDek): ByteArray =
        box.decrypt(wrapped.iv, wrapped.ciphertext)

    override fun deleteKey() = box.deleteKey()
}
