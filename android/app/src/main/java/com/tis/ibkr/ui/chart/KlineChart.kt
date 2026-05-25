package com.tis.ibkr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.tis.ibkr.data.api.Bar
import com.tis.ibkr.ui.theme.LbColors
import kotlin.math.max
import kotlin.math.min

private val MA_COLORS = listOf(
    Color(0xFFF6B26B), // MA5 — orange
    Color(0xFF7AB7FF), // MA10 — blue
    Color(0xFFD18EFF), // MA20 — purple
)

/**
 * Coordinates shared crosshair index + visible-bar window between sibling charts (main K-line
 * + MACD sub-chart). Hoist with [rememberChartLink] from the parent composable.
 */
class ChartLink {
    var crosshairIndex: Int? by mutableStateOf(null)
    // null = "show all bars"; otherwise indices into the data list
    var viewStart: Int? by mutableStateOf(null)
    var viewEnd: Int? by mutableStateOf(null)
}

@Composable
fun rememberChartLink(): ChartLink = remember { ChartLink() }

/**
 * Single execution event (fill) to draw as a buy/sell marker on the chart.
 *  - [time] is epoch seconds matching bar timestamps
 *  - [side] "BUY" or "SELL"
 */
data class ChartExecution(val time: Long, val price: Double, val side: String, val shares: Double)

