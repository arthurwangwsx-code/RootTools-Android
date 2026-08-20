package com.arthur.roottools.model

enum class ComponentKind(val displayName: String) {
    ACTIVITY("Activity"),
    SERVICE("Service"),
    RECEIVER("Receiver"),
    PROVIDER("Provider"),
}

data class AppComponentRecord(
    val componentName: String,
    val className: String,
    val kind: ComponentKind,
    val enabled: Boolean,
    val exported: Boolean,
    val permission: String? = null,
    val directBootAware: Boolean = false,
    val bootReceiver: Boolean = false,
    val foregroundService: Boolean = false,
    val protectedReason: String? = null,
)

data class ComponentSnapshot(
    val packageName: String = "",
    val label: String = "",
    val appEnabled: Boolean = true,
    val systemApp: Boolean = false,
    val components: List<AppComponentRecord> = emptyList(),
    val loadedAtMs: Long = 0L,
) {
    val bootReceiverCount: Int get() = components.count { it.bootReceiver }
    val exportedCount: Int get() = components.count { it.exported }
    val foregroundServiceCount: Int get() = components.count { it.foregroundService }
    val disabledCount: Int get() = components.count { !it.enabled }
}
