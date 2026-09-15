package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FloatingNavBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onFabLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Pill container (Height 64dp, radius 32dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.16f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Slot 1: Home
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

                // Slot 2: Products
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

                // Spacer for Center Raised FAB
                Spacer(modifier = Modifier.weight(1.2f))

                // Slot 4: Due Khata
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

                // Slot 5: Reports
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

        // Slot 3: Center Raised Point of Sale FAB (56dp circle raised 12dp above the bar)
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(58.dp)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .combinedClickable(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onNavigate(AppScreen.POS)
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onFabLongPress()
                    }
                )
                .testTag("pos_center_fab"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PointOfSale,
                    contentDescription = "বিক্রয়",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun NavSlot(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
