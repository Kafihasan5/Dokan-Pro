package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.ShopConfig
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.Brand500
import com.example.ui.theme.Brand700
import com.example.ui.theme.Brand900
import com.example.ui.theme.Gold500
import com.example.ui.theme.dokanColors
import com.example.util.Formatters

@Composable
fun TopHeader(
    config: ShopConfig,
    onOpenMoreMenu: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    isDemoMode: Boolean = false,
    remainingDemoMillis: Long = 0L,
    onBuyLicenseClick: () -> Unit = {},
    unreadNotificationsCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    isUpdateAvailable: Boolean = false,
    onCheckUpdate: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (config.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val bellTint = if (unreadNotificationsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val badgeDangerColor = MaterialTheme.dokanColors.danger

    // Infinite rotation animation when syncing
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin_angle"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        val isOwner = config.userRole == "owner"

        Surface(
            color = Brand700,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.lg)
                        .padding(top = 12.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Left: Two-line layout with shop icon + shop name on top, owner/staff badge + sync indicator + date under it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Brand900, Brand500))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "দোকানের লোগো",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.md))

                    Column {
                        // Line 1: Shop Name in titleLarge
                        Text(
                            text = config.shopName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Line 2: Small row with owner/staff badge, live sync indicator, and date in labelSmall
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            // Pill chip badge: primaryContainer for মালিক, surfaceAlt for কর্মচারী
                            val isOwner = config.userRole == "owner"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(
                                        if (isOwner) MaterialTheme.dokanColors.goldContainer
                                        else Color.White.copy(alpha = 0.16f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isOwner) "মালিক" else "কর্মচারী",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOwner) MaterialTheme.dokanColors.gold
                                    else Color.White
                                )
                            }

                            // Live sync indicator if syncing
                            if (isSyncing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("sync_status_badge")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "হালনাগাদ হচ্ছে",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .rotate(rotation)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "সিঙ্ক...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Date in labelSmall (single line, no wrapping)
                            Text(
                                text = "• " + Formatters.formatBengaliDate(System.currentTimeMillis()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Compact rounded controls keep the shop identity as the visual focus.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Theme toggle 40dp icon button
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "ডে মোড চালু করুন" else "নাইট মোড চালু করুন",
                            tint = if (isDark) Gold500 else Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Notification Bell with Badge
                    Box {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("notification_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge(
                                            containerColor = badgeDangerColor,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (unreadNotificationsCount > 9) "9+" else unreadNotificationsCount.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "বিজ্ঞপ্তি ও বার্তা",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // More options menu button (40dp with Radius.md restyled menu)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                                .testTag("top_menu_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (isUpdateAvailable) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "মেনু",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 4.dp)
                        ) {
                            if (isDemoMode) {
                                val totalSeconds = (remainingDemoMillis / 1000).coerceAtLeast(0)
                                val minutes = totalSeconds / 60
                                val seconds = totalSeconds % 60
                                val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
                                val bengaliTime = Formatters.toBengaliDigits(timeFormatted)

                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "লাইসেন্স কিনুন",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF16A34A)
                                            )
                                            Text(
                                                text = "ফ্রি ডেমো বাকি: $bengaliTime মিনিট",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A)
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm),
                                    onClick = {
                                        showMenu = false
                                        onBuyLicenseClick()
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                            DropdownMenuItem(
                                text = { Text(if (isDark) "☀️ ডে মোড চালু করুন" else "🌙 নাইট মোড চালু করুন", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = if (isDark) MaterialTheme.dokanColors.gold else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.sm),
                                onClick = {
                                    showMenu = false
                                    onToggleTheme()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("তথ্য হালনাগাদ (রিফ্রেশ)", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.sm),
                                onClick = {
                                    showMenu = false
                                    onSyncNow()
                                }
                            )
                            if (isOwner) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("ক্রয় ও সাপ্লায়ার", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocalShipping,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm),
                                    onClick = {
                                        showMenu = false
                                        onOpenMoreMenu(AppScreen.PURCHASES)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("দোকানের খরচ", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.dokanColors.warning
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm),
                                    onClick = {
                                        showMenu = false
                                        onOpenMoreMenu(AppScreen.EXPENSES)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ব্যাকআপ ও ডেটা রিকভারি", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Backup,
                                            contentDescription = null,
                                            tint = MaterialTheme.dokanColors.info
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm),
                                    onClick = {
                                        showMenu = false
                                        onOpenMoreMenu(AppScreen.BACKUP)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("লাইভ সাপোর্ট ও চ্যাট", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SupportAgent,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.sm),
                                onClick = {
                                    showMenu = false
                                    onOpenMoreMenu(AppScreen.LIVE_SUPPORT)
                                }
                            )

                            // App Update check option (accessible to BOTH staff and owner)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isUpdateAvailable) "নতুন আপডেট এসেছে" else "অ্যাপ আপডেট চেক করুন",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isUpdateAvailable) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isUpdateAvailable) {
                                            Surface(
                                                shape = RoundedCornerShape(Radius.pill),
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                Text(
                                                    text = "নতুন",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = if (isUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.sm),
                                onClick = {
                                    showMenu = false
                                    onCheckUpdate()
                                }
                            )
                            if (isOwner) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("সেটিংস", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = Spacing.sm),
                                    onClick = {
                                        showMenu = false
                                        onOpenMoreMenu(AppScreen.SETTINGS)
                                    }
                                )
                            }
                        }
                    }
                }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(Gold500.copy(alpha = 0.8f), Brand500.copy(alpha = 0.42f), Color.Transparent)))
                )
            }
        }

        // Optional Announcement / Notice bar from remote config
        if (config.noticeMessage.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = config.noticeMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
