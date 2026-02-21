package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun GlobalControlCard(
    brightness: Int,
    enabled: Boolean,
    busy: Boolean,
    onBrightnessChanged: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onPowerAllOn: () -> Unit,
    onPowerAllOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.testTag("global_control_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LightMode,
                    contentDescription = null
                )
                Text(
                    text = "全局控制",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部亮度",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$brightness%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { onBrightnessChanged(it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = enabled && !busy,
                    onValueChangeFinished = onBrightnessChangeFinished
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onPowerAllOn,
                    enabled = enabled && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null
                    )
                    Text(text = "全部开启")
                }
                Button(
                    onClick = onPowerAllOff,
                    enabled = enabled && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null
                    )
                    Text(text = "全部关闭")
                }
            }
        }
    }
}
