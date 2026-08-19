package com.arthur.roottools.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.policy.PolicyStore
import com.arthur.roottools.service.CpuPolicyService

class PerformanceTileService : TileService() {
    private lateinit var store: PolicyStore

    override fun onCreate() {
        super.onCreate()
        store = PolicyStore(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val action = {
            val next = when (store.mode) {
                PerformanceMode.AUTO -> PerformanceMode.COOL
                PerformanceMode.COOL -> PerformanceMode.PERFORMANCE
                PerformanceMode.PERFORMANCE -> PerformanceMode.AUTO
            }
            CpuPolicyService.setMode(this, next, source = "QuickTile")
            updateTile(next)
        }
        if (isLocked) unlockAndRun(action) else action()
    }

    private fun updateTile(overrideMode: PerformanceMode? = null) {
        val mode = overrideMode ?: store.mode
        qsTile?.apply {
            label = "CPU · ${mode.displayName}"
            subtitle = when (mode) {
                PerformanceMode.AUTO -> "自动温控"
                PerformanceMode.COOL -> "低温优先"
                PerformanceMode.PERFORMANCE -> "15 分钟性能"
            }
            state = when (mode) {
                PerformanceMode.AUTO -> Tile.STATE_ACTIVE
                PerformanceMode.COOL -> Tile.STATE_INACTIVE
                PerformanceMode.PERFORMANCE -> Tile.STATE_ACTIVE
            }
            updateTile()
        }
    }
}

