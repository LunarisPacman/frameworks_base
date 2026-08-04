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

import android.graphics.drawable.Drawable
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- constants ----------

/** Number of visualizer bars. */
private const val BAR_COUNT = 4

/**
 * Target resting levels for each bar when paused.
 * Slightly staggered heights give the OxygenOS Fluid-bar look.
 * With 4 bars: left-inner and right-inner are taller than the outer ones.
 */
private val PausedRestLevels = floatArrayOf(0.20f, 0.35f, 0.35f, 0.20f)

/**
 * Spring for audio-reactive bounce.
 * Low dampingRatio = visible overshoot on strong beats.
 * High stiffness = snappy upward response to fast transients.
 */
private val BeatSpring = spring<Float>(
    dampingRatio = 0.50f,
    stiffness = 280f,
)

/**
 * Softer spring used when settling from playing → paused, so bars
 * ease down gently rather than snapping to the resting position.
 */
private val SettleSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 140f,
)

/**
 * Idle breathing config per bar (4-bar symmetric layout).
 * Inner bars breathe more, outer bars are subtler.
 */
private data class IdleConfig(
    val minLevel: Float,
    val maxLevel: Float,
    val durationMs: Int,
    val phaseOffsetMs: Int,
)

private val IdleConfigs = arrayOf(
    IdleConfig(minLevel = 0.14f, maxLevel = 0.26f, durationMs = 2600, phaseOffsetMs = 300),
    IdleConfig(minLevel = 0.22f, maxLevel = 0.42f, durationMs = 2200, phaseOffsetMs = 0),
    IdleConfig(minLevel = 0.22f, maxLevel = 0.42f, durationMs = 2200, phaseOffsetMs = 150),
    IdleConfig(minLevel = 0.14f, maxLevel = 0.26f, durationMs = 2600, phaseOffsetMs = 450),
)

// ---------- composable ----------

/**
 * Renders 4 audio-reactive pill bars that bounce to the current audio output.
 * Bar tips receive a subtle tint extracted from [artworkDrawable] so they
 * harmonise with the album art currently shown on the chip.
 *
 * @param isPlaying   Whether media is actively playing (drives Visualizer).
 * @param color       Base bar color (typically chipContentColor — white on dark chip).
 * @param artworkDrawable Optional album-art drawable used to extract an accent tint.
 */
