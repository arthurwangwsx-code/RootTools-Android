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

class WirelessAdbTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { applicationContext.rootToolsContainer.adbRepository }
    private val controller by lazy { applicationContext.rootToolsContainer.createAdbController("WirelessTile") }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable {
            scope.launch {
                val current = repository.read()
                if (current.nativeWirelessSupported) {
                    controller.setNativeWirelessEnabled(!current.nativeWirelessEnabled)
                }
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
            label = getString(R.string.wireless_adb_tile_label)
            subtitle = when {
                !snapshot.rootAvailable -> getString(R.string.common_requires_root)
                !snapshot.nativeWirelessSupported -> getString(R.string.common_not_supported)
                snapshot.nativeWirelessEnabled && snapshot.nativeTlsPort != null -> getString(R.string.wireless_adb_tile_tls_port, snapshot.nativeTlsPort)
                snapshot.nativeWirelessEnabled -> getString(R.string.common_enabled)
                else -> getString(R.string.common_tap_to_enable)
            }
            state = if (snapshot.nativeWirelessEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}