@Composable
fun KlineChart(
    bars: List<Bar>,
    period: String,
    modifier: Modifier = Modifier,
    link: ChartLink? = null,
    costBasis: Double? = null,
    costShares: Double? = null,
    executions: List<ChartExecution> = emptyList(),
) {
    val density = LocalDensity.current
    val labelPx = with(density) { 10.sp.toPx() }
    val maLabelPx = with(density) { 11.sp.toPx() }

    val upColor = LbColors.Up
    val downColor = LbColors.Down
    val gridColor = LbColors.Outline.copy(alpha = 0.35f)
    val mutedColor = LbColors.OnSurfaceMuted
    val onSurface = LbColors.OnSurface
    val tooltipBg = LbColors.SurfaceElevated
    val bgColor = LbColors.Background

    val timeFmt = remember(period) { timeFormatterFor(period) }
    val ma5 = remember(bars) { simpleMA(bars, 5) }
    val ma10 = remember(bars) { simpleMA(bars, 10) }
    val ma20 = remember(bars) { simpleMA(bars, 20) }

    // Local touch Y for the horizontal crosshair line (only K-line cares about Y);
    // shared X-index lives in [link] so MACD sees it too.
    var touchY by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = modifier.background(bgColor)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val priceLabelW = labelPx * 5.0f
        val plotLeft = 0f
        val plotRight = widthPx - priceLabelW
        val plotW = (plotRight - plotLeft).coerceAtLeast(1f)

        // Resolve visible window from [link], fallback to full range
        val viewStart = (link?.viewStart ?: 0).coerceIn(0, bars.size.coerceAtLeast(1) - 1)
        val viewEnd = (link?.viewEnd ?: (bars.size - 1)).coerceIn(viewStart, bars.size.coerceAtLeast(1) - 1)
        val viewN = (viewEnd - viewStart + 1).coerceAtLeast(1)
        val xStep = (plotW / viewN).coerceAtLeast(0.5f)

        // Helper to convert touch.x to a global bar index
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
                        onDragStart = { pos ->
                            touchY = pos.y
                            link?.crosshairIndex = pickIndex(pos.x)
                        },
                        onDragEnd = {
                            touchY = null
                            link?.crosshairIndex = null
                        },
                        onDragCancel = {
                            touchY = null
                            link?.crosshairIndex = null
                        },
                        onDrag = { change, _ ->
                            touchY = change.position.y
                            link?.crosshairIndex = pickIndex(change.position.x)
                        },
                    )
                }
                .pointerInput(bars, viewStart, viewEnd) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (link == null || bars.isEmpty()) return@detectTransformGestures
                        // Horizontal pan (1 finger) shifts the view
                        val barsPerPx = viewN / plotW
                        val deltaBars = (-pan.x * barsPerPx).toInt()
                        // Pinch: zoom around current center
                        val newSpan = (viewN / zoom).toInt().coerceIn(10, bars.size)
                        val center = (viewStart + viewEnd) / 2
                        val newStart = (center - newSpan / 2 + deltaBars).coerceIn(0, bars.size - newSpan)
                        val newEnd = (newStart + newSpan - 1).coerceAtMost(bars.size - 1)
                        link.viewStart = newStart
                        link.viewEnd = newEnd
                    }
                }
                .pointerInput(bars) {
                    detectTapGestures(
                        onTap = {
                            touchY = null
                            link?.crosshairIndex = null
                        },
                        onDoubleTap = {
                            link?.viewStart = null
                            link?.viewEnd = null
                        },
                    )
                },
        ) {
            if (bars.isEmpty()) {
                val paint = makeLabelPaint(mutedColor, labelPx).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "暂无 K 线数据",
                    size.width / 2,
                    size.height / 2,
                    paint,
                )
                return@Canvas
            }

            // ---------- Layout ----------
            val timeLabelH = labelPx * 2.0f
            val maStripH = maLabelPx * 1.5f

            val candleTop = maStripH
            val candleBot = size.height - timeLabelH
            val totalChartH = candleBot - candleTop
            val volH = totalChartH * 0.22f
            val candleH = totalChartH - volH
            val candleBottom = candleTop + candleH
            val volTop = candleBottom + 2f
            val volBottom = candleBot

            // ---------- Visible-slice range ----------
            val visible = bars.subList(viewStart, viewEnd + 1)
            val low = visible.minOf { it.low }
            val high = visible.maxOf { it.high }
            val rawRange = (high - low).coerceAtLeast(maxOf(high * 0.001, 0.01))
            val padded = rawRange * 0.04
            val yMin = low - padded
            val yMax = high + padded
            val yRange = (yMax - yMin).coerceAtLeast(1e-6)
            val volMax = visible.maxOf { it.volume }.coerceAtLeast(1.0)
            val candleW = (xStep * 0.7f).coerceIn(1f, 18f)

            fun xOfLocal(localIdx: Int): Float = plotLeft + localIdx * xStep + xStep / 2
            fun xOfGlobal(globalIdx: Int): Float = xOfLocal(globalIdx - viewStart)
            fun yOfPrice(p: Double): Float =
                (candleTop + ((yMax - p) / yRange * candleH)).toFloat()
            fun yOfVol(v: Double): Float =
                (volTop + (1 - v / volMax) * volH).toFloat()

            // ---------- Grid ----------
            val gridLevels = 5
            for (i in 0..gridLevels) {
                val y = candleTop + candleH * (i.toFloat() / gridLevels)
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 0.5f)
            }

            // ---------- Candles + volume ----------
            for (localI in visible.indices) {
                val b = visible[localI]
                val x = xOfLocal(localI)
                val color = if (b.close >= b.open) upColor else downColor
                drawLine(color, Offset(x, yOfPrice(b.high)), Offset(x, yOfPrice(b.low)), strokeWidth = 1.3f)
                val bodyTop = yOfPrice(max(b.open, b.close))
                val bodyBot = yOfPrice(min(b.open, b.close))
                val bodyH = max(bodyBot - bodyTop, 1.2f)
                drawRect(color, Offset(x - candleW / 2, bodyTop), Size(candleW, bodyH))
                val vy = yOfVol(b.volume)
                drawRect(
                    color.copy(alpha = 0.55f),
                    Offset(x - candleW / 2, vy),
                    Size(candleW, (volBottom - vy).coerceAtLeast(0.5f)),
                )
            }

            // ---------- MA overlays (computed on full bars; sliced to visible) ----------
            fun drawMaLine(values: List<Double?>, color: Color) {
                val path = Path()
                var started = false
                for (localI in visible.indices) {
                    val v = values[viewStart + localI] ?: continue
                    val x = xOfLocal(localI)
                    val y = yOfPrice(v)
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                if (started) drawPath(path, color = color, style = Stroke(width = 1.3f, cap = StrokeCap.Round))
            }
            drawMaLine(ma5, MA_COLORS[0])
            drawMaLine(ma10, MA_COLORS[1])
            drawMaLine(ma20, MA_COLORS[2])

            // ---------- Cost basis line + label ----------
            if (costBasis != null && costBasis in yMin..yMax) {
                val y = yOfPrice(costBasis)
                val costColor = Color(0xFFFBBF24) // amber — distinct from MA/last-price
                drawLine(
                    color = costColor.copy(alpha = 0.85f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 0.9f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
                )
                val sharesLabel = costShares?.let { " · %s 股".format(if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it)) } ?: ""
                val text = "成本 ${formatAxisPrice(costBasis)}$sharesLabel"
                val paint = makeLabelPaint(Color.Black, labelPx)
                val pad = 4f
                val textW = paint.measureText(text)
                val boxW = textW + pad * 2
                val boxH = labelPx * 1.4f
                drawRect(
                    color = costColor.copy(alpha = 0.85f),
                    topLeft = Offset(plotLeft + 4f, y - boxH - 2f),
                    size = Size(boxW, boxH),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    plotLeft + 4f + pad,
                    y - 2f - labelPx * 0.3f,
                    paint,
                )
            }

            // ---------- Execution markers (buy/sell arrows) ----------
            if (executions.isNotEmpty() && bars.size >= 2) {
                // Build a lookup: bar time -> local index in visible slice
                val visStartTime = visible.first().time
                val visEndTime = visible.last().time
                // bar interval to bucket execution into closest bar
                val barInterval = if (visible.size > 1) (visible[1].time - visible[0].time) else 60L
                for (e in executions) {
                    if (e.time < visStartTime - barInterval || e.time > visEndTime + barInterval) continue
                    // Find closest local bar index
                    val localIdx = ((e.time - visStartTime).toFloat() / barInterval).toInt().coerceIn(0, visible.size - 1)
                    val x = xOfLocal(localIdx)
                    val py = if (e.price in yMin..yMax) yOfPrice(e.price)
                             else if (e.price > yMax) candleTop + 6f
                             else candleBottom - 6f
                    val isBuy = e.side.equals("BUY", ignoreCase = true)
                    val mColor = if (isBuy) upColor else downColor
                    val tip = if (isBuy) Offset(x, py + 2f) else Offset(x, py - 2f)
                    val baseY = if (isBuy) py + 14f else py - 14f
                    val path = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(tip.x - 6f, baseY)
                        lineTo(tip.x + 6f, baseY)
                        close()
                    }
                    drawPath(path, color = mColor)
                    // Label letter (B/S) near arrow
                    val letter = if (isBuy) "B" else "S"
                    val paint = makeLabelPaint(Color.White, labelPx * 0.9f).apply {
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        letter,
                        tip.x,
                        if (isBuy) baseY + labelPx * 1.1f else baseY - labelPx * 0.2f,
                        paint,
                    )
                }
            }

            // ---------- Last-price marker on right axis ----------
            run {
                val lastB = bars.last()
                val prevClose = if (bars.size >= 2) bars[bars.size - 2].close else lastB.open
                val markerColor = if (lastB.close >= prevClose) upColor else downColor
                if (lastB.close in yMin..yMax) {
                    val y = yOfPrice(lastB.close)
                    drawLine(
                        markerColor.copy(alpha = 0.5f),
                        Offset(plotLeft, y),
                        Offset(plotRight, y),
                        strokeWidth = 0.6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                    )
                    val text = formatAxisPrice(lastB.close)
                    val paint = makeLabelPaint(Color.White, labelPx)
                    val pad = 4f
                    val boxW = (paint.measureText(text) + pad * 2).coerceAtMost(priceLabelW)
                    val boxH = labelPx * 1.6f
                    drawRect(markerColor, Offset(plotRight, y - boxH / 2), Size(boxW, boxH))
                    drawContext.canvas.nativeCanvas.drawText(text, plotRight + pad, y + labelPx * 0.4f, paint)
                }
            }

            // ---------- Price axis labels (right) ----------
            val priceLabelPaint = makeLabelPaint(mutedColor, labelPx)
            for (i in 0..gridLevels) {
                val frac = i.toFloat() / gridLevels
                val price = yMax - yRange * frac
                val y = candleTop + candleH * frac
                drawContext.canvas.nativeCanvas.drawText(
                    formatAxisPrice(price),
                    plotRight + 4f,
                    y + labelPx * 0.35f,
                    priceLabelPaint,
                )
            }

            // ---------- Cross-day vertical dividers ----------
            // For intraday-bar periods (1min/2min/.../5d), draw a thin vertical line at every
            // day boundary so multi-day data is visually segmented (matches Longbridge 5日 view).
            val isMultiDayIntraday = period in setOf("1min", "2min", "3min", "5min", "15min", "30min", "60min", "1h", "5d")
            if (isMultiDayIntraday && visible.size >= 2) {
                val zone = java.time.ZoneId.systemDefault()
                var prevDay = java.time.Instant.ofEpochSecond(visible[0].time).atZone(zone).dayOfYear
                val dayLabelPaint = makeLabelPaint(mutedColor.copy(alpha = 0.85f), labelPx * 0.9f)
                for (localI in 1 until visible.size) {
                    val dt = java.time.Instant.ofEpochSecond(visible[localI].time).atZone(zone)
                    if (dt.dayOfYear != prevDay) {
                        val x = xOfLocal(localI) - xStep / 2
                        drawLine(
                            color = mutedColor.copy(alpha = 0.55f),
                            start = Offset(x, candleTop),
                            end = Offset(x, volBottom),
                            strokeWidth = 0.8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                        )
                        val lbl = "%02d/%02d".format(dt.monthValue, dt.dayOfMonth)
                        drawContext.canvas.nativeCanvas.drawText(
                            lbl,
                            x + 2f,
                            candleTop + labelPx * 1.0f,
                            dayLabelPaint,
                        )
                        prevDay = dt.dayOfYear
                    }
                }
            }

            // ---------- Time axis labels ----------
            val timeLabelPaint = makeLabelPaint(mutedColor, labelPx)
            val timeLabelCount = 5
            for (i in 0..timeLabelCount) {
                val localIdx = ((viewN - 1).toFloat() * i / timeLabelCount).toInt().coerceIn(0, viewN - 1)
                val globalIdx = viewStart + localIdx
                val x = xOfLocal(localIdx)
                val prevTs = if (globalIdx > 0) bars[globalIdx - 1].time else null
                val text = timeFmt(bars[globalIdx].time, prevTs)
                val textW = timeLabelPaint.measureText(text)
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    (x - textW / 2).coerceIn(0f, plotRight - textW),
                    candleBot + labelPx * 1.4f,
                    timeLabelPaint,
                )
            }

            // ---------- MA label strip ----------
            val lastMa5 = ma5.lastOrNull { it != null }
            val lastMa10 = ma10.lastOrNull { it != null }
            val lastMa20 = ma20.lastOrNull { it != null }
            var stripX = 6f
            fun drawMaStripItem(label: String, value: Double?, color: Color) {
                if (value == null) return
                val text = "$label ${formatAxisPrice(value)}"
                val paint = makeLabelPaint(color, maLabelPx)
                drawContext.canvas.nativeCanvas.drawText(text, stripX, maStripH * 0.7f, paint)
                stripX += paint.measureText(text) + 10f
            }
            drawMaStripItem("MA5", lastMa5, MA_COLORS[0])
            drawMaStripItem("MA10", lastMa10, MA_COLORS[1])
            drawMaStripItem("MA20", lastMa20, MA_COLORS[2])

            // ---------- Crosshair (driven by shared link.crosshairIndex) ----------
            val xhIdx = link?.crosshairIndex
            if (xhIdx != null && xhIdx in viewStart..viewEnd) {
                val x = xOfGlobal(xhIdx)
                drawLine(
                    onSurface.copy(alpha = 0.5f),
                    Offset(x, candleTop), Offset(x, volBottom),
                    strokeWidth = 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                touchY?.let { ty ->
                    val hy = ty.coerceIn(candleTop, candleBottom)
                    drawLine(
                        onSurface.copy(alpha = 0.5f),
                        Offset(plotLeft, hy), Offset(plotRight, hy),
                        strokeWidth = 0.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                    )
                }
                val b = bars[xhIdx]
                val pct = if (b.open != 0.0) (b.close - b.open) / b.open * 100 else 0.0
                val prevTs = if (xhIdx > 0) bars[xhIdx - 1].time else null
                val lines = listOf(
                    "时间 ${timeFmt(b.time, prevTs)}",
                    "开 ${formatAxisPrice(b.open)}",
                    "高 ${formatAxisPrice(b.high)}",
                    "低 ${formatAxisPrice(b.low)}",
                    "收 ${formatAxisPrice(b.close)}",
                    "涨跌 %+.2f%%".format(pct),
                    "量 ${formatVol(b.volume)}",
                )
                drawOhlcTooltip(
                    lines,
                    anchor = Offset(x, touchY ?: (candleTop + candleH / 2)),
                    plotRight = plotRight,
                    labelPx = labelPx,
                    fg = onSurface,
                    bg = tooltipBg,
                    accent = if (b.close >= b.open) upColor else downColor,
                )
            }
        }
    }
}

