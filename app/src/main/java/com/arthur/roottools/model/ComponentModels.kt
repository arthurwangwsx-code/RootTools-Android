package com.arthur.roottools.model

enum class ComponentKind(val displayName: String) {
    ACTIVITY("Activity"),
    SERVICE("Service"),
    RECEIVER("Receiver"),
    PROVIDER("Provider"),
}

data class PackageCatalogItem(
    val packageName: String,
    val label: String,
    val systemApp: Boolean,
    val enabled: Boolean,
)

data class AppComponentRecord(
    val componentName: String,
    val className: String,
    val kind: ComponentKind,
    val enabled: Boolean,
    val exported: Boolean,
    val bootReceiver: Boolean = false,
    val foregroundService: Boolean = false,
    val directBootAware: Boolean = false,
    val permission: String? = null,
    val protectedReason: String? = null,
)

data class ComponentSnapshot(
    val packageName: String = "",
    val label: String = "",
    val systemApp: Boolean = false,
    val components: List<AppComponentRecord> = emptyList(),
    val loadedAtMs: Long = 0L,
) {
    val disabledCount: Int get() = components.count { !it.enabled }
    val bootReceiverCount: Int get() = components.count { it.bootReceiver }
    val exportedCount: Int get() = components.count { it.exported }
    val foregroundServiceCount: Int get() = components.count { it.foregroundService }
}
