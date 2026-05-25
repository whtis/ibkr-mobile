package com.tis.ibkr.ui.chart

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tis.ibkr.data.api.Bar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Period axis-label formatter. `period` matches the keys we use elsewhere (1d/1w/1mo/...).
 * Two-arg form: format with awareness of the previous bar's timestamp so cross-day or
 * cross-month boundaries can promote to a more informative label (date instead of time).
 */
fun timeFormatterFor(period: String): (Long, Long?) -> String {
    val zone = ZoneId.systemDefault()
    return when (period) {
        "1d", "1w" -> { ts, prev ->
            val dt = Instant.ofEpochSecond(ts).atZone(zone)
            val pd = prev?.let { Instant.ofEpochSecond(it).atZone(zone) }
            if (pd != null && pd.year != dt.year) "%d".format(dt.year)
            else if (pd != null && pd.monthValue != dt.monthValue) "%02d月".format(dt.monthValue)
            else "%02d-%02d".format(dt.monthValue, dt.dayOfMonth)
        }
        "1mo", "1y" -> { ts, _ ->
            val dt = Instant.ofEpochSecond(ts).atZone(zone)
            "%d-%02d".format(dt.year % 100, dt.monthValue)
        }
        "5d" -> { ts, prev ->
            val dt = Instant.ofEpochSecond(ts).atZone(zone)
            val pd = prev?.let { Instant.ofEpochSecond(it).atZone(zone) }
            if (pd != null && pd.dayOfMonth != dt.dayOfMonth) "%02d/%02d".format(dt.monthValue, dt.dayOfMonth)
            else "%02d:%02d".format(dt.hour, dt.minute)
        }
        else -> { ts, prev ->
            // Intraday minutes: promote to "MM-DD" when crossing day boundary
            val dt = Instant.ofEpochSecond(ts).atZone(zone)
            val pd = prev?.let { Instant.ofEpochSecond(it).atZone(zone) }
            if (pd != null && pd.dayOfYear != dt.dayOfYear) "%02d-%02d".format(dt.monthValue, dt.dayOfMonth)
            else "%02d:%02d".format(dt.hour, dt.minute)
        }
    }
}

/** Right-side price axis: format with smart precision based on absolute magnitude. */
fun formatAxisPrice(p: Double): String = when {
    p >= 1000 -> "%.2f".format(p)
    p >= 10 -> "%.2f".format(p)
    else -> "%.3f".format(p)
}

/** Simple moving average. Returns null for indices before `period-1`. */
fun simpleMA(bars: List<Bar>, period: Int): List<Double?> {
    if (bars.size < period) return List(bars.size) { null }
    val out = ArrayList<Double?>(bars.size)
    var sum = 0.0
    for (i in bars.indices) {
        sum += bars[i].close
        if (i >= period) sum -= bars[i - period].close
        out.add(if (i >= period - 1) sum / period else null)
    }
    return out
}

/** Exponential MA. */
fun expMA(values: List<Double>, period: Int): DoubleArray {
    val k = 2.0 / (period + 1)
    val out = DoubleArray(values.size)
    if (values.isEmpty()) return out
    out[0] = values[0]
    for (i in 1 until values.size) out[i] = values[i] * k + out[i - 1] * (1 - k)
    return out
}

/** MACD (DIF, DEA, HIST) — standard 12/26/9 params unless overridden. */
data class Macd(val dif: DoubleArray, val dea: DoubleArray, val hist: DoubleArray)

fun macd(closes: List<Double>, short: Int = 12, long: Int = 26, signal: Int = 9): Macd {
    val ema12 = expMA(closes, short)
    val ema26 = expMA(closes, long)
    val dif = DoubleArray(closes.size) { ema12[it] - ema26[it] }
    val dea = expMA(dif.toList(), signal)
    val hist = DoubleArray(closes.size) { (dif[it] - dea[it]) * 2 }
    return Macd(dif, dea, hist)
}

/** Build a `Paint` configured for axis labels. */
fun makeLabelPaint(color: Color, textPx: Float, alignRight: Boolean = false): Paint = Paint().apply {
    isAntiAlias = true
    this.color = color.toArgb()
    textSize = textPx
    typeface = Typeface.DEFAULT
    textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
}