private fun formatVol(v: Double): String = when {
    v >= 1e9 -> "%.2fB".format(v / 1e9)
    v >= 1e6 -> "%.2fM".format(v / 1e6)
    v >= 1e3 -> "%.2fK".format(v / 1e3)
    else -> v.toLong().toString()
}

private fun DrawScope.drawOhlcTooltip(
    lines: List<String>,
    anchor: Offset,
    plotRight: Float,
    labelPx: Float,
    fg: Color,
    bg: Color,
    accent: Color,
) {
    val pad = 6f
    val lineH = labelPx * 1.35f
    val paint = makeLabelPaint(fg, labelPx)
    val maxW = lines.maxOf { paint.measureText(it) }
    val boxW = maxW + pad * 2
    val boxH = lineH * lines.size + pad * 1.6f
    val placeLeft = anchor.x > size.width / 2
    val boxLeft = if (placeLeft) (anchor.x - boxW - 12f).coerceAtLeast(0f) else (anchor.x + 12f).coerceAtMost(plotRight - boxW)
    val boxTop = (anchor.y - boxH / 2).coerceIn(0f, size.height - boxH)
    drawRect(color = bg.copy(alpha = 0.95f), topLeft = Offset(boxLeft, boxTop), size = Size(boxW, boxH))
    drawRect(color = accent.copy(alpha = 0.5f), topLeft = Offset(boxLeft, boxTop), size = Size(2f, boxH))
    lines.forEachIndexed { idx, line ->
        drawContext.canvas.nativeCanvas.drawText(
            line,
            boxLeft + pad,
            boxTop + pad + lineH * (idx + 0.75f),
            paint,
        )
    }
}
