package com.arthur.roottools.feature.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow
import com.arthur.roottools.core.ui.component.RootToolsSectionCard
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.feature.assistant.model.AssistantCandidate
import com.arthur.roottools.feature.assistant.model.AssistantSwitchResult
import com.arthur.roottools.feature.assistant.model.AssistantSwitchStatus
import com.arthur.roottools.feature.assistant.model.PowerKeyAssistantBinding

@Composable
fun AssistantSettingsScreen(
    state: AssistantSettingsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSwitch: (String) -> Unit,
    onDismissFeedback: () -> Unit,
) {
    var pendingCandidate by remember { mutableStateOf<AssistantCandidate?>(null) }

    pendingCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingCandidate = null },
            title = { Text(stringResource(R.string.assistant_switch_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.assistant_switch_confirm_detail,
                        candidate.label,
                        candidate.packageName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCandidate = null
                        onSwitch(candidate.packageName)
                    },
                ) {
                    Text(stringResource(R.string.assistant_switch_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = RootToolsSpacing.md,
                end = RootToolsSpacing.md,
                top = RootToolsSpacing.md,
                bottom = RootToolsSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            item {
                RootToolsDetailHeader(
                    title = stringResource(R.string.assistant_settings_title),
                    subtitle = stringResource(R.string.assistant_settings_subtitle),
                    onBack = onBack,
                    loading = state.loading,
                    onRefresh = onRefresh,
                )
            }

            state.loadFailure?.let { error ->
                item { RootToolsErrorCard(stringResource(R.string.assistant_load_failed, error)) }
            }
            state.snapshot.readError?.let { error ->
                item { RootToolsErrorCard(stringResource(R.string.assistant_role_read_warning, error)) }
            }
            state.feedback?.let { feedback ->
                item { AssistantFeedbackCard(feedback, onDismissFeedback) }
            }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.assistant_current_title),
                    subtitle = stringResource(R.string.assistant_current_subtitle),
                )
            }
            item { CurrentAssistantCard(state) }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.assistant_power_key_title),
                    subtitle = stringResource(R.string.assistant_power_key_subtitle),
                )
            }
            item { PowerKeyCard(state) }

            item {
                RootToolsSectionHeader(
                    title = stringResource(R.string.assistant_candidates_title),
                    subtitle = stringResource(R.string.assistant_candidates_subtitle),
                )
            }
            if (!state.loading && state.snapshot.candidates.isEmpty()) {
                item {
                    RootToolsErrorCard(stringResource(R.string.assistant_candidates_empty))
                }
            }
            items(state.snapshot.candidates, key = { it.packageName }) { candidate ->
                AssistantCandidateCard(
                    candidate = candidate,
                    currentPackage = state.snapshot.currentPackage,
                    switchingPackage = state.switchingPackage,
                    onSwitch = { pendingCandidate = candidate },
                )
            }
        }
    }
}

@Composable
private fun CurrentAssistantCard(state: AssistantSettingsUiState) {
    val snapshot = state.snapshot
    val candidate = snapshot.currentCandidate
    RootToolsSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    candidate?.label ?: snapshot.currentPackage ?: stringResource(R.string.assistant_none),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                snapshot.currentPackage?.let { packageName ->
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RootToolsStatusChip(
                label = if (snapshot.currentPackage != null) {
                    stringResource(R.string.assistant_status_active)
                } else {
                    stringResource(R.string.assistant_status_none)
                },
                tone = if (snapshot.currentPackage != null) RootToolsStatusTone.Success else RootToolsStatusTone.Warning,
            )
        }
        RootToolsKeyValueRow(
            stringResource(R.string.assistant_backend_label),
            snapshot.readBackend.displayName,
        )
        candidate?.voiceServiceComponents?.firstOrNull()?.let { component ->
            RootToolsKeyValueRow(stringResource(R.string.assistant_service_label), component)
        }
    }
}

@Composable
private fun PowerKeyCard(state: AssistantSettingsUiState) {
    val powerKey = state.snapshot.powerKey
    RootToolsSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.assistant_power_key_binding_label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            RootToolsStatusChip(
                label = when (powerKey.binding) {
                    PowerKeyAssistantBinding.ASSISTANT -> stringResource(R.string.assistant_power_key_bound)
                    PowerKeyAssistantBinding.OTHER -> stringResource(R.string.assistant_power_key_other)
                    PowerKeyAssistantBinding.UNKNOWN -> stringResource(R.string.assistant_power_key_unknown)
                },
                tone = when (powerKey.binding) {
                    PowerKeyAssistantBinding.ASSISTANT -> RootToolsStatusTone.Success
                    PowerKeyAssistantBinding.OTHER -> RootToolsStatusTone.Warning
                    PowerKeyAssistantBinding.UNKNOWN -> RootToolsStatusTone.Neutral
                },
            )
        }
        powerKey.oemLongPressValue?.let {
            RootToolsKeyValueRow(stringResource(R.string.assistant_power_key_oem_value), it)
        }
        powerKey.aospLongPressValue?.let {
            RootToolsKeyValueRow(stringResource(R.string.assistant_power_key_aosp_value), it)
        }
        powerKey.aospVeryLongPressValue?.let {
            RootToolsKeyValueRow(stringResource(R.string.assistant_power_key_very_long_value), it)
        }
        Text(
            stringResource(R.string.assistant_power_key_read_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssistantCandidateCard(
    candidate: AssistantCandidate,
    currentPackage: String?,
    switchingPackage: String?,
    onSwitch: () -> Unit,
) {
    val current = candidate.packageName == currentPackage
    val switching = candidate.packageName == switchingPackage
    RootToolsSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(candidate.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    candidate.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current) {
                RootToolsStatusChip(
                    label = stringResource(R.string.assistant_status_active),
                    tone = RootToolsStatusTone.Success,
                )
            }
        }
        candidate.voiceServiceComponents.firstOrNull()?.let { component ->
            Text(
                component,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !current && switchingPackage == null,
            onClick = onSwitch,
        ) {
            if (switching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 2.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                if (current) stringResource(R.string.assistant_current_action)
                else stringResource(R.string.assistant_switch_action),
            )
        }
    }
}

@Composable
private fun AssistantFeedbackCard(
    feedback: AssistantSwitchResult,
    onDismiss: () -> Unit,
) {
    RootToolsSectionCard {
        RootToolsStatusChip(
            label = if (feedback.success) stringResource(R.string.common_success) else stringResource(R.string.common_failed),
            tone = if (feedback.success) RootToolsStatusTone.Success else RootToolsStatusTone.Danger,
        )
        Text(
            when (feedback.status) {
                AssistantSwitchStatus.SWITCHED -> stringResource(
                    R.string.assistant_feedback_switched,
                    feedback.currentPackage.orEmpty(),
                    feedback.backend.displayName,
                )
                AssistantSwitchStatus.ALREADY_SELECTED -> stringResource(R.string.assistant_feedback_already_selected)
                AssistantSwitchStatus.INVALID_PACKAGE -> stringResource(R.string.assistant_feedback_invalid_package)
                AssistantSwitchStatus.NOT_ELIGIBLE -> stringResource(R.string.assistant_feedback_not_eligible)
                AssistantSwitchStatus.WRITE_FAILED -> stringResource(
                    R.string.assistant_feedback_write_failed,
                    feedback.detail,
                )
                AssistantSwitchStatus.VERIFY_FAILED -> stringResource(
                    R.string.assistant_feedback_verify_failed,
                    feedback.currentPackage.orEmpty(),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_dismiss))
        }
    }
}
