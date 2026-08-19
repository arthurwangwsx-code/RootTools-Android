package com.arthur.roottools.policy

import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.privilege.PrivilegeInputValidator

enum class ComponentMutationRejection {
    INVALID_PACKAGE,
    SYSTEM_APP,
    PROTECTED_PACKAGE,
    INVALID_COMPONENT,
    CROSS_PACKAGE_COMPONENT,
    STALE_COMPONENT,
    PROTECTED_COMPONENT,
}

data class ComponentMutationDecision(
    val allowed: Boolean,
    val componentName: String? = null,
    val rejection: ComponentMutationRejection? = null,
)

/** Pure safety gate shared by every future component-management entry point. */
object ComponentSafetyPolicy {
    fun evaluate(
        snapshot: ComponentSnapshot,
        component: AppComponentRecord,
        protectedPackages: Set<String> = PackagePolicyController.PROTECTED_PACKAGES,
    ): ComponentMutationDecision {
        val pkg = PrivilegeInputValidator.packageName(snapshot.packageName)
            ?: return ComponentMutationDecision(false, rejection = ComponentMutationRejection.INVALID_PACKAGE)
        if (snapshot.systemApp) return ComponentMutationDecision(false, rejection = ComponentMutationRejection.SYSTEM_APP)
        if (pkg in protectedPackages) return ComponentMutationDecision(false, rejection = ComponentMutationRejection.PROTECTED_PACKAGE)
        val componentName = PrivilegeInputValidator.componentName(component.componentName)
            ?: return ComponentMutationDecision(false, rejection = ComponentMutationRejection.INVALID_COMPONENT)
        if (!componentName.startsWith("$pkg/")) {
            return ComponentMutationDecision(false, componentName, ComponentMutationRejection.CROSS_PACKAGE_COMPONENT)
        }
        if (snapshot.components.none { it.componentName == component.componentName }) {
            return ComponentMutationDecision(false, componentName, ComponentMutationRejection.STALE_COMPONENT)
        }
        if (component.protectedReason != null) {
            return ComponentMutationDecision(false, componentName, ComponentMutationRejection.PROTECTED_COMPONENT)
        }
        return ComponentMutationDecision(true, componentName)
    }
}
