package com.arthur.nfclab.platform

import android.content.Context
import com.arthur.nfclab.domain.NfcDeviceProfile
class DeviceNfcProfileRepository(
    context: Context,
    private val providers: List<NfcProfileProvider> = defaultProviders(),
) {
    private val appContext = context.applicationContext

    fun collect(): NfcDeviceProfile {
        var profile = GenericAndroidProfileCollector.collect(appContext)
        providers
            .asSequence()
            .filter { provider -> runCatching { provider.supports(appContext) }.getOrDefault(false) }
            .sortedBy { it.priority }
            .forEach { provider ->
                profile = runCatching { provider.enrich(appContext, profile) }
                    .getOrElse { error ->
                        profile.copy(
                            error = listOfNotNull(
                                profile.error,
                                "${provider.id}: ${error.javaClass.simpleName}: ${error.message}",
                            ).joinToString("; "),
                        )
                    }
            }
        return profile.copy(collectedAtMs = System.currentTimeMillis())
    }

    companion object {
        fun defaultProviders(): List<NfcProfileProvider> = NfcProfileProviderRegistry.defaults()
    }
}
