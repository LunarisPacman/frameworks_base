package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * A minimalist 3-bar vertical audio waveform visualizer.
 * The bars animate randomly when [isPlaying] is true to simulate audio activity.
 */
@Composable
fun AudioWaveform(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    isPlaying: Boolean = true,
) {
    val barCount = 3
    
    Row(
        modifier = modifier.height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            // Use a per-bar target that updates at different intervals
            var heightFactor by remember { mutableFloatStateOf(if (isPlaying) 0.5f else 0.2f) }
            
            val animatedHeight by animateFloatAsState(
                targetValue = if (isPlaying) heightFactor else 0.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "waveform_bar_$index"
            )

            if (isPlaying) {
                LaunchedEffect(isPlaying) {
                    // Unique offset for each bar to prevent synchronization
                    delay(index * 120L)
                    while (true) {
                        heightFactor = 0.3f + (Random.nextFloat() * 0.7f)
                        delay(200L + Random.nextLong(0, 100))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(animatedHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = if (isPlaying) 0.9f else 0.4f))
            )
        }
    }
}
