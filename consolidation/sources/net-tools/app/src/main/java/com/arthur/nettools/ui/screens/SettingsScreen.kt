package com.arthur.nettools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nettools.intercept.AddonStatus
import com.arthur.nettools.intercept.InterceptionState
import com.arthur.nettools.security.CaStatus

@Composable
fun SettingsScreen(
    ca: CaStatus,
    addon: AddonStatus,
    intercept: InterceptionState,
    onCertificates: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Security & runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            SettingsRow(Icons.Default.Key, "Certificate Manager", when {
                ca.systemTrusted -> "System trusted · ${ca.source ?: "CA"}"
                ca.systemModuleInstalled -> "Staged · reboot required"
                ca.generated -> "Certificate available · not system trusted"
                else -> "Not configured"
            }, onCertificates)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Memory, null); Spacer(Modifier.padding(5.dp)); Text("MITM runtime", fontWeight = FontWeight.SemiBold) }
                    Text(if (addon.installed) "PCAPdroid MITM add-on ${addon.versionName}" else "MITM add-on not installed")
                    addon.latestVersion?.let { Text("Latest stable: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Proxy port ${intercept.proxyPort} · transparent root mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Maintenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        item { SettingsRow(Icons.Default.BugReport, "Diagnostics", "Root, capture backend, proxy chains and storage paths", onDiagnostics) }
        item { SettingsRow(Icons.Default.Info, "About & licenses", "Architecture, open-source attribution and safety model", onAbout) }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null); Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
