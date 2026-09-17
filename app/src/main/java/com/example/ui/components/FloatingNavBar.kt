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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingNavBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onFabLongPress: () -> Unit,
    onQrScanClick: () -> Unit = { onNavigate(AppScreen.POS) },
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
        // Pill container: 66dp tall with Radius.pill, soft shadow & subtle border
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(Radius.pill),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(Radius.pill)
                ),
            shape = RoundedCornerShape(Radius.pill),
            color = MaterialTheme.colorScheme.surface,
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

                // 3. বিক্রয় (Regular POS Cash Counter)
                NavSlot(
                    icon = Icons.Default.PointOfSale,
                    label = "বিক্রয়",
                    isSelected = currentScreen == AppScreen.POS,
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

                // 4. QR সেল (Dedicated QR & Barcode Scan Sale)
                NavSlot(
                    icon = Icons.Default.QrCodeScanner,
                    label = "QR সেল",
                    isSelected = false,
                    isFeatured = true,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onQrScanClick()
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onFabLongPress()
                    },
                    modifier = Modifier.weight(1.05f)
                )

                // 5. খাতা (Due Khata)
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

                // 6. রিপোর্ট (Reports)
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
            isFeatured -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_icon_tint"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFeatured -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_text_color"
    )

    val pillBackground by animateColorAsState(
        targetValue = when {
            isFeatured -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(pillBackground)
                .then(
                    if (isFeatured) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(Radius.sm)
                        )
                    } else Modifier
                )
                .padding(horizontal = 3.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(if (isFeatured) 22.dp else 21.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected || isFeatured) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
