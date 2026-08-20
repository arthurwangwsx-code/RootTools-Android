package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.AppActionPlan
import com.arthur.roottools.model.AppPlanStatus
import com.arthur.roottools.model.AppPlanStep
import com.arthur.roottools.model.AppPlanStepKind
import com.arthur.roottools.model.AppPolicyProfileId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppActionPlanStore(context: Context) {
    private val file = File(context.filesDir, "app-control-action-plans.json")

    @Synchronized
    fun append(plan: AppActionPlan) {
        val current = readAll().filterNot { it.id == plan.id }.toMutableList()
        current += plan
        write(current.takeLast(MAX_PLANS))
    }

    @Synchronized
    fun readAll(): List<AppActionPlan> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) decode(array.getJSONObject(index))?.let(::add)
            }
        }.getOrDefault(emptyList())
    }

    fun lastApplied(): AppActionPlan? = readAll().lastOrNull { it.status == AppPlanStatus.APPLIED }

    private fun write(plans: List<AppActionPlan>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val array = JSONArray().apply { plans.forEach { put(encode(it)) } }
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    private fun encode(plan: AppActionPlan): JSONObject = JSONObject().apply {
        put("id", plan.id)
        put("createdAtMs", plan.createdAtMs)
        put("profileId", plan.profileId.name)
        put("packages", JSONArray(plan.packages))
        put("warnings", JSONArray(plan.warnings))
        put("status", plan.status.name)
        put("appliedAtMs", plan.appliedAtMs ?: JSONObject.NULL)
        put("steps", JSONArray().apply {
            plan.steps.forEach { step ->
                put(JSONObject().apply {
                    put("packageName", step.packageName)
                    put("kind", step.kind.name)
                    put("key", step.key)
                    put("before", step.before)
                    put("after", step.after)
                })
            }
        })
    }

    private fun decode(json: JSONObject): AppActionPlan? = runCatching {
        AppActionPlan(
            id = json.getString("id"),
            createdAtMs = json.getLong("createdAtMs"),
            profileId = AppPolicyProfileId.valueOf(json.getString("profileId")),
            packages = json.getJSONArray("packages").toStringList(),
            warnings = json.optJSONArray("warnings")?.toStringList().orEmpty(),
            status = AppPlanStatus.valueOf(json.getString("status")),
            appliedAtMs = json.optLong("appliedAtMs").takeIf { json.has("appliedAtMs") && !json.isNull("appliedAtMs") },
            steps = buildList {
                val steps = json.getJSONArray("steps")
                for (index in 0 until steps.length()) {
                    val item = steps.getJSONObject(index)
                    add(
                        AppPlanStep(
                            packageName = item.getString("packageName"),
                            kind = AppPlanStepKind.valueOf(item.getString("kind")),
                            key = item.getString("key"),
                            before = item.getString("before"),
                            after = item.getString("after"),
                        )
                    )
                }
            },
        )
    }.getOrNull()

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private companion object {
        const val MAX_PLANS = 24
    }
}
