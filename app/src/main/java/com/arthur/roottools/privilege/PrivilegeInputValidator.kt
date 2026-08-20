package com.arthur.roottools.privilege

/**
 * Central validation for values that can cross into privileged shell/Binder adapters.
 *
 * The privileged layer never accepts arbitrary command text. Every dynamic token must pass one of
 * these semantic validators before it can be interpolated into a fixed command template.
 */
object PrivilegeInputValidator {
    private val packageRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val componentRegex = Regex(
        "^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+/[A-Za-z0-9_.$]+$"
    )
    private val appOpRegex = Regex("^[A-Z0-9_]{2,64}$")
    private val permissionRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val appOpModes = setOf("allow", "ignore", "deny", "default", "foreground")
    private val standbyBuckets = setOf(5, 10, 20, 30, 40, 45)

    fun packageName(value: String?): String? = value?.takeIf(packageRegex::matches)

    fun componentName(value: String?): String? = value?.takeIf(componentRegex::matches)

    fun appOpName(value: String?): String? = value?.uppercase()?.takeIf(appOpRegex::matches)

    fun permissionName(value: String?): String? = value?.takeIf(permissionRegex::matches)

    fun appOpMode(value: String?): String? = value?.lowercase()?.takeIf { it in appOpModes }

    fun standbyBucket(value: Int): Int? = value.takeIf { it in standbyBuckets }
}
