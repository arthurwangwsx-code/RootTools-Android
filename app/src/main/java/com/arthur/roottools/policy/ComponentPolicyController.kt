package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.privilege.PrivilegeRouter

class ComponentPolicyController(
    private val router: PrivilegeRouter,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "UI",
) {
    suspend fun setEnabled(snapshot: ComponentSnapshot, component: AppComponentRecord, enabled: Boolean): PackageActionResult {
        val safety = ComponentSafetyPolicy.evaluate(snapshot, component, PROTECTED_PACKAGES)
        if (!safety.allowed) return PackageActionResult(false, safety.reason)
        val result = router.setComponentEnabled(component.componentName, enabled)
        auditStore?.record(
            source = "$auditSource/${result.backend.displayName}",
            feature = "components",
            action = if (enabled) "enable_component" else "disable_component",
            target = component.componentName,
            before = if (component.enabled) "enabled" else "disabled",
            after = if (result.success) if (enabled) "enabled" else "disabled" else "failed",
            success = result.success,
            rollbackHint = "恢复组件为 ${if (component.enabled) "enabled" else "disabled"}",
        )
        return if (result.success) {
            PackageActionResult(true, "组件已${if (enabled) "启用" else "禁用"} · ${result.backend.displayName}")
        } else {
            PackageActionResult(false, "组件操作失败：${result.detail.take(160)}")
        }
    }

    suspend fun launch(snapshot: ComponentSnapshot, component: AppComponentRecord): PackageActionResult {
        if (component.kind != com.arthur.roottools.model.ComponentKind.ACTIVITY) return PackageActionResult(false, "只有 Activity 可以启动")
        if (!component.enabled) return PackageActionResult(false, "Activity 当前已禁用")
        if (!component.componentName.startsWith("${snapshot.packageName}/")) return PackageActionResult(false, "组件不属于当前应用")
        val result = router.launchActivity(component.componentName)
        return if (result.success) {
            PackageActionResult(true, "Activity 已启动 · ${result.backend.displayName}")
        } else {
            PackageActionResult(false, "Activity 启动失败：${result.detail.take(160)}")
        }
    }

    companion object {
        val PROTECTED_PACKAGES = PackagePolicyController.PROTECTED_PACKAGES + setOf(
            "com.topjohnwu.magisk",
            "org.matrix.vector.manager",
        )
    }
}
