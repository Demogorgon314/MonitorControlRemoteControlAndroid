package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidator
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScanMatchKind
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScannedHostCandidate
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.components.DisplayCard
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.components.GlobalControlCard
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.settings.ConnectionSettingsDialog

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanHostsRequested: (Boolean) -> Unit,
    onScanResultSelected: (String) -> Unit,
    onDismissScanResultPicker: () -> Unit,
    onSaveSettings: () -> Unit,
    onGlobalBrightnessChanged: (Int) -> Unit,
    onGlobalBrightnessChangeFinished: () -> Unit,
    onGlobalVolumeChanged: (Int) -> Unit,
    onGlobalVolumeChangeFinished: () -> Unit,
    onPowerAllOn: () -> Unit,
    onPowerAllOff: () -> Unit,
    onDisplayBrightnessChanged: (Long, Int) -> Unit,
    onDisplayBrightnessChangeFinished: (Long) -> Unit,
    onDisplayVolumeChanged: (Long, Int) -> Unit,
    onDisplayVolumeChangeFinished: (Long) -> Unit,
    onDisplayPowerToggle: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentValidation = ConnectionSettingsValidator.validate(
        host = uiState.settingsDraft.host,
        port = uiState.settingsDraft.port,
        token = uiState.settingsDraft.token
    )

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                HeaderBar(
                    connectionStatus = uiState.connectionStatus,
                    refreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .testTag("display_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    GlobalControlCard(
                        brightness = uiState.globalBrightness,
                        volume = uiState.globalVolume,
                        enabled = uiState.connectionStatus == ConnectionStatus.Connected,
                        canControlBrightness = uiState.displays.any {
                            it.canControlBrightness && it.powerOn
                        },
                        canControlVolume = uiState.displays.any {
                            it.canControlVolume && it.powerOn
                        },
                        busy = uiState.isGlobalBusy,
                        onBrightnessChanged = onGlobalBrightnessChanged,
                        onBrightnessChangeFinished = onGlobalBrightnessChangeFinished,
                        onVolumeChanged = onGlobalVolumeChanged,
                        onVolumeChangeFinished = onGlobalVolumeChangeFinished,
                        onPowerAllOn = onPowerAllOn,
                        onPowerAllOff = onPowerAllOff
                    )
                }
                item {
                    Text(
                        text = "显示器列表 (${uiState.displays.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.displays.isEmpty()) {
                    item {
                        EmptyDisplayCard()
                    }
                } else {
                    items(
                        items = uiState.displays,
                        key = { it.id }
                    ) { display ->
                        DisplayCard(
                            display = display,
                            connected = uiState.connectionStatus == ConnectionStatus.Connected,
                            onBrightnessChanged = onDisplayBrightnessChanged,
                            onBrightnessChangeFinished = onDisplayBrightnessChangeFinished,
                            onVolumeChanged = onDisplayVolumeChanged,
                            onVolumeChangeFinished = onDisplayVolumeChangeFinished,
                            onPowerToggle = onDisplayPowerToggle
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (uiState.showSettingsDialog) {
        ConnectionSettingsDialog(
            draft = uiState.settingsDraft,
            validation = uiState.settingsValidation,
            isScanningHosts = uiState.isScanningHosts,
            scanErrorMessage = uiState.scanErrorMessage,
            saveEnabled = currentValidation.isValid,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onTokenChange = onTokenChange,
            onScanHosts = { onScanHostsRequested(true) },
            onSave = onSaveSettings,
            onDismiss = onDismissSettings
        )
    }

    if (uiState.showScanResultPicker && uiState.scanCandidates.isNotEmpty()) {
        ScanResultPickerDialog(
            candidates = uiState.scanCandidates,
            onSelect = onScanResultSelected,
            onDismiss = onDismissScanResultPicker
        )
    }
}

@Composable
private fun ScanResultPickerDialog(
    candidates: List<ScannedHostCandidate>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择可用主机") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEach { candidate ->
                    Button(
                        onClick = { onSelect(candidate.host) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = candidate.host)
                            Text(
                                text = "${candidate.latencyMs} ms · ${candidate.matchKind.label()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun HeaderBar(
    connectionStatus: ConnectionStatus,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DesktopWindows,
                contentDescription = null
            )
            Column {
                Text(
                    text = "MonitorControl",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "远程显示器控制",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val (icon, tint) = when (connectionStatus) {
                ConnectionStatus.Connected -> Icons.Outlined.Wifi to Color(0xFF1AAE4A)
                ConnectionStatus.Connecting -> Icons.Outlined.Sync to MaterialTheme.colorScheme.onSurfaceVariant
                ConnectionStatus.Disconnected -> Icons.Outlined.WifiOff to Color(0xFFCC3D3D)
            }

            Icon(
                imageVector = icon,
                tint = tint,
                contentDescription = "连接状态"
            )

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefresh()
                },
                enabled = !refreshing,
                modifier = Modifier.testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新"
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenSettings()
                },
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置"
                )
            }
        }
    }
}

private fun ScanMatchKind.label(): String = when (this) {
    ScanMatchKind.HEALTH_OK -> "服务可直接连接"
    ScanMatchKind.UNAUTHORIZED_SIGNATURE -> "检测到服务（需填 Token）"
}

@Composable
private fun EmptyDisplayCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "未发现可控显示器",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "请确认 Mac 端已开启 Remote HTTP API，且设备在同一局域网。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
