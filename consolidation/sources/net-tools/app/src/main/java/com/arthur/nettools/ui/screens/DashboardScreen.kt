package com.arthur.nettools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nettools.capture.CaptureState
import com.arthur.nettools.intercept.AddonStatus
import com.arthur.nettools.intercept.InterceptionPhase
import com.arthur.nettools.intercept.InterceptionState
import com.arthur.nettools.security.CaStatus

@Composable
fun DashboardScreen(
    capture: CaptureState,
    intercept: InterceptionState,
    ca: CaStatus,
    addon: AddonStatus,
    onTraffic: () -> Unit,
    onDecrypt: () -> Unit,
    onCertificates: () -> Unit,
) {
    val decryptReady = intercept.rootAvailable && addon.installed && ca.systemTrusted
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Network inspection workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Raw packets, per-app root capture and decrypted TLS payloads in one workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (capture.rootAvailable && capture.tcpdumpPath != null) Icons.Default.CheckCircle else Icons.Default.Error, null)
                        Spacer(Modifier.padding(5.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Raw capture", fontWeight = FontWeight.SemiBold)
                            Text(if (capture.rootAvailable) "Root capture backend ready" else "Root unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${capture.sessions.size} saved capture sessions", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onTraffic, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NetworkCheck, null); Spacer(Modifier.padding(4.dp)); Text("Open traffic workspace")
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (decryptReady) Icons.Default.CheckCircle else Icons.Default.Https, null)
                        Spacer(Modifier.padding(5.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TLS decryption", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    intercept.phase == InterceptionPhase.RUNNING -> "Decrypting ${intercept.target?.label ?: "selected app"}"
                                    decryptReady -> "Transparent interception ready"
                                    !addon.installed -> "MITM add-on setup required"
                                    !ca.generated -> "Import the add-on CA"
                                    ca.requiresReboot -> "CA staged; reboot required"
                                    else -> "System trust setup required"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (intercept.session != null) {
                        Text("Latest: ${intercept.session.decryptedEvents} decrypted events · ${intercept.session.httpRequests} HTTP requests")
                    }
                    Button(onClick = onDecrypt, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Https, null); Spacer(Modifier.padding(4.dp)); Text(if (decryptReady) "Open decryption workspace" else "Complete decryption setup")
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, null); Spacer(Modifier.padding(5.dp)); Text("Certificate trust", fontWeight = FontWeight.SemiBold)
                    }
                    Text(ca.subject ?: "No interception CA imported", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            ca.systemTrusted -> "Active in Android system trust store"
                            ca.systemModuleInstalled -> "Magisk module staged; reboot required"
                            ca.generated -> "Certificate available but not system trusted"
                            else -> "Not configured"
                        },
                    )
                    FilledTonalButton(onClick = onCertificates, modifier = Modifier.fillMaxWidth()) { Text("Manage certificates") }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
