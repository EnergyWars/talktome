package com.wafflehq.uikit.database

object SqlCipherNativeLibrary {
    fun load() {
        System.loadLibrary("sqlcipher")
    }
}
