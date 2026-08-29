package com.arthur.nettools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.nettools.MainViewModel
import com.arthur.nettools.capture.CaptureRepository
import com.arthur.nettools.capture.CaptureSession
import com.arthur.nettools.capture.CaptureState
import com.arthur.nettools.capture.PacketSummary
import com.arthur.nettools.intercept.AddonStatus
import com.arthur.nettools.intercept.DecryptedEvent
import com.arthur.nettools.intercept.InterceptionState
import com.arthur.nettools.intercept.InterceptionSession
import com.arthur.nettools.security.CaStatus
import java.text.DateFormat
import java.util.Date

private enum class CaptureDetailMode { SUMMARY, PACKETS, FLOWS }

@Composable
fun SessionDetailScreen(session: CaptureSession?, onPacket: (Int) -> Unit) {
    if (session == null) {
        Column(Modifier.padding(24.dp)) { Text("Capture session not found") }
        return
    }
    val a = session.analysis
    var mode by remember { mutableStateOf(CaptureDetailMode.SUMMARY) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(session.appLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(session.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormat.getDateTimeInstance().format(Date(session.startedAt)))
                    if (a != null) Text("${a.packetCount} packets · ${CaptureRepository.formatBytes(a.byteCount)}", fontWeight = FontWeight.Medium)
                    Text(session.pcapPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (a != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == CaptureDetailMode.SUMMARY, onClick = { mode = CaptureDetailMode.SUMMARY }, label = { Text("Summary") })
                    FilterChip(selected = mode == CaptureDetailMode.PACKETS, onClick = { mode = CaptureDetailMode.PACKETS }, label = { Text("Packets (${a.packets.size})") })
                    FilterChip(selected = mode == CaptureDetailMode.FLOWS, onClick = { mode = CaptureDetailMode.FLOWS }, label = { Text("Flows") })
                }
            }
            when (mode) {
                CaptureDetailMode.SUMMARY -> {
                    item { Text("Protocols", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                a.protocols.forEach { Text("${it.protocol} · ${it.packets} packets") }
                            }
                        }
                    }
                    item {
                        Text("Packet inspection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Open Packets to inspect individual frames with protocol-aware fields, text preview and hex bytes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CaptureDetailMode.PACKETS -> {
                    if (a.packets.isEmpty()) item { Text("Preparing packet index from the PCAP…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(a.packets, key = { it.id }) { packet ->
                        PacketRow(packet, onClick = { onPacket(packet.id) })
                    }
                }
                CaptureDetailMode.FLOWS -> {
                    items(a.flows.take(300)) { flow ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${flow.protocol} · ${flow.host ?: flow.destination}", fontWeight = FontWeight.Medium)
                                Text(flow.hint ?: "${flow.source} → ${flow.destination}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${flow.packets} packets", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PacketRow(packet: PacketSummary, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${packet.id}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.padding(4.dp))
                Text(packet.protocol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text("${packet.originalLength} B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(packet.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${packet.source}  →  ${packet.destination}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            packet.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun PacketDetailScreen(packet: PacketSummary?) {
    if (packet == null) {
        Column(Modifier.padding(24.dp)) { Text("Packet not found. Reopen the capture session to rebuild its packet index.") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(packet.protocol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${packet.capturedLength}/${packet.originalLength} B", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(packet.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${packet.source}  →  ${packet.destination}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Timestamp ${packet.timestampMicros} µs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text(protocolSectionTitle(packet.protocol), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    packet.fields.forEach { field ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(field.label, modifier = Modifier.weight(.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(field.value, modifier = Modifier.weight(.58f), style = MaterialTheme.typography.bodySmall, fontFamily = if (field.value.length > 24) FontFamily.Monospace else FontFamily.Default)
                        }
                    }
                }
            }
        }
        packet.payloadText?.let { text ->
            item { Text("Text payload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(14.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
        }
        packet.payloadHex?.let { hex ->
            item { Text("Hex preview · first 256 bytes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Text(hex, Modifier.padding(14.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

private fun protocolSectionTitle(protocol: String): String = when {
    protocol == "DNS" -> "DNS message"
    protocol == "HTTP" -> "HTTP message"
    protocol == "TLS" -> "TLS record"
    protocol.startsWith("QUIC") -> "QUIC packet"
    protocol == "TCP" -> "TCP segment"
    protocol == "UDP" -> "UDP datagram"
    protocol == "ICMP" -> "ICMP message"
    else -> "Packet fields"
}

@Composable
fun DecryptedEventDetailScreen(event: DecryptedEvent?) {
    if (event == null) {
        Column(Modifier.padding(24.dp)) { Text("Decrypted event is no longer in the live window. The raw payload remains in the session directory.") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${event.kind.wireName} · TCP/IP ${event.ipVersion} · port ${event.port} · ${event.size} B")
                    event.payloadPath?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Text("Plaintext preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(event.preview.ifBlank { "<empty payload>" }, modifier = Modifier.padding(14.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DecryptSessionDetailScreen(session: InterceptionSession?, events: List<DecryptedEvent>) {
    if (session == null) {
        Column(Modifier.padding(24.dp)) { Text("Decryption session not found") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(session.target.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(session.target.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormat.getDateTimeInstance().format(Date(session.startedAt)))
                    Text("${session.decryptedEvents} decrypted events · ${session.httpRequests} HTTP requests · ${session.httpResponses} responses · ${session.tlsErrors} TLS errors")
                    Text(session.directory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (events.isEmpty()) item { Text("No plaintext events were stored for this session.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events.take(500), key = { "stored-${it.id}" }) { event ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(event.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${event.size} B", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(event.preview, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    event.payloadPath?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun CertificateScreen(ca: CaStatus, addon: AddonStatus, viewModel: MainViewModel) {
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Key, null); Spacer(Modifier.padding(5.dp)); Text("Active interception CA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    Text(ca.subject ?: "No certificate imported")
                    ca.source?.let { Text("Source: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ca.fingerprint?.let { Text("SHA-256\n$it", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
                    ca.notAfter?.let { Text("Expires: ${DateFormat.getDateInstance().format(Date(it))}") }
                    ca.certificateFile?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trust state", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TrustLine("Certificate available", ca.generated)
                    TrustLine("Magisk system module staged", ca.systemModuleInstalled)
                    TrustLine("Visible in active system trust", ca.systemTrusted)
                    if (ca.requiresReboot) Text("A reboot is required before apps will trust the new CA.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Button(onClick = viewModel::importMitmCa, enabled = addon.installed, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.padding(4.dp)); Text(if (ca.source?.contains("mitm", true) == true) "Refresh CA from MITM add-on" else "Import CA from MITM add-on")
            }
        }
        item {
            Button(onClick = viewModel::installCa, enabled = ca.generated && !ca.systemModuleInstalled, modifier = Modifier.fillMaxWidth()) { Text("Stage as system CA with Magisk") }
        }
        if (ca.requiresReboot) item {
            FilledTonalButton(onClick = { confirmReboot = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.padding(4.dp)); Text("Reboot now") }
        }
        if (ca.systemModuleInstalled) item {
            OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.padding(4.dp)); Text("Remove system CA module") }
        }
        item {
            OutlinedButton(onClick = viewModel::generateCa, modifier = Modifier.fillMaxWidth()) { Text("Generate standalone Net Tools CA (advanced)") }
            Text("Standalone CA generation is for future/custom proxy backends. The built-in mitmproxy workflow must use the CA imported from the MITM add-on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (confirmReboot) AlertDialog(onDismissRequest = { confirmReboot = false }, title = { Text("Reboot device?") }, text = { Text("Rebooting activates the Magisk CA overlay. ADB will disconnect briefly.") }, confirmButton = { TextButton(onClick = { confirmReboot = false; viewModel.rebootDevice() }) { Text("Reboot") } }, dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancel") } })
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Remove system CA?") }, text = { Text("The CA will be removed from the Magisk overlay. Reboot is required to fully apply the change.") }, confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.removeCa() }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } })
}

@Composable
private fun TrustLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null); Spacer(Modifier.padding(4.dp)); Text(label) }
}

@Composable
fun DiagnosticsScreen(capture: CaptureState, intercept: InterceptionState, addon: AddonStatus, ca: CaStatus, viewModel: MainViewModel) {
    var confirmUninstall by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Root: ${capture.rootAvailable}")
                    Text("tcpdump: ${capture.tcpdumpPath ?: "not found"}")
                    Text("MITM add-on: ${addon.versionName ?: "not installed"}")
                    Text("Interception: ${intercept.phase}")
                    Text("Proxy port: ${intercept.proxyPort}")
                    Text("System CA: ${ca.systemTrusted}")
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    capture.sessions.firstOrNull()?.let { Text("Captures: ${it.pcapPath.substringBeforeLast('/')}", style = MaterialTheme.typography.bodySmall) }
                    intercept.session?.let { Text("Intercepts: ${it.directory}", style = MaterialTheme.typography.bodySmall) }
                    ca.certificateFile?.let { Text("CA: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh runtime state") } }
        item { OutlinedButton(onClick = viewModel::cleanupInterceptionRules, modifier = Modifier.fillMaxWidth()) { Text("Clean stale iptables rules") } }
        if (addon.installed) item { OutlinedButton(onClick = { confirmUninstall = true }, modifier = Modifier.fillMaxWidth()) { Text("Uninstall MITM add-on") } }
    }
    if (confirmUninstall) AlertDialog(onDismissRequest = { confirmUninstall = false }, title = { Text("Uninstall MITM add-on?") }, text = { Text("TLS decryption will stop working until the runtime is installed again. Raw capture is unaffected.") }, confirmButton = { TextButton(onClick = { confirmUninstall = false; viewModel.uninstallMitmAddon() }) { Text("Uninstall") } }, dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Cancel") } })
}

@Composable
fun AboutScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Net Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Root-first Android network inspection console", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("• PCAPdroid pcapd for per-UID root packet capture")
                    Text("• mitmproxy through the PCAPdroid MITM add-on for TLS plaintext")
                    Text("• Magisk system CA overlay for reversible trust injection")
                    Text("• Optional QUIC/HTTP3 fallback to TCP for decryptable sessions")
                    Text("• Local-only session storage; no remote capture server is required")
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open-source attribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("PCAPdroid / pcapd — Emanuele Faranda. pcapd binaries are redistributed under the integration terms documented by the project.")
                    Text("PCAPdroid MITM add-on — GPL-3.0, distributed as an independent package and managed separately by Net Tools.")
                    Text("mitmproxy — open-source interactive HTTPS proxy used by the independent add-on runtime.")
                }
            }
        }
        item {
            Text("Scope", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("System trust alone cannot defeat every certificate-pinning or application-owned trust implementation. Net Tools reports TLS errors explicitly and keeps raw capture available when plaintext interception is not possible.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
