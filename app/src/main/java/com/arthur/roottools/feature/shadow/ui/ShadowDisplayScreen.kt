package com.arthur.roottools.feature.shadow.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.component.RootToolsDetailHeader
import com.arthur.roottools.core.ui.component.RootToolsErrorCard
import com.arthur.roottools.core.ui.component.RootToolsKeyValueRow
import com.arthur.roottools.core.ui.component.RootToolsRiskBanner
import com.arthur.roottools.core.ui.component.RootToolsSectionCard
import com.arthur.roottools.core.ui.component.RootToolsSectionHeader
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRiskLevel
import com.arthur.roottools.core.ui.token.RootToolsStatusTone
import com.arthur.roottools.model.ShadowDisplayRuntimeState

@Composable
fun ShadowDisplayScreen(
    state: ShadowDisplayUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStart: (String, String, String) -> Unit,
    onStop: () -> Unit,
    onLaunchPackage: (String) -> Unit,
    onTap: (String, String) -> Unit,
    onSwipe: (String, String, String, String, String) -> Unit,
    onTypeText: (String) -> Unit,
    onCapturePreview: () -> Unit,
    onDismissFeedback: () -> Unit,
) {
    var width by rememberSaveable { mutableStateOf("720") }
    var height by rememberSaveable { mutableStateOf("1600") }
    var dpi by rememberSaveable { mutableStateOf("320") }
    var packageName by rememberSaveable { mutableStateOf(MAPS_PACKAGE) }
    var tapX by rememberSaveable { mutableStateOf("360") }
    var tapY by rememberSaveable { mutableStateOf("800") }
    var swipeX1 by rememberSaveable { mutableStateOf("360") }
    var swipeY1 by rememberSaveable { mutableStateOf("1200") }
    var swipeX2 by rememberSaveable { mutableStateOf("360") }
    var swipeY2 by rememberSaveable { mutableStateOf("400") }
    var swipeDuration by rememberSaveable { mutableStateOf("350") }
    var inputText by rememberSaveable { mutableStateOf("") }
    var confirmStop by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.status.running, state.status.config) {
        if (state.status.running) {
            width = state.status.config.width.toString()
            height = state.status.config.height.toString()
            dpi = state.status.config.densityDpi.toString()
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.shadow_display_stop_confirm_title)) },
            text = { Text(stringResource(R.string.shadow_display_stop_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        onStop()
                    },
                ) {
                    Text(stringResource(R.string.shadow_display_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(R.string.shadow_display_cancel))
                }
            },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                RootToolsDetailHeader(
                    title = stringResource(R.string.shadow_display_title),
                    subtitle = stringResource(R.string.shadow_display_subtitle),
                    onBack = onBack,
                    loading = state.loading || state.actionRunning,
                    onRefresh = onRefresh,
                )
            }

            item { StatusCard(state) }

            item {
                LifecycleCard(
                    state = state,
                    width = width,
                    height = height,
                    dpi = dpi,
                    onWidth = { width = it },
                    onHeight = { height = it },
                    onDpi = { dpi = it },
                    onStart = { onStart(width, height, dpi) },
                    onStop = { confirmStop = true },
                )
            }

            item {
                AppLaunchCard(
                    running = state.status.running,
                    busy = state.actionRunning,
                    packageName = packageName,
                    onPackageName = { packageName = it },
                    onLaunch = { onLaunchPackage(packageName) },
                    onPreset = { packageName = it },
                )
            }

            item {
                PointerCard(
                    running = state.status.running,
                    busy = state.actionRunning,
                    tapX = tapX,
                    tapY = tapY,
                    onTapX = { tapX = it },
                    onTapY = { tapY = it },
                    onTap = { onTap(tapX, tapY) },
                    swipeX1 = swipeX1,
                    swipeY1 = swipeY1,
                    swipeX2 = swipeX2,
                    swipeY2 = swipeY2,
                    duration = swipeDuration,
                    onSwipeX1 = { swipeX1 = it },
                    onSwipeY1 = { swipeY1 = it },
                    onSwipeX2 = { swipeX2 = it },
                    onSwipeY2 = { swipeY2 = it },
                    onDuration = { swipeDuration = it },
                    onSwipe = { onSwipe(swipeX1, swipeY1, swipeX2, swipeY2, swipeDuration) },
                )
            }

            item {
                TextInputCard(
                    running = state.status.running,
                    busy = state.actionRunning,
                    text = inputText,
                    onText = { inputText = it },
                    onSend = { onTypeText(inputText) },
                )
            }

            item {
                PreviewCard(
                    running = state.status.running,
                    busy = state.actionRunning,
                    jpeg = state.previewJpeg,
                    onRefresh = onCapturePreview,
                )
            }

            item {
                RootToolsRiskBanner(
                    title = stringResource(R.string.shadow_display_safety_title),
                    detail = stringResource(R.string.shadow_display_safety_body),
                    level = RootToolsRiskLevel.Caution,
                )
            }

            state.messageRes?.let { messageRes ->
                item {
                    FeedbackCard(
                        text = stringResource(messageRes),
                        onDismiss = onDismissFeedback,
                    )
                }
            }
            state.errorRes?.let { errorRes ->
                item {
                    val detail = state.errorDetail.orEmpty()
                    RootToolsErrorCard(
                        message = if (detail.isBlank()) stringResource(errorRes)
                        else stringResource(R.string.shadow_display_operation_failed, detail),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: ShadowDisplayUiState) {
    val status = state.status
    val stateLabel = when (status.state) {
        ShadowDisplayRuntimeState.RUNNING -> stringResource(R.string.shadow_display_state_running)
        ShadowDisplayRuntimeState.STARTING -> stringResource(R.string.shadow_display_state_starting)
        ShadowDisplayRuntimeState.STOPPED -> stringResource(R.string.shadow_display_state_stopped)
        ShadowDisplayRuntimeState.ERROR -> stringResource(R.string.shadow_display_state_error)
    }
    val tone = when {
        status.running -> RootToolsStatusTone.Success
        status.state == ShadowDisplayRuntimeState.ERROR -> RootToolsStatusTone.Danger
        status.state == ShadowDisplayRuntimeState.STARTING -> RootToolsStatusTone.Info
        else -> RootToolsStatusTone.Neutral
    }
    RootToolsSectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RootToolsSectionHeader(
                title = stringResource(R.string.shadow_display_status_title),
                subtitle = stringResource(R.string.shadow_display_status_subtitle),
                modifier = Modifier.weight(1f),
            )
            RootToolsStatusChip(stateLabel, tone)
        }
        RootToolsKeyValueRow(
            stringResource(R.string.shadow_display_display_id),
            status.displayId?.toString() ?: stringResource(R.string.common_dash),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.shadow_display_daemon_pid),
            status.pid?.toString() ?: stringResource(R.string.common_dash),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.shadow_display_resolution),
            stringResource(R.string.shadow_display_display_value, status.config.width, status.config.height),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.shadow_display_density),
            stringResource(R.string.shadow_display_dpi_value, status.config.densityDpi),
        )
        RootToolsKeyValueRow(
            stringResource(R.string.shadow_display_backend),
            stringResource(R.string.shadow_display_backend_root),
        )
        status.error?.let { RootToolsErrorCard(it) }
    }
}

