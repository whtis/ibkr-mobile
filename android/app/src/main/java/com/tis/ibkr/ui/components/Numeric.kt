package com.tis.ibkr.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.tis.ibkr.ui.theme.LbColors
import java.text.DecimalFormat
import kotlin.math.abs

private val priceFormat = DecimalFormat("#,##0.00")
private val priceFormat3 = DecimalFormat("#,##0.000")
private val pctFormat = DecimalFormat("0.00")
private val intFormat = DecimalFormat("#,##0.####")

@Composable
fun NumericText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTextStyle.current.color,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        fontWeight = fontWeight,
        style = style.copy(fontFeatureSettings = "tnum"),
    )
}

/** Longbridge-style two-tier price: integer part big, ".xx" small subscript. */
@Composable
fun StackedPriceText(
    value: Double?,
    intStyle: TextStyle,
    fracStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fractionDigits: Int = 2,
) {
    if (value == null) {
        NumericText(text = "--", color = color, style = intStyle, modifier = modifier)
        return
    }
    val fmt = if (fractionDigits == 3) priceFormat3 else priceFormat
    val formatted = fmt.format(value)
    val dot = formatted.lastIndexOf('.')
    val intPart = if (dot >= 0) formatted.substring(0, dot) else formatted
    val fracPart = if (dot >= 0) formatted.substring(dot) else ""
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        NumericText(text = intPart, color = color, style = intStyle)
        if (fracPart.isNotEmpty()) {
            NumericText(text = fracPart, color = color, style = fracStyle)
        }
    }
}

fun formatPrice(value: Double?): String =
    if (value == null) "--" else priceFormat.format(value)

/** 3-decimal price like Longbridge uses for current price column (e.g. 1.690, 81.140). */
fun formatPrice3(value: Double?): String =
    if (value == null) "--" else priceFormat3.format(value)

fun formatQty(value: Double): String = intFormat.format(value)

/** Compact Chinese number formatting: 12345 → "1.23万", 1.5e8 → "1.50亿". */
fun formatBigNumber(value: Double?): String {
    if (value == null) return "--"
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_0000_0000.0 -> DecimalFormat("0.00").format(value / 1_0000_0000.0) + "亿"
        abs >= 10_000.0 -> DecimalFormat("0.00").format(value / 10_000.0) + "万"
        else -> DecimalFormat("#,##0").format(value)
    }
}

fun formatSignedPrice(value: Double): String {
    val sign = if (value > 0) "+" else if (value < 0) "-" else ""
    return "$sign${priceFormat.format(abs(value))}"
}

fun formatSignedPct(value: Double): String {
    val sign = if (value > 0) "+" else if (value < 0) "-" else ""
    return "$sign${pctFormat.format(abs(value))}%"
}

@Composable
fun changeColor(delta: Double): Color = when {
    delta > 0 -> LbColors.Up
    delta < 0 -> LbColors.Down
    else -> LbColors.Flat
}

/** Background color (with alpha) matching delta direction for color-block treatments. */
@Composable
fun changeBgColor(delta: Double, alpha: Float = 1.0f): Color = when {
    delta > 0 -> LbColors.Up.copy(alpha = alpha)
    delta < 0 -> LbColors.Down.copy(alpha = alpha)
    else -> LbColors.Flat.copy(alpha = alpha * 0.3f)
}
