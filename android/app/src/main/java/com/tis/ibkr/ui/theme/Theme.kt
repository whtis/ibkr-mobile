package com.tis.ibkr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun darkScheme(c: IbkrColors) = darkColorScheme(
    background = c.Background,
    onBackground = c.OnSurface,
    surface = c.Surface,
    onSurface = c.OnSurface,
    surfaceVariant = c.SurfaceElevated,
    onSurfaceVariant = c.OnSurfaceMuted,
    outline = c.Outline,
    outlineVariant = c.Outline,
    primary = c.Accent,
    onPrimary = Color.White,
    error = c.Error,
    onError = Color.White,
)

private fun lightScheme(c: IbkrColors) = lightColorScheme(
    background = c.Background,
    onBackground = c.OnSurface,
    surface = c.Surface,
    onSurface = c.OnSurface,
    surfaceVariant = c.SurfaceElevated,
    onSurfaceVariant = c.OnSurfaceMuted,
    outline = c.Outline,
    outlineVariant = c.Outline,
    primary = c.Accent,
    onPrimary = Color.White,
    error = c.Error,
    onError = Color.White,
)

@Composable
fun IbkrTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ibkr = if (useDarkTheme) DarkIbkrColors else LightIbkrColors
    val scheme = if (useDarkTheme) darkScheme(ibkr) else lightScheme(ibkr)
    CompositionLocalProvider(LocalIbkrColors provides ibkr) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IbkrTypography,
            content = content,
        )
    }
}
