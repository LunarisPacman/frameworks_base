/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.featurepods.bluetooth.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.statusbar.featurepods.bluetooth.shared.model.AudioDeviceKind
import com.android.systemui.statusbar.featurepods.bluetooth.shared.model.BluetoothAudioPopupModel
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupSurface

private val PopupShape = RoundedCornerShape(32.dp)
private val CardBg = Color(0xFF16181C)
private val GreenActive = Color(0xFF26E07F)
private val BlueAccent = Color(0xFF4DA3FF)

/** Expanded audio accessory popup in dynamic island. */
@Composable
fun BluetoothAudioPopup(
    model: BluetoothAudioPopupModel,
    modifier: Modifier = Modifier,
) {
    val floatOffset = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        floatOffset.animateTo(
            targetValue = 6f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
        )
    }

    PopupSurface(
        shape = PopupShape,
        modifier = modifier.widthIn(min = 300.dp, max = 360.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Icon + Device Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconRes =
                    when (model.deviceKind) {
                        AudioDeviceKind.HEADPHONES ->
                            com.android.settingslib.R.drawable.ic_headphone
                        AudioDeviceKind.EARPHONES ->
                            com.android.settingslib.R.drawable.ic_bt_untethered_earbuds
                        AudioDeviceKind.SPEAKER ->
                            com.android.settingslib.R.drawable.ic_bt_le_audio_speakers
                        AudioDeviceKind.CAR ->
                            com.android.systemui.res.R.drawable.ic_bluetooth_connected
                        AudioDeviceKind.OTHER ->
                            com.android.settingslib.R.drawable.ic_headphone
                    }

                Box(
                    modifier =
                        Modifier.size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(BlueAccent.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                            .border(1.dp, BlueAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusBarIcon(
                        icon =
                            Icon.Resource(
                                resId = iconRes,
                                contentDescription = ContentDescription.Loaded(model.deviceName),
                            ),
                        modifier =
                            Modifier.size(28.dp).graphicsLayer {
                                translationY = floatOffset.value
                            },
                        tint = BlueAccent,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(7.dp).background(GreenActive, CircleShape)
                        )
                        Text(
                            text =
                                when (model.deviceKind) {
                                    AudioDeviceKind.HEADPHONES -> "Headphones Connected"
                                    AudioDeviceKind.EARPHONES -> "Earphones Connected"
                                    AudioDeviceKind.SPEAKER -> "Speaker Connected"
                                    AudioDeviceKind.CAR -> "Car Audio Connected"
                                    AudioDeviceKind.OTHER -> "Connected"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            // Battery section
            val hasDetailedBatteries =
                model.leftBattery != null || model.rightBattery != null || model.caseBattery != null

            if (hasDetailedBatteries) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    model.leftBattery?.let {
                        BatteryMiniBadge(label = "Left", percent = it)
                    }
                    model.rightBattery?.let {
                        BatteryMiniBadge(label = "Right", percent = it)
                    }
                    model.caseBattery?.let {
                        BatteryMiniBadge(label = "Case", percent = it)
                    }
                }
            } else if (model.batteryLevel != null) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Battery",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${model.batteryLevel}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (model.batteryLevel <= 20) Color(0xFFFF5252) else GreenActive,
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xFF2A2E35))
                            .clickable(onClick = model.openSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }

                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(0xFF3B2023))
                            .clickable(onClick = model.disconnect),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF7A85),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryMiniBadge(label: String, percent: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
        Text(
            text = "$percent%",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (percent <= 20) Color(0xFFFF5252) else GreenActive,
        )
    }
}
