package com.tis.ibkr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Longbridge-style solid-color block showing a signed percentage:
 *   red block (white text) for +x%, green block for -x%.
 * Used in watchlists and market lists where the % needs to pop.
 */
@Composable
fun ChangePctBlock(
    pct: Double,
    modifier: Modifier = Modifier,
    minWidth: androidx.compose.ui.unit.Dp = 64.dp,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val bg = changeBgColor(pct, alpha = 1.0f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        NumericText(
            text = formatSignedPct(pct),
            color = Color.White,
            style = style.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
