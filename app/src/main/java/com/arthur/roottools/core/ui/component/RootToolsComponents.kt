package com.arthur.roottools.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.token.RootToolsRadius
import com.arthur.roottools.core.ui.token.RootToolsRiskLevel
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone

@Composable
fun RootToolsSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RootToolsDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh))
            }
        }
    }
}

@Composable
fun RootToolsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            content()
        }
    }
}

@Composable
fun RootToolsMetricTile(
    label: String,
    value: String,
    note: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(RootToolsRadius.card),
            )
            .padding(RootToolsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RootToolsKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = RootToolsSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RootToolsTemperatureMetric(
    label: String,
    value: Float?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(RootToolsRadius.card),
    ) {
        Column(
            Modifier.padding(horizontal = RootToolsSpacing.sm, vertical = RootToolsSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value?.let { "%.1f°".format(it) } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun RootToolsStatusDot(
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = RoundedCornerShape(50),
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        content = {},
    )
}

@Composable
fun RootToolsStatusChip(
    label: String,
    tone: RootToolsStatusTone = RootToolsStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val foreground = statusColor(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RootToolsRadius.chip),
        color = foreground.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = RootToolsSpacing.xs, vertical = RootToolsSpacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun RootToolsErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(RootToolsSpacing.md),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun RootToolsRiskBanner(
    title: String,
    detail: String,
    level: RootToolsRiskLevel,
    modifier: Modifier = Modifier,
) {
    val foreground = riskColor(level)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(foreground.copy(alpha = 0.12f), RoundedCornerShape(RootToolsRadius.card))
            .padding(RootToolsSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.xxs)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = foreground)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun statusColor(tone: RootToolsStatusTone): Color = when (tone) {
    RootToolsStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    RootToolsStatusTone.Info -> MaterialTheme.colorScheme.secondary
    RootToolsStatusTone.Success -> MaterialTheme.colorScheme.primary
    RootToolsStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
    RootToolsStatusTone.Danger -> MaterialTheme.colorScheme.error
    RootToolsStatusTone.Privileged -> MaterialTheme.colorScheme.primary
}

@Composable
private fun riskColor(level: RootToolsRiskLevel): Color = when (level) {
    RootToolsRiskLevel.Safe -> MaterialTheme.colorScheme.primary
    RootToolsRiskLevel.Caution -> MaterialTheme.colorScheme.tertiary
    RootToolsRiskLevel.Dangerous -> MaterialTheme.colorScheme.error
    RootToolsRiskLevel.Destructive -> MaterialTheme.colorScheme.error
}
