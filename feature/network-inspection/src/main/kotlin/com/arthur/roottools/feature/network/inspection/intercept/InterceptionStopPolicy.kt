package com.arthur.roottools.feature.network.inspection.intercept

object InterceptionStopPolicy {
    fun outcome(cleanupSucceeded: Boolean, technicalDetail: String?): InterceptionStopOutcome =
        if (cleanupSucceeded) {
            InterceptionStopOutcome(
                phase = InterceptionPhase.IDLE,
                status = InterceptionStatus.STOPPED,
                lastError = null,
            )
        } else {
            InterceptionStopOutcome(
                phase = InterceptionPhase.ERROR,
                status = InterceptionStatus.RULE_CLEANUP_FAILED,
                lastError = technicalDetail?.takeIf { it.isNotBlank() }
                    ?: "Interception stopped, but RootTools could not verify that redirect rules were removed",
            )
        }
}
