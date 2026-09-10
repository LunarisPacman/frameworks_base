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

package com.android.systemui.statusbar.featurepods.popups.ui.compose

import android.media.AudioManager
import android.view.DisplayCutout
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.android.systemui.statusbar.featurepods.bluetooth.shared.model.AudioDeviceKind
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandScale
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.screenrecord.shared.model.ScreenRecordPopupModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Single centered status bar capsule styled like a compact dynamic island. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusBarDynamicIslandChip(
    viewModel: PopupChipModel.Shown,
    pageCount: Int,
    cutoutSpec: DynamicIslandCutoutSpec,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onChipBoundsChanged: (Rect) -> Unit = {},
) {
    val isMediaChip = viewModel.popupContent is PopupContentModel.Media
    val chipShape = RoundedCornerShape(50)
    val colors = viewModel.colors
    val (widthScale, heightScale) = rememberDynamicIslandSizeScale()
    val chipContentColor =
        colors.chipContent(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val chipOutline =
        colors.chipOutline(
            isPopupShown = viewModel.isPopupShown,
            colorScheme = MaterialTheme.colorScheme,
        )
    val view = LocalView.current
    val boundsModifier =
        Modifier.onGloballyPositioned { coordinates ->
            onChipBoundsChanged(coordinates.boundsInScreen(view))
        }
    val hapticOnTap: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onTap()
    }
    val mediaOpenApp: (() -> Unit)? =
        (viewModel.popupContent as? PopupContentModel.Media)
            ?.takeIf { it.model.isPlaying }
            ?.model
            ?.openApp
    val hapticOnLongPress: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        mediaOpenApp?.invoke()
    }
    if (viewModel.popupContent.isUtilityStatusContent() && viewModel.icons.isNotEmpty()) {
        UtilityStatusIslandChip(
            viewModel = viewModel,
            onTap = hapticOnTap,
            cutoutSpec = cutoutSpec,
            widthScale = widthScale,
            heightScale = heightScale,
            chipContentColor = chipContentColor,
            chipOutline = chipOutline,
            modifier = modifier.then(boundsModifier),
        )
        return
    }

    val compactWidth = compactIslandWidthFor(viewModel.popupContent)?.times(widthScale)
    val hasInlineTimer = viewModel.popupContent is PopupContentModel.Stopwatch
    val trailingDecorationWidth =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media ->
                if (popupContent.model.isPlaying) {
                    14.dp
                } else if (pageCount > 1) {
                    11.dp
                } else {
                    0.dp
                }
            is PopupContentModel.ScreenRecord -> 11.dp
            else -> {
                if (pageCount > 1) 11.dp else 0.dp
            }
        }
    val leadingDecorationWidth =
        when {
            viewModel.icons.isEmpty() -> 0.dp
            else -> 18.dp + (8.dp * (viewModel.icons.size - 1))
        }
    val maxTextWidth =
        ((CompactIslandMaxWidth * widthScale) - 24.dp - leadingDecorationWidth - trailingDecorationWidth)
            .coerceAtLeast(56.dp)

    val collapseState = rememberDynamicIslandCollapseState(viewModel.isPopupShown)

    Row(
        modifier =
            modifier
                .then(boundsModifier)
                .defaultMinSize(minHeight = 32.dp * heightScale)
                .widthIn(
                    min = compactWidth ?: 0.dp,
                    max = compactWidth ?: (CompactIslandMaxWidth * widthScale),
                )
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = 0.74f,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                )
                .graphicsLayer {
                    scaleX = collapseState.scaleX
                    scaleY = collapseState.scaleY
                }
                .clip(chipShape)
                .background(Color.Black)
                .border(width = 1.dp, color = chipOutline, shape = chipShape)
                .combinedClickable(
                    onClick = hapticOnTap,
                    onLongClick = mediaOpenApp?.let { { hapticOnLongPress() } },
                )
                .padding(
                    start = 10.dp * widthScale,
                    end = 12.dp * widthScale,
                    top = 7.dp * heightScale,
                    bottom = 7.dp * heightScale,
                )
                .graphicsLayer { alpha = collapseState.contentAlpha },
        horizontalArrangement =
            if (isMediaChip) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        viewModel.icons.forEachIndexed { index, chipIcon ->
            val isArtworkLike =
                index == 0 &&
                    (viewModel.popupContent is PopupContentModel.Media ||
                        viewModel.popupContent is PopupContentModel.LiveScore)
            Icon(
                icon = chipIcon.icon,
                modifier = Modifier
                    .size(if (isArtworkLike) 18.dp else 16.dp)
                    .then(
                        if (isArtworkLike) {
                            Modifier.clip(CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                tint = if (isArtworkLike) Color.Unspecified else chipContentColor,
            )
        }

        viewModel.chipText
            ?.takeIf {
                !isMediaChip &&
                    viewModel.popupContent !is PopupContentModel.ScreenRecord &&
                    !hasInlineTimer &&
                    it.isNotBlank()
            }
            ?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = maxTextWidth),
                )
            }

        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.Media -> {
                val artworkDrawable = remember(popupContent.model.artworkIcon) {
                    (popupContent.model.artworkIcon as? IconModel.Loaded)?.drawable
                }
                if (popupContent.model.isPlaying) {
                    AudioReactiveBars(
                        isPlaying = true,
                        color = chipContentColor,
                        artworkDrawable = artworkDrawable,
                    )
                } else if (pageCount > 1) {
                    SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                }
            }
            is PopupContentModel.ScreenRecord ->
                when (val model = popupContent.model) {
                    is ScreenRecordPopupModel.Starting ->
                        StatusContent(
                            text = viewModel.chipText.orEmpty(),
                            color = chipContentColor,
                            showSwipeHint = pageCount > 1,
                        )
                    is ScreenRecordPopupModel.Recording ->
                        StatusContent(
                            text = viewModel.chipText.orEmpty(),
                            color = chipContentColor,
                            showSwipeHint = pageCount > 1,
                        )
                }
            is PopupContentModel.Stopwatch ->
                StatusContent(
                    text =
                        popupContent.model.elapsedTimeText
                            ?: rememberElapsedDurationText(
                                popupContent.model.baseElapsedRealtimeMs
                            ),
                    color = chipContentColor,
                    showSwipeHint = pageCount > 1,
                )
            else -> {
                if (pageCount > 1) {
                    SwipeHint(color = chipContentColor.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun UtilityStatusIslandChip(
    viewModel: PopupChipModel.Shown,
    onTap: () -> Unit,
    cutoutSpec: DynamicIslandCutoutSpec,
    widthScale: Float = 1f,
    heightScale: Float = 1f,
    chipContentColor: Color,
    chipOutline: Color,
    modifier: Modifier = Modifier,
) {
    val collapseState = rememberDynamicIslandCollapseState(viewModel.isPopupShown)
    val rightSegmentWidth =
        when (viewModel.popupContent) {
            is PopupContentModel.Flashlight -> 52.dp
            is PopupContentModel.Alarm -> 76.dp
            is PopupContentModel.Call -> 100.dp
            is PopupContentModel.RingerMode -> 80.dp
            is PopupContentModel.BluetoothAudio -> 48.dp
            else -> 88.dp
        } * widthScale
    val connectedIslandWidth =
        ((CompactUtilityConnectedIslandChromeWidth * widthScale) +
                cutoutSpec.embeddedGapWidth +
                rightSegmentWidth)
            .coerceIn(
                CompactUtilityConnectedIslandMinWidth * widthScale,
                CompactUtilityConnectedIslandMaxWidth * widthScale,
            )
    val utilityText =
        when (val popupContent = viewModel.popupContent) {
            is PopupContentModel.ScreenRecord ->
                when (val model = popupContent.model) {
                    is ScreenRecordPopupModel.Starting -> "${model.secondsUntilStarted}s"
                    is ScreenRecordPopupModel.Recording ->
                        rememberElapsedDurationText(model.startElapsedRealtimeMs)
                }
            is PopupContentModel.Stopwatch ->
                popupContent.model.elapsedTimeText
                    ?: rememberElapsedDurationText(popupContent.model.baseElapsedRealtimeMs)
            is PopupContentModel.Alarm -> viewModel.chipText.orEmpty()
            is PopupContentModel.Flashlight -> viewModel.chipText.orEmpty()
            is PopupContentModel.Call ->
                if (popupContent.model.state == com.android.systemui.statusbar.featurepods.calls.shared.model.CallState.ONGOING && popupContent.model.callStartTimeMs > 0) {
                    rememberElapsedDurationText(popupContent.model.callStartTimeMs)
                } else {
                    viewModel.chipText.orEmpty()
                }
            is PopupContentModel.RingerMode -> popupContent.model.label
            is PopupContentModel.BluetoothAudio -> ""
            else -> ""
        }

    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = collapseState.scaleX
                    scaleY = collapseState.scaleY
                }
                .defaultMinSize(minHeight = 32.dp * heightScale)
                .width(connectedIslandWidth)
                .clip(RoundedCornerShape(50))
                .background(Color.Black)
                .border(width = 1.dp, color = chipOutline, shape = RoundedCornerShape(50))
                .clickable(onClick = onTap)
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                )
                .graphicsLayer { alpha = collapseState.contentAlpha },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(10.dp * widthScale))
        when (val content = viewModel.popupContent) {
            is PopupContentModel.BluetoothAudio ->
                AnimatedBluetoothAudioIcon(
                    deviceKind = content.model.deviceKind,
                    color = chipContentColor,
                )
            is PopupContentModel.RingerMode ->
                AnimatedRingerIcon(
                    mode = content.model.mode,
                    color = chipContentColor,
                )
            is PopupContentModel.Call ->
                AnimatedCallIcon(
                    isIncoming = viewModel.chipText != null,
                    color = chipContentColor,
                )
            else ->
                Icon(
                    icon = viewModel.icons.first().icon,
                    modifier = Modifier.size(16.dp),
                    tint = chipContentColor,
                )
        }
        Spacer(modifier = Modifier.width(cutoutSpec.embeddedGapWidth))
        val isBluetoothChip = viewModel.popupContent is PopupContentModel.BluetoothAudio
        Box(
            modifier =
                Modifier.width(rightSegmentWidth)
                    .padding(
                        start = 6.dp * widthScale,
                        top = 7.dp * heightScale,
                        bottom = 7.dp * heightScale,
                        end = 6.dp * widthScale,
                    ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (isBluetoothChip) {
                AnimatedConnectedIcon(color = chipContentColor)
            } else {
                Text(
                    text = utilityText,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp * widthScale))
    }
}

@Composable
private fun AnimatedConnectedIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = 0.5f,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
        }
        launch {
            iconAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(250),
            )
        }
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = iconAlpha.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = IconModel.Resource(
                com.android.systemui.res.R.drawable.ic_bluetooth_connected,
                null,
            ),
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF26E07F),
        )
    }
}

