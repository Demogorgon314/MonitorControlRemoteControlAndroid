package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsDraft
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidation
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidator
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScannedHostCandidate

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
}

data class DisplayUiModel(
    val id: Long,
    val name: String,
    val brightness: Int,
    val volume: Int,
    val powerOn: Boolean,
    val canControlBrightness: Boolean,
    val canControlVolume: Boolean,
    val canControlPower: Boolean,
    val isVirtual: Boolean,
    val isBusy: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val displays: List<DisplayUiModel> = emptyList(),
    val globalBrightness: Int = 50,
    val globalVolume: Int = 50,
    val isGlobalBusy: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val isScanningHosts: Boolean = false,
    val scanCandidates: List<ScannedHostCandidate> = emptyList(),
    val showScanResultPicker: Boolean = false,
    val scanErrorMessage: String? = null,
    val hasAutoScanRunForDialog: Boolean = false,
    val settingsDraft: ConnectionSettingsDraft = ConnectionSettingsDraft(
        port = ConnectionSettingsValidator.DEFAULT_PORT.toString()
    ),
    val settingsValidation: ConnectionSettingsValidation = ConnectionSettingsValidation()
)
