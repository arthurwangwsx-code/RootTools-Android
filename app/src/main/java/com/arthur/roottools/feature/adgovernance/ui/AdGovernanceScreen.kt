package com.arthur.roottools.feature.adgovernance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow
import com.arthur.roottools.core.ui.component.RootToolsMetricTile
import com.arthur.roottools.core.ui.component.RootToolsRiskBanner
import com.arthur.roottools.core.ui.component.RootToolsSectionCard
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRiskLevel
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.adgovernance.model.AdActionEvent
import com.arthur.roottools.feature.adgovernance.model.AdGovernanceSnapshot

@Composable
fun AdGovernanceScreen(
    state: AdGovernanceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenGkd: () -> Unit,
    onOpenAdAway: () -> Unit,
) {
    val snapshot = state.snapshot
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            item {
                RootToolsDetailHeader(
                    title = stringResource(R.string.ad_governance_title),
                    subtitle = stringResource(R.string.ad_governance_subtitle),
                    onBack = onBack,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }

            item { OverviewCard(snapshot) }
            item { GkdCard(snapshot, onOpenGkd) }
            item { ValidationCard(snapshot) }
            item { NetworkLayerCard(snapshot, onOpenAdAway) }
            item { HyperOsCard(snapshot) }

            if (!snapshot.rootAvailable && !state.loading) {
                item {
                    RootToolsRiskBanner(
                        title = stringResource(R.string.ad_governance_root_required_title),
                        detail = stringResource(R.string.ad_governance_root_required_body),
                        level = RootToolsRiskLevel.Caution,
                    )
                }
            }
            if (snapshot.tailscale.active) {
                item {
                    RootToolsRiskBanner(
                        title = stringResource(R.string.ad_governance_tailnet_safety_title),
                        detail = stringResource(R.string.ad_governance_tailnet_safety_body),
                        level = RootToolsRiskLevel.Caution,
                    )
                }
            }
            state.error?.let { detail ->
                item { RootToolsErrorCard(stringResource(R.string.ad_governance_probe_failed, detail)) }
            }
        }
    }
}

@Composable
private fun OverviewCard(snapshot: AdGovernanceSnapshot) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.ad_governance_overview_title),
            subtitle = stringResource(R.string.ad_governance_overview_subtitle),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            RootToolsMetricTile(
                label = stringResource(R.string.ad_governance_metric_ui_engine),
                value = if (snapshot.gkd.engineReady) stringResource(R.string.ad_governance_ready)
                else stringResource(R.string.ad_governance_off),
                note = stringResource(R.string.ad_governance_gkd_name),
                modifier = Modifier.weight(1f),
            )
            RootToolsMetricTile(
                label = stringResource(R.string.ad_governance_metric_actions),
                value = snapshot.recentActions.size.toString(),
                note = stringResource(R.string.ad_governance_recent_log_window),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            RootToolsMetricTile(
                label = stringResource(R.string.ad_governance_metric_hosts),
                value = if (snapshot.hosts.active) stringResource(R.string.ad_governance_on)
                else stringResource(R.string.ad_governance_off),
                note = stringResource(R.string.ad_governance_hosts_lines, snapshot.hosts.lineCount),
                modifier = Modifier.weight(1f),
            )
            RootToolsMetricTile(
                label = stringResource(R.string.ad_governance_metric_tailnet),
                value = if (snapshot.tailscale.active) stringResource(R.string.ad_governance_online)
                else stringResource(R.string.ad_governance_offline),
                note = snapshot.tailscale.ipv4 ?: stringResource(R.string.common_dash),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GkdCard(snapshot: AdGovernanceSnapshot, onOpenGkd: () -> Unit) {
    val gkd = snapshot.gkd
    RootToolsSectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ad_governance_gkd_title), fontWeight = FontWeight.SemiBold)
            RootToolsStatusChip(
                label = if (gkd.engineReady) stringResource(R.string.ad_governance_ready)
                else stringResource(R.string.ad_governance_needs_attention),
                tone = if (gkd.engineReady) RootToolsStatusTone.Success else RootToolsStatusTone.Warning,
            )
        }
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_installed),
            booleanLabel(gkd.installed),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_running),
            booleanLabel(gkd.running),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_automator_mode),
            automatorModeLabel(gkd.automatorMode),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_privileged_worker),
            booleanLabel(gkd.userServiceRunning && gkd.shizukuServerRunning),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_subscriptions),
            gkd.subscriptionCount.toString(),
        )
        OutlinedButton(onClick = onOpenGkd, enabled = gkd.installed) {
            Text(stringResource(R.string.ad_governance_open_gkd))
        }
    }
}

