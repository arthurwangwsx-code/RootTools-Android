package com.arthur.roottools.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arthur.roottools.R
import com.arthur.roottools.app.rootToolsContainer
import com.arthur.roottools.data.AdbRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.policy.AdbController
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdbTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { applicationContext.rootToolsContainer.adbRepository }
    private val controller by lazy { applicationContext.rootToolsContainer.createAdbController("QuickTile") }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable {
            scope.launch {
                val current = repository.read()
                if (!current.rootTcpEnabled) controller.setRootTcpEnabled(true)
                refreshTile()
            }
        }
        if (isLocked) unlockAndRun(action) else action.run()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshTile() {
        val snapshot = repository.read()
        qsTile?.apply {
            label = if (snapshot.rootTcpEnabled) {
                getString(R.string.adb_tile_label_port, snapshot.rootTcpPort ?: 5555)
            } else {
                getString(R.string.adb_tile_label_root)
            }
            subtitle = when {
                !snapshot.rootAvailable -> getString(R.string.common_requires_root)
                snapshot.rootTcpEnabled && snapshot.tailscaleIpv4 != null -> snapshot.tailscaleIpv4
                snapshot.rootTcpEnabled -> getString(R.string.common_enabled)
                else -> getString(R.string.common_tap_to_enable)
            }
            state = if (snapshot.rootTcpEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

