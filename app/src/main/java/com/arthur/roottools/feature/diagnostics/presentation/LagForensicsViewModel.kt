package com.arthur.roottools.feature.diagnostics.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.arthur.roottools.app.RootToolsApp
import com.arthur.roottools.model.LagForensicsState
import com.arthur.roottools.service.CpuPolicyService
import kotlinx.coroutines.flow.StateFlow

class LagForensicsViewModel(application: Application) : AndroidViewModel(application) {
    private val monitor = (application as RootToolsApp).container.lagForensicsMonitor

    val state: StateFlow<LagForensicsState> = monitor.state

    fun setEnabled(enabled: Boolean) {
        monitor.setEnabled(enabled)
        if (enabled) {
            CpuPolicyService.ensureRunning(getApplication(), source = "lag-forensics")
        }
    }

    fun latestEvidence(): String = monitor.readLatestEvidence()
}
