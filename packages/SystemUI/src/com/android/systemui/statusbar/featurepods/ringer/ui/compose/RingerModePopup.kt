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

package com.android.systemui.statusbar.featurepods.ringer.ui.compose

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupSurface
import com.android.systemui.statusbar.featurepods.ringer.shared.model.RingerModePopupModel

private val PopupShape = RoundedCornerShape(32.dp)
private val DarkBg = Color(0xFF1C1D22)
private val RedSilent = Color(0xFFFF453A)
private val AmberVibrate = Color(0xFFFF9F0A)
private val BlueRing = Color(0xFF0A84FF)

/** Expanded ringer mode selector card in Dynamic Island. */
@Composable
fun RingerModePopup(
    model: RingerModePopupModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(AudioManager::class.java)

    PopupSurface(
        shape = PopupShape,
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mode Title
            Text(
                text = "Sound Mode: ${model.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // 3 Mode selection buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingerOption(
                    label = "Silent",
                    iconRes = R.drawable.ic_volume_ringer_mute,
                    isSelected = model.mode == AudioManager.RINGER_MODE_SILENT,
                    accentColor = RedSilent,
                    onClick = {
                        audioManager?.ringerModeInternal = AudioManager.RINGER_MODE_SILENT
                    },
                )

                RingerOption(
                    label = "Vibrate",
                    iconRes = R.drawable.ic_volume_ringer_vibrate,
                    isSelected = model.mode == AudioManager.RINGER_MODE_VIBRATE,
                    accentColor = AmberVibrate,
                    onClick = {
                        audioManager?.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE
                    },
                )

                RingerOption(
                    label = "Ring",
                    iconRes = R.drawable.ic_volume_ringer,
                    isSelected = model.mode == AudioManager.RINGER_MODE_NORMAL,
                    accentColor = BlueRing,
                    onClick = {
                        audioManager?.ringerModeInternal = AudioManager.RINGER_MODE_NORMAL
                    },
                )
            }
        }
    }
}

@Composable
private fun RingerOption(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else DarkBg)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            StatusBarIcon(
                icon =
                    Icon.Resource(
                        resId = iconRes,
                        contentDescription = ContentDescription.Loaded(label),
                    ),
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) Color.White else Color(0xFF8E8E93),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accentColor else Color(0xFF8E8E93),
        )
    }
}
