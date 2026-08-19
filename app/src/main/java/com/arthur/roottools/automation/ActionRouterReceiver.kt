package com.arthur.roottools.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arthur.roottools.data.DeviceHealthCollector
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.DiagnosticReportStore
import com.arthur.roottools.data.DiagnosticsRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.policy.PackagePolicyController
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.privilege.ShizukuBridge
import com.arthur.roottools.privilege.ShizukuUserServiceClient
import com.arthur.roottools.root.RootShell
import com.arthur.roottools.service.CpuPolicyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActionRouterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val component = intent.component ?: return
        if (component.packageName != context.packageName || component.className != javaClass.name) return
        if (!ActionTokenStore(context).matches(intent.getStringExtra(EXTRA_TOKEN))) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { execute(context.applicationContext, intent) }
            pending.finish()
        }
    }

    private suspend fun execute(context: Context, intent: Intent) {
        val shell = RootShell()
        val audit = RootActionAuditStore(context)
        when (intent.getStringExtra(EXTRA_COMMAND)?.uppercase()) {
            "SET_MODE" -> {
                val mode = runCatching { PerformanceMode.valueOf(intent.getStringExtra(EXTRA_MODE)?.uppercase().orEmpty()) }.getOrNull() ?: return
                CpuPolicyService.setMode(context, mode, source = "Automation")
            }
            "SET_ADB" -> {
                // Remote automation may ensure ADB is ON, but cannot turn off the current
                // management lifeline. Disabling is intentionally restricted to confirmed UI.
                if (intent.getBooleanExtra(EXTRA_ENABLED, true)) {
                    DeviceRepository(shell, audit, "Automation").setAdbTcpEnabled(true)
                }
            }
            "FREEZE" -> intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
                withPackageController(context, shell, audit) { freeze(packageName) }
            }
            "UNFREEZE" -> intent.getStringExtra(EXTRA_PACKAGE)?.let { packageName ->
                withPackageController(context, shell, audit) { enable(packageName) }
            }
            "RUN_DIAGNOSTIC" -> {
                val health = DeviceHealthCollector(shell).collect(includeProcesses = true)
                val repository = DiagnosticsRepository(shell)
                val diagnostic = repository.collect()
                DiagnosticReportStore(context).write(repository.buildSnapshotText(health, diagnostic))
            }
        }
    }

    private suspend fun withPackageController(
        context: Context,
        shell: RootShell,
        audit: RootActionAuditStore,
        block: suspend PackagePolicyController.() -> Unit,
    ) {
        val bridge = ShizukuBridge(context)
        val client = ShizukuUserServiceClient(context)
        try {
            val router = PrivilegeRouter(bridge, client, shell)
            block(PackagePolicyController(router, audit, "Automation"))
        } finally {
            client.close()
            bridge.close()
        }
    }

    companion object {
        const val ACTION = "com.arthur.roottools.ACTION"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_PACKAGE = "package"
    }
}
