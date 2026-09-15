package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.util.Formatters

@Composable
fun TopHeader(
    config: ShopConfig,
    onOpenMoreMenu: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit = {},
    onToggleTheme: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Store branding (Storefront icon + Shop Name + Role badge + Date)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
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

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = config.shopName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // User role badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (config.userRole == "owner") "মালিক" else "কর্মচারী",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = Formatters.formatBengaliDate(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Day/Night Toggle + Supabase Sync Badge + Three dots menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Day / Night quick mode toggle button
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "ডে মোড চালু করুন" else "নাইট মোড চালু করুন",
                            tint = if (isDark) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Subtle refresh indicator when actively updating (No "Cloud Sync" text)
                    if (isSyncing) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.testTag("sync_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "হালনাগাদ হচ্ছে",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .rotate(rotation)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "হালনাগাদ হচ্ছে...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // More options menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .testTag("top_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "মেনু",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isDark) "☀️ ডে মোড চালু করুন" else "🌙 নাইট মোড চালু করুন") },
                                leadingIcon = {
                                    Icon(
                                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleTheme()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("তথ্য হালনাগাদ (রিফ্রেশ)") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = {
                                    showMenu = false
                                    onSyncNow()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("ক্রয় ও সাপ্লায়ার") },
                                leadingIcon = { Icon(Icons.Default.LocalShipping, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenMoreMenu(AppScreen.PURCHASES)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("দোকানের খরচ") },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenMoreMenu(AppScreen.EXPENSES)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ব্যাকআপ ও ডেটা রিকভারি") },
                                leadingIcon = { Icon(Icons.Default.Backup, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenMoreMenu(AppScreen.BACKUP)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("সেটিংস") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
