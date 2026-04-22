package com.android.systemui.axdynamicbar.ui.compose

import androidx.core.graphics.ColorUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.drawable.Drawable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.AlphaIconBg
import com.android.systemui.axdynamicbar.shared.AlphaSecondary
import com.android.systemui.axdynamicbar.shared.AlphaTertiary
import com.android.systemui.axdynamicbar.shared.PillPrimary
import com.android.systemui.axdynamicbar.shared.ShapeXl
import com.android.systemui.axdynamicbar.shared.ShapeXs
import com.android.systemui.axdynamicbar.shared.SizeBadge
import com.android.systemui.axdynamicbar.shared.SpaceXs
import com.android.systemui.axdynamicbar.shared.TsBadge
import com.android.systemui.axdynamicbar.shared.chipAccentColorFor
import com.android.systemui.axdynamicbar.shared.chipContentColorOn
import com.android.systemui.axdynamicbar.shared.chipProgressFor
import com.android.systemui.axdynamicbar.shared.darkenColor
import com.android.systemui.axdynamicbar.shared.iconKeyFor
import com.android.systemui.axdynamicbar.shared.rememberIslandColors
import com.android.systemui.axdynamicbar.shared.rememberMediaColors
import com.android.systemui.axdynamicbar.shared.textKeyFor
import com.android.systemui.axdynamicbar.shared.toScaledBitmap
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.res.R
import kotlin.math.abs

private val ChipShape = ShapeXl
private val ChipHeight = 24.dp
private val StatusBarChipPrimaryStyle: TextStyle
    @Composable get() = PillPrimary.copy(fontSize = 10.5.sp)
