package com.tis.ibkr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val baseTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

// Density-tightened type scale to match Longbridge's tight layout (≈ 1sp tighter across the
// body/label set). Title sizes stay close so headings still pop.
val IbkrTypography = Typography(
    displayLarge = baseTextStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = baseTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = baseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    titleSmall = baseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = baseTextStyle.copy(fontSize = 15.sp),
    bodyMedium = baseTextStyle.copy(fontSize = 13.sp),
    bodySmall = baseTextStyle.copy(fontSize = 11.sp),
    labelLarge = baseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = baseTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = baseTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)
