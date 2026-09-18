package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Mode Selector: "owner" (মালিক মোড) or "staff" (কর্মচারী মোড)
    var selectedRoleMode by remember { mutableStateOf("owner") }

    // Owner - New Shop Activation States
    var emailInput by remember { mutableStateOf("") }
    val isActivating by viewModel.isActivating.collectAsState()
    val activationError by viewModel.activationError.collectAsState()
    val isDemoUsed by viewModel.isDemoUsed.collectAsState()
    val isDemoExpired by viewModel.isDemoExpired.collectAsState()

    // Owner - Secure Restore States
    var ownerRestoreCodeInput by remember { mutableStateOf("") }
    var ownerRestoreMasterPin by remember { mutableStateOf("") }
    var isOwnerRestoring by remember { mutableStateOf(false) }
    var ownerRestoreError by remember { mutableStateOf<String?>(null) }

    // Staff - Secure Join States
    var staffShopCodeInput by remember { mutableStateOf("") }
    var staffPinInput by remember { mutableStateOf("") }
    var staffNameInput by remember { mutableStateOf("") }
    var isStaffJoining by remember { mutableStateOf(false) }
    var staffJoinError by remember { mutableStateOf<String?>(null) }

    val deviceId = remember { viewModel.getDeviceId() }
    var copiedRecently by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand900)
    ) {
        // Subtle radial glow drawn in Canvas at the top center
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Brand500.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.16f),
                    radius = size.width * 0.85f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))

            // Centered 96dp app mark
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Brand700,
                                Brand500
                            )
                        )
                    )
                    .border(1.5.dp, Brand500.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Title & Official Brand Badge
            Text(
                text = "Dokan-Pro অ্যাক্টিভেশন",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Brand500.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Brand500.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "Webix Solution Official Software",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand500,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Text(
                text = "আপনার পদবী অনুযায়ী মোড নির্বাচন করে অ্যাপে প্রবেশ করুন:",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )

            // =========================================================================
            // 🌟 TWO PRIMARY MODES: 👑 মালিক মোড (Owner) vs 👤 কর্মচারী মোড (Staff)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 👑 দোকান মালিক মোড
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

                    // 👤 কর্মচারী মোড
                    Surface(
                        onClick = { selectedRoleMode = "staff" },
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (selectedRoleMode == "staff") MaterialTheme.dokanColors.success else Color.Transparent,
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

            AnimatedContent(
                targetState = selectedRoleMode,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ModeContentTransition"
            ) { mode ->
                if (mode == "owner") {
                    // =================================================================
                    // 👑 OWNER MODE CONTENT
                    // =================================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Card A: নতুন দোকান শুরু (New Shop Setup)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.lg),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "নতুন দোকান শুরু করুন",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "আপনি যদি নতুন ইউজার হন, তবে ক্রয়কৃত ইমেইল দিয়ে অ্যাক্টিভ করুন অথবা ১ দিনের ফ্রি ডেমো টেস্ট করে দেখুন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )

                                // 1-Day Demo Button
                                if (!isDemoUsed || !isDemoExpired) {
                                    Surface(
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = Brand500.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Brand500.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "১ দিনের ফ্রি ডেমো টেস্ট (২৪ ঘণ্টা)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Brand700
                                                )
                                                Text(
                                                    text = "৭ দিনের ডামি ডাটা সহ সব ফিচার যাচাই করুন",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.startOneHourDemo() },
                                                enabled = !isActivating,
                                                shape = RoundedCornerShape(Radius.sm),
                                                colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (isActivating) "যাচাই..." else "ফ্রি ট্রায়াল",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "⚠️ আপনার ডিভাইসে ১ দিনের ফ্রি ডেমোর মেয়াদ শেষ হয়েছে।",
                                        fontSize = 11.sp,
                                        color = StatusDanger,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "নিবন্ধিত ইমেইল এড্রেস:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                DokanTextField(
                                    value = emailInput,
                                    onValueChange = {
                                        emailInput = it
                                        if (activationError != null) viewModel.clearActivationError()
                                    },
                                    label = "ইমেইল এড্রেস",
                                    placeholder = "যেমন: owner@gmail.com",
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
                                    text = "অ্যাপ সক্রিয় করুন (নতুন দোকান)",
                                    isLoading = isActivating,
                                    enabled = emailInput.isNotBlank() && !isActivating,
                                    onClick = {
                                        keyboardController?.hide()
                                        viewModel.activateApp(emailInput.trim())
                                    }
                                )
                            }
                        }

                        // Card B: পূর্বের দোকান রিস্টোর (Existing Shop Restore)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.lg),
                            colors = CardDefaults.cardColors(
                                containerColor = Brand700.copy(alpha = 0.40f)
                            ),
                            border = BorderStroke(1.5.dp, Brand500.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Brand500,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "পূর্বের দোকান রিস্টোর (মাস্টার পিন সহ)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "আগের দোকানের সমস্ত হিসাব, পণ্য ও বাকি নতুন ফোনে নামিয়ে নিতে আপনার নিবন্ধিত ইমেইল/কোড এবং গোপন মাস্টার পিন দিন। ডাটা লোড হয়ে সরাসরি অ্যাপের ড্যাশবোর্ডে প্রবেশ করবে।",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 17.sp
                                )

                                DokanTextField(
                                    value = ownerRestoreCodeInput,
                                    onValueChange = {
                                        ownerRestoreCodeInput = it
                                        ownerRestoreError = null
                                    },
                                    label = "দোকান কোড অথবা নিবন্ধিত ইমেইল",
                                    placeholder = "যেমন: SHOP-XXXXXX বা owner@gmail.com",
                                    leadingIcon = Icons.Default.Storefront
                                )

                                DokanTextField(
                                    value = ownerRestoreMasterPin,
                                    onValueChange = {
                                        if (it.length <= 8) ownerRestoreMasterPin = it
                                        ownerRestoreError = null
                                    },
                                    label = "মাস্টার সিকিউরিটি পিন",
                                    placeholder = "আপনার গোপন মাস্টার পিন লিখুন",
                                    keyboardType = KeyboardType.NumberPassword,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = Icons.Default.Key
                                )

                                AnimatedVisibility(visible = ownerRestoreError != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = StatusDanger.copy(alpha = 0.20f),
                                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.5f))
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
                                                text = ownerRestoreError ?: "",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                DokanPrimaryButton(
                                    text = if (isOwnerRestoring) "ডাটা রিস্টোর হচ্ছে..." else "🔄 ডাটা রিস্টোর করে সরাসরি অ্যাপে প্রবেশ করুন",
                                    isLoading = isOwnerRestoring,
                                    enabled = !isOwnerRestoring && ownerRestoreCodeInput.isNotBlank() && ownerRestoreMasterPin.isNotBlank(),
                                    onClick = {
                                        keyboardController?.hide()
                                        ownerRestoreError = null
                                        isOwnerRestoring = true
                                        viewModel.secureRestoreOwnerShop(
                                            ownerRestoreCodeInput.trim(),
                                            ownerRestoreMasterPin.trim()
                                        ) { success, msg ->
                                            isOwnerRestoring = false
                                            if (!success) {
                                                ownerRestoreError = msg
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Purchase Card (Webix Solution Official License Link)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.lg),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = Brand500,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "লাইসেন্স ক্রয় করুন (আজীবন মেয়াদ)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = Color(0xFF2E7D32)
                                    ) {
                                        Text(
                                            text = "অফিসিয়াল লাইসেন্স",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Webix Solution ওয়েবসাইট থেকে Dokan-Pro এর আজীবন অফিসিয়াল লাইসেন্স ক্রয় করুন। বর্তমান প্যাকেজ ও মূল্য দেখতে নিচের বাটনে ক্লিক করুন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )

                                DokanSecondaryButton(
                                    text = "ওয়েবসাইট থেকে লাইসেন্স সংগ্রহ করুন",
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
                                                "ওয়েবসাইট ব্রাউজারে খুলুন: webixsolution.store/product/dokan-pro",
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
                    // 👤 STAFF MODE CONTENT
                    // =================================================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.lg),
                            colors = CardDefaults.cardColors(
                                containerColor = Brand700.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.dokanColors.success.copy(alpha = 0.75f))
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.dokanColors.success,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "কর্মচারী হিসেবে যুক্ত হোন",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = MaterialTheme.dokanColors.success
                                    ) {
                                        Text(
                                            text = "ফ্রি এক্সেস",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "দোকান মালিকের কাছ থেকে পাওয়া দোকান কোড অথবা ইমেইল এবং ৪-ডিজিটের কর্মচারী পিন দিয়ে প্রবেশ করুন। প্রবেশের সাথে সাথে সকল পণ্য ও স্টক স্বয়ংক্রিয়ভাবে সিঙ্ক হয়ে যাবে। কর্মচারীদের জন্য কোনো লাইসেন্স ফি লাগে না।",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 17.sp
                                )

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
                                        color = StatusDanger.copy(alpha = 0.20f),
                                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.5f))
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
                                                color = Color.White,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                DokanPrimaryButton(
                                    text = if (isStaffJoining) "যুক্ত হচ্ছে ও সিঙ্ক হচ্ছে..." else "🚀 কর্মচারী হিসেবে অ্যাপে প্রবেশ করুন",
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
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Gold500,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "🔒 গোপনীয়তা রক্ষা: কর্মচারীরা দোকানের মোট লাভ বা মালিকের ব্যক্তিগত হিসাব দেখতে পারবে না। শুধুমাত্র নিজস্ব বিক্রি ও পণ্যের স্টক তথ্য দেখতে পারবে।",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.88f),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Monospace pill for device ID & Support Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(deviceId))
                        copiedRecently = true
                        Toast.makeText(context, "ডিভাইস আইডি কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (copiedRecently) "কপি হয়েছে! ✓ $deviceId" else "ডিভাইস আইডি: $deviceId",
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
