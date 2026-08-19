package com.arthur.roottools.privilege

data class ShizukuSelfTestResult(
    val uid: Int? = null,
    val packageControl: Boolean = false,
    val activityControl: Boolean = false,
    val appOps: Boolean = false,
) {
    val allPassed: Boolean get() = uid != null && packageControl && activityControl && appOps
}

/** Pure parser for the bounded UserService self-test protocol. */
object ShizukuSelfTestParser {
    fun parse(raw: String): ShizukuSelfTestResult {
        val values = raw.lineSequence()
            .flatMap { it.split(';').asSequence() }
            .mapNotNull { token ->
                val key = token.substringBefore('=', "").trim()
                val value = token.substringAfter('=', "").trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
        return ShizukuSelfTestResult(
            uid = values["uid"]?.toIntOrNull(),
            packageControl = values["pm"] == "ok",
            activityControl = values["activity"] == "ok",
            appOps = values["appops"] == "ok",
        )
    }
}