@Composable
private fun AnimatedBluetoothAudioIcon(
    deviceKind: AudioDeviceKind,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0.4f) }
    val rotation = remember { Animatable(-18f) }
    val bobOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = 0.52f,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec =
                    spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
        delay(400)
        bobOffset.animateTo(
            targetValue = -1.5f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
        )
    }

    val iconRes =
        when (deviceKind) {
            AudioDeviceKind.HEADPHONES -> com.android.settingslib.R.drawable.ic_headphone
            AudioDeviceKind.EARPHONES -> com.android.settingslib.R.drawable.ic_bt_untethered_earbuds
            AudioDeviceKind.SPEAKER -> com.android.settingslib.R.drawable.ic_bt_le_audio_speakers
            AudioDeviceKind.CAR -> com.android.systemui.res.R.drawable.ic_bluetooth_connected
            AudioDeviceKind.OTHER -> com.android.settingslib.R.drawable.ic_headphone
        }

    Box(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
                translationY = bobOffset.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = IconModel.Resource(iconRes, null),
            modifier = Modifier.size(17.dp),
            tint = color,
        )
    }
}

@Composable
private fun AnimatedRingerIcon(
    mode: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    val shakeX = remember { Animatable(0f) }
    val scale = remember { Animatable(0.4f) }

    LaunchedEffect(mode) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                rotation.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        keyframes {
                            durationMillis = 650
                            0f at 0
                            -22f at 80 using FastOutSlowInEasing
                            22f at 180 using FastOutSlowInEasing
                            -14f at 280
                            14f at 380
                            -6f at 480
                            0f at 650
                        },
                )
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                shakeX.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        keyframes {
                            durationMillis = 500
                            0f at 0
                            -3.5f at 50
                            3.5f at 100
                            -3f at 150
                            3f at 200
                            -2f at 250
                            2f at 300
                            -1f at 380
                            0f at 500
                        },
                )
            }
            AudioManager.RINGER_MODE_SILENT -> {
                shakeX.snapTo(0f)
                rotation.snapTo(0f)
            }
        }
    }

    val (iconRes, iconTint) =
        when (mode) {
            AudioManager.RINGER_MODE_SILENT ->
                com.android.systemui.res.R.drawable.ic_volume_ringer_mute to Color(0xFFFF5252)
            AudioManager.RINGER_MODE_VIBRATE ->
                com.android.systemui.res.R.drawable.ic_volume_ringer_vibrate to Color(0xFFFFB74D)
            else ->
                com.android.systemui.res.R.drawable.ic_volume_ringer to color
        }

    Box(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
                translationX = shakeX.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = IconModel.Resource(iconRes, null),
            modifier = Modifier.size(17.dp),
            tint = iconTint,
        )
    }
}

