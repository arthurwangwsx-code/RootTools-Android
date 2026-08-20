package com.arthur.roottools.automation

enum class AutomationScope {
    READ_STATUS,
    RUN_DIAGNOSTIC,
    SET_PERFORMANCE,
    SET_ADB_ENABLE,
    SET_NATIVE_ADB,
    APP_POLICY,
    INTEGRITY_SCAN,
}

enum class AutomationCommand(
    val wireName: String,
    val requiredScope: AutomationScope,
) {
    GET_STATUS("GET_STATUS", AutomationScope.READ_STATUS),
    SET_MODE("SET_MODE", AutomationScope.SET_PERFORMANCE),
    SET_ADB("SET_ADB", AutomationScope.SET_ADB_ENABLE),
    SET_NATIVE_ADB("SET_NATIVE_ADB", AutomationScope.SET_NATIVE_ADB),
    RUN_DIAGNOSTIC("RUN_DIAGNOSTIC", AutomationScope.RUN_DIAGNOSTIC),
    FREEZE("FREEZE", AutomationScope.APP_POLICY),
    UNFREEZE("UNFREEZE", AutomationScope.APP_POLICY),
    INTEGRITY_FAST_SCAN("INTEGRITY_FAST_SCAN", AutomationScope.INTEGRITY_SCAN),
    INTEGRITY_DEEP_SCAN("INTEGRITY_DEEP_SCAN", AutomationScope.INTEGRITY_SCAN),
    INTEGRITY_EXPORT_LAST_REPORT("INTEGRITY_EXPORT_LAST_REPORT", AutomationScope.INTEGRITY_SCAN),
    ;

    companion object {
        fun parse(value: String?): AutomationCommand? = entries.firstOrNull {
            it.wireName == value?.trim()?.uppercase()
        }
    }
}

object AutomationAuthorizationPolicy {
    fun isAllowed(
        scopes: Set<AutomationScope>,
        command: AutomationCommand,
        enabled: Boolean? = null,
    ): Boolean {
        if (command.requiredScope !in scopes) return false
        // The external automation API may keep the management lifeline enabled, but it must not
        // be able to turn Root TCP ADB off and strand the device.
        if (command == AutomationCommand.SET_ADB && enabled == false) return false
        return true
    }

    val termuxDefaultScopes: Set<AutomationScope> = setOf(
        AutomationScope.READ_STATUS,
        AutomationScope.RUN_DIAGNOSTIC,
        AutomationScope.SET_PERFORMANCE,
        AutomationScope.SET_ADB_ENABLE,
        AutomationScope.SET_NATIVE_ADB,
        AutomationScope.APP_POLICY,
        AutomationScope.INTEGRITY_SCAN,
    )
}

