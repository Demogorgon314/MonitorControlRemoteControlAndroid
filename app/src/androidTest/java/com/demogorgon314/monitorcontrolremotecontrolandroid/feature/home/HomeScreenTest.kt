package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsDraft
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidation
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScanMatchKind
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScannedHostCandidate
import com.demogorgon314.monitorcontrolremotecontrolandroid.ui.theme.MonitorControlRemoteControlAndroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_should_render_display_cards() {
        val state = HomeUiState(
            connectionStatus = ConnectionStatus.Connected,
            displays = listOf(
                DisplayUiModel(
                    id = 1L,
                    name = "LG UltraWide",
                    brightness = 68,
                    powerOn = true,
                    canControlBrightness = true,
                    canControlPower = true,
                    isVirtual = false
                ),
                DisplayUiModel(
                    id = 2L,
                    name = "Dell P2722H",
                    brightness = 60,
                    powerOn = true,
                    canControlBrightness = true,
                    canControlPower = true,
                    isVirtual = false
                )
            )
        )

        composeRule.setContent {
            MonitorControlRemoteControlAndroidTheme {
                HomeScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onHostChange = {},
                    onPortChange = {},
                    onTokenChange = {},
                    onScanHostsRequested = {},
                    onScanResultSelected = {},
                    onDismissScanResultPicker = {},
                    onSaveSettings = {},
                    onGlobalBrightnessChanged = {},
                    onGlobalBrightnessChangeFinished = {},
                    onPowerAllOn = {},
                    onPowerAllOff = {},
                    onDisplayBrightnessChanged = { _, _ -> },
                    onDisplayBrightnessChangeFinished = {},
                    onDisplayPowerToggle = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("display_card_1").assertIsDisplayed()
        composeRule.onNodeWithTag("display_card_2").assertIsDisplayed()
    }

    @Test
    fun screen_should_show_settings_validation_errors() {
        val state = HomeUiState(
            showSettingsDialog = true,
            settingsDraft = ConnectionSettingsDraft(
                host = "",
                port = "",
                token = ""
            ),
            settingsValidation = ConnectionSettingsValidation(
                hostError = "请输入主机地址",
                portError = "请输入端口",
                tokenError = "请输入 Bearer Token"
            )
        )

        composeRule.setContent {
            MonitorControlRemoteControlAndroidTheme {
                HomeScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onHostChange = {},
                    onPortChange = {},
                    onTokenChange = {},
                    onScanHostsRequested = {},
                    onScanResultSelected = {},
                    onDismissScanResultPicker = {},
                    onSaveSettings = {},
                    onGlobalBrightnessChanged = {},
                    onGlobalBrightnessChangeFinished = {},
                    onPowerAllOn = {},
                    onPowerAllOff = {},
                    onDisplayBrightnessChanged = { _, _ -> },
                    onDisplayBrightnessChangeFinished = {},
                    onDisplayPowerToggle = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("settings_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("请输入主机地址").assertIsDisplayed()
        composeRule.onNodeWithText("请输入端口").assertIsDisplayed()
    }

    @Test
    fun refresh_button_should_invoke_callback() {
        var refreshed = false
        val state = HomeUiState(connectionStatus = ConnectionStatus.Connected)

        composeRule.setContent {
            MonitorControlRemoteControlAndroidTheme {
                HomeScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = { refreshed = true },
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onHostChange = {},
                    onPortChange = {},
                    onTokenChange = {},
                    onScanHostsRequested = {},
                    onScanResultSelected = {},
                    onDismissScanResultPicker = {},
                    onSaveSettings = {},
                    onGlobalBrightnessChanged = {},
                    onGlobalBrightnessChangeFinished = {},
                    onPowerAllOn = {},
                    onPowerAllOff = {},
                    onDisplayBrightnessChanged = { _, _ -> },
                    onDisplayBrightnessChangeFinished = {},
                    onDisplayPowerToggle = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("refresh_button").performClick()
        assertTrue(refreshed)
    }

    @Test
    fun settings_dialog_should_show_scan_progress() {
        val state = HomeUiState(
            showSettingsDialog = true,
            isScanningHosts = true
        )

        composeRule.setContent {
            MonitorControlRemoteControlAndroidTheme {
                HomeScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onHostChange = {},
                    onPortChange = {},
                    onTokenChange = {},
                    onScanHostsRequested = {},
                    onScanResultSelected = {},
                    onDismissScanResultPicker = {},
                    onSaveSettings = {},
                    onGlobalBrightnessChanged = {},
                    onGlobalBrightnessChangeFinished = {},
                    onPowerAllOn = {},
                    onPowerAllOff = {},
                    onDisplayBrightnessChanged = { _, _ -> },
                    onDisplayBrightnessChangeFinished = {},
                    onDisplayPowerToggle = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("正在扫描局域网主机...").assertIsDisplayed()
    }

    @Test
    fun scan_picker_should_show_candidates() {
        val state = HomeUiState(
            showScanResultPicker = true,
            scanCandidates = listOf(
                ScannedHostCandidate(
                    host = "192.168.1.10",
                    latencyMs = 42,
                    matchKind = ScanMatchKind.HEALTH_OK
                ),
                ScannedHostCandidate(
                    host = "192.168.1.22",
                    latencyMs = 55,
                    matchKind = ScanMatchKind.UNAUTHORIZED_SIGNATURE
                )
            )
        )

        composeRule.setContent {
            MonitorControlRemoteControlAndroidTheme {
                HomeScreen(
                    uiState = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onOpenSettings = {},
                    onDismissSettings = {},
                    onHostChange = {},
                    onPortChange = {},
                    onTokenChange = {},
                    onScanHostsRequested = {},
                    onScanResultSelected = {},
                    onDismissScanResultPicker = {},
                    onSaveSettings = {},
                    onGlobalBrightnessChanged = {},
                    onGlobalBrightnessChangeFinished = {},
                    onPowerAllOn = {},
                    onPowerAllOff = {},
                    onDisplayBrightnessChanged = { _, _ -> },
                    onDisplayBrightnessChangeFinished = {},
                    onDisplayPowerToggle = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("192.168.1.10").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.22").assertIsDisplayed()
    }
}