@Composable
private fun AnimatedCallIcon(
    isIncoming: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isIncoming) {
        if (isIncoming) {
            scale.animateTo(
                targetValue = 1.22f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(450, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
            )
        } else {
            scale.snapTo(1f)
        }
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon = IconModel.Resource(com.android.systemui.res.R.drawable.ic_call, null),
            modifier = Modifier.size(16.dp),
            tint = if (isIncoming) Color(0xFF4CAF50) else color,
        )
    }
}

@Composable
private fun StatusContent(
    text: String,
    color: Color,
    showSwipeHint: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
        if (showSwipeHint) {
            SwipeHint(color = color.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SwipeHint(color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(2) {
            Box(
                modifier = Modifier.size(width = 3.dp, height = 3.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

private val CompactIslandMaxWidth = 192.dp
private val CompactMediaIslandWidth = 108.dp
private val CompactTimerIslandWidth = 116.dp
private val CompactRecordingIslandWidth = 88.dp
private val CompactAlarmIslandWidth = 92.dp
private val CompactUtilityIslandWidth = 74.dp
private val CompactUtilityConnectedIslandChromeWidth = 42.dp
private val CompactUtilityConnectedIslandMinWidth = 132.dp
private val CompactUtilityConnectedIslandMaxWidth = 236.dp
private val DynamicIslandEmbeddedGapFallbackWidth = 38.dp
private val DynamicIslandEmbeddedGapMinWidth = 34.dp
private val DynamicIslandEmbeddedGapMaxWidth = 88.dp
private val DynamicIslandEmbeddedGapSidePadding = 10.dp

data class DynamicIslandCutoutSpec(
    val embeddedGapWidth: Dp,
    val horizontalOffset: Dp,
)

@Composable
fun rememberDynamicIslandCutoutSpec(): DynamicIslandCutoutSpec {
    val density = LocalDensity.current
    val view = LocalView.current
    val displayCutout = view.rootWindowInsets?.displayCutout ?: view.display?.cutout
    val topCutout = displayCutout?.topBoundingRectOrNull()
    val rootWidthPx =
        when {
            view.rootView.width > 0 -> view.rootView.width
            view.width > 0 -> view.width
            else -> view.resources.configuration.windowConfiguration.maxBounds.width()
        }

    return with(density) {
        if (topCutout == null || rootWidthPx <= 0) {
            DynamicIslandCutoutSpec(
                embeddedGapWidth = 0.dp,
                horizontalOffset = 0.dp,
            )
        } else {
            val embeddedGapWidthDp =
                (topCutout.width().toDp() + (DynamicIslandEmbeddedGapSidePadding * 2))
                    .coerceIn(
                        DynamicIslandEmbeddedGapMinWidth,
                        DynamicIslandEmbeddedGapMaxWidth,
                    )
            val horizontalOffsetDp = (topCutout.exactCenterX() - (rootWidthPx / 2f)).toDp()
            DynamicIslandCutoutSpec(
                embeddedGapWidth = embeddedGapWidthDp,
                horizontalOffset = horizontalOffsetDp,
            )
        }
    }
}

private fun DisplayCutout.topBoundingRectOrNull() =
    getBoundingRectTop().takeUnless { it.isEmpty }

private fun PopupContentModel.isUtilityStatusContent(): Boolean {
    return this is PopupContentModel.ScreenRecord ||
        this is PopupContentModel.Stopwatch ||
        this is PopupContentModel.Alarm ||
        this is PopupContentModel.Flashlight ||
        this is PopupContentModel.Call ||
        this is PopupContentModel.RingerMode ||
        this is PopupContentModel.BluetoothAudio
}

private fun compactIslandWidthFor(content: PopupContentModel): Dp? {
    return when (content) {
        is PopupContentModel.Media -> CompactMediaIslandWidth
        is PopupContentModel.ScreenRecord ->
            when (content.model) {
                is ScreenRecordPopupModel.Starting -> CompactTimerIslandWidth
                is ScreenRecordPopupModel.Recording -> CompactRecordingIslandWidth
            }
        is PopupContentModel.Stopwatch -> CompactTimerIslandWidth
        is PopupContentModel.Alarm -> CompactAlarmIslandWidth
        is PopupContentModel.Flashlight -> CompactUtilityIslandWidth
        is PopupContentModel.Call -> 130.dp
        is PopupContentModel.RingerMode -> 116.dp
        is PopupContentModel.BluetoothAudio -> 138.dp
        else -> null
    }
}

@Composable
private fun rememberDynamicIslandSizeScale(): Pair<Float, Float> {
    val context = LocalContext.current
    val widthScale by
        remember { observeDynamicIslandScale(context, DynamicIslandFeatureSettings.WIDTH_SCALE) }
            .collectAsState(initial = 1f)
    val heightScale by
        remember { observeDynamicIslandScale(context, DynamicIslandFeatureSettings.HEIGHT_SCALE) }
            .collectAsState(initial = 1f)
    return widthScale to heightScale
}

private data class DynamicIslandCollapseState(
    val scaleX: Float,
    val scaleY: Float,
    val contentAlpha: Float,
)

@Composable
private fun rememberDynamicIslandCollapseState(isOpen: Boolean): DynamicIslandCollapseState {
    val scaleX = remember { Animatable(1f, visibilityThreshold = 0.0005f) }
    val scaleY = remember { Animatable(1f, visibilityThreshold = 0.0005f) }
    val currentIsOpen by rememberUpdatedState(isOpen)

    LaunchedEffect(Unit) {
        snapshotFlow { currentIsOpen }
            .drop(1)
            .collectLatest { open ->
                if (open) {
                    coroutineScope {
                        launch {
                            scaleX.animateTo(
                                targetValue = 0f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.72f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            )
                        }
                        launch {
                            scaleY.animateTo(
                                targetValue = 0f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.65f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            )
                        }
                    }
                } else {
                    coroutineScope {
                        launch {
                            scaleX.animateTo(
                                targetValue = 1f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.68f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            )
                        }
                        launch {
                            scaleY.animateTo(
                                targetValue = 1f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.65f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            )
                        }
                    }
                }
            }
    }
    val fadeThreshold = 0.7f
    val alpha = (scaleX.value / fadeThreshold).coerceIn(0f, 1f)
    return DynamicIslandCollapseState(
        scaleX = scaleX.value,
        scaleY = scaleY.value,
        contentAlpha = alpha,
    )
}

private fun LayoutCoordinates.boundsInScreen(view: android.view.View): Rect {
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    return boundsInRoot().translate(Offset(location[0].toFloat(), location[1].toFloat()))
}
