package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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

/**
 * 🌟 Dokan-Pro Activation & Welcome Flow
 * Official Software by Webix Solution
 *
 * Screen 1 (Landing):
 *   - Webix Solution Official Branding
 *   - 2 Clear Roles: 👑 "আমি দোকান মালিক" and 👤 "আমি কর্মচারী"
 *   - 🎁 1-Day Free Trial (২৪ ঘণ্টা ফ্রি ট্রায়াল) Option
 *   - 🛍️ Webix Solution Official License Purchase Card
 *
 * Screen 2 (Owner Flow):
 *   - Option 1 (First): 🔑 "নতুন লাইসেন্স সক্রিয় করুন" (Email Activation)
 *   - Option 2 (Second): 🔄 "পূর্বের দোকান ও ক্লাউড ডাটা পুনরুদ্ধার" (Cloud Restore)
 *
 * Screen 3 (Staff Flow):
 *   - 👤 Shop Code + Staff PIN Login
 */
@Composable
fun AppActivationScreen(
    viewModel: PaponViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Navigation Flow: "role_select" (ভূমিকা নির্বাচন), "owner_flow" (মালিক অপশন), "staff_flow" (কর্মচারী লগইন)
    var activeFlow by remember { mutableStateOf("role_select") }

    // Owner Sub-Tab: "license" (নতুন লাইসেন্স সক্রিয়করণ - ১ম) vs "restore" (পূর্বের দোকান পুনরুদ্ধার - ২য়)
    var ownerChoice by remember { mutableStateOf("license") }

    // Owner - License Activation States
    var emailInput by remember { mutableStateOf("") }
    val isActivating by viewModel.isActivating.collectAsState()
    val activationError by viewModel.activationError.collectAsState()
    val isDemoUsed by viewModel.isDemoUsed.collectAsState()
    val isDemoExpired by viewModel.isDemoExpired.collectAsState()

    // Owner - Cloud Restore States
    var restoreShopCodeOrEmail by remember { mutableStateOf("") }
    var restoreMasterPin by remember { mutableStateOf("") }
    var isRestoringCloud by remember { mutableStateOf(false) }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }

    // Staff - Secure Join States
    var staffShopCodeInput by remember { mutableStateOf("") }
    var staffPinInput by remember { mutableStateOf("") }
    var staffNameInput by remember { mutableStateOf("") }
    var isStaffJoining by remember { mutableStateOf(false) }
    var staffJoinError by remember { mutableStateOf<String?>(null) }

    val deviceId = remember { viewModel.getDeviceId() }
    var copiedRecently by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Automatically reset scroll position when navigating flows or switching tabs
    LaunchedEffect(activeFlow, ownerChoice) {
        try {
            scrollState.scrollTo(0)
        } catch (_: Throwable) {}
    }

    // Intercept hardware back button when inside a sub-flow
    BackHandler(enabled = activeFlow != "role_select") {
        try {
            activeFlow = "role_select"
        } catch (_: Throwable) {}
    }

    // Modern Deep Obsidian Gradient Background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF031610),
            Color(0xFF07241A),
            Color(0xFF02100C)
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
            // 🌟 1. WEBIX SOLUTION OFFICIAL APP BRAND HEADER
            // =========================================================================
            Box(
                modifier = Modifier
                    .size(76.dp)
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
                    modifier = Modifier.size(42.dp)
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
                    text = "Webix Solution অফিসিয়াল সফটওয়্যার",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand300
                )
            }

            // Trust & Official Version Badge
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Brand500.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Brand300,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Webix Solution • v${BuildConfig.VERSION_NAME}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand100
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // =========================================================================
            // 🌟 2. DYNAMIC SCREEN CONTENT
            // =========================================================================
            AnimatedContent(
                targetState = activeFlow,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "FlowTransition"
            ) { currentFlow ->
                when (currentFlow) {
                    // =================================================================
                    // 🌟 SCREEN 1: LANDING & ROLE SELECTION
                    // =================================================================
                    "role_select" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Text(
                                text = "অ্যাপে প্রবেশ করতে আপনার ভূমিকা বেছে নিন:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.90f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )

                            // 👑 কার্ড ১: আমি দোকান মালিক (I am Shop Owner)
                            Card(
                                onClick = {
                                    try {
                                        ownerChoice = "license" // Default to new license activation first
                                        activeFlow = "owner_flow"
                                    } catch (t: Throwable) {
                                        android.util.Log.e("AppActivation", "Error entering owner flow", t)
                                    }
                                },
                                shape = RoundedCornerShape(Radius.lg),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.5.dp, Brand500.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(Brand700, Brand500))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "আমি দোকান মালিক",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "দোকানের সম্পূর্ণ হিসাব, লাইসেন্স বা ডাটা রিস্টোর",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Go",
                                        tint = Brand500,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 👤 কার্ড ২: আমি কর্মচারী (I am Staff / Employee) - NO 'Free Access' badge
                            Card(
                                onClick = {
                                    try {
                                        activeFlow = "staff_flow"
                                    } catch (t: Throwable) {
                                        android.util.Log.e("AppActivation", "Error entering staff flow", t)
                                    }
                                },
                                shape = RoundedCornerShape(Radius.lg),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.5.dp, Success.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.lg),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(Success, Color(0xFF10B981)))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "আমি কর্মচারী",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "মালিকের দোকানে যুক্ত হয়ে পণ্য বিক্রি ও স্টক পরিচালনা করুন",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Go",
                                        tint = Success,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 🎁 ৩. ফ্রি ট্রায়াল অপশন (১ দিনের ফ্রি ডেমো টেস্ট)
                            if (!isDemoUsed || !isDemoExpired) {
                                Surface(
                                    shape = RoundedCornerShape(Radius.md),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Gold500.copy(alpha = 0.45f)),
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
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "১ দিনের ফ্রি ট্রায়াল টেস্ট (২৪ ঘণ্টা)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "অ্যাপ কেনার আগে সম্পূর্ণ ফ্রিতে সব ফিচার যাচাই করুন",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                try {
                                                    viewModel.startOneHourDemo()
                                                } catch (t: Throwable) {
                                                    android.util.Log.e("AppActivation", "Error in startOneHourDemo", t)
                                                }
                                            },
                                            enabled = !isActivating,
                                            shape = RoundedCornerShape(Radius.sm),
                                            colors = ButtonDefaults.buttonColors(containerColor = Gold500),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "ফ্রি ট্রায়াল",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Brand900
                                            )
                                        }
                                    }
                                }
                            }

                            // 🛍️ ৪. WEBIX SOLUTION অফিসিয়াল লাইসেন্স কার্ড (আজীবন লাইসেন্স ও ব্র্যান্ডিং)
                            Surface(
                                shape = RoundedCornerShape(Radius.lg),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.5.dp, Brand500.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(Brand500.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = Brand300,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Webix Solution অফিসিয়াল লাইসেন্স",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "আজীবন মেয়াদের লাইসেন্স কী (Lifetime License)",
                                                fontSize = 11.sp,
                                                color = Brand300
                                            )
                                        }
                                    }

                                    Text(
                                        text = "দোকানের সমস্ত ফিচার, ক্লাউড ব্যাকআপ ও সার্বক্ষণিক সাপোর্ট পেতে Webix Solution স্টোর থেকে সরাসরি লাইসেন্স সংগ্রহ করুন।",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.80f),
                                        lineHeight = 16.sp
                                    )

                                    Button(
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
                                                    "ওয়েবসাইট ভিজিট করুন: webixsolution.store/product/dokan-pro",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        shape = RoundedCornerShape(Radius.md),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Brand500,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "লাইসেন্স নিন (webixsolution.store)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =================================================================
                    // 🌟 SCREEN 2: 👑 OWNER FLOW (১ম: নতুন লাইসেন্স, ২য়: দোকান পুনরুদ্ধার)
                    // =================================================================
                    "owner_flow" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            // Back Navigation Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable {
                                        try {
                                            activeFlow = "role_select"
                                        } catch (_: Throwable) {}
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Brand300,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "পেছনে যান • ভূমিকা পরিবর্তন",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Brand300
                                )
                            }

                            // Sub-Selector Toggle: অপশন ১ (নতুন লাইসেন্স) প্রথমে, অপশন ২ (দোকান রিস্টোর) দ্বিতীয়তে
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = Color.White.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // অপশন ১: নতুন লাইসেন্স সক্রিয় (FIRST)
                                    Surface(
                                        onClick = {
                                            try {
                                                ownerChoice = "license"
                                            } catch (_: Throwable) {}
                                        },
                                        shape = RoundedCornerShape(Radius.pill),
                                        color = if (ownerChoice == "license") Brand500 else Color.Transparent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "নতুন লাইসেন্স সক্রিয়",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // অপশন ২: পূর্বের দোকান পুনরুদ্ধার (SECOND)
                                    Surface(
                                        onClick = {
                                            try {
                                                ownerChoice = "restore"
                                            } catch (_: Throwable) {}
                                        },
                                        shape = RoundedCornerShape(Radius.pill),
                                        color = if (ownerChoice == "restore") Brand500 else Color.Transparent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "দোকান পুনরুদ্ধার",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // 🔑 OPTION 1 (DEFAULT FIRST): নতুন লাইসেন্স সক্রিয়করণ
                            if (ownerChoice == "license") {
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
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(Radius.sm))
                                                    .background(Brand500.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = Brand500,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "নতুন লাইসেন্স সক্রিয় করুন",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "ক্রয়কৃত লাইসেন্স ইমেইল দিয়ে অ্যাপ সক্রিয় করে নতুন দোকান শুরু করুন",
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
                                            label = "নিবন্ধিত লাইসেন্স ইমেইল এড্রেস",
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
                                            text = if (isActivating) "যাচাই করা হচ্ছে..." else "লাইসেন্স সক্রিয় করে প্রবেশ করুন",
                                            isLoading = isActivating,
                                            enabled = emailInput.isNotBlank() && !isActivating,
                                            onClick = {
                                                try {
                                                    keyboardController?.hide()
                                                    viewModel.activateApp(emailInput.trim())
                                                } catch (t: Throwable) {
                                                    android.util.Log.e("AppActivation", "Error clicking activate button", t)
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
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Brand500,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "ইমেইল সক্রিয় হওয়ার পর আপনি নিজের দোকানের নাম ও তথ্য দিয়ে নতুন দোকান শুরু করতে পারবেন।",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 🔄 OPTION 2: পূর্বের দোকান ও ক্লাউড ডাটা পুনরুদ্ধার (RESTORE)
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
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(Radius.sm))
                                                    .background(Brand500.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = null,
                                                    tint = Brand500,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "পূর্বের দোকান ও ক্লাউড ব্যাকআপ পুনরুদ্ধার",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "নতুন ডিভাইসে আপনার আগের দোকানের সমস্ত পণ্য, কাস্টমার ও বাকি-বিক্রির হিসাব ফিরিয়ে আনুন",
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
                                            text = if (isRestoringCloud) "দোকানের তথ্য নামানো হচ্ছে..." else "দোকানের তথ্য রিস্টোর করুন",
                                            isLoading = isRestoringCloud,
                                            enabled = !isRestoringCloud && restoreShopCodeOrEmail.isNotBlank() && restoreMasterPin.isNotBlank(),
                                            onClick = {
                                                try {
                                                    keyboardController?.hide()
                                                    restoreErrorMessage = null
                                                    isRestoringCloud = true
                                                    viewModel.secureRestoreOwnerShop(
                                                        restoreShopCodeOrEmail.trim(),
                                                        restoreMasterPin.trim()
                                                    ) { success, msg ->
                                                        try {
                                                            isRestoringCloud = false
                                                            if (!success) {
                                                                restoreErrorMessage = msg
                                                            }
                                                        } catch (_: Throwable) {}
                                                    }
                                                } catch (e: Exception) {
                                                    isRestoringCloud = false
                                                    restoreErrorMessage = "ত্রুটি: ${e.message ?: "পুনরায় চেষ্টা করুন"}"
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
                                                    text = "🔒 শতভাগ সুরক্ষিত: সঠিক মাস্টার পিন যাচাই ছাড়া ক্লাউড থেকে তথ্য উন্মুক্ত করা হয় না।",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // FREE DEMO / TRIAL CARD (INSIDE OWNER FLOW)
                            if (!isDemoUsed || !isDemoExpired) {
                                Surface(
                                    shape = RoundedCornerShape(Radius.md),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Gold500.copy(alpha = 0.40f)),
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
                                                    text = "১ দিনের ফ্রি ট্রায়াল টেস্ট (২৪ ঘণ্টা)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "সব ফিচার ফ্রিতে যাচাই করে দেখুন",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                try {
                                                    viewModel.startOneHourDemo()
                                                } catch (t: Throwable) {
                                                    android.util.Log.e("AppActivation", "Error in startOneHourDemo", t)
                                                }
                                            },
                                            enabled = !isActivating,
                                            shape = RoundedCornerShape(Radius.sm),
                                            colors = ButtonDefaults.buttonColors(containerColor = Gold500),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "ফ্রি ট্রায়াল",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Brand900
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =================================================================
                    // 🌟 SCREEN 3: 👤 STAFF FLOW (কর্মচারী লগইন)
                    // =================================================================
                    "staff_flow" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            // Back Navigation Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable {
                                        try {
                                            activeFlow = "role_select"
                                        } catch (_: Throwable) {}
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Success,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "পেছনে যান • ভূমিকা পরিবর্তন",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Success
                                )
                            }

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
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(Success.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Badge,
                                                contentDescription = null,
                                                tint = Success,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "কর্মচারী হিসেবে লগইন করুন",
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
                                            try {
                                                keyboardController?.hide()
                                                staffJoinError = null
                                                isStaffJoining = true
                                                viewModel.secureJoinAsStaff(
                                                    staffShopCodeInput.trim(),
                                                    staffPinInput.trim(),
                                                    staffNameInput.trim()
                                                ) { success, msg ->
                                                    try {
                                                        isStaffJoining = false
                                                        if (!success) {
                                                            staffJoinError = msg
                                                        }
                                                    } catch (_: Throwable) {}
                                                }
                                            } catch (t: Throwable) {
                                                isStaffJoining = false
                                                staffJoinError = "যুক্ত হতে সমস্যা হয়েছে: ${t.message ?: "আবার চেষ্টা করুন"}"
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
            }

            // =========================================================================
            // 🌟 3. FOOTER: DEVICE ID & WEBIX SUPPORT INFO
            // =========================================================================
            Spacer(modifier = Modifier.height(Spacing.xs))

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
