package com.arthur.roottools.feature.network.inspection.capture

object CaptureReadinessPolicy {
    fun status(
        rootAvailable: Boolean,
        pcapdAvailable: Boolean,
        tcpdumpAvailable: Boolean,
        activeSessionRecovered: Boolean,
    ): CaptureStatus = when {
        !rootAvailable -> CaptureStatus.ROOT_REQUIRED
        activeSessionRecovered -> CaptureStatus.ACTIVE_RECOVERED
        pcapdAvailable -> CaptureStatus.READY_PCAPD
        tcpdumpAvailable -> CaptureStatus.READY_TCPDUMP
        else -> CaptureStatus.BACKEND_UNAVAILABLE
    }
}
