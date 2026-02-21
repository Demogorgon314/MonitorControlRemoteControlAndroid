package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.DisplayUiModel

@Composable
fun DisplayCard(
    display: DisplayUiModel,
    connected: Boolean,
    onBrightnessChanged: (Long, Int) -> Unit,
    onBrightnessChangeFinished: (Long) -> Unit,
    onVolumeChanged: (Long, Int) -> Unit,
    onVolumeChangeFinished: (Long) -> Unit,
    onPowerToggle: (Long, Boolean) -> Unit,
    onInputSelected: (Long, Int, String?) -> Unit,
    onInputCustomCodeSubmit: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticBrightness by remember(display.id) { mutableIntStateOf(display.brightness) }
    var lastHapticVolume by remember(display.id) { mutableIntStateOf(display.volume) }
    var inputMenuExpanded by remember(display.id) { mutableStateOf(false) }
    var showCustomCodeDialog by remember(display.id) { mutableStateOf(false) }
    var customCodeInput by remember(display.id) { mutableStateOf("") }
    var customCodeError by remember(display.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(display.id, display.brightness) {
        lastHapticBrightness = display.brightness
    }

    LaunchedEffect(display.id, display.volume) {
        lastHapticVolume = display.volume
    }

    val canAdjustBrightness = connected &&
        !display.isBusy &&
        display.canControlBrightness &&
        display.powerOn
    val canAdjustVolume = connected &&
        !display.isBusy &&
        display.canControlVolume &&
        display.powerOn
    val canTogglePower = connected && !display.isBusy && display.canControlPower
    val canSwitchInput = connected && !display.isBusy && display.canControlInput
    val currentInputText = display.currentInput?.let { "${it.name} (${it.code})" } ?: "当前输入未知"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.testTag("display_card_${display.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DesktopWindows,
                        contentDescription = null
                    )
                    Text(
                        text = display.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Switch(
                    checked = display.powerOn,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPowerToggle(display.id, it)
                    },
                    enabled = canTogglePower
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "亮度",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${display.brightness}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Slider(
                    value = display.brightness.toFloat(),
                    onValueChange = {
                        val nextValue = it.toInt().coerceIn(0, 100)
                        if (nextValue != lastHapticBrightness) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticBrightness = nextValue
                        }
                        onBrightnessChanged(display.id, nextValue)
                    },
                    valueRange = 0f..100f,
                    enabled = canAdjustBrightness,
                    onValueChangeFinished = { onBrightnessChangeFinished(display.id) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "音量",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${display.volume}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Slider(
                    value = display.volume.toFloat(),
                    onValueChange = {
                        val nextValue = it.toInt().coerceIn(0, 100)
                        if (nextValue != lastHapticVolume) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticVolume = nextValue
                        }
                        onVolumeChanged(display.id, nextValue)
                    },
                    valueRange = 0f..100f,
                    enabled = canAdjustVolume,
                    onValueChangeFinished = { onVolumeChangeFinished(display.id) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "输入源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentInputText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (display.isInputFromLocalCache) {
                    Text(
                        text = "上次设置（本地）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { inputMenuExpanded = true },
                        enabled = canSwitchInput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_source_button_${display.id}")
                    ) {
                        Text(
                            text = display.currentInput?.name ?: "选择输入源",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = inputMenuExpanded,
                        onDismissRequest = { inputMenuExpanded = false }
                    ) {
                        if (display.availableInputs.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("无预设输入源") },
                                onClick = {},
                                enabled = false
                            )
                        } else {
                            display.availableInputs.forEach { input ->
                                DropdownMenuItem(
                                    text = { Text("${input.name} (${input.code})") },
                                    onClick = {
                                        inputMenuExpanded = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onInputSelected(display.id, input.code, input.name)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("自定义代码...") },
                            onClick = {
                                inputMenuExpanded = false
                                customCodeInput = ""
                                customCodeError = null
                                showCustomCodeDialog = true
                            }
                        )
                    }
                }
            }

            if (!display.canControlBrightness) {
                Text(
                    text = "该显示器不支持亮度控制",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!display.canControlVolume) {
                Text(
                    text = "该显示器不支持音量控制",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!display.canControlInput) {
                Text(
                    text = "该显示器不支持输入源切换",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!display.powerOn && (display.canControlBrightness || display.canControlVolume)) {
                Text(
                    text = "显示器已关闭，亮度和音量滑杆暂不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showCustomCodeDialog) {
        AlertDialog(
            modifier = Modifier.testTag("input_custom_code_dialog_${display.id}"),
            onDismissRequest = { showCustomCodeDialog = false },
            title = { Text(text = "输入源代码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customCodeInput,
                        onValueChange = {
                            customCodeInput = it.filter { char -> char.isDigit() }
                            customCodeError = null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        label = { Text("输入 0-255") },
                        isError = customCodeError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_custom_code_field_${display.id}")
                    )
                    customCodeError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = customCodeInput.toIntOrNull()
                        if (code == null || code !in 0..255) {
                            customCodeError = "请输入 0-255 的整数"
                            return@TextButton
                        }
                        showCustomCodeDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onInputCustomCodeSubmit(display.id, code)
                    },
                    modifier = Modifier.testTag("input_custom_code_confirm_${display.id}")
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomCodeDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