@Composable
fun AudioReactiveBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    artworkDrawable: Drawable? = null,
    barWidth: Dp = 2.5.dp,
    maxBarHeight: Dp = 13.dp,
    spacing: Dp = 1.6.dp,
    startPadding: Dp = 0.dp,
) {
    // ---------- Album-art accent color ----------
    // Extracted asynchronously from the artwork; falls back to `color` if unavailable.
    var rawAccentColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artworkDrawable) {
        rawAccentColor = null   // reset on artwork change
        if (artworkDrawable == null) return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) {
            runCatching {
                // Scale bitmap down to speed up palette extraction
                val bmp = artworkDrawable.toBitmap(width = 64, height = 64)
                val palette = Palette.from(bmp).generate()
                // Prefer vibrant; fall back through muted → dominant
                palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
            }.getOrNull()
        }
        rawAccentColor = extracted?.let { Color(it) }
    }

    // Smoothly animate the accent from null/previous → new palette color.
    // We blend 30% accent into the base chip color so it stays readable
    // on the tiny chip without clashing.
    val accentColor by animateColorAsState(
        targetValue = rawAccentColor?.let { accent ->
            // Lerp: 30% accent, 70% base color for a subtle tint
            Color(
                red   = color.red   * 0.70f + accent.red   * 0.30f,
                green = color.green * 0.70f + accent.green * 0.30f,
                blue  = color.blue  * 0.70f + accent.blue  * 0.30f,
                alpha = color.alpha,
            )
        } ?: color,
        animationSpec = tween(durationMillis = 600),
        label = "visualizer_accent",
    )

    // ---------- State ----------
    val rawLevels = remember {
        mutableStateListOf(*PausedRestLevels.toTypedArray())
    }

    val animatedLevels: List<Animatable<Float, AnimationVector1D>> = remember {
        List(BAR_COUNT) { i -> Animatable(PausedRestLevels[i]) }
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // ---------- Visualizer lifecycle ----------
    DisposableEffect(isPlaying) {
        if (!isPlaying) {
            PausedRestLevels.forEachIndexed { i, v -> rawLevels[i] = v }
            onDispose {}
        } else {
            var visualizer: Visualizer? = null
            val listener = object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int,
                ) {
                    val safe = waveform ?: return
                    if (safe.isEmpty()) return

                    // Split waveform into BAR_COUNT windows.
                    // Map left half → bars 0,1  and  mirror right half → bars 3,2
                    // so the layout is symmetric (outer=low-freq, inner=high-freq).
                    val half = safe.size / 2
                    val windowSize = half / (BAR_COUNT / 2)
                    val leftBands = FloatArray(BAR_COUNT / 2) { idx ->
                        val s = idx * windowSize
                        val e = if (idx == BAR_COUNT / 2 - 1) half else s + windowSize
                        safe.windowEnergy(s, e)
                    }
                    // Mirror symmetrically: [L0, L1, L1, L0]
                    val mirrored = floatArrayOf(
                        leftBands[0],
                        leftBands[1],
                        leftBands[1],
                        leftBands[0],
                    )

                    mainHandler.post {
                        for (i in mirrored.indices) {
                            rawLevels[i] = (rawLevels[i] * 0.55f + mirrored[i] * 0.45f)
                                .coerceIn(0.08f, 1f)
                        }
                    }
                }

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int,
                ) = Unit
            }

            runCatching {
                visualizer = Visualizer(0).apply {
                    val captureSizes = Visualizer.getCaptureSizeRange()
                    captureSize = captureSizes[1].coerceAtMost(256)
                    setDataCaptureListener(
                        listener,
                        Visualizer.getMaxCaptureRate() / 2,
                        /* waveform = */ true,
                        /* fft = */ false,
                    )
                    enabled = true
                }
            }.onFailure {
                PausedRestLevels.forEachIndexed { i, v -> rawLevels[i] = v }
            }

            onDispose {
                runCatching { visualizer?.enabled = false }
                runCatching { visualizer?.release() }
            }
        }
    }

    // ---------- Spring animation: track rawLevels ----------
    LaunchedEffect(Unit) {
        snapshotFlow { rawLevels.toList() to isPlaying }
            .distinctUntilChanged()
            .collect { (targets, playing) ->
                val spec = if (playing) BeatSpring else SettleSpring
                coroutineScope {
                    targets.forEachIndexed { index, target ->
                        launch {
                            animatedLevels[index].animateTo(target, animationSpec = spec)
                        }
                    }
                }
            }
    }

    // ---------- Idle breathing ----------
    val idleTransition = rememberInfiniteTransition(label = "visualizer_idle")
    val idleFloats = Array(BAR_COUNT) { index ->
        val cfg = IdleConfigs[index]
        val start = if (index % 2 == 0) cfg.minLevel else cfg.maxLevel
        val end   = if (index % 2 == 0) cfg.maxLevel else cfg.minLevel
        idleTransition.animateFloat(
            initialValue = start,
            targetValue = end,
            animationSpec = InfiniteRepeatableSpec(
                animation = tween(
                    durationMillis = cfg.durationMs,
                    delayMillis = cfg.phaseOffsetMs,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "idle_$index",
        )
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) return@LaunchedEffect
        while (true) {
            for (i in 0 until BAR_COUNT) {
                rawLevels[i] = idleFloats[i].value
            }
            kotlinx.coroutines.delay(32L)
        }
    }

    // ---------- Canvas rendering ----------
    val totalWidth = barWidth * BAR_COUNT + spacing * (BAR_COUNT - 1)

    Canvas(
        modifier = modifier
            .padding(start = startPadding)
            .size(width = totalWidth, height = maxBarHeight),
    ) {
        val barPx     = barWidth.toPx()
        val spacingPx = spacing.toPx()
        val maxH      = size.height
        val radius    = CornerRadius(barPx / 2f, barPx / 2f)

        for (index in 0 until BAR_COUNT) {
            val springLevel = animatedLevels[index].value
            val idleLevel   = idleFloats[index].value

            val level = (if (isPlaying) springLevel else idleLevel).coerceIn(0.08f, 1f)

            val barH = maxH * level
            val left = index * (barPx + spacingPx)
            val top  = maxH - barH

            // Gradient:
            //  root (bottom) → base color at low opacity
            //  tip  (top)    → accentColor at full opacity (album-art tinted)
            // The accent is most visible on tall bars (loud beats) and fades to
            // the base color on short bars, keeping it tasteful at low volumes.
            val peakAlpha  = (0.65f + level * 0.35f).coerceIn(0.65f, 1.00f)
            val rootAlpha  = (0.22f + level * 0.18f).coerceIn(0.22f, 0.40f)

            val brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    // top of bar (peak) — accent tinted
                    0f to accentColor.copy(alpha = peakAlpha * accentColor.alpha),
                    // bottom of bar (root) — base color, more translucent
                    1f to color.copy(alpha = rootAlpha * color.alpha),
                ),
                startY = top,   // top of bar (peak)
                endY   = maxH,  // bottom of bar (root)
            )

            drawRoundRect(
                brush        = brush,
                topLeft      = Offset(left, top),
                size         = Size(barPx, barH),
                cornerRadius = radius,
            )
        }
    }
}

// ---------- helpers ----------

/**
 * RMS energy of a waveform window, squash-curved for perceptual response.
 * Returns [0.08, 1.0].
 */
private fun ByteArray.windowEnergy(start: Int, end: Int): Float {
    if (start >= end || start < 0 || end > size) return 0.12f
    var sum = 0f
    var count = 0
    for (i in start until end) {
        // Visualizer waveform data is 8-bit unsigned PCM where 128 is silence.
        // In Kotlin, ByteArray is signed, so unsigned 128 becomes signed -128.
        val unsigned = this[i].toInt() and 0xFF
        val amplitude = unsigned - 128
        val n = abs(amplitude) / 128f
        sum += n * n
        count++
    }
    if (count == 0) return 0.12f
    return sqrt(sqrt(sum / count)).coerceIn(0.08f, 1f)
}
