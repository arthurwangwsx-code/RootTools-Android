package com.arthur.nfclab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.nfc.TagSnapshot
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LastScanCard(snapshot: TagSnapshot, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.padding(10.dp).size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(snapshot.idHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Text(
                    snapshot.technologies.joinToString(" · ") { it.substringAfterLast('.') },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("查看", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun HistoryItem(snapshot: TagSnapshot) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Nfc, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(snapshot.idHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                Text(
                    snapshot.technologies.joinToString(" · ") { it.substringAfterLast('.') },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.timestampMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SnapshotBody(snapshot: TagSnapshot) {
    DetailRow("UID / Tag ID", snapshot.idHex, mono = true)
    DetailRow("Technologies", snapshot.technologies.joinToString(", ") { it.substringAfterLast('.') })
    snapshot.warning?.let {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    snapshot.details.forEach { (key, value) -> DetailRow(key, value, mono = true) }
    if (snapshot.ndefRecords.isNotEmpty()) {
        Text("NDEF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        snapshot.ndefRecords.forEach { record ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Text(
                        record,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
