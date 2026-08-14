package com.arthur.nettools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.nettools.MainViewModel
import com.arthur.nettools.capture.AppTarget
import com.arthur.nettools.capture.CaptureRepository
import com.arthur.nettools.capture.CaptureState
import java.text.DateFormat
import java.util.Date

private enum class TrafficMode { CAPTURE, SESSIONS }

@Composable
fun TrafficScreen(capture: CaptureState, apps: List<AppTarget>, viewModel: MainViewModel, onSession: (String) -> Unit) {
    var mode by remember { mutableStateOf(TrafficMode.CAPTURE) }
    var picker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppTarget?>(null) }

    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == TrafficMode.CAPTURE, onClick = { mode = TrafficMode.CAPTURE }, label = { Text("Capture") })
            FilterChip(selected = mode == TrafficMode.SESSIONS, onClick = { mode = TrafficMode.SESSIONS }, label = { Text("Sessions (${capture.sessions.size})") })
        }
        if (mode == TrafficMode.CAPTURE) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (capture.rootAvailable) "Root capture ready" else "Root unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(capture.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Per-app mode uses PCAPdroid pcapd UID filtering; whole-device mode uses tcpdump.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Text("Capture target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = capture.active == null) { picker = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, null); Spacer(Modifier.padding(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected?.label ?: "Whole device", fontWeight = FontWeight.Medium)
                                Text(selected?.packageName ?: "All apps and interfaces", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.UnfoldMore, null)
                        }
                    }
                }
                item {
                    Button(
                        onClick = { if (capture.active == null) viewModel.start(selected) else viewModel.stop() },
                        enabled = capture.rootAvailable,
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(if (capture.active == null) Icons.Default.FiberManualRecord else Icons.Default.Stop, null)
                        Spacer(Modifier.padding(4.dp)); Text(if (capture.active == null) "Start raw capture" else "Stop & analyze")
                    }
                }
                if (capture.active != null) item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Capturing ${capture.active.appLabel}", fontWeight = FontWeight.SemiBold)
                            Text(capture.active.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    Text("Protocol analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("PCAP is parsed locally for DNS, TCP, UDP, TLS/SNI, QUIC, HTTP and ICMP. TLS plaintext is intentionally handled in the Decrypt workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (capture.sessions.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No capture sessions yet", fontWeight = FontWeight.SemiBold)
                    Text("Start a raw capture to create a PCAP session", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(capture.sessions, key = { it.id }) { session ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { onSession(session.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(session.appLabel, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAt)), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(session.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            session.analysis?.let { a ->
                                Text("${a.packetCount} packets · ${CaptureRepository.formatBytes(a.byteCount)} · ${a.protocols.take(4).joinToString { it.protocol }}")
                            }
                        }
                    }
                }
            }
        }
    }
    if (picker) AppPickerSheet(apps, selected, true, { selected = it; picker = false }, { picker = false })
}
