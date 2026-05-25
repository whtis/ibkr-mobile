package com.tis.ibkr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class IbkrColors(
    val Background: Color,
    val Surface: Color,
    val SurfaceElevated: Color,
    val Outline: Color,
    val OnSurface: Color,
    val OnSurfaceMuted: Color,
    val Disabled: Color,
    val Up: Color,
    val Down: Color,
    val Flat: Color,
    val Accent: Color,
    val Warning: Color,
    val Error: Color,
)

val DarkIbkrColors = IbkrColors(
    Background = Color(0xFF0E1117),
    Surface = Color(0xFF161B22),
    SurfaceElevated = Color(0xFF1F2937),
    Outline = Color(0xFF2D3748),
    OnSurface = Color(0xFFE5E7EB),
    OnSurfaceMuted = Color(0xFF9CA3AF),
    Disabled = Color(0xFF6B7280),
    Up = Color(0xFFFF4D4F),
    Down = Color(0xFF00C087),
    Flat = Color(0xFF9CA3AF),
    Accent = Color(0xFF3B82F6),
    Warning = Color(0xFFF59E0B),
    Error = Color(0xFFEF4444),
)

val LightIbkrColors = IbkrColors(
    Background = Color(0xFFFFFFFF),
    Surface = Color(0xFFFFFFFF),
    SurfaceElevated = Color(0xFFF5F6F8),
    Outline = Color(0xFFE5E7EB),
    OnSurface = Color(0xFF1A1D24),
    OnSurfaceMuted = Color(0xFF6B7280),
    Disabled = Color(0xFFB5BAC2),
    Up = Color(0xFFFF4D4F),
    Down = Color(0xFF00C087),
    Flat = Color(0xFF9CA3AF),
    Accent = Color(0xFF2563EB),
    Warning = Color(0xFFD97706),
    Error = Color(0xFFDC2626),
)

val LocalIbkrColors = staticCompositionLocalOf { DarkIbkrColors }

val LbColors: IbkrColors
    @Composable
    @ReadOnlyComposable
    get() = LocalIbkrColors.current
