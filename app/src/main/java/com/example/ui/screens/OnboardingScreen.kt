package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanSecondaryButton
import com.example.ui.components.DokanTextField
import com.example.ui.theme.*

@Composable
fun OnboardingScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var isSeedingData by remember { mutableStateOf(false) }

    // Step 1 Form State
    var shopName by remember { mutableStateOf(config.shopName.ifBlank { "Dokan Pro" }.let { if (it == "দোকান প্রো") "Dokan Pro" else it }) }
    var shopAddress by remember { mutableStateOf(config.shopAddress) }
    var shopPhone by remember { mutableStateOf(config.shopPhone) }
    var ownerEmail by remember { mutableStateOf(config.ownerEmail) }
    var tagline by remember { mutableStateOf(config.tagline) }
    var masterPin by remember { mutableStateOf(config.pinCode.ifBlank { "1234" }) }
    var staffPin by remember { mutableStateOf(config.staffPin.ifBlank { "0000" }) }

    // Step 2 Preferences State
    var useBengaliNumerals by remember { mutableStateOf(config.useBengaliNumerals) }
    var currencySymbol by remember { mutableStateOf(config.currencySymbol) }
    var vatEnabled by remember { mutableStateOf(config.vatEnabled) }
    var vatPercentage by remember { mutableStateOf(if (config.vatPercentage > 0) config.vatPercentage.toString() else "5.0") }
    var themeMode by remember { mutableStateOf(config.themeMode) }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                // Header & Brand
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(Brand500),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            text = "Dokan Pro সেটআপ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "প্রথম ব্যবহারের প্রস্তুতি",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Progress Stepper Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepItem(
                        step = 1,
                        label = "দোকানের তথ্য",
                        isActive = currentStep == 1,
                        isCompleted = currentStep > 1,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (currentStep > 1) Brand500 else MaterialTheme.colorScheme.outlineVariant)
                    )
                    StepItem(
                        step = 2,
                        label = "পছন্দ",
                        isActive = currentStep == 2,
                        isCompleted = currentStep > 2,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (currentStep > 2) Brand500 else MaterialTheme.colorScheme.outlineVariant)
                    )
                    StepItem(
                        step = 3,
                        label = "শুরু করুন",
                        isActive = currentStep == 3,
                        isCompleted = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            when (currentStep) {
                1 -> {
                    // STEP 1: দোকানের তথ্য
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "দোকানের তথ্য",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "এই তথ্যগুলো প্রতিটি মেমো ও চালানে প্রিন্ট হবে।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand500,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            DokanTextField(
                                value = shopName,
                                onValueChange = { shopName = it },
                                label = "দোকানের নাম *",
                                placeholder = "যেমন: মায়ের দোয়া স্টোর",
                                leadingIcon = Icons.Default.Storefront
                            )

                            DokanTextField(
                                value = shopAddress,
                                onValueChange = { shopAddress = it },
                                label = "দোকানের ঠিকানা",
                                placeholder = "যেমন: বাজার রোড, ঢাকা",
                                leadingIcon = Icons.Default.LocationOn
                            )

                            DokanTextField(
                                value = shopPhone,
                                onValueChange = { shopPhone = it },
                                label = "মোবাইল নম্বর",
                                placeholder = "যেমন: ০১৭১১-০০০০০০",
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = Icons.Default.Phone
                            )

                            DokanTextField(
                                value = ownerEmail,
                                onValueChange = { ownerEmail = it },
                                label = "মালিকের ইমেইল (ঐচ্ছিক / রিস্টোরের জন্য)",
                                placeholder = "যেমন: yourname@gmail.com",
                                keyboardType = KeyboardType.Email,
                                leadingIcon = Icons.Default.Email
                            )

                            DokanTextField(
                                value = masterPin,
                                onValueChange = { if (it.length <= 6) masterPin = it },
                                label = "মাস্টার সিকিউরিটি পিন (৪-৬ ডিজিট) *",
                                placeholder = "যেমন: 1234",
                                keyboardType = KeyboardType.NumberPassword,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = Icons.Default.Key
                            )

                            DokanTextField(
                                value = staffPin,
                                onValueChange = { if (it.length <= 6) staffPin = it },
                                label = "কর্মচারী এক্সেস পিন (৪ ডিজিট)",
                                placeholder = "যেমন: 0000",
                                keyboardType = KeyboardType.NumberPassword,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = Icons.Default.Lock
                            )

                            DokanTextField(
                                value = tagline,
                                onValueChange = { tagline = it },
                                label = "স্লোগান / ট্যাগলাইন",
                                placeholder = "যেমন: আপনার বিশ্বস্ত মুদি দোকান",
                                leadingIcon = Icons.Default.FormatQuote
                            )
                        }
                    }

                    DokanPrimaryButton(
                        text = "পরবর্তী ধাপ",
                        enabled = shopName.isNotBlank(),
                        onClick = {
                            if (shopName.isNotBlank()) {
                                currentStep = 2
                            }
                        }
                    )
                }

                2 -> {
                    // STEP 2: পছন্দ ও সেটিংস
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "পছন্দ ও সেটিংস",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "আপনার সুবিধা অনুযায়ী হিসাব ও প্রদর্শনের সেটিংস ঠিক করুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            // Bengali Numerals Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "বাংলা সংখ্যা ব্যবহার করুন",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "টাকা ও হিসাব বাংলায় দেখাবে (যেমন: ৳১,৫০০)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useBengaliNumerals,
                                    onCheckedChange = { useBengaliNumerals = it }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Currency Symbol Selector
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "মুদ্রার প্রতীক",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    listOf("৳", "Tk", "$", "Rs").forEach { symbol ->
                                        val isSelected = currencySymbol == symbol
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { currencySymbol = symbol },
                                            label = { Text(symbol, fontWeight = FontWeight.Bold) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Brand500,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // VAT Toggle and Percentage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ভ্যাট (VAT) সক্রিয় করুন",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "চালানে স্বয়ংক্রিয়ভাবে ভ্যাট যুক্ত হবে",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = vatEnabled,
                                    onCheckedChange = { vatEnabled = it }
                                )
                            }

                            if (vatEnabled) {
                                DokanTextField(
                                    value = vatPercentage,
                                    onValueChange = { vatPercentage = it },
                                    label = "ভ্যাটের শতকরা হার (%)",
                                    placeholder = "যেমন: 5.0",
                                    keyboardType = KeyboardType.Decimal
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Theme Mode Selection
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "থিম নির্বাচন করুন",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    listOf(
                                        Triple("system", "সিস্টেম", Icons.Default.BrightnessAuto),
                                        Triple("light", "লাইট", Icons.Default.LightMode),
                                        Triple("dark", "ডার্ক", Icons.Default.DarkMode)
                                    ).forEach { (mode, title, icon) ->
                                        val isSelected = themeMode == mode
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(Radius.md))
                                                .clickable { themeMode = mode },
                                            shape = RoundedCornerShape(Radius.md),
                                            color = if (isSelected) Brand500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (isSelected) Brand500 else Color.Transparent
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Brand500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Brand500 else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        DokanSecondaryButton(
                            text = "পূর্ববর্তী",
                            modifier = Modifier.weight(1f),
                            onClick = { currentStep = 1 }
                        )
                        DokanPrimaryButton(
                            text = "পরবর্তী ধাপ",
                            modifier = Modifier.weight(1f),
                            onClick = { currentStep = 3 }
                        )
                    }
                }

                3 -> {
                    // STEP 3: শুরু করুন
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "শুরু করুন",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "আপনার দোকান পরিচালনার জন্য কীভাবে শুরু করতে চান?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Option 1: ২০টি ডেমো পণ্য দিয়ে দেখি
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .clickable(enabled = !isSeedingData) {
                                isSeedingData = true
                                val parsedVat = vatPercentage.toDoubleOrNull() ?: 0.0
                                val finalShopName = shopName.trim().ifBlank { "Dokan Pro" }
                                val updated = config.copy(
                                    shopName = if (finalShopName == "দোকান প্রো") "Dokan Pro" else finalShopName,
                                    shopAddress = shopAddress.trim(),
                                    shopPhone = shopPhone.trim(),
                                    ownerEmail = ownerEmail.trim(),
                                    tagline = tagline.trim(),
                                    pinCode = masterPin.trim().ifBlank { "1234" },
                                    staffPin = staffPin.trim().ifBlank { "0000" },
                                    pinEnabled = true,
                                    userRole = "owner",
                                    useBengaliNumerals = useBengaliNumerals,
                                    currencySymbol = currencySymbol,
                                    vatEnabled = vatEnabled,
                                    vatPercentage = parsedVat,
                                    themeMode = themeMode,
                                    isOnboardingCompleted = true
                                )
                                viewModel.updateShopConfig(updated)
                                viewModel.updateSecurityPins(masterPin, staffPin)
                                viewModel.resetAllData {
                                    isSeedingData = false
                                    viewModel.navigateTo(AppScreen.DASHBOARD)
                                }
                            },
                        shape = RoundedCornerShape(Radius.lg),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, Brand500.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Brand500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSeedingData) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Brand500,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Brand500,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSeedingData) "২০টি ডেমো পণ্য লোড হচ্ছে..." else "২০টি ডেমো পণ্য দিয়ে দেখি",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isSeedingData) "দয়া করে অপেক্ষা করুন, ডাটাবেসে ২০টি পণ্য ও হিসাব লোড করা হচ্ছে..." else "দোকানের হিসাব-নিকাশ সহজে বুঝতে ২০টি নমুনা পণ্য ও ক্যাটাগরি যোগ করে অ্যাপটি পরীক্ষা করুন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )
                            }
                            if (isSeedingData) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Brand500,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Brand500
                                )
                            }
                        }
                    }

                    // Option 2: নিজের পণ্য যোগ করব
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.lg))
                            .clickable(enabled = !isSeedingData) {
                                val parsedVat = vatPercentage.toDoubleOrNull() ?: 0.0
                                val finalShopName = shopName.trim().ifBlank { "Dokan Pro" }
                                val updated = config.copy(
                                    shopName = if (finalShopName == "দোকান প্রো") "Dokan Pro" else finalShopName,
                                    shopAddress = shopAddress.trim(),
                                    shopPhone = shopPhone.trim(),
                                    ownerEmail = ownerEmail.trim(),
                                    tagline = tagline.trim(),
                                    pinCode = masterPin.trim().ifBlank { "1234" },
                                    staffPin = staffPin.trim().ifBlank { "0000" },
                                    pinEnabled = true,
                                    userRole = "owner",
                                    useBengaliNumerals = useBengaliNumerals,
                                    currencySymbol = currencySymbol,
                                    vatEnabled = vatEnabled,
                                    vatPercentage = parsedVat,
                                    themeMode = themeMode,
                                    isOnboardingCompleted = true
                                )
                                viewModel.updateShopConfig(updated)
                                viewModel.updateSecurityPins(masterPin, staffPin)
                                viewModel.clearAllDummyData {
                                    viewModel.navigateTo(AppScreen.PRODUCTS)
                                }
                            },
                        shape = RoundedCornerShape(Radius.lg),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddBusiness,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "নিজের পণ্য যোগ করব",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "সরাসরি আপনার দোকানের পণ্য যোগ করে নতুনভাবে বেচাকেনা শুরু করুন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    DokanSecondaryButton(
                        text = "পূর্ববর্তী ধাপে যান",
                        onClick = { currentStep = 2 }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    step: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> Brand500
                        isActive -> Brand500
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "$step",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive || isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
