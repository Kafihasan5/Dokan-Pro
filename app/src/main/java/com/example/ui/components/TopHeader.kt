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
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
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
    onBuyLicenseClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDemoDialog by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (config.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

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
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = 10.dp, bottom = Spacing.sm),
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "দোকানের লোগো",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.md))

                    Column {
                        // Line 1: Shop Name in titleLarge
                        Text(
                            text = config.shopName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                                        if (isOwner) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.dokanColors.surfaceAlt
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isOwner) "মালিক" else "কর্মচারী",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // IF DEMO MODE: Sleek compact clickable chip
                            if (isDemoMode) {
                                val totalSeconds = (remainingDemoMillis / 1000).coerceAtLeast(0)
                                val minutes = totalSeconds / 60
                                val seconds = totalSeconds % 60
                                val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
                                val bengaliTime = Formatters.toBengaliDigits(timeFormatted)

                                Surface(
                                    onClick = { showDemoDialog = true },
                                    shape = RoundedCornerShape(Radius.pill),
                                    color = Color(0xFFFEF3C7),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "ডেমো: $bengaliTime",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }

                            // Live sync indicator if syncing
                            if (isSyncing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("sync_status_badge")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "হালনাগাদ হচ্ছে",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .rotate(rotation)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "সিঙ্ক...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Date in labelSmall
                            Text(
                                text = "• " + Formatters.formatBengaliDate(System.currentTimeMillis()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right: 40dp Icon Buttons with circular ripple (Theme Toggle, Refresh, Overflow Menu)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Theme toggle 40dp icon button
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "ডে মোড চালু করুন" else "নাইট মোড চালু করুন",
                            tint = if (isDark) MaterialTheme.dokanColors.gold else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Refresh 40dp icon button
                    IconButton(
                        onClick = onSyncNow,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("sync_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "তথ্য হালনাগাদ (রিফ্রেশ)",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                    }

                    // More options menu button (40dp with Radius.md restyled menu)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .testTag("top_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "মেনু",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
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
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "লাইসেন্স কিনুন (৳৪৯০)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
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

    if (showDemoDialog) {
        val totalSeconds = (remainingDemoMillis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
        val bengaliTime = Formatters.toBengaliDigits(timeFormatted)

        AlertDialog(
            onDismissRequest = { showDemoDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "১ ঘণ্টার ফ্রি ডেমো মোড",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Color(0xFFFEF3C7).copy(alpha = 0.6f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏱️ বাকি সময়: $bengaliTime মিনিট",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "• ২০টি স্যাম্পল পণ্য ও ক্যাটাগরি সংযুক্ত\n• ৭ দিনের বাস্তবসম্মত বেচাকেনা ও বাকির খাতা\n• অ্যাপের সমস্ত ফিচারের পূর্ণ অ্যাক্সেস",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "ডেমো মেয়াদ শেষ হওয়ার আগে মাত্র ৳৪৯০ টাকায় আজীবন লাইসেন্স সংগ্রহ করে নিয়মিত ব্যবহার করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDemoDialog = false
                        onBuyLicenseClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(Radius.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("লাইসেন্স কিনুন (৳৪৯০)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }
}
