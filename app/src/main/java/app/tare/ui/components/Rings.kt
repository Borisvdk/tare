package app.tare.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tare.ui.theme.Tare

private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f

/**
 * The hero calorie gauge: a 270° dial open at the bottom, like a kitchen scale. Fills with
 * coral as you eat; past the target the overflow is drawn in the "over" colour on top.
 */
@Composable
fun CalorieGauge(
    eaten: Double,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp,
    stroke: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Tare.colors
    val fraction = if (target <= 0) 0f else (eaten / target).toFloat().coerceAtLeast(0f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "gauge",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            drawArcStroke(colors.track, GAUGE_START, GAUGE_SWEEP, strokePx)
            val main = animated.coerceAtMost(1f)
            if (main > 0.001f) drawArcStroke(colors.kcal, GAUGE_START, GAUGE_SWEEP * main, strokePx)
            if (animated > 1f) {
                val over = (animated - 1f).coerceAtMost(1f)
                drawArcStroke(colors.over, GAUGE_START, GAUGE_SWEEP * over, strokePx)
            }
            // Zero tick at the start of the dial, a nod to the app icon.
            if (main <= 0.001f) drawArcStroke(colors.textFaint, GAUGE_START, 0.5f, strokePx)
        }
        content()
    }
}

/** Small progress ring for the week strip and list rows. */
@Composable
fun MiniRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    stroke: Dp = 3.dp,
    color: Color = Tare.colors.kcal,
    track: Color = Tare.colors.track,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val over = Tare.colors.over
    val animated by animateFloatAsState(fraction.coerceAtLeast(0f), spring(stiffness = Spring.StiffnessLow), label = "mini")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            drawArcStroke(track, -90f, 360f, strokePx)
            if (animated > 0.001f) drawArcStroke(color, -90f, 360f * animated.coerceAtMost(1f), strokePx)
            if (animated > 1.05f) drawArcStroke(over, -90f, 360f * (animated - 1f).coerceAtMost(1f), strokePx)
        }
        content()
    }
}

private fun DrawScope.drawArcStroke(color: Color, start: Float, sweep: Float, strokePx: Float) {
    val inset = strokePx / 2f
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokePx, size.height - strokePx),
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )
}
