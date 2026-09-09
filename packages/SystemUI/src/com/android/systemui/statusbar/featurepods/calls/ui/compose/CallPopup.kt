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

package com.android.systemui.statusbar.featurepods.calls.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon as StatusBarIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallPopupModel
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallState
import com.android.systemui.statusbar.featurepods.popups.ui.compose.PopupSurface
import com.android.systemui.statusbar.featurepods.popups.ui.compose.rememberElapsedDurationText

private val PopupShape = RoundedCornerShape(32.dp)
private val GreenCall = Color(0xFF26E07F)
private val RedHangup = Color(0xFFFF453A)
private val DarkButtonBg = Color(0xFF2C2C2E)
private val DarkButtonActive = Color(0xFFFFFFFF)

/** Expanded call popup in dynamic island for incoming or ongoing calls. */
@Composable
fun CallPopup(
    model: CallPopupModel,
    modifier: Modifier = Modifier,
) {
    val isIncoming = model.state == CallState.INCOMING
    val pulseScale = remember { Animatable(1f) }

    LaunchedEffect(isIncoming) {
        if (isIncoming) {
            pulseScale.animateTo(
                targetValue = 1.15f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        }
    }

    PopupSurface(
        shape = PopupShape,
        modifier = modifier.widthIn(min = 300.dp, max = 360.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Caller details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(46.dp)
                            .graphicsLayer {
                                if (isIncoming) {
                                    scaleX = pulseScale.value
                                    scaleY = pulseScale.value
                                }
                            }
                            .clip(CircleShape)
                            .background(if (isIncoming) GreenCall else Color(0xFF3A3D44)),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusBarIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_call,
                                contentDescription = ContentDescription.Loaded("Call"),
                            ),
                        modifier = Modifier.size(24.dp),
                        tint = if (isIncoming) Color.Black else GreenCall,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.callerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (isIncoming) "Incoming Call..."
                            else if (model.callStartTimeMs > 0)
                                rememberElapsedDurationText(model.callStartTimeMs)
                            else "Ongoing Call",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isIncoming) GreenCall
                            else LocalContentColor.current.copy(alpha = 0.72f),
                        fontWeight = if (isIncoming) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            // Controls
            if (isIncoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Decline button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.size(56.dp)
                                    .clip(CircleShape)
                                    .background(RedHangup)
                                    .clickable(onClick = model.declineOrHangup),
                            contentAlignment = Alignment.Center,
                        ) {
                            StatusBarIcon(
                                icon =
                                    Icon.Resource(
                                        resId = R.drawable.ic_call,
                                        contentDescription = ContentDescription.Loaded("Decline"),
                                    ),
                                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = 135f },
                                tint = Color.White,
                            )
                        }
                        Text(text = "Decline", fontSize = 12.sp, color = Color.White)
                    }

                    // Answer button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier.size(56.dp)
                                    .clip(CircleShape)
                                    .background(GreenCall)
                                    .clickable(onClick = model.answer),
                            contentAlignment = Alignment.Center,
                        ) {
                            StatusBarIcon(
                                icon =
                                    Icon.Resource(
                                        resId = R.drawable.ic_call,
                                        contentDescription = ContentDescription.Loaded("Answer"),
                                    ),
                                modifier = Modifier.size(28.dp),
                                tint = Color.Black,
                            )
                        }
                        Text(text = "Answer", fontSize = 12.sp, color = Color.White)
                    }
                }
            } else {
                // Ongoing call action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mute Toggle
                    model.toggleMute?.let { toggle ->
                        CallControlButton(
                            label = if (model.isMuted) "Muted" else "Mute",
                            iconRes = R.drawable.ic_mic_off,
                            isActive = model.isMuted,
                            onClick = toggle,
                        )
                    }

                    // Speaker Toggle
                    model.toggleSpeaker?.let { toggle ->
                        CallControlButton(
                            label = if (model.isSpeakerOn) "Speaker On" else "Speaker",
                            iconRes = R.drawable.ic_speaker_on,
                            isActive = model.isSpeakerOn,
                            onClick = toggle,
                        )
                    }

                    // End Call button
                    Box(
                        modifier =
                            Modifier.size(52.dp)
                                .clip(CircleShape)
                                .background(RedHangup)
                                .clickable(onClick = model.declineOrHangup),
                        contentAlignment = Alignment.Center,
                    ) {
                        StatusBarIcon(
                            icon =
                                Icon.Resource(
                                    resId = R.drawable.ic_call,
                                    contentDescription = ContentDescription.Loaded("End Call"),
                                ),
                            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = 135f },
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    label: String,
    iconRes: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(if (isActive) DarkButtonActive else DarkButtonBg)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            StatusBarIcon(
                icon =
                    Icon.Resource(
                        resId = iconRes,
                        contentDescription = ContentDescription.Loaded(label),
                    ),
                modifier = Modifier.size(22.dp),
                tint = if (isActive) Color.Black else Color.White,
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isActive) GreenCall else LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}
