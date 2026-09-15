package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.FloatingNavBar
import com.example.ui.components.QuickActionBottomSheet
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*

@Composable
fun DokanProApp(
    viewModel: PaponViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val shopConfig by viewModel.shopConfig.collectAsState()
    val isPinUnlocked by viewModel.isPinUnlocked.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val quickActionsOpen by viewModel.quickActionsOpen.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val showUpdateDialogEvent by viewModel.showUpdateDialogEvent.collectAsState()

    val isAppActivated by viewModel.isAppActivated.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val remainingDemoMillis by viewModel.remainingDemoMillis.collectAsState()

    // Handle toast messages
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Handle system back navigation
    BackHandler(enabled = isAppActivated && currentScreen != AppScreen.DASHBOARD) {
        when (currentScreen) {
            AppScreen.RECEIPT -> viewModel.navigateTo(AppScreen.DASHBOARD)
            AppScreen.PURCHASES, AppScreen.EXPENSES, AppScreen.BACKUP, AppScreen.SETTINGS -> {
                viewModel.navigateTo(AppScreen.DASHBOARD)
            }
            else -> viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    if (!isAppActivated) {
        AppActivationScreen(viewModel = viewModel)
    } else if (!shopConfig.isOnboardingCompleted) {
        OnboardingScreen(viewModel = viewModel, config = shopConfig)
    } else if (shopConfig.pinEnabled && !isPinUnlocked) {
        PinLockScreen(viewModel = viewModel, config = shopConfig)
    } else {
        Scaffold(
            topBar = {
                if (isDemoMode) {
                    DemoCountdownTopBar(
                        remainingMillis = remainingDemoMillis,
                        onBuyClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://webixsolution.store/product/dokan-pro")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "ওয়েবসাইট: webixsolution.store/product/dokan-pro", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            bottomBar = {
                // Floating navigation bar only on primary 5 tabs
                val showNavBar = when (currentScreen) {
                    AppScreen.DASHBOARD,
                    AppScreen.PRODUCTS,
                    AppScreen.POS,
                    AppScreen.DUE_KHATA,
                    AppScreen.REPORTS -> true
                    else -> false
                }

                if (showNavBar) {
                    FloatingNavBar(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) },
                        onFabLongPress = { viewModel.toggleQuickActions() }
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val isReduced = com.example.ui.theme.rememberReducedMotion()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val slidePx = with(density) { 12.dp.roundToPx() }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (isReduced) {
                            fadeIn(animationSpec = com.example.ui.theme.Motion.MotionFast)
                                .togetherWith(fadeOut(animationSpec = com.example.ui.theme.Motion.MotionFast))
                        } else {
                            val isDeeper = targetState.ordinal > initialState.ordinal
                            if (isDeeper) {
                                (slideInHorizontally(animationSpec = com.example.ui.theme.Motion.MotionStandardIntOffset) { slidePx } + fadeIn(animationSpec = com.example.ui.theme.Motion.MotionStandard))
                                    .togetherWith(slideOutHorizontally(animationSpec = com.example.ui.theme.Motion.MotionStandardIntOffset) { -slidePx } + fadeOut(animationSpec = com.example.ui.theme.Motion.MotionStandard))
                            } else {
                                (slideInHorizontally(animationSpec = com.example.ui.theme.Motion.MotionStandardIntOffset) { -slidePx } + fadeIn(animationSpec = com.example.ui.theme.Motion.MotionStandard))
                                    .togetherWith(slideOutHorizontally(animationSpec = com.example.ui.theme.Motion.MotionStandardIntOffset) { slidePx } + fadeOut(animationSpec = com.example.ui.theme.Motion.MotionStandard))
                            }
                        }
                    },
                    label = "ScreenTransition",
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    when (screen) {
                        AppScreen.DASHBOARD -> {
                            DashboardScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                        }
                        AppScreen.PRODUCTS -> {
                            ProductsScreen(
                                viewModel = viewModel,
                                config = shopConfig
                            )
                        }
                        AppScreen.POS -> {
                            PosScreen(
                                viewModel = viewModel,
                                config = shopConfig
                            )
                        }
                        AppScreen.DUE_KHATA -> {
                            DueKhataScreen(
                                viewModel = viewModel,
                                config = shopConfig
                            )
                        }
                        AppScreen.REPORTS -> {
                            ReportsScreen(
                                viewModel = viewModel,
                                config = shopConfig
                            )
                        }
                        AppScreen.RECEIPT -> {
                            ReceiptScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onNewSale = { viewModel.navigateTo(AppScreen.POS) },
                                onGoHome = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.PURCHASES -> {
                            PurchasesScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.EXPENSES -> {
                            ExpensesScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.BACKUP -> {
                            BackupScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                config = shopConfig,
                                onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                            )
                        }
                    }
                }

                // Quick Action Sheet triggered from FAB long-press
                if (quickActionsOpen) {
                    QuickActionBottomSheet(
                        onDismiss = { viewModel.closeQuickActions() },
                        onQuickSale = { viewModel.navigateTo(AppScreen.POS) },
                        onBarcodeScan = { viewModel.navigateTo(AppScreen.POS) },
                        onCollectDue = { viewModel.navigateTo(AppScreen.DUE_KHATA) },
                        onAddExpense = { viewModel.navigateTo(AppScreen.EXPENSES) },
                        onAddProduct = { viewModel.navigateTo(AppScreen.PRODUCTS) }
                    )
                }
            }
        }
    }

    // In-App Auto Update Dialog
    if (showUpdateDialogEvent && appUpdateInfo.isUpdateAvailable) {
        UpdateDialog(
            updateInfo = com.example.util.AppUpdateInfo(
                versionCode = appUpdateInfo.latestVersionCode,
                versionName = appUpdateInfo.latestVersionName,
                downloadUrl = appUpdateInfo.apkDownloadUrl,
                releaseNotes = appUpdateInfo.updateNotes
            ),
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}

@Composable
fun PaponApp(viewModel: PaponViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    DokanProApp(viewModel = viewModel)
}

@Composable
private fun DemoCountdownTopBar(
    remainingMillis: Long,
    onBuyClick: () -> Unit
) {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    val bengaliTime = com.example.util.Formatters.toBengaliDigits(timeFormatted)

    Surface(
        color = Color(0xFF0F172A), // Dark slate premium tone
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24), // Vibrant Amber
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ফ্রি ডেমো • বাকি: $bengaliTime মিনিট",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "৭ দিনের ডামি ডাটা সংযুক্ত আছে",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Button(
                onClick = onBuyClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A) // Vibrant Green
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "লাইসেন্স কিনুন (৳৪৯০)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

