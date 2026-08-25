package com.arthur.roottools.feature.diagnostics.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.feature.diagnostics.presentation.LagForensicsViewModel
import com.arthur.roottools.model.LagPressureLevel

@Composable
fun LagForensicsCard(viewModel: LagForensicsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latest = state.latestSample
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lag_forensics_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.lag_forensics_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LagMetric(
                    label = stringResource(R.string.lag_forensics_memory_psi),
                    value = latest?.let { "%.1f".format(it.memorySome10) } ?: stringResource(R.string.lag_forensics_unknown),
                    note = latest?.level?.let { levelLabel(it) } ?: stringResource(R.string.lag_forensics_waiting),
                    modifier = Modifier.weight(1f),
                )
                LagMetric(
                    label = stringResource(R.string.lag_forensics_io_psi),
                    value = latest?.let { "%.1f".format(it.ioSome10) } ?: stringResource(R.string.lag_forensics_unknown),
                    note = stringResource(R.string.lag_forensics_some_avg10),
                    modifier = Modifier.weight(1f),
                )
                LagMetric(
                    label = stringResource(R.string.lag_forensics_incidents),
                    value = state.incidents.size.toString(),
                    note = if (state.captureInProgress) {
                        stringResource(R.string.lag_forensics_capturing)
                    } else {
                        stringResource(R.string.lag_forensics_stored)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            state.incidents.firstOrNull()?.let { incident ->
                HorizontalDivider()
                Text(
                    stringResource(R.string.lag_forensics_recent, levelLabel(incident.level)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(
                        R.string.lag_forensics_recent_metrics,
                        incident.memorySome10,
                        incident.memoryFull10,
                        incident.ioSome10,
                        incident.ioFull10,
                        incident.cpuSome10,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val evidence = viewModel.latestEvidence()
                        if (evidence.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("lag-forensics", evidence))
                        }
                    },
                ) {
                    Text(stringResource(R.string.lag_forensics_copy_latest))
                }
            }
            Text(
                stringResource(R.string.lag_forensics_cadence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LagMetric(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun levelLabel(level: LagPressureLevel): String = when (level) {
    LagPressureLevel.NORMAL -> stringResource(R.string.lag_forensics_level_normal)
    LagPressureLevel.ELEVATED -> stringResource(R.string.lag_forensics_level_elevated)
    LagPressureLevel.SEVERE -> stringResource(R.string.lag_forensics_level_severe)
}
