package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home.DisplayUiModel

@Composable
fun DisplayCard(
    display: DisplayUiModel,
    connected: Boolean,
    onBrightnessChanged: (Long, Int) -> Unit,
    onBrightnessChangeFinished: (Long) -> Unit,
    onPowerToggle: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val canAdjustBrightness = connected &&
        !display.isBusy &&
        display.canControlBrightness &&
        display.powerOn
    val canTogglePower = connected && !display.isBusy && display.canControlPower

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
                    onCheckedChange = { onPowerToggle(display.id, it) },
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
                    onValueChange = { onBrightnessChanged(display.id, it.toInt()) },
                    valueRange = 0f..100f,
                    enabled = canAdjustBrightness,
                    onValueChangeFinished = { onBrightnessChangeFinished(display.id) }
                )
            }

            if (!display.canControlBrightness) {
                Text(
                    text = "该显示器不支持亮度控制",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!display.powerOn) {
                Text(
                    text = "显示器已关闭，亮度滑杆暂不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
