package com.arthur.nfclab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.arthur.nfclab.domain.NfcOperatingMode

@Composable
fun NfcLabApp(
    state: NfcToolsUiState,
    actions: NfcToolsActions,
) {
    NfcToolsTheme {
        var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

        fun navigate(tab: AppTab) {
            selectedTab = tab
            when (tab) {
                AppTab.READER -> actions.onModeChange(NfcOperatingMode.READER)
                AppTab.HOME, AppTab.LAB, AppTab.SYSTEM -> actions.onModeChange(NfcOperatingMode.DEFAULT)
            }
        }

        NfcToolsShell(
            selectedTab = selectedTab,
            onNavigate = ::navigate,
        ) { contentPadding ->
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    contentPadding = contentPadding,
                    state = state,
                    onNavigate = ::navigate,
                    onRefreshDeviceProfile = actions.onRefreshDeviceProfile,
                    onOpenWallet = actions.onOpenWallet,
                )

                AppTab.READER -> ReaderScreen(
                    contentPadding = contentPadding,
                    state = state,
                    onModeChange = actions.onModeChange,
                    onClearHistory = actions.onClearHistory,
                )

                AppTab.LAB -> HceLabScreen(
                    contentPadding = contentPadding,
                    state = state,
                    onModeChange = actions.onModeChange,
                    onPayloadChange = actions.onHcePayloadChange,
                    onOpenWallet = actions.onOpenWallet,
                )

                AppTab.SYSTEM -> SystemScreen(
                    contentPadding = contentPadding,
                    state = state,
                    onRefreshDeviceProfile = actions.onRefreshDeviceProfile,
                    onRunRootDiagnostics = actions.onRunRootDiagnostics,
                    onStartAccessDiagnostic = actions.onStartAccessDiagnostic,
                    onFinishAccessDiagnostic = actions.onFinishAccessDiagnostic,
                    onOpenWallet = actions.onOpenWallet,
                    onClearAccessDiagnosticHistory = actions.onClearAccessDiagnosticHistory,
                )
            }
        }
    }
}
