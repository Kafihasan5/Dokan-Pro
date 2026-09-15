package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.PaponApp
import com.example.ui.PaponViewModel
import com.example.ui.theme.PaponShopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: PaponViewModel = viewModel()
            val config by viewModel.shopConfig.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (config.themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            PaponShopTheme(darkTheme = isDark) {
                PaponApp(viewModel = viewModel)
            }
        }
    }
}

