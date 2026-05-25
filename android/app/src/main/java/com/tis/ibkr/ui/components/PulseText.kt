package com.tis.ibkr.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tis.ibkr.ui.theme.LbColors
import kotlinx.coroutines.delay

/** Wraps a NumericText to flash green/red briefly when [price] changes. */
@Composable
fun PulsePriceText(
    price: Double?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    digits: Int = 3,
) {
    val upPulse = LbColors.Up.copy(alpha = 0.18f)
    val downPulse = LbColors.Down.copy(alpha = 0.18f)
    var lastPrice by remember { mutableStateOf(price) }
    var pulseColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(price) {
        val prev = lastPrice
        if (prev != null && price != null && price != prev) {
            pulseColor = if (price > prev) upPulse else downPulse
            delay(260)
            pulseColor = Color.Transparent
        }
        lastPrice = price
    }
    val bg by animateColorAsState(targetValue = pulseColor, animationSpec = tween(220), label = "pulse")

    NumericText(
        text = if (digits == 3) formatPrice3(price) else formatPrice(price),
        color = color,
        style = style,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 2.dp),
    )
}
