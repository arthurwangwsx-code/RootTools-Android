package com.arthur.nfclab.platform

import android.content.Context
import com.arthur.nfclab.domain.NfcDeviceProfile

interface NfcProfileProvider {
    val id: String
    val priority: Int

    fun supports(context: Context): Boolean

    fun enrich(context: Context, base: NfcDeviceProfile): NfcDeviceProfile
}
