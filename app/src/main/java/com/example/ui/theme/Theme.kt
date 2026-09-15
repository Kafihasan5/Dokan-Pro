package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Brand300,
    onPrimary = Brand900,
    primaryContainer = Brand900,
    onPrimaryContainer = Brand100,
    secondary = Brand500,
    onSecondary = Brand900,
    background = BgDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = Ink2Dark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = Danger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Brand500,
    onPrimary = Color.White,
    primaryContainer = Brand100,
    onPrimaryContainer = Brand900,
    secondary = Brand700,
    onSecondary = Color.White,
    background = BgLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = Ink2Light,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = Danger,
    onError = Color.White
)

@Composable
fun DokanProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val dokanColors = if (darkTheme) DarkDokanColors else LightDokanColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDokanColors provides dokanColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun PaponShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    DokanProTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    DokanProTheme(darkTheme = darkTheme, content = content)
}
