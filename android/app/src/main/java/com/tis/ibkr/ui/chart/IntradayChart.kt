package com.tis.ibkr.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.tis.ibkr.data.api.IntradayPoint
import com.tis.ibkr.ui.theme.LbColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private val AVG_COLOR = Color(0xFFF6B26B) // orange — same as Longbridge

/**
 * Native Compose Canvas intraday (分时) chart. Two polylines + volume:
 *   - Price line (blue), filled with red/green gradient against prev_close
 *   - Avg price line (orange)
 *
 * Long-press for crosshair + tooltip.
 */
@Composable
fun IntradayChart(
    points: List<IntradayPoint>,
    prevClose: Double?,
    modifier: Modifier = Modifier,
    costBasis: Double? = null,
    costShares: Double? = null,
    executions: List<ChartExecution> = emptyList(),
) {
    val density = LocalDensity.current
    val labelPx = with(density) { 10.sp.toPx() }

    val upColor = LbColors.Up
    val downColor = LbColors.Down
    val gridColor = LbColors.Outline.copy(alpha = 0.35f)
    val mutedColor = LbColors.OnSurfaceMuted
    val onSurface = LbColors.OnSurface
    val tooltipBg = LbColors.SurfaceElevated
    val bgColor = LbColors.Background
    val priceLineColor = Color(0xFF4A90E2) // blue

    var crosshair by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.background(bgColor)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { crosshair = it },
                        onDragEnd = { crosshair = null },
                        onDragCancel = { crosshair = null },
                        onDrag = { change, _ -> crosshair = change.position },
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(onTap = { crosshair = null })
                },
        ) {
            if (points.isEmpty()) {
                val paint = makeLabelPaint(mutedColor, labelPx).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "暂无分时数据",
                    size.width / 2,
                    size.height / 2,
                    paint,
                )
                return@Canvas
            }

            val priceLabelW = labelPx * 5.0f
            val timeLabelH = labelPx * 2.0f
            val plotLeft = 0f
            val plotRight = size.width - priceLabelW
            val plotW = plotRight - plotLeft

            val plotTop = 0f
            val plotBot = size.height - timeLabelH
            val volH = (plotBot - plotTop) * 0.22f
            val priceH = (plotBot - plotTop) - volH
            val priceBottom = plotTop + priceH
            val volTop = priceBottom + 2f

            // Price range: include prev_close so the reference line is always inside
            val rawLow = points.minOf { it.price }
            val rawHigh = points.maxOf { it.price }
            val baseLow = if (prevClose != null) minOf(rawLow, prevClose) else rawLow
            val baseHigh = if (prevClose != null) maxOf(rawHigh, prevClose) else rawHigh
            // Symmetric padding around prev_close so up/down regions are visually fair
            val ref = prevClose ?: ((baseLow + baseHigh) / 2)
            val maxDiff = maxOf(abs(baseHigh - ref), abs(baseLow - ref)).coerceAtLeast(ref * 0.005)
            val yMin = ref - maxDiff * 1.05
            val yMax = ref + maxDiff * 1.05
            val yRange = (yMax - yMin).coerceAtLeast(1e-6)

            val volMax = points.maxOf { it.volume ?: 0.0 }.coerceAtLeast(1.0)

            val n = points.size
            val xStep = (plotW / n.coerceAtLeast(1)).coerceAtLeast(0.5f)

            fun xOf(i: Int): Float = plotLeft + i * xStep + xStep / 2
            fun yOfPrice(p: Double): Float =
                (plotTop + ((yMax - p) / yRange * priceH)).toFloat()
            fun yOfVol(v: Double): Float =
                (volTop + (1 - v / volMax) * volH).toFloat()

            // --- Session backgrounds + vertical dividers + top labels ---
            val sessionLabel: (Int) -> String? = { lt ->
                when (lt) { 1 -> "盘前"; 0 -> "盘中"; 2 -> "盘后"; 3 -> "夜盘"; else -> null }
            }
            val sessionTint: (Int) -> Color? = { lt ->
                when (lt) {
                    1 -> Color(0xFF1E3A5F).copy(alpha = 0.18f)   // Pre  — dim blue
                    2 -> Color(0xFF5F3A1E).copy(alpha = 0.18f)   // Post — dim amber
                    3 -> Color(0xFF3A1E5F).copy(alpha = 0.20f)   // Overnight — dim purple
                    else -> null
                }
            }
            run {
                val labelPaint = makeLabelPaint(mutedColor, labelPx * 0.85f)
                var runStart = 0
                var runType = points[0].lineType
                for (i in 1..points.size) {
                    val end = i == points.size || points[i].lineType != runType
                    if (end) {
                        val tint = sessionTint(runType)
                        val x0 = xOf(runStart) - xStep / 2
                        val x1 = (if (i < points.size) xOf(i) - xStep / 2 else plotRight)
                        if (tint != null) {
                            drawRect(
                                color = tint,
                                topLeft = Offset(x0.coerceAtLeast(plotLeft), plotTop),
                                size = Size((x1 - x0).coerceAtLeast(0.5f), priceH + volH),
                            )
                        }
                        // Vertical divider on the LEFT edge of every non-first run
                        if (runStart > 0) {
                            drawLine(
                                color = mutedColor.copy(alpha = 0.45f),
                                start = Offset(x0, plotTop),
                                end = Offset(x0, plotBot),
                                strokeWidth = 0.7f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                            )
                        }
                        // Top-of-segment session label, if the segment is wide enough
                        val segW = x1 - x0
                        sessionLabel(runType)?.let { lbl ->
                            if (segW > labelPaint.measureText(lbl) + 8f) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    lbl,
                                    x0 + 4f,
                                    plotTop + labelPx * 1.0f,
                                    labelPaint,
                                )
                            }
                        }
                        if (i < points.size) {
                            runStart = i
                            runType = points[i].lineType
                        }
                    }
                }
            }

            // --- Horizontal grid ---
            val gridLevels = 4
            for (i in 0..gridLevels) {
                val y = plotTop + priceH * (i.toFloat() / gridLevels)
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 0.5f)
            }

            // --- prev_close reference line ---
            if (prevClose != null) {
                val refY = yOfPrice(prevClose)
                drawLine(
                    color = mutedColor.copy(alpha = 0.7f),
                    start = Offset(plotLeft, refY),
                    end = Offset(plotRight, refY),
                    strokeWidth = 0.7f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }

            // --- Filled area: price vs prev_close ---
            if (prevClose != null && points.size >= 2) {
                val refY = yOfPrice(prevClose)
                val fillPath = Path()
                fillPath.moveTo(xOf(0), refY)
                for (i in points.indices) {
                    fillPath.lineTo(xOf(i), yOfPrice(points[i].price))
                }
                fillPath.lineTo(xOf(points.size - 1), refY)
                fillPath.close()
                val last = points.last().price
                val fillColor = if (last >= prevClose) upColor else downColor
                drawPath(fillPath, color = fillColor.copy(alpha = 0.12f))
            }

            // --- Price polyline ---
            run {
                val path = Path()
                for (i in points.indices) {
                    val x = xOf(i)
                    val y = yOfPrice(points[i].price)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = priceLineColor, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
            }

            // --- Avg price polyline (orange) ---
            run {
                val path = Path()
                var started = false
                for (i in points.indices) {
                    val v = points[i].avgPrice ?: continue
                    val x = xOf(i)
                    val y = yOfPrice(v)
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                if (started) drawPath(path, color = AVG_COLOR, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            }

            // --- Volume bars ---
            for (i in points.indices) {
                val p = points[i]
                val v = p.volume ?: continue
                if (v <= 0) continue
                val x = xOf(i)
                val barW = (xStep * 0.7f).coerceIn(0.5f, 8f)
                val y = yOfVol(v)
                // Color volume bar by tick direction vs reference
                val refColor = if (prevClose != null && p.price >= prevClose) upColor else downColor
                drawRect(
                    color = refColor.copy(alpha = 0.55f),
                    topLeft = Offset(x - barW / 2, y),
                    size = Size(barW, ((volTop + volH) - y).coerceAtLeast(0.5f)),
                )
            }

            // --- Cost basis line ---
            if (costBasis != null) {
                if (costBasis in yMin..yMax) {
                    val y = yOfPrice(costBasis)
                    val costColor = Color(0xFFFBBF24)
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
                    val boxW = paint.measureText(text) + pad * 2
                    val boxH = labelPx * 1.4f
                    drawRect(
                        color = costColor.copy(alpha = 0.85f),
                        topLeft = Offset(plotLeft + 4f, y - boxH - 2f),
                        size = Size(boxW, boxH),
                    )
                    drawContext.canvas.nativeCanvas.drawText(text, plotLeft + 4f + pad, y - 2f - labelPx * 0.3f, paint)
                }
            }

            // --- Execution markers ---
            if (executions.isNotEmpty() && points.size >= 2) {
                val tFirst = points.first().time
                val tLast = points.last().time
                for (e in executions) {
                    if (e.time < tFirst - 60 || e.time > tLast + 60) continue
                    // Find closest point by time
                    var closest = 0
                    var best = Long.MAX_VALUE
                    for (i in points.indices) {
                        val d = kotlin.math.abs(points[i].time - e.time)
                        if (d < best) { best = d; closest = i }
                    }
                    val x = xOf(closest)
                    val py = if (e.price in yMin..yMax) yOfPrice(e.price)
                             else if (e.price > yMax) plotTop + 6f
                             else priceBottom - 6f
                    val isBuy = e.side.equals("BUY", ignoreCase = true)
                    val mColor = if (isBuy) upColor else downColor
                    val tip = if (isBuy) Offset(x, py + 2f) else Offset(x, py - 2f)
                    val baseY = if (isBuy) py + 14f else py - 14f
                    val path = Path().apply {
                        moveTo(tip.x, tip.y); lineTo(tip.x - 6f, baseY); lineTo(tip.x + 6f, baseY); close()
                    }
                    drawPath(path, color = mColor)
                    val letter = if (isBuy) "B" else "S"
                    val paint = makeLabelPaint(Color.White, labelPx * 0.9f).apply {
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        letter, tip.x,
                        if (isBuy) baseY + labelPx * 1.1f else baseY - labelPx * 0.2f,
                        paint,
                    )
                }
            }

            // --- Last-price marker on right axis ---
            run {
                val last = points.last().price
                val markerColor = if (prevClose != null && last >= prevClose) upColor else downColor
                val y = yOfPrice(last)
                val text = formatAxisPrice(last)
                val paint = makeLabelPaint(Color.White, labelPx)
                val textW = paint.measureText(text)
                val pad = 4f
                val boxW = (textW + pad * 2).coerceAtMost(priceLabelW)
                val boxH = labelPx * 1.6f
                drawRect(
                    color = markerColor,
                    topLeft = Offset(plotRight, y - boxH / 2),
                    size = Size(boxW, boxH),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    plotRight + pad,
                    y + labelPx * 0.4f,
                    paint,
                )
            }

            // --- Right price labels ---
            val priceLabelPaint = makeLabelPaint(mutedColor, labelPx)
            for (i in 0..gridLevels) {
                val frac = i.toFloat() / gridLevels
                val price = yMax - yRange * frac
                val y = plotTop + priceH * frac
                drawContext.canvas.nativeCanvas.drawText(
                    formatAxisPrice(price),
                    plotRight + 4f,
                    y + labelPx * 0.35f,
                    priceLabelPaint,
                )
            }

            // --- % labels on the right (relative to prev_close) ---
            if (prevClose != null && prevClose != 0.0) {
                val pctPaint = makeLabelPaint(mutedColor, labelPx)
                for (i in 0..gridLevels) {
                    val frac = i.toFloat() / gridLevels
                    val price = yMax - yRange * frac
                    val pct = (price - prevClose) / prevClose * 100
                    val text = "%+.2f%%".format(pct)
                    val y = plotTop + priceH * frac
                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        plotRight + 4f,
                        y + labelPx * 1.55f,
                        pctPaint,
                    )
                }
            }

            // --- Time axis labels ---
            val timeLabelPaint = makeLabelPaint(mutedColor, labelPx)
            val zone = ZoneId.systemDefault()
            val timeFmt: (Long) -> String = { ts ->
                val dt = Instant.ofEpochSecond(ts).atZone(zone)
                "%02d:%02d".format(dt.hour, dt.minute)
            }
            val timeLabelCount = 5
            for (i in 0..timeLabelCount) {
                val idx = ((points.size - 1).toFloat() * i / timeLabelCount).toInt().coerceIn(0, points.size - 1)
                val x = xOf(idx)
                val text = timeFmt(points[idx].time)
                val textW = timeLabelPaint.measureText(text)
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    (x - textW / 2).coerceIn(0f, plotRight - textW),
                    plotBot + labelPx * 1.4f,
                    timeLabelPaint,
                )
            }

            // --- Crosshair ---
            crosshair?.let { pos ->
                val i = ((pos.x - plotLeft) / xStep).toInt().coerceIn(0, points.size - 1)
                val x = xOf(i)
                drawLine(
                    color = onSurface.copy(alpha = 0.5f),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBot),
                    strokeWidth = 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                val hy = pos.y.coerceIn(plotTop, priceBottom)
                drawLine(
                    color = onSurface.copy(alpha = 0.5f),
                    start = Offset(plotLeft, hy),
                    end = Offset(plotRight, hy),
                    strokeWidth = 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
                val p = points[i]
                val pct = if (prevClose != null && prevClose != 0.0)
                    (p.price - prevClose) / prevClose * 100 else 0.0
                val lines = listOf(
                    "时间 ${timeFmt(p.time)}",
                    "价格 ${formatAxisPrice(p.price)}",
                    "涨跌 %+.2f%%".format(pct),
                    "均价 ${if (p.avgPrice != null) formatAxisPrice(p.avgPrice) else "—"}",
                    "量 ${formatVolIntraday(p.volume ?: 0.0)}",
                )
                drawTooltip(lines, pos, plotRight, labelPx, onSurface, tooltipBg, priceLineColor)
            }
        }
    }
}

private fun formatVolIntraday(v: Double): String = when {
    v >= 1e6 -> "%.2fM".format(v / 1e6)
    v >= 1e3 -> "%.2fK".format(v / 1e3)
    else -> v.toLong().toString()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTooltip(
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
