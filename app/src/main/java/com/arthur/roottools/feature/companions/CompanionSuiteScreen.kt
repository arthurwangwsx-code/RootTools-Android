package com.arthur.roottools.feature.companions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthur.roottools.R
import com.arthur.roottools.core.ui.action.openPackage
import com.arthur.roottools.core.ui.component.RootToolsStatusChip
import com.arthur.roottools.core.ui.token.RootToolsRadius
import com.arthur.roottools.core.ui.token.RootToolsSpacing
import com.arthur.roottools.core.ui.token.RootToolsStatusTone

private const val RELEASES_URL = "https://github.com/arthurwangwsx-code/RootTools-Android/releases/latest"

@Composable
fun CompanionSuiteRoute(
    onBack: () -> Unit,
    viewModel: CompanionSuiteViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompanionSuiteScreen(state, onBack, viewModel::refresh)
}

@Composable
private fun CompanionSuiteScreen(
    state: CompanionSuiteUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.companion_suite_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.companion_suite_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(RootToolsRadius.dialog),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(RootToolsSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.md),
                    ) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.companion_suite_summary, state.installedCount, CompanionSuiteRegistry.tools.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.companion_suite_summary_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            items(state.tools, key = { it.spec.id.name }) { tool ->
                CompanionToolCard(
                    tool = tool,
                    onOpen = { openPackage(context, tool.spec.packageName) },
                    onSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${tool.spec.packageName}"),
                                ),
                            )
                        }
                    },
                    onReleases = { runCatching { uriHandler.openUri(RELEASES_URL) } },
                )
            }
        }
    }
}

@Composable
private fun CompanionToolCard(
    tool: CompanionToolState,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onReleases: () -> Unit,
) {
    val title = stringResource(tool.spec.id.titleRes())
    val statusText = when (tool.availability) {
        CompanionAvailability.INSTALLED -> tool.versionName?.let {
            stringResource(R.string.companion_suite_installed_version, it)
        } ?: stringResource(R.string.companion_suite_installed)
        CompanionAvailability.DISABLED -> stringResource(R.string.companion_suite_disabled)
        CompanionAvailability.MISSING -> stringResource(R.string.companion_suite_missing)
    }
    val tone = when (tool.availability) {
        CompanionAvailability.INSTALLED -> RootToolsStatusTone.Success
        CompanionAvailability.DISABLED -> RootToolsStatusTone.Warning
        CompanionAvailability.MISSING -> RootToolsStatusTone.Neutral
    }
    Card(
        shape = RoundedCornerShape(RootToolsRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(RootToolsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(tool.spec.id.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RootToolsStatusChip(statusText, tone)
            }
            Text(
                stringResource(tool.spec.role.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.companion_suite_package, tool.spec.packageName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RootToolsSpacing.sm)) {
                if (tool.launchable) {
                    Button(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.companion_suite_launch))
                    }
                }
                if (tool.availability != CompanionAvailability.MISSING) {
                    OutlinedButton(onClick = onSettings) {
                        Text(stringResource(R.string.companion_suite_app_info))
                    }
                } else {
                    OutlinedButton(onClick = onReleases) {
                        Text(stringResource(R.string.companion_suite_releases))
                    }
                }
            }
        }
    }
}

private fun CompanionToolId.titleRes(): Int = when (this) {
    CompanionToolId.BACKGROUND_SERVER -> R.string.companion_background_title
    CompanionToolId.HYPEROS_CREDENTIAL_FIX -> R.string.companion_hyperos_title
    CompanionToolId.NFC_LAB -> R.string.companion_nfc_title
}

private fun CompanionToolId.descriptionRes(): Int = when (this) {
    CompanionToolId.BACKGROUND_SERVER -> R.string.companion_background_desc
    CompanionToolId.HYPEROS_CREDENTIAL_FIX -> R.string.companion_hyperos_desc
    CompanionToolId.NFC_LAB -> R.string.companion_nfc_desc
}

private fun CompanionRole.labelRes(): Int = when (this) {
    CompanionRole.CORE -> R.string.companion_role_core
    CompanionRole.DEVICE_SPECIFIC -> R.string.companion_role_device
    CompanionRole.OPTIONAL -> R.string.companion_role_optional
}
