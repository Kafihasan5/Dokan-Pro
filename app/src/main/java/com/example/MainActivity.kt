package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.AppScreen
import com.example.ui.DokanProApp
import com.example.ui.PaponViewModel
import com.example.ui.theme.DokanProTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PaponViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash visible only while the first data load is in flight — no artificial delay
        splashScreen.setKeepOnScreenCondition {
            viewModel.isSyncing.value
        }

        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleNotificationIntent(intent)

        setContent {
            val config by viewModel.shopConfig.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (config.themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            DokanProTheme(darkTheme = isDark) {
                DokanProApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getStringExtra("NAVIGATE_TO") == "LIVE_SUPPORT") {
            viewModel.navigateTo(AppScreen.LIVE_SUPPORT)
        }
    }
}
