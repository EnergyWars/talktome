package com.wafflehq.uikit.database.state

object DatabaseKeyRecoveryState {
    @Volatile
    var keyUnavailable: Boolean = false
}
