package com.demogorgon314.monitorcontrolremotecontrolandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.HomeScreen
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.HomeViewModel
import com.demogorgon314.monitorcontrolremotecontrolandroid.ui.theme.MonitorControlRemoteControlAndroidTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.provideFactory(
            settingsStore = HomeViewModel.createDefaultSettingsStore(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonitorControlRemoteControlAndroidTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onRefresh = viewModel::refresh,
                    onOpenSettings = viewModel::openSettings,
                    onDismissSettings = viewModel::dismissSettings,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onTokenChange = viewModel::updateToken,
                    onScanHostsRequested = viewModel::onScanHostsRequested,
                    onScanResultSelected = viewModel::onScanResultSelected,
                    onDismissScanResultPicker = viewModel::dismissScanResultPicker,
                    onSaveSettings = viewModel::saveSettings,
                    onGlobalBrightnessChanged = viewModel::onGlobalBrightnessChanged,
                    onGlobalBrightnessChangeFinished = viewModel::onGlobalBrightnessChangeFinished,
                    onGlobalVolumeChanged = viewModel::onGlobalVolumeChanged,
                    onGlobalVolumeChangeFinished = viewModel::onGlobalVolumeChangeFinished,
                    onPowerAllOn = { viewModel.onPowerAll(turnOn = true) },
                    onPowerAllOff = { viewModel.onPowerAll(turnOn = false) },
                    onDisplayBrightnessChanged = viewModel::onDisplayBrightnessChanged,
                    onDisplayBrightnessChangeFinished = viewModel::onDisplayBrightnessChangeFinished,
                    onDisplayVolumeChanged = viewModel::onDisplayVolumeChanged,
                    onDisplayVolumeChangeFinished = viewModel::onDisplayVolumeChangeFinished,
                    onDisplayPowerToggle = viewModel::onDisplayPowerToggle
                )
            }
        }
    }
}
