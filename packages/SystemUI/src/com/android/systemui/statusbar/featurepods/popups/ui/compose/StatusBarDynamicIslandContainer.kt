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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Phone-only centered dynamic island that pages through active popup chips. */
@Composable
fun StatusBarDynamicIslandContainer(
    chips: List<PopupChipModel.Shown>,
    onMediaControlPopupVisibilityChanged: (Boolean) -> Unit,
    onIslandBoundsChanged: (android.graphics.Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cutoutSpec = rememberDynamicIslandCutoutSpec()
    var selectedChipId by remember { mutableStateOf<PopupChipId?>(null) }
    var popupAnchorChip by remember { mutableStateOf<PopupChipModel.Shown?>(null) }
    var popupVisible by remember { mutableStateOf(false) }
    var knownChipIds by remember { mutableStateOf<List<PopupChipId>>(emptyList()) }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(chips) {
        val currentChipIds = chips.map { it.chipId }
        val newestChipId =
            if (knownChipIds.isEmpty()) {
                null
            } else {
                currentChipIds.lastOrNull { it !in knownChipIds }
            }
        selectedChipId =
            when {
                newestChipId != null -> newestChipId
                chips.any { it.chipId == PopupChipId.Call } -> PopupChipId.Call
                chips.any { it.chipId == PopupChipId.RingerMode } -> PopupChipId.RingerMode
                chips.any { it.chipId == selectedChipId } -> selectedChipId
                else -> chips.firstOrNull()?.chipId
            }
        knownChipIds = currentChipIds
    }

    val selectedIndex = chips.indexOfFirst { it.chipId == selectedChipId }.coerceAtLeast(0)
    val selectedChip = chips.getOrNull(selectedIndex)
    val shownChip = chips.firstOrNull { it.isPopupShown }

    LaunchedEffect(shownChip) {
        if (shownChip != null) {
            selectedChipId = shownChip.chipId
            popupAnchorChip = shownChip
            popupVisible = true
        } else if (popupAnchorChip != null) {
            popupVisible = false
            delay(220)
            popupAnchorChip = null
        }
    }

    LaunchedEffect(chips) {
        onMediaControlPopupVisibilityChanged(
            chips.any { it.chipId == PopupChipId.MediaControl && it.isPopupShown }
        )
    }

    fun selectRelative(direction: Int) {
        if (chips.size <= 1) return
        val newIndex = (selectedIndex + direction).mod(chips.size)
        val newChip = chips[newIndex]
        selectedChipId = newChip.chipId
        if (popupVisible) {
            newChip.showPopup()
        }
    }

    Box(
        modifier =
            modifier
                .padding(horizontal = 8.dp)
                .offset(x = cutoutSpec.horizontalOffset)
                .onGloballyPositioned { coordinates ->
                    val b = coordinates.boundsInWindow()
                    onIslandBoundsChanged(
                        android.graphics.Rect(
                            b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt(),
                        )
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selectedChip != null,
            enter =
                scaleIn(
                    initialScale = 0.35f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec =
                        spring(
                            dampingRatio = 0.68f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ) + fadeIn(animationSpec = tween(160)),
            exit =
                scaleOut(
                    targetScale = 0.35f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec =
                        spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ) + fadeOut(animationSpec = tween(140)),
            modifier = Modifier.clip(RoundedCornerShape(50)),
        ) {
            val currentChip = selectedChip ?: return@AnimatedVisibility

            AnimatedContent(
                targetState = currentChip.chipId,
                transitionSpec = {
                    val enterTransition =
                        fadeIn(animationSpec = tween(180, delayMillis = 40)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.72f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            )
                    val exitTransition =
                        fadeOut(animationSpec = tween(120)) +
                            scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(120),
                            )

                    (enterTransition togetherWith exitTransition).using(
                        SizeTransform(clip = true) { _, _ ->
                            spring(
                                dampingRatio = 0.74f,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        }
                    )
                },
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
                label = "dynamic_island_chip",
            ) { chipId ->
                val chip = chips.firstOrNull { it.chipId == chipId } ?: return@AnimatedContent
                var horizontalDragPx by remember(chipId, chips.size) { mutableFloatStateOf(0f) }
                val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

                StatusBarDynamicIslandChip(
                    viewModel = chip,
                    pageCount = chips.size,
                    cutoutSpec = cutoutSpec,
                    onChipBoundsChanged = { bounds -> anchorBounds = bounds },
                    modifier =
                        Modifier.pointerInput(chips.size, chip.chipId) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        horizontalDragPx <= -thresholdPx -> selectRelative(1)
                                        horizontalDragPx >= thresholdPx -> selectRelative(-1)
                                    }
                                    horizontalDragPx = 0f
                                },
                                onDragCancel = { horizontalDragPx = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    horizontalDragPx += dragAmount
                                    if (chips.size > 1 && abs(horizontalDragPx) > 8f) {
                                        change.consume()
                                    }
                                },
                            )
                        },
                    onTap = {
                        if (chip.isPopupShown) chip.hidePopup() else chip.showPopup()
                    },
                )
            }
        }

        popupAnchorChip?.let { anchoredChip ->
            StatusBarPopup(
                viewModel = anchoredChip,
                isVisible = popupVisible,
                chipBoundsInScreen = anchorBounds,
            )
        }
    }
}
