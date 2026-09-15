package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    } else if (shopConfig.pinEnabled && !isPinUnlocked) {
        PinLockScreen(viewModel = viewModel, config = shopConfig)
    } else {
        Scaffold(
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
                when (currentScreen) {
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
