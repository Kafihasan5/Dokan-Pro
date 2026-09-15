package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==============================================================================
// 1. SPACING TOKENS
// ==============================================================================
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

// ==============================================================================
// 2. RADIUS TOKENS
// ==============================================================================
object Radius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val pill: Dp = 999.dp
}

// ==============================================================================
// 3. ELEVATION & SOFT SHADOW HELPER
// ==============================================================================
object Elevation {
    fun Modifier.softShadow(
        level: Int,
        shape: Shape = RoundedCornerShape(Radius.md)
    ): Modifier = this.then(
        when (level) {
            1 -> Modifier.border(1.dp, BorderLight, shape)
            2 -> Modifier.shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            3 -> Modifier.shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            else -> Modifier
        }
    )
}

// Top-level extension so it can be called cleanly without needing `Elevation.` scope
fun Modifier.softShadow(
    level: Int,
    shape: Shape = RoundedCornerShape(Radius.md)
): Modifier = with(Elevation) {
    this@softShadow.softShadow(level, shape)
}

// ==============================================================================
// 4. DOKAN EXTRA COLORS & COMPOSITION LOCAL
// ==============================================================================
data class DokanExtraColors(
    val gold: Color,
    val goldContainer: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val ink2: Color,
    val ink3: Color,
    val surfaceAlt: Color,
    val border: Color = BorderLight
)

val LightDokanColors = DokanExtraColors(
    gold = Gold500,
    goldContainer = Gold100,
    success = Success,
    successContainer = SuccessContainerLight,
    warning = Warning,
    warningContainer = WarningContainerLight,
    danger = Danger,
    dangerContainer = DangerContainerLight,
    info = Info,
    infoContainer = InfoContainerLight,
    ink2 = Ink2Light,
    ink3 = Ink3Light,
    surfaceAlt = SurfaceAltLight,
    border = BorderLight
)

val DarkDokanColors = DokanExtraColors(
    gold = Gold500,
    goldContainer = Color(0xFF453000),
    success = Success,
    successContainer = SuccessContainerDark,
    warning = Warning,
    warningContainer = WarningContainerDark,
    danger = Danger,
    dangerContainer = DangerContainerDark,
    info = Info,
    infoContainer = InfoContainerDark,
    ink2 = Ink2Dark,
    ink3 = Ink3Dark,
    surfaceAlt = SurfaceAltDark,
    border = BorderDark
)

val LocalDokanColors = staticCompositionLocalOf { LightDokanColors }

val MaterialTheme.dokanColors: DokanExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDokanColors.current
