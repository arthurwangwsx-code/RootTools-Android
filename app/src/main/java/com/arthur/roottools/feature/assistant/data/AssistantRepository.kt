package com.arthur.roottools.feature.assistant.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import com.arthur.roottools.feature.assistant.model.AssistantCandidate
import com.arthur.roottools.feature.assistant.model.AssistantSnapshot
import com.arthur.roottools.feature.assistant.model.PowerKeyAssistantBinding
import com.arthur.roottools.feature.assistant.model.PowerKeyAssistantState
import com.arthur.roottools.privilege.PrivilegeRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantRepository(
    context: Context,
    private val privilegeRouter: PrivilegeRouter,
) {
    private val appContext = context.applicationContext

    suspend fun snapshot(): AssistantSnapshot = withContext(Dispatchers.IO) {
        val holderResult = privilegeRouter.getAssistantRoleHolder()
        val currentPackage = holderResult.value?.trim()?.ifBlank { null }
        val candidates = discoverVoiceInteractionCandidates(currentPackage)
        AssistantSnapshot(
            currentPackage = currentPackage,
            candidates = candidates,
            powerKey = readPowerKeyState(),
            readBackend = holderResult.backend,
            readError = holderResult.detail.takeIf { !holderResult.success },
        )
    }

    private fun discoverVoiceInteractionCandidates(currentPackage: String?): List<AssistantCandidate> {
        val packageManager = appContext.packageManager
        val services = packageManager.queryIntentServices(
            Intent(VoiceInteractionService.SERVICE_INTERFACE),
            PackageManager.MATCH_ALL,
        )
            .mapNotNull { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
                if (serviceInfo.permission != Manifest.permission.BIND_VOICE_INTERACTION) return@mapNotNull null
                serviceInfo
            }
            .groupBy { it.packageName }

        return services.map { (packageName, packageServices) ->
            val applicationInfo = packageServices.first().applicationInfo
            AssistantCandidate(
                packageName = packageName,
                label = runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
                    .getOrDefault(packageName),
                voiceServiceComponents = packageServices
                    .map { ComponentName(it.packageName, it.name).flattenToShortString() }
                    .distinct()
                    .sorted(),
            )
        }.sortedWith(
            compareByDescending<AssistantCandidate> { it.packageName == currentPackage }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName },
        )
    }

    private fun readPowerKeyState(): PowerKeyAssistantState {
        val resolver = appContext.contentResolver
        val oemLongPress = runCatching {
            Settings.System.getString(resolver, OEM_LONG_PRESS_POWER_KEY)
        }.getOrNull()?.takeUnless { it == "null" }
        val aospLongPress = runCatching {
            Settings.Global.getString(resolver, AOSP_LONG_PRESS_POWER_KEY)
        }.getOrNull()?.takeUnless { it == "null" }
        val aospVeryLongPress = runCatching {
            Settings.Global.getString(resolver, AOSP_VERY_LONG_PRESS_POWER_KEY)
        }.getOrNull()?.takeUnless { it == "null" }

        val binding = when {
            oemLongPress == OEM_LAUNCH_VOICE_ASSISTANT -> PowerKeyAssistantBinding.ASSISTANT
            oemLongPress != null -> PowerKeyAssistantBinding.OTHER
            else -> PowerKeyAssistantBinding.UNKNOWN
        }
        return PowerKeyAssistantState(
            binding = binding,
            oemLongPressValue = oemLongPress,
            aospLongPressValue = aospLongPress,
            aospVeryLongPressValue = aospVeryLongPress,
        )
    }

    private companion object {
        const val OEM_LONG_PRESS_POWER_KEY = "long_press_power_key"
        const val OEM_LAUNCH_VOICE_ASSISTANT = "launch_voice_assistant"
        const val AOSP_LONG_PRESS_POWER_KEY = "power_button_long_press"
        const val AOSP_VERY_LONG_PRESS_POWER_KEY = "power_button_very_long_press"
    }
}
