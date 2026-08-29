package com.arthur.roottools.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.feature.network.inspection.ui.NetworkCaptureRoute
import com.arthur.roottools.ui.DashboardUiState
import com.arthur.roottools.ui.NetworkDiagnosticsScreen

private enum class NetworkInspectionTab {
    DIAGNOSTICS,
    CAPTURE,
}

@Composable
internal fun NetworkInspectionRoute(
    dashboardState: DashboardUiState,
    onBack: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onPing: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(NetworkInspectionTab.DIAGNOSTICS) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == NetworkInspectionTab.DIAGNOSTICS,
                onClick = { tab = NetworkInspectionTab.DIAGNOSTICS },
                label = { Text(stringResource(R.string.network_inspection_tab_diagnostics)) },
            )
            FilterChip(
                selected = tab == NetworkInspectionTab.CAPTURE,
                onClick = { tab = NetworkInspectionTab.CAPTURE },
                label = { Text(stringResource(R.string.network_inspection_tab_capture)) },
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                NetworkInspectionTab.DIAGNOSTICS -> NetworkDiagnosticsScreen(
                    state = dashboardState,
                    onBack = onBack,
                    onRefresh = onRefreshDiagnostics,
                    onPing = onPing,
                )
                NetworkInspectionTab.CAPTURE -> NetworkCaptureRoute(onBack = onBack)
            }
        }
    }
}
