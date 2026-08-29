package com.arthur.nfclab.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRequirementState

@Composable
internal fun StatusPill(label: String, ok: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun CapabilityCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    available: Boolean,
    subtitle: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (available) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                if (available) "$subtitle · 可用" else "$subtitle · 未确认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(126.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
internal fun InsightCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun LoadingCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(message)
        }
    }
}

@Composable
internal fun EmptyStateCard(
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

internal fun routeLabel(route: Int): String = when (route) {
    0 -> "Host (0x00)"
    1 -> "eSE (0x01)"
    2 -> "UICC1 (0x02)"
    3 -> "UICC2 (0x03)"
    else -> "0x${route.toString(16).uppercase()}"
}

internal fun routeLabel(route: NfcCardRoute): String = when (route) {
    NfcCardRoute.HOST -> "Host"
    NfcCardRoute.ESE -> "eSE"
    NfcCardRoute.UICC -> "UICC"
    NfcCardRoute.UNKNOWN -> "未知路由"
}

internal fun techMaskLabel(mask: Int): String {
    val techs = buildList {
        if (mask and 0x01 != 0) add("A")
        if (mask and 0x02 != 0) add("B")
        if (mask and 0x04 != 0) add("F")
        if (mask and 0x08 != 0) add("V")
        if (mask and 0x40 != 0) add("Active")
    }
    return if (techs.isEmpty()) "0x${mask.toString(16).uppercase()}"
    else "${techs.joinToString(" / ")} (0x${mask.toString(16).uppercase()})"
}

internal fun provisioningReadinessLabel(value: ProvisioningReadiness): String = when (value) {
    ProvisioningReadiness.READY -> "已就绪"
    ProvisioningReadiness.MANAGED_EXTERNALLY -> "官方入口可用"
    ProvisioningReadiness.PARTNER_REQUIRED -> "需要合作方授权"
    ProvisioningReadiness.PRIVILEGED_ONLY -> "仅系统/特权调用"
    ProvisioningReadiness.BLOCKED -> "当前不可用"
    ProvisioningReadiness.UNKNOWN -> "待验证"
}

internal fun provisioningRequirementStateLabel(value: ProvisioningRequirementState): String = when (value) {
    ProvisioningRequirementState.SATISFIED -> "已满足"
    ProvisioningRequirementState.ACTION_AVAILABLE -> "可执行"
    ProvisioningRequirementState.PARTNER_REQUIRED -> "需要合作方"
    ProvisioningRequirementState.PRIVILEGED_ONLY -> "需要系统权限"
    ProvisioningRequirementState.MISSING -> "尚缺"
    ProvisioningRequirementState.UNKNOWN -> "待确认"
}

@Composable
internal fun NfcToolsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
