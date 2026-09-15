package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.DokanProApp
import com.example.ui.PaponViewModel
import com.example.ui.theme.DokanProTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PaponViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash visible only while the first data load is in flight — no artificial delay
        splashScreen.setKeepOnScreenCondition {
            viewModel.isSyncing.value
        }

        enableEdgeToEdge()

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
}