@Composable
private fun ValidationCard(snapshot: AdGovernanceSnapshot) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.ad_governance_validation_title),
            subtitle = stringResource(R.string.ad_governance_validation_subtitle),
        )
        AppActionRow(
            label = stringResource(R.string.ad_governance_zhihu),
            count = snapshot.actionCountFor(ZHIHU_PACKAGE),
            latest = snapshot.latestActionFor(ZHIHU_PACKAGE),
        )
        AppActionRow(
            label = stringResource(R.string.ad_governance_jd),
            count = snapshot.actionCountFor(JD_PACKAGE),
            latest = snapshot.latestActionFor(JD_PACKAGE),
        )
    }
}

@Composable
private fun AppActionRow(label: String, count: Int, latest: AdActionEvent?) {
    val latestText = latest?.let {
        stringResource(R.string.ad_governance_latest_action, it.time, it.groupName)
    } ?: stringResource(R.string.ad_governance_no_action)
    RootToolsKeyValueRow(
        label = label,
        value = stringResource(R.string.ad_governance_action_count, count),
    )
    Text(
        text = latestText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NetworkLayerCard(snapshot: AdGovernanceSnapshot, onOpenAdAway: () -> Unit) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.ad_governance_network_title),
            subtitle = stringResource(R.string.ad_governance_network_subtitle),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_hosts_filter),
            if (snapshot.hosts.active) stringResource(R.string.ad_governance_active)
            else stringResource(R.string.ad_governance_not_active),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_systemless_hosts),
            booleanLabel(snapshot.hosts.systemless),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.ad_governance_adaway_name),
            when {
                snapshot.adAway.running -> stringResource(R.string.ad_governance_running)
                snapshot.adAway.installed -> stringResource(R.string.ad_governance_installed)
                else -> stringResource(R.string.ad_governance_not_installed)
            },
        )
        OutlinedButton(onClick = onOpenAdAway, enabled = snapshot.adAway.installed) {
            Text(stringResource(R.string.ad_governance_open_adaway))
        }
    }
}

@Composable
private fun HyperOsCard(snapshot: AdGovernanceSnapshot) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.ad_governance_hyperos_title),
            subtitle = stringResource(R.string.ad_governance_hyperos_subtitle),
        )
        PackageStateRow(
            packageName = stringResource(R.string.ad_governance_hyper_ads_package),
            installed = snapshot.hyperOs.systemAdInstalled,
            enabled = snapshot.hyperOs.systemAdEnabled,
        )
        PackageStateRow(
            packageName = stringResource(R.string.ad_governance_miui_analytics_package),
            installed = snapshot.hyperOs.analyticsInstalled,
            enabled = snapshot.hyperOs.analyticsEnabled,
        )
        Text(
            text = stringResource(R.string.ad_governance_hyperos_read_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackageStateRow(packageName: String, installed: Boolean, enabled: Boolean) {
    RootToolsKeyValueRow(
        label = packageName,
        value = when {
            !installed -> stringResource(R.string.ad_governance_not_installed)
            enabled -> stringResource(R.string.ad_governance_enabled)
            else -> stringResource(R.string.ad_governance_disabled)
        },
    )
}

@Composable
private fun booleanLabel(value: Boolean): String = if (value) {
    stringResource(R.string.ad_governance_yes)
} else {
    stringResource(R.string.ad_governance_no)
}

@Composable
private fun automatorModeLabel(mode: Int): String = when (mode) {
    1 -> stringResource(R.string.ad_governance_mode_accessibility)
    2 -> stringResource(R.string.ad_governance_mode_automation)
    else -> stringResource(R.string.ad_governance_mode_unknown)
}

private const val ZHIHU_PACKAGE = "com.zhihu.android"
private const val JD_PACKAGE = "com.jingdong.app.mall"
