package io.github.thiemotorres.huru

import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Shimmer position: one sweep across the screen per cycle, shared by all blocks of a skeleton. */
@Composable
private fun rememberPulse(): State<Float> = rememberInfiniteTransition(label = "skeleton").animateFloat(
    0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "shimmer",
)

private val SHIMMER_BAND = 280.dp

@Composable
private fun Block(pulse: State<Float>, modifier: Modifier) {
    // Position in the window, so the band runs across all blocks as one light instead of per block.
    var left by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val screen = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val band = with(density) { SHIMMER_BAND.toPx() }
    Box(
        modifier
            .onGloballyPositioned { left = it.positionInWindow().x }
            .clip(RoundedCornerShape(6.dp))
            // Read in the draw phase only: the animation never recomposes the layout.
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.06f))
                val x = pulse.value * (screen + 2 * band) - band - left
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent),
                        startX = x - band / 2, endX = x + band / 2,
                    ),
                )
            },
    )
}

// Fixed widths so the skeleton looks like text, not a wall of equal bars.
private val CATEGORY_WIDTHS = listOf(0.5f, 0.8f, 0.65f, 0.9f, 0.55f, 0.75f, 0.6f, 0.85f)

@Composable
private fun CategoryColumn(header: @Composable () -> Unit, pulse: State<Float>) {
    Column(Modifier.width(260.dp)) {
        header()
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CATEGORY_WIDTHS.forEach { w ->
                Box(Modifier.height(48.dp).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Block(pulse, Modifier.fillMaxWidth(w).height(18.dp))
                }
            }
        }
    }
}

/** Live TV while the channel list loads: categories left, guide rows right. */
@Composable
fun LiveSkeleton(header: @Composable () -> Unit) {
    val pulse = rememberPulse()
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        CategoryColumn(header, pulse)
        Column(Modifier.weight(1f).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(8) { row ->
                Row(Modifier.fillMaxWidth().height(64.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Block(pulse, Modifier.size(40.dp))
                    Block(pulse, Modifier.padding(start = 12.dp).width(160.dp).height(18.dp))
                    Spacer(Modifier.width(36.dp))
                    // Programme blocks of different lengths, shifted per row.
                    val split = 0.3f + (row * 0.17f) % 0.4f
                    Block(pulse, Modifier.weight(split).fillMaxHeight().padding(horizontal = 1.dp))
                    Block(pulse, Modifier.weight(1f - split).fillMaxHeight().padding(horizontal = 1.dp))
                }
            }
        }
    }
}

/** Movies/series while the lists load: categories left, poster grid right. */
@Composable
fun GridSkeleton(header: @Composable () -> Unit) {
    val pulse = rememberPulse()
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        CategoryColumn(header, pulse)
        // Same grid as the real one, so posters land where they will appear.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            userScrollEnabled = false,
        ) {
            items(12) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Block(pulse, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    Block(pulse, Modifier.fillMaxWidth(0.7f).height(14.dp))
                }
            }
        }
    }
}

/** List rows (title and a shorter second line) for episodes and side panels. */
@Composable
fun ListSkeleton(rows: Int = 5) {
    val pulse = rememberPulse()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { i ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(pulse, Modifier.fillMaxWidth(CATEGORY_WIDTHS[i % CATEGORY_WIDTHS.size]).height(18.dp))
                Block(pulse, Modifier.fillMaxWidth(0.3f).height(12.dp))
            }
        }
    }
}