@Composable
private fun LifecycleCard(
    state: ShadowDisplayUiState,
    width: String,
    height: String,
    dpi: String,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit,
    onDpi: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.shadow_display_lifecycle_title),
            subtitle = stringResource(R.string.shadow_display_lifecycle_subtitle),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(width, onWidth, R.string.shadow_display_width, Modifier.weight(1f), enabled = !state.status.running)
            NumberField(height, onHeight, R.string.shadow_display_height, Modifier.weight(1f), enabled = !state.status.running)
            NumberField(dpi, onDpi, R.string.shadow_display_dpi, Modifier.weight(1f), enabled = !state.status.running)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                enabled = !state.status.running && !state.actionRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (state.actionRunning) R.string.shadow_display_busy else R.string.shadow_display_start))
            }
            OutlinedButton(
                onClick = onStop,
                enabled = state.status.running && !state.actionRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.shadow_display_stop))
            }
        }
    }
}

@Composable
private fun AppLaunchCard(
    running: Boolean,
    busy: Boolean,
    packageName: String,
    onPackageName: (String) -> Unit,
    onLaunch: () -> Unit,
    onPreset: (String) -> Unit,
) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.shadow_display_app_title),
            subtitle = stringResource(R.string.shadow_display_app_subtitle),
        )
        OutlinedTextField(
            value = packageName,
            onValueChange = onPackageName,
            label = { Text(stringResource(R.string.shadow_display_package)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onPreset(MAPS_PACKAGE) }, label = { Text(stringResource(R.string.shadow_display_quick_maps)) })
            AssistChip(onClick = { onPreset(CHROME_PACKAGE) }, label = { Text(stringResource(R.string.shadow_display_quick_chrome)) })
            AssistChip(onClick = { onPreset(TAOBAO_PACKAGE) }, label = { Text(stringResource(R.string.shadow_display_quick_taobao)) })
            AssistChip(onClick = { onPreset(SETTINGS_PACKAGE) }, label = { Text(stringResource(R.string.shadow_display_quick_settings)) })
        }
        Button(onClick = onLaunch, enabled = running && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shadow_display_launch))
        }
    }
}

