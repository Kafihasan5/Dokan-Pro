package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.theme.Radius
import com.example.ui.theme.Brand900
import com.example.ui.theme.Brand500
import com.example.ui.theme.Ink2Light
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold500

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingNavBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onFabLongPress: () -> Unit,
    onQrScanClick: () -> Unit = { onNavigate(AppScreen.POS) },
    isStaff: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Compact white navigation bar with a small icon-only active marker.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .border(
                    width = 1.dp,
                    color = BorderLight,
                    shape = RoundedCornerShape(22.dp)
                ),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceLight,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. হোম (Dashboard)
                NavSlot(
                    icon = Icons.Default.Home,
                    label = "হোম",
                    isSelected = currentScreen == AppScreen.DASHBOARD,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onNavigate(AppScreen.DASHBOARD)
                    },
                    modifier = Modifier.weight(1f)
                )

                // 2. পণ্য (Products)
                NavSlot(
                    icon = Icons.Default.Inventory2,
                    label = "পণ্য",
                    isSelected = currentScreen == AppScreen.PRODUCTS,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onNavigate(AppScreen.PRODUCTS)
                    },
                    modifier = Modifier.weight(1f)
                )

                // 3. বিক্রয় (opens the cash counter with the scanner ready)
                NavSlot(
                    icon = Icons.Default.PointOfSale,
                    label = "বিক্রয়",
                    isSelected = currentScreen == AppScreen.POS,
                    isFeatured = true,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onNavigate(AppScreen.POS)
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onFabLongPress()
                    },
                    modifier = Modifier.weight(1f)
                )

                // QR scanning remains a dedicated shortcut alongside regular POS.
                NavSlot(
                    icon = Icons.Default.QrCodeScanner,
                    label = "স্ক্যান",
                    isSelected = false,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onQrScanClick()
                    },
                    modifier = Modifier.weight(1f)
                )

                // 4. খাতা (Due Khata)
                NavSlot(
                    icon = Icons.Default.MenuBook,
                    label = "খাতা",
                    isSelected = currentScreen == AppScreen.DUE_KHATA,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onNavigate(AppScreen.DUE_KHATA)
                    },
                    modifier = Modifier.weight(1f)
                )

                // 5. রিপোর্ট (Reports) - শুধুমাত্র দোকান মালিকের জন্য
                if (!isStaff) {
                    NavSlot(
                        icon = Icons.Default.BarChart,
                        label = "রিপোর্ট",
                        isSelected = currentScreen == AppScreen.REPORTS,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onNavigate(AppScreen.REPORTS)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavSlot(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isFeatured: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconTint by animateColorAsState(
        targetValue = when {
            isFeatured -> Color.White
            isSelected -> Brand500
            else -> Ink2Light
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_icon_tint"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> Brand500
            else -> Ink2Light
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_text_color"
    )

    val pillBackground by animateColorAsState(
        targetValue = when {
            isFeatured -> Brand500
            isSelected -> Brand500.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_pill_bg"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillBackground)
                    .then(
                        Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(if (isFeatured) 22.dp else 21.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
