package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
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

                // Floating navigation bar truly floating over content at bottom
                val showNavBar = when (currentScreen) {
                    AppScreen.DASHBOARD,
                    AppScreen.PRODUCTS,
                    AppScreen.POS,
                    AppScreen.DUE_KHATA,
                    AppScreen.REPORTS -> true
                    else -> false
                }

                if (showNavBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        FloatingNavBar(
                            currentScreen = currentScreen,
                            onNavigate = { viewModel.navigateTo(it) },
                            onFabLongPress = { viewModel.toggleQuickActions() }
                        )
                    }
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