@Composable
private fun PointerCard(
    running: Boolean,
    busy: Boolean,
    tapX: String,
    tapY: String,
    onTapX: (String) -> Unit,
    onTapY: (String) -> Unit,
    onTap: () -> Unit,
    swipeX1: String,
    swipeY1: String,
    swipeX2: String,
    swipeY2: String,
    duration: String,
    onSwipeX1: (String) -> Unit,
    onSwipeY1: (String) -> Unit,
    onSwipeX2: (String) -> Unit,
    onSwipeY2: (String) -> Unit,
    onDuration: (String) -> Unit,
    onSwipe: () -> Unit,
) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.shadow_display_pointer_title),
            subtitle = stringResource(R.string.shadow_display_pointer_subtitle),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(tapX, onTapX, R.string.shadow_display_x, Modifier.weight(1f))
            NumberField(tapY, onTapY, R.string.shadow_display_y, Modifier.weight(1f))
            Button(onClick = onTap, enabled = running && !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shadow_display_tap))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(swipeX1, onSwipeX1, R.string.shadow_display_x1, Modifier.weight(1f))
            NumberField(swipeY1, onSwipeY1, R.string.shadow_display_y1, Modifier.weight(1f))
            NumberField(swipeX2, onSwipeX2, R.string.shadow_display_x2, Modifier.weight(1f))
            NumberField(swipeY2, onSwipeY2, R.string.shadow_display_y2, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(duration, onDuration, R.string.shadow_display_duration_ms, Modifier.weight(1f))
            Button(onClick = onSwipe, enabled = running && !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shadow_display_swipe))
            }
        }
    }
}

@Composable
private fun TextInputCard(
    running: Boolean,
    busy: Boolean,
    text: String,
    onText: (String) -> Unit,
    onSend: () -> Unit,
) {
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.shadow_display_text_title),
            subtitle = stringResource(R.string.shadow_display_text_subtitle),
        )
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.shadow_display_text_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSend, enabled = running && !busy && text.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shadow_display_send_text))
        }
    }
}

@Composable
private fun PreviewCard(
    running: Boolean,
    busy: Boolean,
    jpeg: ByteArray?,
    onRefresh: () -> Unit,
) {
    val bitmap = remember(jpeg) {
        jpeg?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    RootToolsSectionCard {
        RootToolsSectionHeader(
            title = stringResource(R.string.shadow_display_preview_title),
            subtitle = stringResource(R.string.shadow_display_preview_subtitle),
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.shadow_display_preview_title),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = stringResource(R.string.shadow_display_preview_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = running && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shadow_display_refresh_preview))
        }
    }
}

@Composable
private fun FeedbackCard(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.integrity_dismiss_symbol))
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit)) },
        label = { Text(stringResource(labelRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
    )
}

private const val MAPS_PACKAGE = "com.google.android.apps.maps"
private const val CHROME_PACKAGE = "com.android.chrome"
private const val TAOBAO_PACKAGE = "com.taobao.taobao"
private const val SETTINGS_PACKAGE = "com.android.settings"
