package com.tis.ibkr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Tiny inline trend line for list rows (Longbridge-style). Draws a polyline over
 * [points] (e.g. the day's intraday prices), colored by [color]. An optional
 * [baseline] (e.g. previous close) is drawn as a faint dashed horizontal line.
 *
 * No axes, labels, or interaction — purely decorative density. Renders nothing
 * when there are fewer than two points (caller shows blank space).
 */
@Composable
fun Sparkline(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    baseline: Double? = null,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val min = points.minOrNull()!!
        val max = points.maxOrNull()!!
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val padV = h * 0.14f
        fun y(v: Double): Float = padV + (1f - ((v - min) / range).toFloat()) * (h - 2 * padV)
        fun x(i: Int): Float = if (points.size == 1) 0f else w * i / (points.size - 1)

        baseline?.takeIf { it in min..max }?.let { b ->
            val by = y(b)
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, by),
                end = Offset(w, by),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }

        val path = Path().apply {
            moveTo(x(0), y(points[0]))
            for (i in 1 until points.size) lineTo(x(i), y(points[i]))
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
