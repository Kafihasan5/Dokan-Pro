package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

object Motion {
    // 150ms FastOutSlowInEasing
    val MotionFast: TweenSpec<Float> = tween(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    // 250ms FastOutSlowInEasing
    val MotionStandard: TweenSpec<Float> = tween(
        durationMillis = 250,
        easing = FastOutSlowInEasing
    )

    // 350ms, spring with low bounce
    val MotionEmphasis: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun isReducedMotion(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Reads Settings.Global.ANIMATOR_DURATION_SCALE to respect accessibility setting
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Motion.isReducedMotion(context)
    }
}

/**
 * Smooth press scale animation to 0.98 for tappable cards and rows
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.98f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val isReduced = rememberReducedMotion()
    if (isReduced) {
        return@composed this
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = Motion.MotionFast,
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
