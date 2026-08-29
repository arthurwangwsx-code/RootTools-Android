package com.arthur.nfclab.platform.runtime

interface NfcModeDriver {
    val available: Boolean
    val enabled: Boolean

    fun setHceTestServiceEnabled(enabled: Boolean)

    fun enableReaderMode()

    fun disableReaderMode()
}
