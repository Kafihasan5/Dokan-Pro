package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.PaponViewModel
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanSecondaryButton
import com.example.ui.components.DokanTextField
import com.example.ui.theme.*

@Composable
fun AppActivationScreen(
    viewModel: PaponViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Primary Role Selector: "owner" (মালিক মোড) or "staff" (কর্মচারী মোড)
    var selectedRoleMode by remember { mutableStateOf("owner") }

    // Owner Sub-Tab: "restore" (দোকান রিস্টোর) or "activate" (নতুন লাইসেন্স সক্রিয়করণ)
    var ownerSubTab by remember { mutableStateOf("restore") }

    // Owner - Cloud Restore States
    var restoreShopCodeOrEmail by remember { mutableStateOf("") }
    var restoreMasterPin by remember { mutableStateOf("") }
    var isRestoringCloud by remember { mutableStateOf(false) }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }

    // Owner - License Activation States
    var emailInput by remember { mutableStateOf("") }
    val isActivating by viewModel.isActivating.collectAsState()
    val activationError by viewModel.activationError.collectAsState()
    val isDemoUsed by viewModel.isDemoUsed.collectAsState()
    val isDemoExpired by viewModel.isDemoExpired.collectAsState()

    // Staff - Secure Join States
    var staffShopCodeInput by remember { mutableStateOf("") }
    var staffPinInput by remember { mutableStateOf("") }
    var staffNameInput by remember { mutableStateOf("") }
    var isStaffJoining by remember { mutableStateOf(false) }
    var staffJoinError by remember { mutableStateOf<String?>(null) }

    val deviceId = remember { viewModel.getDeviceId() }
    var copiedRecently by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Sophisticated background gradient: deep obsidian emerald
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF041812),
            Color(0xFF08261D),
            Color(0xFF04130E)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))

            // =========================================================================
            // 🌟 1. ELEGANT APP BRAND HEADER
            // =========================================================================
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Brand500,
                                Brand700
                            )
                        )
                    )
                    .border(1.5.dp, Brand300.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = "App Logo",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "দোকান প্রো",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "সহজ ও নির্ভরযোগ্য ডিজিটাল হিসাব খাতা",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            // Trust & Official Badge
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Brand500.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.40f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Brand300,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "অফিসিয়াল সফটওয়্যার • v${BuildConfig.VERSION_NAME}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Brand100
                    )
                }
            }

            // =========================================================================
            // 🌟 2. PRIMARY ROLE SELECTOR: 👑 দোকান মালিক vs 👤 কর্মচারী
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 👑 দোকান মালিক ট্যাব
                    Surface(
                        onClick = { selectedRoleMode = "owner" },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (selectedRoleMode == "owner") Brand500 else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "মালিক মোড",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }

                    // 👤 কর্মচারী ট্যাব
                    Surface(
                        onClick = { selectedRoleMode = "staff" },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (selectedRoleMode == "staff") Success else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "কর্মচারী মোড",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 🌟 3. MAIN CONTENT (OWNER vs STAFF)
            // =========================================================================
            AnimatedContent(
                targetState = selectedRoleMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "RoleModeTransition"
            ) { roleMode ->
                if (roleMode == "owner") {
                    // =================================================================
                    // 👑 OWNER WORKSPACE
                    // =================================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Sub-selector for Owner: 🔄 দোকান রিস্টোর vs 🔑 নতুন অ্যাক্টিভেশন
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { ownerSubTab = "restore" },
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = if (ownerSubTab == "restore") Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = if (ownerSubTab == "restore") Brand300 else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "দোকান রিস্টোর",
                                            fontSize = 13.sp,
                                            fontWeight = if (ownerSubTab == "restore") FontWeight.Bold else FontWeight.Medium,
                                            color = if (ownerSubTab == "restore") Color.White else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { ownerSubTab = "activate" },
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = if (ownerSubTab == "activate") Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = if (ownerSubTab == "activate") Brand300 else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "নতুন লাইসেন্স",
                                            fontSize = 13.sp,
                                            fontWeight = if (ownerSubTab == "activate") FontWeight.Bold else FontWeight.Medium,
                                            color = if (ownerSubTab == "activate") Color.White else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // SUB-TAB 1: 🔄 CLOUD SHOP RESTORE
                        if (ownerSubTab == "restore") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Radius.lg),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(Brand500.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = Brand500,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "ক্লাউড থেকে দোকান রিস্টোর",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "আগের দোকানের পণ্য, কাস্টমার ও সমস্ত হিসাব ফিরিয়ে আনুন",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    DokanTextField(
                                        value = restoreShopCodeOrEmail,
                                        onValueChange = {
                                            restoreShopCodeOrEmail = it
                                            restoreErrorMessage = null
                                        },
                                        label = "দোকান কোড অথবা নিবন্ধিত ইমেইল",
                                        placeholder = "যেমন: SHOP-XXXXXX বা owner@gmail.com",
                                        leadingIcon = Icons.Default.Storefront
                                    )

                                    DokanTextField(
                                        value = restoreMasterPin,
                                        onValueChange = {
                                            if (it.length <= 8) restoreMasterPin = it
                                            restoreErrorMessage = null
                                        },
                                        label = "মাস্টার সিকিউরিটি পিন (৪-৬ ডিজিট)",
                                        placeholder = "যেমন: 1234",
                                        keyboardType = KeyboardType.NumberPassword,
                                        visualTransformation = PasswordVisualTransformation(),
                                        leadingIcon = Icons.Default.Lock
                                    )

                                    AnimatedVisibility(visible = restoreErrorMessage != null) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(Radius.sm),
                                            color = StatusDanger.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = StatusDanger,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = restoreErrorMessage ?: "",
                                                    fontSize = 12.sp,
                                                    color = StatusDanger,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    DokanPrimaryButton(
                                        text = if (isRestoringCloud) "দোকানের তথ্য নামানো হচ্ছে..." else "দোকান রিস্টোর করে প্রবেশ করুন",
                                        isLoading = isRestoringCloud,
                                        enabled = !isRestoringCloud && restoreShopCodeOrEmail.isNotBlank() && restoreMasterPin.isNotBlank(),
                                        onClick = {
                                            keyboardController?.hide()
                                            restoreErrorMessage = null
                                            isRestoringCloud = true
                                            viewModel.secureRestoreOwnerShop(
                                                restoreShopCodeOrEmail.trim(),
                                                restoreMasterPin.trim()
                                            ) { success, msg ->
                                                isRestoringCloud = false
                                                if (!success) {
                                                    restoreErrorMessage = msg
                                                }
                                            }
                                        }
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = Brand500.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = Brand500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "🔒 ১০০% সুরক্ষিত: সঠিক মাস্টার পিন যাচাই ছাড়া ক্লাউড থেকে কোনো তথ্য উন্মুক্ত করা হয় না।",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // SUB-TAB 2: 🔑 NEW LICENSE ACTIVATION
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(Radius.lg),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(Brand500.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VpnKey,
                                                contentDescription = null,
                                                tint = Brand500,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "লাইসেন্স দিয়ে অ্যাপ সক্রিয় করুন",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "ক্রয়কৃত লাইসেন্স ইমেইল দিয়ে নতুন দোকান শুরু করুন",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    DokanTextField(
                                        value = emailInput,
                                        onValueChange = {
                                            emailInput = it
                                            if (activationError != null) viewModel.clearActivationError()
                                        },
                                        label = "নিবন্ধিত ইমেইল এড্রেস",
                                        placeholder = "যেমন: yourname@gmail.com",
                                        keyboardType = KeyboardType.Email,
                                        leadingIcon = Icons.Default.Email,
                                        trailingIcon = {
                                            if (emailInput.isNotEmpty()) {
                                                IconButton(onClick = { emailInput = "" }) {
                                                    Icon(
                                                        Icons.Default.Clear,
                                                        contentDescription = "Clear",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    AnimatedVisibility(visible = activationError != null) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(Radius.sm),
                                            color = StatusDanger.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(Spacing.sm),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = StatusDanger,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = activationError ?: "",
                                                    fontSize = 12.sp,
                                                    color = StatusDanger,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }

                                    DokanPrimaryButton(
                                        text = if (isActivating) "যাচাই করা হচ্ছে..." else "অ্যাপ সক্রিয় করে প্রবেশ করুন",
                                        isLoading = isActivating,
                                        enabled = emailInput.isNotBlank() && !isActivating,
                                        onClick = {
                                            keyboardController?.hide()
                                            viewModel.activateApp(emailInput.trim())
                                        }
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = Brand500.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Brand500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ইমেইল সক্রিয় হওয়ার পর আপনি নতুন দোকান তৈরি ও কাস্টমাইজ করার সুযোগ পাবেন।",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // FREE DEMO / TRIAL CARD
                        if (!isDemoUsed || !isDemoExpired) {
                            Surface(
                                shape = RoundedCornerShape(Radius.md),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Timelapse,
                                                contentDescription = null,
                                                tint = Gold500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "১ দিনের ফ্রি ডেমো টেস্ট (২৪ ঘণ্টা)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "সকল প্রিমিয়াম ফিচার সম্পূর্ণ ফ্রিতে যাচাই করুন",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.startOneHourDemo() },
                                        enabled = !isActivating,
                                        shape = RoundedCornerShape(Radius.sm),
                                        colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "ফ্রি ডেমো",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // LICENSE PURCHASE CARD (Webix Solution Official Link)
                        Surface(
                            shape = RoundedCornerShape(Radius.md),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "আজীবন মেয়াদের অফিসিয়াল লাইসেন্স",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Webix Solution স্টোর থেকে সরাসরি লাইসেন্স সংগ্রহ করুন",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                                DokanSecondaryButton(
                                    text = "লাইসেন্স নিন",
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://webixsolution.store/product/dokan-pro")
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                context,
                                                "ব্রাউজারে ভিজিট করুন: webixsolution.store/product/dokan-pro",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // =================================================================
                    // 👤 STAFF WORKSPACE
                    // =================================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.lg),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(Radius.sm))
                                            .background(Success.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = null,
                                            tint = Success,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "কর্মচারী হিসেবে যুক্ত হোন",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "মালিকের দেওয়া কোড ও কর্মচারী পিন দিয়ে প্রবেশ করুন",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = Success
                                    ) {
                                        Text(
                                            text = "ফ্রি এক্সেস",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                DokanTextField(
                                    value = staffShopCodeInput,
                                    onValueChange = {
                                        staffShopCodeInput = it
                                        staffJoinError = null
                                    },
                                    label = "দোকান কোড অথবা মালিকের ইমেইল",
                                    placeholder = "যেমন: SHOP-XXXXXX বা owner@gmail.com",
                                    leadingIcon = Icons.Default.Storefront
                                )

                                DokanTextField(
                                    value = staffPinInput,
                                    onValueChange = {
                                        if (it.length <= 6) staffPinInput = it
                                        staffJoinError = null
                                    },
                                    label = "কর্মচারী পিন (৪ ডিজিট)",
                                    placeholder = "যেমন: 0000",
                                    keyboardType = KeyboardType.NumberPassword,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = Icons.Default.Lock
                                )

                                DokanTextField(
                                    value = staffNameInput,
                                    onValueChange = {
                                        staffNameInput = it
                                        staffJoinError = null
                                    },
                                    label = "আপনার নাম (ঐচ্ছিক)",
                                    placeholder = "যেমন: মোঃ করিম",
                                    leadingIcon = Icons.Default.Person
                                )

                                AnimatedVisibility(visible = staffJoinError != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = StatusDanger.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = StatusDanger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = staffJoinError ?: "",
                                                fontSize = 12.sp,
                                                color = StatusDanger,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                DokanPrimaryButton(
                                    text = if (isStaffJoining) "দোকানে যুক্ত হওয়া হচ্ছে..." else "কর্মচারী হিসেবে প্রবেশ করুন",
                                    isLoading = isStaffJoining,
                                    enabled = !isStaffJoining && staffShopCodeInput.isNotBlank() && staffPinInput.isNotBlank(),
                                    onClick = {
                                        keyboardController?.hide()
                                        staffJoinError = null
                                        isStaffJoining = true
                                        viewModel.secureJoinAsStaff(
                                            staffShopCodeInput.trim(),
                                            staffPinInput.trim(),
                                            staffNameInput.trim()
                                        ) { success, msg ->
                                            isStaffJoining = false
                                            if (!success) {
                                                staffJoinError = msg
                                            }
                                        }
                                    }
                                )

                                // Privacy reassurance notice
                                Surface(
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = Brand500.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Brand500,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "🔒 গোপনীয়তা রক্ষা: কর্মচারীরা শুধুমাত্র নিজস্ব বিক্রয় ও পণ্য স্টক দেখতে পারবেন। দোকানের মোট লাভ বা মালিকের ব্যক্তিগত হিসাব সম্পূর্ণ সুরক্ষিত থাকবে।",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 🌟 4. FOOTER: DEVICE ID & SUPPORT
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(deviceId))
                        copiedRecently = true
                        Toast.makeText(context, "ডিভাইস আইডি কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (copiedRecently) "কপি হয়েছে! ✓ $deviceId" else "ডিভাইস আইডি: $deviceId",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (copiedRecently) Gold500 else Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Device ID",
                            modifier = Modifier.size(14.dp),
                            tint = if (copiedRecently) Gold500 else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Text(
                    text = "সহায়তার জন্য Webix Solution সাপোর্টে যোগাযোগ করুন",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
