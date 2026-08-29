package com.arthur.nfclab.hce

object IsoDepLabProfiles {
    val default = IsoDepLabProfile(
        aid = LabApduProtocol.AID,
        displayName = "NFC Tools ISO-DEP Lab",
        applicationData = "synthetic-access-lab".encodeToByteArray(),
        testKey = byteArrayOf(
            0x10, 0x32, 0x54, 0x76, 0x01, 0x23, 0x45, 0x67,
            0x11, 0x33, 0x55, 0x77, 0x02, 0x24, 0x46, 0x68,
        ),
    )
}

