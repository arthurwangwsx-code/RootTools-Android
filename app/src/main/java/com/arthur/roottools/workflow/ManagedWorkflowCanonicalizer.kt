package com.arthur.roottools.workflow

object ManagedWorkflowCanonicalizer {
    fun canonicalPayload(
        request: ManagedWorkflowRequest,
        deviceId: String,
        createdAtEpochMs: Long,
    ): String {
        require(DEVICE_ID_REGEX.matches(deviceId)) { "Invalid developer device id" }
        require(createdAtEpochMs > 0L) { "Invalid workflow timestamp" }
        val validation = ManagedWorkflowPolicy.validate(request)
        require(validation.valid) { validation.message }
        val definition = ManagedWorkflowCatalog.get(request.workflowId)
        val steps = definition.steps.joinToString(",") { step ->
            buildString {
                append("{\"type\":")
                append(quote(step.type.name))
                step.performanceMode?.let {
                    append(",\"performanceMode\":")
                    append(quote(it.name))
                }
                append('}')
            }
        }
        return buildString {
            append("{\"schemaVersion\":1")
            append(",\"workflowId\":").append(quote(definition.id.name))
            append(",\"workflowVersion\":").append(definition.version)
            append(",\"deviceId\":").append(quote(deviceId))
            append(",\"createdAtEpochMs\":").append(createdAtEpochMs)
            append(",\"packageName\":")
            append(request.packageName?.let(::quote) ?: "null")
            append(",\"steps\":[").append(steps).append("]}")
        }
    }

    internal fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private val DEVICE_ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
}