private val StatusBarChipBadgeStyle: TextStyle
    @Composable get() = TsBadge.copy(fontSize = 8.5.sp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxDynamicBarChip(
    viewModel: AxDynamicBarChipViewModel,
    modifier: Modifier = Modifier,
    ignoreKeyguard: Boolean = false,
) {
    val state by viewModel.chipState.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isExpanded.collectAsStateWithLifecycle()
    val isOnKeyguard by viewModel.isOnKeyguard.collectAsStateWithLifecycle()
    val keyguardCarrier by viewModel.keyguardCarrierText.collectAsStateWithLifecycle()
    val chipState = state
    val displayEvent = chipState?.let { it.notificationAlert ?: it.event }
    val hideCompactMediaChip = isExpanded && displayEvent is IslandEvent.Media

    val carrierName = if (isOnKeyguard && ignoreKeyguard) keyguardCarrier.takeIf { it.isNotBlank() } else null
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val hapticFeedback = LocalHapticFeedback.current

    val motionScheme = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = state != null && (ignoreKeyguard || !isOnKeyguard) && !hideCompactMediaChip,
        enter = fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(initialScale = 0.8f, animationSpec = motionScheme.defaultSpatialSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(targetScale = 0.8f, animationSpec = motionScheme.fastSpatialSpec()),
        modifier = modifier
            .padding(start = 4.dp, end = 2.dp)
            .widthIn(min = 25.dp, max = 90.dp)
            .pointerInput(viewModel) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    
                    val startX = down.position.x
                    val startY = down.position.y
                    var dragging = false
                    var totalDx = 0f
                    var decided = false 
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            
                            if (dragging) {
                                change.consume()
                                if (totalDx > 0) viewModel.cyclePrev()
                                else viewModel.cycleNext()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!decided) {
                                
                                change.consume()
                                viewModel.togglePanel()
                            }
                            
                            break
                        }
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (!decided && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            if (abs(dx) >= abs(dy)) {
                                
                                decided = true
                                dragging = true
                                totalDx = dx
                                change.consume()
                            } else {
                                
                                decided = true
                                break
                            }
                        } else if (dragging) {
                            totalDx = dx
                            change.consume()
                        }
                    }
                }
            }
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val centerX = (bounds.left + bounds.right) / 2f
                if (screenWidthPx > 0f) {
                    viewModel.updateChipCenterX(centerX / screenWidthPx)
                }
            },
    ) {
        state?.let { chipState ->
            val displayEvent = chipState.notificationAlert ?: chipState.event
            val isAlert = chipState.notificationAlert != null

            AnimatedContent(
                targetState = ChipDisplay(displayEvent, isAlert),
                transitionSpec = {
                    (fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(initialScale = 0.92f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
                        (fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(targetScale = 0.92f, animationSpec = motionScheme.fastSpatialSpec())) using
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() })
                },
                contentKey = { if (it.isAlert) "alert" else it.event::class.simpleName },
                label = "chip_event",
            ) { display ->
                val rawAccent = chipAccentColorFor(display.event)
                val accent by animateColorAsState(rawAccent, MaterialTheme.motionScheme.fastEffectsSpec(), label = "accent")
                val contentColor by animateColorAsState(
                    chipContentColorOn(rawAccent), MaterialTheme.motionScheme.fastEffectsSpec(), label = "content",
                )
                val chipColors =
                    when (display.event) {
                        is IslandEvent.Media -> rememberMediaColors(display.event)
                        else -> rememberIslandColors(display.event)
                    }
                val isMediaEvent = display.event is IslandEvent.Media
                val statusChipBrush =
                    if (isMediaEvent) {
                        null
                    } else {
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to lerp(chipColors.accent, Color(0xFF17131D), 0.82f).copy(alpha = 0.98f),
                                0.45f to lerp(chipColors.surfaceTint, Color(0xFF201725), 0.74f).copy(alpha = 0.96f),
                                1.0f to Color(0xFF16131A).copy(alpha = 0.94f),
                            )
                        )
                    }
                val chipBorderColor =
                    if (isMediaEvent) {
                        Color.Transparent
                    } else {
                        lerp(chipColors.accent, Color.White, 0.20f).copy(alpha = 0.16f)
                    }
                val resolvedContentColor =
                    if (isMediaEvent) {
                        contentColor
                    } else {
                        val darkSurface = darkenColor(lerp(chipColors.accent, Color(0xFF18141D), 0.78f), keep = 1f)
                        if (ColorUtils.calculateLuminance(darkSurface.toArgb()) > 0.42f) {
                            Color(0xFF161118)
                        } else {
                            Color.White
                        }
                    }
                val badgeBg =
                    if (isMediaEvent) {
                        lerp(accent, contentColor, 0.3f)
                    } else {
                        lerp(chipColors.accent, Color(0xFF18141C), 0.64f).copy(alpha = 0.96f)
                    }
                val badgeBorder =
                    if (isMediaEvent) {
                        Color.Transparent
                    } else {
                        lerp(chipColors.accent, Color.White, 0.20f).copy(alpha = 0.14f)
                    }
                val rawProgress = chipProgressFor(display.event)
                val progressTarget = rawProgress ?: 0f
                val progressAnim = remember { Animatable(progressTarget) }
                LaunchedEffect(progressTarget) {
                    if (abs(progressTarget - progressAnim.value) > 0.05f) {
                        progressAnim.animateTo(progressTarget, tween(300, easing = FastOutSlowInEasing))
                    } else {
                        progressAnim.snapTo(progressTarget)
                    }
                }
                val progress = if (rawProgress != null) progressAnim.value else null

                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier =
                            Modifier.height(ChipHeight)
                                .clip(ChipShape)
                                .then(
                                    if (statusChipBrush != null) {
                                        Modifier
                                            .background(statusChipBrush, ChipShape)
                                            .border(1.dp, chipBorderColor, ChipShape)
                                    } else {
                                        Modifier.background(accent)
                                    }
                                )
                                .animateContentSize(motionScheme.defaultSpatialSpec())
                                .then(
                                    if (progress != null) {
                                        val trackColor =
                                            if (isMediaEvent) lerp(accent, contentColor, 0.2f)
                                            else badgeBg.copy(alpha = 0.72f)
                                        val fillColor =
                                            if (isMediaEvent) lerp(accent, contentColor, 0.6f)
                                            else lerp(chipColors.accent, Color.White, 0.12f).copy(alpha = 0.92f)
                                        Modifier.drawWithContent {
                                            drawContent()
                                            val barH = 2.dp.toPx()
                                            val y = size.height - barH
                                            drawRect(
                                                trackColor,
                                                topLeft = Offset(0f, y),
                                                size = Size(size.width, barH),
                                            )
                                            drawRect(
                                                fillColor,
                                                topLeft = Offset(0f, y),
                                                size = Size(size.width * progress, barH),
                                            )
                                        }
                                    } else Modifier
                                )
                                .padding(start = 5.dp, end = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (carrierName != null) {
                            Text(
                                text = carrierName,
                                style = MaterialTheme.typography.labelSmall,
                                color = resolvedContentColor.copy(alpha = AlphaSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 56.dp),
                            )
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = resolvedContentColor.copy(alpha = AlphaTertiary),
                            )
                        }
                        if (display.isAlert && display.event is IslandEvent.Notification) {
                            AnimatedContent(
                                targetState = display.event,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                                        .using(sizeTransform = null)
                                },
                                contentKey = {
                                    (it as? IslandEvent.Notification)?.sbn?.key
                                },
                                label = "alert_content",
                            ) { event ->
                                val notif = event as IslandEvent.Notification
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    notif.appIcon?.let { icon ->
                                        Image(
                                            bitmap = icon.toScaledBitmap(16.dp),
                                            contentDescription = null,
                                            modifier =
                                                Modifier.size(16.dp)
                                                    .clip(ShapeXs),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(Modifier.width(SpaceXs))
                                    }
                                    Text(
                                        text = notif.appName ?: "",
                                        style = StatusBarChipPrimaryStyle,
                                        color = resolvedContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 78.dp).basicMarquee(iterations = 1),
                                    )
                                }
                            }
                        } else if (display.event is IslandEvent.Sports && (display.event as IslandEvent.Sports).team2Name.isNotEmpty()) {
                            val sport = display.event as IslandEvent.Sports
                            StatusBarSportsTeamBadge(sport.team1Name, sport.team1Icon, resolvedContentColor)
                            Spacer(Modifier.width(SpaceXs))
                            Text(
                                if (sport.score1.isNotEmpty()) "${sport.score1} - ${sport.score2}"
                                    else stringResource(R.string.ax_dynamic_bar_sports_vs),
                                style = StatusBarChipPrimaryStyle,
                                color = resolvedContentColor,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(SpaceXs))
                            StatusBarSportsTeamBadge(sport.team2Name, sport.team2Icon, resolvedContentColor)
                        } else {
                            AnimatedContent(
                                targetState = display.event,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                                        scaleIn(initialScale = 0.85f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
                                        (fadeOut(motionScheme.fastEffectsSpec()) +
                                            scaleOut(targetScale = 0.85f, animationSpec = motionScheme.fastSpatialSpec())) using
                                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() })
                                },
                                contentKey = { iconKeyFor(it) },
                                label = "chip_icon",
                            ) { event ->
                                PillEventIcon(event, tint = resolvedContentColor)
                            }
                            Spacer(Modifier.width(3.dp))
                            AnimatedContent(
                                targetState = display.event,
                                transitionSpec = {
                                    (fadeIn(motionScheme.defaultEffectsSpec()) +
                                        scaleIn(initialScale = 0.85f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
                                        (fadeOut(motionScheme.fastEffectsSpec()) +
                                            scaleOut(targetScale = 0.85f, animationSpec = motionScheme.fastSpatialSpec())) using
                                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() })
                                },
                                contentKey = { textKeyFor(it) },
                                label = "chip_text",
                                modifier = Modifier.weight(1f, fill = false),
                            ) { event ->
                                PillEventText(
                                    event,
                                    Modifier.widthIn(max = 84.dp),
                                    overrideColor = resolvedContentColor,
                                )
                            }
                            if (chipState.eventCount > 1) {
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(SizeBadge + 1.dp)
                                        .widthIn(min = SizeBadge + 1.dp)
                                        .background(
                                            badgeBg,
                                            RoundedCornerShape(SizeBadge / 2),
                                        )
                                        .border(1.dp, badgeBorder, RoundedCornerShape(SizeBadge / 2))
                                        .padding(horizontal = 3.dp),
                                ) {
                                    Text(
                                        text = "${chipState.eventCount}",
                                        style = StatusBarChipBadgeStyle,
                                        color = resolvedContentColor,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarSportsTeamBadge(name: String, icon: Drawable?, contentColor: Color) {
    val badgeSize = 16.dp
    if (icon != null) {
        Image(
            bitmap = icon.toScaledBitmap(badgeSize),
            contentDescription = name,
            modifier = Modifier.size(badgeSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(badgeSize).clip(CircleShape)
                .background(contentColor.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                style = TsBadge,
                color = contentColor,
            )
        }
    }
}

private data class ChipDisplay(val event: IslandEvent, val isAlert: Boolean)
