package com.arthur.roottools.root

import android.util.Log
import com.arthur.roottools.core.privilege.RootCommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RootAuthorizationStatus {
    UNKNOWN,
    REQUESTING,
    GRANTED,
    DENIED_OR_TIMEOUT,
}

data class RootAuthorizationSnapshot(
    val status: RootAuthorizationStatus = RootAuthorizationStatus.UNKNOWN,
    val detail: String = "",
) {
    val granted: Boolean get() = status == RootAuthorizationStatus.GRANTED
}

internal object RootAuthorizationPolicy {
    fun fromProbe(result: RootCommandResult): RootAuthorizationSnapshot {
        val uid = result.output.trim()
        return when {
            result.success && uid == "0" -> RootAuthorizationSnapshot(RootAuthorizationStatus.GRANTED)
            result.timedOut -> RootAuthorizationSnapshot(
                RootAuthorizationStatus.DENIED_OR_TIMEOUT,
                "Root authorization timed out",
            )
            result.output.isNotBlank() -> RootAuthorizationSnapshot(
                RootAuthorizationStatus.DENIED_OR_TIMEOUT,
                result.output.take(240),
            )
            else -> RootAuthorizationSnapshot(
                RootAuthorizationStatus.DENIED_OR_TIMEOUT,
                "Root authorization was not granted",
            )
        }
    }
}

/**
 * Owns the one explicit Magisk/SU authorization flow for the app process.
 *
 * Background collectors must keep their probes short. Only this explicit flow is allowed to hold
 * a pending su request long enough for the user to act on Magisk's confirmation UI.
 */
class RootAuthorizationManager(private val shell: RootShell) {
    private val _state = MutableStateFlow(RootAuthorizationSnapshot())
    val state: StateFlow<RootAuthorizationSnapshot> = _state.asStateFlow()

    suspend fun request(timeoutSeconds: Long = 60): RootAuthorizationSnapshot {
        _state.value = RootAuthorizationSnapshot(RootAuthorizationStatus.REQUESTING)
        val probe = shell.execute("id -u", timeoutSeconds = timeoutSeconds.coerceIn(10, 120))
        Log.i(
            LOG_TAG,
            "probe exit=${probe.exitCode} timedOut=${probe.timedOut} output=${probe.output.trim().take(80)}",
        )
        val snapshot = RootAuthorizationPolicy.fromProbe(probe)
        _state.value = snapshot
        return snapshot
    }

    private companion object {
        const val LOG_TAG = "RootToolsRootAuth"
    }
}
