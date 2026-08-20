package com.arthur.roottools.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.arthur.roottools.BuildConfig
import com.arthur.roottools.data.AdbRepository
import com.arthur.roottools.data.AppControlRepository
import com.arthur.roottools.data.ComponentRepository
import com.arthur.roottools.data.PermissionAppOpsRepository
import com.arthur.roottools.privilege.PrivilegeRouter
import com.arthur.roottools.privilege.ShizukuBridge
import com.arthur.roottools.privilege.ShizukuUserServiceClient
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Debug-only, read-only validation endpoint for ADB-driven device regression checks.
 *
 * The manifest protects this receiver with android.permission.DUMP, so normal third-party apps
 * cannot call it. Release does not include the debug source set at all.
 */
class DebugDeviceSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val bridge = ShizukuBridge(context)
            val client = ShizukuUserServiceClient(context)
            try {
                bridge.refresh()
                val rootShell = RootShell()
                val rootAvailable = rootShell.isAvailable(timeoutSeconds = 4)
                val adb = AdbRepository(context, rootShell).read()
                val shizuku = bridge.state.value
                val router = PrivilegeRouter(bridge, client, rootShell)
                val selfTest = if (shizuku.ready) {
                    client.selfTest().fold(
                        onSuccess = { it },
                        onFailure = { "error:${it.javaClass.simpleName}:${it.message.orEmpty()}" },
                    )
                } else {
                    "not-ready"
                }
                val appControl = AppControlRepository(context, rootShell)
                val inventory = appControl.readInventory(allowRootRunningProbe = false)
                val ownDetail = appControl.readDetail(context.packageName)
                val components = ComponentRepository(context).read(context.packageName)
                val permissions = PermissionAppOpsRepository(context, router).read(
                    context.packageName,
                    includeAppOps = shizuku.ready || rootAvailable,
                )

                val payload = JSONObject()
                    .put("package", context.packageName)
                    .put("pid", Process.myPid())
                    .put("rootAvailable", rootAvailable)
                    .put(
                        "shizuku",
                        JSONObject()
                            .put("binderAlive", shizuku.binderAlive)
                            .put("permissionGranted", shizuku.permissionGranted)
                            .put("backend", shizuku.backend.name)
                            .put("uid", shizuku.uid ?: JSONObject.NULL)
                            .put("serverVersion", shizuku.serverVersion ?: JSONObject.NULL)
                            .put("serverPatchVersion", shizuku.serverPatchVersion ?: JSONObject.NULL)
                            .put("suiAvailable", shizuku.suiAvailable)
                            .put("selfTest", selfTest),
                    )
                    .put(
                        "adb",
                        JSONObject()
                            .put("rootTcpEnabled", adb.rootTcpEnabled)
                            .put("rootTcpPort", adb.rootTcpPort ?: JSONObject.NULL)
                            .put("rootTcpListening", adb.rootTcpListening)
                            .put("nativeWirelessSupported", adb.nativeWirelessSupported)
                            .put("nativeWirelessEnabled", adb.nativeWirelessEnabled)
                            .put("nativeTlsPort", adb.nativeTlsPort ?: JSONObject.NULL)
                            .put("usbDebuggingEnabled", adb.usbDebuggingEnabled)
                            .put("usbTransportActive", adb.usbTransportActive)
                            .put("tailscaleIpv4", adb.tailscaleIpv4 ?: JSONObject.NULL)
                            .put("localIpv4", adb.localIpv4 ?: JSONObject.NULL),
                    )
                    .put(
                        "appControl",
                        JSONObject()
                            .put("inventoryCount", inventory.apps.size)
                            .put("runningProbeAvailable", inventory.runningProbeAvailable)
                            .put("ownPackageVisible", inventory.apps.any { it.packageName == context.packageName })
                            .put("ownTargetSdk", ownDetail?.targetSdk ?: JSONObject.NULL)
                            .put("ownSplitCount", ownDetail?.splitSourceDirs?.size ?: JSONObject.NULL)
                            .put("componentCount", components?.components?.size ?: JSONObject.NULL)
                            .put("bootReceiverCount", components?.bootReceiverCount ?: JSONObject.NULL)
                            .put("exportedComponentCount", components?.exportedCount ?: JSONObject.NULL)
                            .put("requestedPermissionCount", permissions?.permissions?.size ?: JSONObject.NULL)
                            .put("supportedAppOpCount", permissions?.appOps?.count { it.supported } ?: JSONObject.NULL)
                            .put(
                                "appOpBackends",
                                permissions?.appOps
                                    ?.filter { it.supported }
                                    ?.map { it.backend.name }
                                    ?.distinct()
                                    ?.sorted()
                                    ?.joinToString(",")
                                    ?: JSONObject.NULL,
                            ),
                    )

                pending.resultCode = RESULT_OK
                pending.resultData = payload.toString()
            } catch (error: Throwable) {
                pending.resultCode = RESULT_ERROR
                pending.resultData = JSONObject()
                    .put("error", error.javaClass.simpleName)
                    .put("detail", error.message.orEmpty())
                    .toString()
            } finally {
                client.close()
                bridge.close()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.arthur.roottools.DEBUG_DEVICE_SNAPSHOT"
        const val RESULT_OK = 0
        const val RESULT_ERROR = 1
    }
}
