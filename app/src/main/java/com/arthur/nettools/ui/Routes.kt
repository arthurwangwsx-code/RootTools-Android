package com.arthur.nettools.ui

data object DashboardRoute
data object TrafficRoute
data object DecryptRoute
data object SettingsRoute
data object CertificateRoute
data object DiagnosticsRoute
data object AboutRoute
data class CaptureSessionRoute(val id: String)
data class DecryptedEventRoute(val id: Long)
data class DecryptSessionRoute(val id: String)

enum class TopDestination(val label: String) {
    DASHBOARD("Overview"), TRAFFIC("Traffic"), DECRYPT("Decrypt"), SETTINGS("Settings")
}
