package com.arthur.roottools.policy

import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.privilege.PrivilegeInputValidator

data class ComponentMutationDecision(
    val allowed: Boolean,
    val reason: String = "",
)

/**
 * Pure safety gate for component mutations.
 *
 * Keep these invariants out of Compose/Android APIs so they remain unit-testable and apply equally
 * to UI, automation and future profile/batch entry points.
 */
object ComponentSafetyPolicy {
    fun evaluate(
        snapshot: ComponentSnapshot,
        component: AppComponentRecord,
        protectedPackages: Set<String> = ComponentPolicyController.PROTECTED_PACKAGES,
    ): ComponentMutationDecision {
        if (PrivilegeInputValidator.packageName(snapshot.packageName) == null) {
            return ComponentMutationDecision(false, "非法 package name")
        }
        if (snapshot.systemApp) {
            return ComponentMutationDecision(false, "首版不允许修改系统 App 组件")
        }
        if (snapshot.packageName in protectedPackages) {
            return ComponentMutationDecision(false, "受保护应用不能修改组件")
        }
        val componentName = PrivilegeInputValidator.componentName(component.componentName)
            ?: return ComponentMutationDecision(false, "非法 component name")
        if (!componentName.startsWith("${snapshot.packageName}/")) {
            return ComponentMutationDecision(false, "组件不属于当前应用")
        }
        if (snapshot.components.none { it.componentName == component.componentName }) {
            return ComponentMutationDecision(false, "组件不在当前快照中")
        }
        component.protectedReason?.let {
            return ComponentMutationDecision(false, "受保护组件：$it")
        }
        return ComponentMutationDecision(true)
    }
}
