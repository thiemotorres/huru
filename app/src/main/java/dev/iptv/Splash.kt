package dev.iptv

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// Brand colours (branding/BRAND.md)
val Plum = Color(0xFF3B1F3A)
val Coral = Color(0xFFFF6B4A)
val CoralDark = Color(0xFFE5502F)
val Mustard = Color(0xFFFFC23D)
val Cream = Color(0xFFFFF4E6)

// Geometry as in branding/icon.svg (256 grid, screen at 40..216 × 64..192)
private val SCREEN = PathParser().parsePathString(
    "M68 64H188A28 28 0 0 1 216 92V164A28 28 0 0 1 188 192H68A28 28 0 0 1 40 164V92A28 28 0 0 1 68 64Z"
).toPath()
private val GROUND = PathParser().parsePathString(
    "M40 168H216V164A28 28 0 0 1 188 192H68A28 28 0 0 1 40 164Z"
).toPath()
private val GULL = PathParser().parsePathString(
    "M-62 8C-46 -20 -20 -24 0 4C20 -24 46 -20 62 8C42 -4 20 -2 0 20C-20 -2 -42 -4 -62 8Z"
).toPath()

/** Splash: the sun rises, the gull flaps into place, then [onDone]. */
@Composable
fun Splash(onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { delay(500); word.animateTo(1f, tween(500)) }
        delay(250)
        fly.animateTo(1f, tween(1300, easing = LinearOutSlowInEasing))
        delay(400)
        onDone()
    }
    val flap by rememberInfiniteTransition(label = "flap").animateFloat(
        1f, 0.4f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "flap",
    )

    Box(Modifier.fillMaxSize().background(Plum), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Canvas(Modifier.size(264.dp, 192.dp)) {
                scene(rise.value, fly.value, flap)
            }
            Image(painterResource(R.drawable.wordmark), contentDescription = "Huru", Modifier.height(56.dp).alpha(word.value))
        }
    }
}

private fun DrawScope.scene(rise: Float, fly: Float, flap: Float) {
    val s = size.width / 176f
    withTransform({ scale(s, s, Offset.Zero); translate(-40f, -64f) }) {
        clipPath(SCREEN) {
            drawPath(SCREEN, Coral)
            // The sun rises from below the horizon to its place (base y=168)
            val base = 168f + (1 - rise) * 56f
            drawArc(Mustard, 180f, 180f, useCenter = true, topLeft = Offset(78f, base - 50f), size = Size(100f, 100f))
            drawPath(GROUND, CoralDark)
            if (fly > 0f) {
                // Fly in from bottom left in a gentle arc to the resting position (176, 100)
                val x = 10f + (176f - 10f) * fly
                val y = 150f + (100f - 150f) * fly - sin(fly * PI).toFloat() * 24f
                val beat = flap + (1f - flap) * fly * fly * fly // wing beat settles at the end
                translate(x, y) {
                    withTransform({ rotate(-10f, Offset.Zero); scale(0.42f, 0.42f * beat, Offset.Zero) }) {
                        drawPath(GULL, Cream)
                    }
                }
            }
        }
    }
}
