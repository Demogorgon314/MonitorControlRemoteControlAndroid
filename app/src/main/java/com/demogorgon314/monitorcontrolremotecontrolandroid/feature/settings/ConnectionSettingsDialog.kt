package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsDraft
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidation

@Composable
fun ConnectionSettingsDialog(
    draft: ConnectionSettingsDraft,
    validation: ConnectionSettingsValidation,
    isScanningHosts: Boolean,
    scanErrorMessage: String?,
    saveEnabled: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanHosts: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val tokenVisualTransformation = if (showToken) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("settings_dialog"),
        title = {
            Text(text = "连接设置")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "配置 MonitorControl API 连接信息")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (draft.host.isBlank()) "自动扫描主机" else "重新扫描主机"
                    )
                    TextButton(
                        onClick = onScanHosts,
                        enabled = !isScanningHosts
                    ) {
                        Text(
                            text = when {
                                isScanningHosts -> "扫描中..."
                                draft.host.isBlank() -> "自动扫描"
                                else -> "重新扫描"
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.host,
                    onValueChange = onHostChange,
                    label = { Text(text = "主机地址") },
                    placeholder = { Text(text = "192.168.1.10") },
                    singleLine = true,
                    isError = validation.hostError != null,
                    supportingText = {
                        validation.hostError?.let { Text(text = it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isScanningHosts) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(text = "正在扫描局域网主机...")
                    }
                }
                if (!scanErrorMessage.isNullOrBlank()) {
                    Text(
                        text = scanErrorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = onPortChange,
                    label = { Text(text = "端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validation.portError != null,
                    supportingText = {
                        validation.portError?.let { Text(text = it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.token,
                    onValueChange = onTokenChange,
                    label = { Text(text = "Bearer Token") },
                    singleLine = true,
                    visualTransformation = tokenVisualTransformation,
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showToken) "隐藏 Token" else "显示 Token"
                            )
                        }
                    },
                    isError = validation.tokenError != null,
                    supportingText = {
                        validation.tokenError?.let { Text(text = it) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = saveEnabled
            ) {
                Text(text = "保存")
            }
        }
    )
}
