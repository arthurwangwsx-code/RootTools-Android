package com.arthur.roottools.model

data class MagiskModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val disabledMarker: Boolean,
    val removeMarker: Boolean,
    val protected: Boolean = false,
)

data class VectorModuleInfo(
    val packageName: String,
    val uid: Int,
    val enabled: Boolean,
)

data class VectorScopeEntry(
    val packageName: String,
    val userId: Int,
)

data class ModuleCenterSnapshot(
    val magiskModules: List<MagiskModuleInfo> = emptyList(),
    val vectorModules: List<VectorModuleInfo> = emptyList(),
    val vectorActive: Boolean = false,
    val scopes: Map<String, List<VectorScopeEntry>> = emptyMap(),
) {
    val enabledMagiskCount: Int get() = magiskModules.count { !it.disabledMarker && !it.removeMarker }
    val enabledVectorCount: Int get() = vectorModules.count { it.enabled }
}
