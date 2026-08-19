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
    suspend fun setEnabled(
        snapshot: ComponentSnapshot,
        component: AppComponentRecord,
        enabled: Boolean,
    ): PackageActionResult {
        val decision = ComponentSafetyPolicy.evaluate(snapshot, component)
        if (!decision.allowed) return PackageActionResult(false, rejectionMessage(decision.rejection))
        val componentName = requireNotNull(decision.componentName)
        val result = router.setComponentEnabled(componentName, enabled)
        auditStore?.record(
            source = "$auditSource/${result.backend.displayName}",
            feature = "components",
            action = if (enabled) "enable_component" else "disable_component",
            target = componentName,
            before = if (component.enabled) "enabled" else "disabled",
            after = if (result.success) if (enabled) "enabled" else "disabled" else "failed",
            success = result.success,
            rollbackHint = "恢复为 ${if (component.enabled) "enabled" else "disabled"}",
        )
        return if (result.success) {
            PackageActionResult(
                true,
                "${component.kind.displayName} · ${if (component.enabled) "enabled" else "disabled"} → ${if (enabled) "enabled" else "disabled"} · ${result.backend.displayName}",
            )
        } else {
            PackageActionResult(false, "组件修改失败：${result.detail.take(160)}")
        }
    }

    private fun rejectionMessage(rejection: ComponentMutationRejection?): String = when (rejection) {
        ComponentMutationRejection.INVALID_PACKAGE -> "非法 package name"
        ComponentMutationRejection.SYSTEM_APP -> "首版不允许修改系统 App 组件"
        ComponentMutationRejection.PROTECTED_PACKAGE -> "受保护应用不能修改组件"
        ComponentMutationRejection.INVALID_COMPONENT -> "非法 component name"
        ComponentMutationRejection.CROSS_PACKAGE_COMPONENT -> "组件不属于当前应用"
        ComponentMutationRejection.STALE_COMPONENT -> "组件状态已变化，请刷新后重试"
        ComponentMutationRejection.PROTECTED_COMPONENT -> "关键启动组件受保护"
        null -> "组件修改被安全策略拒绝"
    }
}
