package com.tis.ibkr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.tis.ibkr.data.api.Bar
import com.tis.ibkr.ui.theme.LbColors
import kotlin.math.max

/**
 * MACD sub-chart driven by the same [ChartLink] as the main K-line so the crosshair stays
 * in sync. Long-pressing here also moves the crosshair on the parent K-line.
 */
@Composable
fun MacdSubChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    link: ChartLink? = null,
) {
    val density = LocalDensity.current
    val labelPx = with(density) { 9.sp.toPx() }
    val upColor = LbColors.Up
    val downColor = LbColors.Down
    val mutedColor = LbColors.OnSurfaceMuted
    val gridLineColor = LbColors.Outline.copy(alpha = 0.5f)
    val bgColor = LbColors.Background
    val onSurface = LbColors.OnSurface
    val difColor = Color(0xFFE5E7EB)
    val deaColor = Color(0xFFF6B26B)

    val macdData = remember(bars) { macd(bars.map { it.close }) }

    BoxWithConstraints(modifier = modifier.background(bgColor)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val priceLabelW = labelPx * 5.0f
        val plotLeft = 0f
        val plotRight = widthPx - priceLabelW
        val plotW = (plotRight - plotLeft).coerceAtLeast(1f)

        val viewStart = (link?.viewStart ?: 0).coerceIn(0, bars.size.coerceAtLeast(1) - 1)
        val viewEnd = (link?.viewEnd ?: (bars.size - 1)).coerceIn(viewStart, bars.size.coerceAtLeast(1) - 1)
        val viewN = (viewEnd - viewStart + 1).coerceAtLeast(1)
        val xStep = (plotW / viewN).coerceAtLeast(0.5f)

        fun pickIndex(touchX: Float): Int? {
            if (bars.isEmpty()) return null
            val local = ((touchX - plotLeft) / xStep).toInt().coerceIn(0, viewN - 1)
            return (viewStart + local).coerceIn(0, bars.size - 1)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bars, viewStart, viewEnd) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos -> link?.crosshairIndex = pickIndex(pos.x) },
                        onDragEnd = { link?.crosshairIndex = null },
                        onDragCancel = { link?.crosshairIndex = null },
                        onDrag = { change, _ -> link?.crosshairIndex = pickIndex(change.position.x) },
                    )
                }
                .pointerInput(bars) {
                    detectTapGestures(onTap = { link?.crosshairIndex = null })
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val barW = (xStep * 0.7f).coerceIn(0.5f, 14f)

            val dif = macdData.dif
            val dea = macdData.dea
            val hist = macdData.hist

            // Compute abs-max over the VISIBLE slice so y scaling matches what's shown.
            val visStart = viewStart
            val visEnd = viewEnd
            var absMax = 1e-6
            for (i in visStart..visEnd) {
                absMax = max(absMax, kotlin.math.abs(dif[i]))
                absMax = max(absMax, kotlin.math.abs(dea[i]))
                absMax = max(absMax, kotlin.math.abs(hist[i]))
            }

            val cy = size.height / 2f
            fun yOf(v: Double): Float = (cy - (v / absMax) * (size.height / 2 * 0.85)).toFloat()
            fun xOfLocal(localIdx: Int): Float = plotLeft + localIdx * xStep + xStep / 2
            fun xOfGlobal(globalIdx: Int): Float = xOfLocal(globalIdx - viewStart)

            // Zero line
            drawLine(gridLineColor, Offset(plotLeft, cy), Offset(plotRight, cy), strokeWidth = 0.5f)

            // Histogram
            for (localI in 0 until viewN) {
                val globalI = viewStart + localI
                val v = hist[globalI]
                val color = if (v >= 0) upColor else downColor
                val x = xOfLocal(localI)
                val y = yOf(v)
                val topY = if (v >= 0) y else cy
                val botY = if (v >= 0) cy else y
                drawRect(
                    color.copy(alpha = 0.7f),
                    Offset(x - barW / 2, topY),
                    Size(barW, (botY - topY).coerceAtLeast(0.5f)),
                )
            }

            // DIF + DEA lines
            fun drawLineSeries(arr: DoubleArray, color: Color) {
                val path = Path()
                for (localI in 0 until viewN) {
                    val globalI = viewStart + localI
                    val x = xOfLocal(localI); val y = yOf(arr[globalI])
                    if (localI == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 1.1f, cap = StrokeCap.Round))
            }
            drawLineSeries(dif, difColor)
            drawLineSeries(dea, deaColor)

            // Top label: live values (under crosshair if active, else last)
            val displayIdx = link?.crosshairIndex ?: (bars.size - 1)
            val labelPaint = makeLabelPaint(mutedColor, labelPx)
            drawContext.canvas.nativeCanvas.drawText(
                "MACD(12,26,9)  DIF %.3f  DEA %.3f  M %.3f".format(
                    dif[displayIdx], dea[displayIdx], hist[displayIdx],
                ),
                4f, labelPx * 1.2f, labelPaint,
            )

            // Crosshair vertical (synced with main K-line)
            val xhIdx = link?.crosshairIndex
            if (xhIdx != null && xhIdx in viewStart..viewEnd) {
                drawLine(
                    onSurface.copy(alpha = 0.5f),
                    Offset(xOfGlobal(xhIdx), 0f),
                    Offset(xOfGlobal(xhIdx), size.height),
                    strokeWidth = 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
            }
        }
    }
}
