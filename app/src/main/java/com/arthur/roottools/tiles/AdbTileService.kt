package com.arthur.roottools.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arthur.roottools.data.DeviceRepository
import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdbTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { DeviceRepository(RootShell(), RootActionAuditStore(this), "QuickTile") }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable {
            scope.launch {
                val current = repository.readSnapshot()
                if (!current.adbEnabled) repository.setAdbTcpEnabled(true)
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
        val snapshot = repository.readSnapshot()
        qsTile?.apply {
            label = if (snapshot.adbEnabled) "ADB · 5555" else "Root ADB"
            subtitle = when {
                !snapshot.rootAvailable -> "需要 Root"
                snapshot.adbEnabled && snapshot.tailscaleIpv4 != null -> snapshot.tailscaleIpv4
                snapshot.adbEnabled -> "已开启"
                else -> "点击开启"
            }
            state = if (snapshot.adbEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

