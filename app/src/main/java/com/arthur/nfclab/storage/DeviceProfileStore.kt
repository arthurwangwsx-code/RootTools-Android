package com.arthur.nfclab.storage

import android.content.Context
import com.arthur.nfclab.domain.NfcDeviceProfile
import org.json.JSONObject
import java.io.File

class DeviceProfileStore(context: Context) {
    private val cacheFile = File(context.noBackupFilesDir, "device_nfc_profile_cache.json")

    @Synchronized
    fun load(): NfcDeviceProfile? {
        if (!cacheFile.isFile) return null
        return runCatching {
            NfcDeviceProfile.fromJson(JSONObject(cacheFile.readText()))
        }.getOrNull()
    }

    @Synchronized
    fun save(profile: NfcDeviceProfile) {
        val temp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        runCatching {
            temp.writeText(profile.toJson().toString())
            if (!temp.renameTo(cacheFile)) {
                cacheFile.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure {
            temp.delete()
        }
    }
}
