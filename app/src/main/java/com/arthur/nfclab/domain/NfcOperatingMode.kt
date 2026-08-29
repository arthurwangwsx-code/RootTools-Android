package com.arthur.nfclab.domain

enum class NfcOperatingMode {
    /** Normal Android NFC routing. Reader mode is not held by NFC Tools. */
    DEFAULT,

    /** NFC Tools owns foreground Reader Mode for tag inspection. */
    READER,

    /** Reader Mode is released so the app's HostApduService can answer external readers. */
    HCE,
}
