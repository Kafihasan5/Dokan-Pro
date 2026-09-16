package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var shopName by remember { mutableStateOf(config.shopName) }
    var shopAddress by remember { mutableStateOf(config.shopAddress) }
    var shopPhone by remember { mutableStateOf(config.shopPhone) }
    var tagline by remember { mutableStateOf(config.tagline) }
    var currencySymbol by remember { mutableStateOf(config.currencySymbol) }
    var useBengaliNumerals by remember { mutableStateOf(config.useBengaliNumerals) }
    var themeMode by remember { mutableStateOf(config.themeMode) }
    var vatEnabled by remember { mutableStateOf(config.vatEnabled) }
    var vatPercentageText by remember { mutableStateOf(config.vatPercentage.toString()) }
    var pinEnabled by remember { mutableStateOf(config.pinEnabled) }
    var pinCode by remember { mutableStateOf(config.pinCode) }
    var userRole by remember { mutableStateOf(config.userRole) } // "owner" or "staff"
    var allowNegativeStock by remember { mutableStateOf(config.allowNegativeStock) }
    var showWipeAllDataDialog by remember { mutableStateOf(false) }
    var showCategoryUnitManager by remember { mutableStateOf(false) }
    var initialManageTab by remember { mutableStateOf(0) }

    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()
    val licenseInfo by viewModel.licenseInfo.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val remainingDemoMillis by viewModel.remainingDemoMillis.collectAsState()
    var showDemoActivationDialog by remember { mutableStateOf(false) }
    var demoActivationEmail by remember { mutableStateOf("") }

    val customerSupabaseUrl by viewModel.customerSupabaseUrl.collectAsState()
    val customerSupabaseKey by viewModel.customerSupabaseKey.collectAsState()
    val isCustomerCloudConfigured by viewModel.isCustomerCloudConfigured.collectAsState()

    var cloudUrlInput by remember(customerSupabaseUrl) { mutableStateOf(customerSupabaseUrl) }
    var cloudKeyInput by remember(customerSupabaseKey) { mutableStateOf(customerSupabaseKey) }
    var isCloudKeyVisible by remember { mutableStateOf(false) }
    var isTestingCloud by remember { mutableStateOf(false) }
    var cloudTestMessage by remember { mutableStateOf<String?>(null) }
    var showSqlSchemaDialog by remember { mutableStateOf(false) }
    var showCloudRestoreConfirmDialog by remember { mutableStateOf(false) }

    DokanScreenScaffold(
        title = "দোকান ও অ্যাপ সেটিংস",
        onBack = onBack,
        floatingAction = null
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // 1. দোকানের প্রোফাইল
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "দোকানের প্রোফাইল", icon = Icons.Default.Storefront)
                    Text(
                        text = "রসিদ ও মেমোতে এই তথ্যগুলো মুদ্রিত হবে",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    DokanTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = "দোকানের নাম"
                    )

                    DokanTextField(
                        value = shopAddress,
                        onValueChange = { shopAddress = it },
                        label = "দোকানের ঠিকানা"
                    )

                    DokanTextField(
                        value = shopPhone,
                        onValueChange = { shopPhone = it },
                        label = "মোবাইল নম্বর",
                        keyboardType = KeyboardType.Phone
                    )

                    DokanTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = "স্লোগান / ট্যাগলাইন"
                    )
                }
            }

            // 2. ভাষা ও সংখ্যা প্রদর্শন
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "ভাষা ও সংখ্যা প্রদর্শন", icon = Icons.Default.Language)

                    SettingRow(
                        label = "বাংলা সংখ্যা ব্যবহার",
                        helperText = "চালু থাকলে ৳১২৫.০০, বন্ধ থাকলে ৳125.00 দেখাবে"
                    ) {
                        Switch(
                            checked = useBengaliNumerals,
                            onCheckedChange = { useBengaliNumerals = it },
                            colors = brandSwitchColors()
                        )
                    }

                    DokanTextField(
                        value = currencySymbol,
                        onValueChange = { currencySymbol = it },
                        label = "মুদ্রা প্রতীক"
                    )
                }
            }

            // 3. ভ্যাট ও স্টক নীতি
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "ভ্যাট ও স্টক নীতি", icon = Icons.Default.Receipt)

                    SettingRow(
                        label = "ভ্যাট (VAT) সক্রিয় করুন",
                        helperText = "ইনভয়েসে নির্ধারিত হারে ভ্যাট যুক্ত হবে"
                    ) {
                        Switch(
                            checked = vatEnabled,
                            onCheckedChange = { vatEnabled = it },
                            colors = brandSwitchColors()
                        )
                    }

                    if (vatEnabled) {
                        DokanTextField(
                            value = vatPercentageText,
                            onValueChange = { vatPercentageText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "ভ্যাট হার (%)",
                            keyboardType = KeyboardType.Decimal
                        )
                    }

                    SettingRow(
                        label = "নেগেটিভ স্টক অনুমোদন",
                        helperText = "স্টক শূন্য থাকলেও তাৎক্ষণিক বিক্রি চালু রাখা"
                    ) {
                        Switch(
                            checked = allowNegativeStock,
                            onCheckedChange = { allowNegativeStock = it },
                            colors = brandSwitchColors()
                        )
                    }
                }
            }

            // 4. ক্যাটাগরি ও একক
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "ক্যাটাগরি ও একক", icon = Icons.Default.Category)
                    Text(
                        text = "পণ্যের ক্যাটাগরি এবং পরিমাপের একক (কেজি, পিস, লিটার) নতুন যোগ ও পরিচালনা করুন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        DokanSecondaryButton(
                            text = "ক্যাটাগরি (${categories.size})",
                            onClick = {
                                initialManageTab = 0
                                showCategoryUnitManager = true
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DokanSecondaryButton(
                            text = "এককসমূহ (${units.size})",
                            onClick = {
                                initialManageTab = 1
                                showCategoryUnitManager = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. থিম ও ডিসপ্লে (3 icon+label tiles: ডে / নাইট / সিস্টেম)
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "থিম ও ডিসপ্লে", icon = Icons.Default.Palette)
                    Text(
                        text = "আপনার চোখের আরাম ও দোকানের আলোর সাথে মানানসই মোড নির্বাচন করুন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        ThemeTile(
                            title = "ডে",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == "light",
                            onClick = {
                                themeMode = "light"
                                viewModel.setThemeMode("light")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ThemeTile(
                            title = "নাইট",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == "dark",
                            onClick = {
                                themeMode = "dark"
                                viewModel.setThemeMode("dark")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ThemeTile(
                            title = "সিস্টেম",
                            icon = Icons.Default.SettingsBrightness,
                            isSelected = themeMode == "system",
                            onClick = {
                                themeMode = "system"
                                viewModel.setThemeMode("system")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 6. অনলাইন ক্লাউড সিঙ্ক
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeaderWithIcon(title = "অনলাইন ক্লাউড সিঙ্ক", icon = Icons.Default.CloudSync)

                        // Live Status Pill: সংযুক্ত ✓ in success tone, সেটআপ করা নেই in neutral tone
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.successContainer else MaterialTheme.dokanColors.surfaceAlt,
                            border = BorderStroke(1.dp, if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success.copy(alpha = 0.3f) else MaterialTheme.dokanColors.border)
                        ) {
                            Text(
                                text = if (isCustomerCloudConfigured) "সংযুক্ত ✓" else "সেটআপ করা নেই",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "আপনার নিজস্ব Supabase প্রজেক্টের URL এবং API Key এখানে সেভ করে যেকোনো ডিভাইস থেকে ব্যাকআপ ও রিস্টোর করুন।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    DokanTextField(
                        value = cloudUrlInput,
                        onValueChange = {
                            cloudUrlInput = it
                            cloudTestMessage = null
                        },
                        label = "Supabase Project URL",
                        placeholder = "https://xxxx.supabase.co"
                    )

                    DokanTextField(
                        value = cloudKeyInput,
                        onValueChange = {
                            cloudKeyInput = it
                            cloudTestMessage = null
                        },
                        label = "Supabase Secret / Anon API Key",
                        placeholder = "eyJhbGciOi...",
                        trailingIcon = {
                            IconButton(onClick = { isCloudKeyVisible = !isCloudKeyVisible }) {
                                Icon(
                                    if (isCloudKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    if (cloudTestMessage != null) {
                        Text(
                            text = cloudTestMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (cloudTestMessage?.contains("সফল") == true) MaterialTheme.dokanColors.success else MaterialTheme.dokanColors.danger
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        DokanPrimaryButton(
                            text = "সংরক্ষণ করুন",
                            onClick = { viewModel.saveCustomerCloudConfig(cloudUrlInput, cloudKeyInput) },
                            enabled = cloudUrlInput.isNotBlank() && cloudKeyInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )

                        DokanSecondaryButton(
                            text = if (isTestingCloud) "পরীক্ষা..." else "কানেকশন টেস্ট",
                            onClick = {
                                isTestingCloud = true
                                cloudTestMessage = "টেস্ট করা হচ্ছে..."
                                viewModel.testCustomerCloudConnection(cloudUrlInput, cloudKeyInput) { ok, msg ->
                                    isTestingCloud = false
                                    cloudTestMessage = msg
                                }
                            },
                            enabled = !isTestingCloud && cloudUrlInput.isNotBlank() && cloudKeyInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isCustomerCloudConfigured) {
                        HorizontalDivider(color = MaterialTheme.dokanColors.border)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            DokanSecondaryButton(
                                text = if (isSyncing) "সেভ..." else "ক্লাউড ব্যাকআপ",
                                onClick = { viewModel.backupToCustomerCloud { _, _ -> } },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            )

                            DokanSecondaryButton(
                                text = "ক্লাউড রিস্টোর",
                                onClick = { showCloudRestoreConfirmDialog = true },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        DokanDangerButton(
                            text = "ক্লাউড ডিসকানেক্ট করুন",
                            onClick = {
                                viewModel.clearCustomerCloudConfig {
                                    cloudUrlInput = ""
                                    cloudKeyInput = ""
                                    cloudTestMessage = null
                                }
                            }
                        )
                    }

                    TextButton(
                        onClick = { showSqlSchemaDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সুপাবেস ডাটাবেস স্ক্রিপ্ট (SQL Setup)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // 7. নিরাপত্তা ও ইউজার রোল
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "নিরাপত্তা ও ইউজার রোল", icon = Icons.Default.Security)

                    SettingRow(
                        label = "বর্তমান অ্যাক্টিভ রোল",
                        helperText = "মালিক বা কর্মচারীর অধিকার নির্ধারণ"
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            FilterChip(
                                selected = userRole == "owner",
                                onClick = { userRole = "owner" },
                                label = { Text("মালিক") },
                                shape = RoundedCornerShape(Radius.pill)
                            )
                            FilterChip(
                                selected = userRole == "staff",
                                onClick = { userRole = "staff" },
                                label = { Text("কর্মচারী") },
                                shape = RoundedCornerShape(Radius.pill)
                            )
                        }
                    }

                    SettingRow(
                        label = "৪-ডিজিট অ্যাপ পিন লক",
                        helperText = "অ্যাপ চালুতে পিন সুরক্ষা"
                    ) {
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = { pinEnabled = it },
                            colors = brandSwitchColors()
                        )
                    }

                    if (pinEnabled) {
                        DokanTextField(
                            value = pinCode,
                            onValueChange = { pinCode = it.filter { c -> c.isDigit() }.take(4) },
                            label = "৪ ডিজিট পিন কোড",
                            keyboardType = KeyboardType.NumberPassword
                        )

                        DokanSecondaryButton(
                            text = "এখনই অ্যাপ লক করুন",
                            onClick = {
                                val updated = config.copy(pinEnabled = true, pinCode = pinCode.trim())
                                viewModel.updateShopConfig(updated)
                                viewModel.lockApp()
                            }
                        )
                    }
                }
            }

            // 8. লাইসেন্স (Subtle Brand900 background, monospace email & device id, copy button)
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = Brand900,
                    border = BorderStroke(1.dp, Brand700),
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(1, RoundedCornerShape(Radius.lg))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Brand300,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "সফটওয়্যার লাইসেন্স",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = Brand700
                            ) {
                                Text(
                                    text = "Webix Verified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Brand100,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val lic = licenseInfo
                        val deviceId = viewModel.getDeviceId()

                        if (lic != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "নিবন্ধিত ইমেইল:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Brand100.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = lic.email,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "ডিভাইস আইডি:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Brand100.copy(alpha = 0.7f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lic.deviceId,
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White)
                                    )
                                    IconButton(
                                        onClick = { copyToClipboard(context, lic.deviceId, "ডিভাইস আইডি কপি করা হয়েছে") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "কপি করুন",
                                            tint = Brand300,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "স্ট্যাটাস: সক্রিয় (আজীবন লাইসেন্স)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Brand300
                            )
                        } else if (isDemoMode) {
                            val totalSeconds = (remainingDemoMillis / 1000).coerceAtLeast(0)
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
                            val bengaliTime = Formatters.toBengaliDigits(timeFormatted)

                            Text(
                                text = "স্ট্যাটাস: ১ ঘণ্টার ফ্রি ডেমো মোড (বাকি: $bengaliTime মিনিট)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.gold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ডিভাইস আইডি:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Brand100.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = deviceId,
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White)
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(context, deviceId, "ডিভাইস আইডি কপি করা হয়েছে") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "কপি করুন",
                                        tint = Brand300,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://webixsolution.store/product/dokan-pro"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.dokanColors.gold),
                                    shape = RoundedCornerShape(Radius.md)
                                ) {
                                    Text("লাইসেন্স কিনুন (৳৪৯০)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Brand900)
                                }

                                DokanSecondaryButton(
                                    text = "অ্যাক্টিভ করুন",
                                    onClick = { showDemoActivationDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ডিভাইস আইডি:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Brand100.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = deviceId,
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White)
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(context, deviceId, "ডিভাইস আইডি কপি করা হয়েছে") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "কপি করুন",
                                        tint = Brand300,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "স্ট্যাটাস: সক্রিয় (অফলাইন লোকাল মোড)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Brand300
                            )
                        }
                    }
                }
            }

            // 8.5. লাইভ সাপোর্ট ও সাহায্য
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "লাইভ সাপোর্ট ও চ্যাট", icon = Icons.Default.SupportAgent)

                    Text(
                        text = "অ্যাপ ব্যবহারের যেকোনো সমস্যা বা সহায়তার জন্য আমাদের অফিসিয়াল টেলিগ্রাম বটের মাধ্যমে সরাসরি চ্যাট করুন। প্রতিটি ডিভাইসের জন্য ডেডিকেটেড সাপোর্ট দেওয়া হয়।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    DokanPrimaryButton(
                        text = "💬 লাইভ চ্যাট সাপোর্ট শুরু করুন",
                        onClick = { viewModel.navigateTo(AppScreen.LIVE_SUPPORT) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 9. অ্যাপ ভার্সন
            item {
                SettingsCard {
                    SectionHeaderWithIcon(title = "অ্যাপ ভার্সন", icon = Icons.Default.Info)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "বর্তমান সংস্করণ: v${appUpdateInfo.currentVersionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "বিল্ড কোড: ${appUpdateInfo.currentVersionCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }

                        DokanSecondaryButton(
                            text = if (isCheckingUpdate) "যাচাই হচ্ছে..." else "আপডেট চেক",
                            onClick = { viewModel.checkForUpdates(silent = false) },
                            enabled = !isCheckingUpdate
                        )
                    }

                    if (appUpdateInfo.isUpdateAvailable) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    text = "নতুন আপডেট পাওয়া গেছে: v${appUpdateInfo.latestVersionName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (appUpdateInfo.updateNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "পরিবর্তনসমূহ: ${appUpdateInfo.updateNotes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                DokanPrimaryButton(
                                    text = "এখনই আপডেট ইনস্টল করুন",
                                    onClick = { viewModel.openUpdateDialog() }
                                )
                            }
                        }
                    }
                }
            }

            // 10. বিপজ্জনক অঞ্চল (Clearly separated danger card with 1dp danger border)
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.dokanColors.dangerContainer.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.dokanColors.danger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(1, RoundedCornerShape(Radius.lg))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.dokanColors.danger,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "বিপজ্জনক অঞ্চল",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.danger
                            )
                        }

                        Text(
                            text = "ক্লাউড (Supabase) এবং ফোন (লোকাল স্টোরেজ) থেকে সকল পণ্য, বিক্রি, কাস্টমার, বাকি খাতা ও খরচের সমস্ত হিসাব সম্পূর্ণ মুছে ফ্রেশ করুন। এটি নিশ্চিত করতে পাসওয়ার্ড প্রয়োজন হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DokanDangerButton(
                            text = "সকল ডেটা মুছে ফেলুন",
                            onClick = { showWipeAllDataDialog = true }
                        )
                    }
                }
            }

            // Bottom Save Settings Button
            item {
                DokanPrimaryButton(
                    text = "সেটিংস সংরক্ষণ করুন",
                    onClick = {
                        val updated = config.copy(
                            shopName = shopName.trim(),
                            shopAddress = shopAddress.trim(),
                            shopPhone = shopPhone.trim(),
                            tagline = tagline.trim(),
                            currencySymbol = currencySymbol.trim(),
                            useBengaliNumerals = useBengaliNumerals,
                            themeMode = themeMode,
                            vatEnabled = vatEnabled,
                            vatPercentage = vatPercentageText.toDoubleOrNull() ?: 0.0,
                            pinEnabled = pinEnabled,
                            pinCode = pinCode.trim(),
                            userRole = userRole,
                            allowNegativeStock = allowNegativeStock
                        )
                        viewModel.updateShopConfig(updated)
                        onBack()
                    },
                    modifier = Modifier.testTag("save_settings_btn")
                )
            }
        }
    }

    // Wipe All Data Password Dialog (Preserved exactly as is)
    if (showWipeAllDataDialog) {
        var inputPassword by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isWiping by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isWiping) showWipeAllDataDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.dokanColors.danger,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "সকল ডেটা সম্পূর্ণ মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.dokanColors.danger
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "সতর্কতা: ফোন থেকে সকল পণ্য, বিক্রির রেকর্ড, কাস্টমার তালিকা, বাকি খাতা ও খরচের সমস্ত হিসাব স্থায়ীভাবে মুছে যাবে। এটি আর ফিরিয়ে আনা সম্ভব নয়!\n\nমুছে ফেলতে পাসওয়ার্ড লিখুন (dokanpro):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DokanTextField(
                        value = inputPassword,
                        onValueChange = {
                            inputPassword = it
                            errorMessage = null
                        },
                        label = "নিরাপত্তা পাসওয়ার্ড",
                        placeholder = "পাসওয়ার্ড লিখুন",
                        isError = errorMessage != null,
                        errorText = errorMessage,
                        enabled = !isWiping,
                        keyboardType = KeyboardType.Password,
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    if (isWiping) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.dokanColors.danger
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("ক্লাউড ও লোকাল ডেটা মোছা হচ্ছে...", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPassword.trim().lowercase() != "dokanpro") {
                            errorMessage = "ভুল পাসওয়ার্ড! সঠিক পাসওয়ার্ড লিখুন।"
                            return@Button
                        }
                        isWiping = true
                        errorMessage = null
                        viewModel.wipeAllDataCloudAndLocal(
                            password = inputPassword,
                            onSuccess = {
                                isWiping = false
                                showWipeAllDataDialog = false
                            },
                            onError = { err ->
                                isWiping = false
                                errorMessage = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.dokanColors.danger),
                    enabled = !isWiping && inputPassword.isNotBlank(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("হ্যাঁ, সব ডেটা মুছুন")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeAllDataDialog = false },
                    enabled = !isWiping
                ) {
                    Text("বাতিল")
                }
            },
            shape = RoundedCornerShape(Radius.lg)
        )
    }

    if (showCategoryUnitManager) {
        CategoryUnitManagerDialog(
            viewModel = viewModel,
            initialTab = initialManageTab,
            onDismiss = { showCategoryUnitManager = false }
        )
    }

    if (showCloudRestoreConfirmDialog) {
        DokanConfirmDialog(
            title = "ক্লাউড থেকে রিস্টোর করতে চান?",
            message = "আপনার Supabase ক্লাউডে সংরক্ষিত পণ্য, বিক্রি, বাকি খাতা ও খরচের হিসাব বর্তমান ফোনে লোড ও সিঙ্ক হবে। আপনি কি রিস্টোর করতে চান?",
            confirmLabel = "হ্যাঁ, রিস্টোর করুন",
            onConfirm = {
                showCloudRestoreConfirmDialog = false
                viewModel.restoreFromCustomerCloud { _, _ -> }
            },
            onDismiss = { showCloudRestoreConfirmDialog = false }
        )
    }

    if (showSqlSchemaDialog) {
        val schemaSql = """
CREATE TABLE IF NOT EXISTS public.categories (id BIGINT PRIMARY KEY, name_bn TEXT NOT NULL, name_en TEXT DEFAULT '', icon_name TEXT DEFAULT 'category', sort_order INT DEFAULT 0);
CREATE TABLE IF NOT EXISTS public.products (id BIGINT PRIMARY KEY, name_bn TEXT NOT NULL, name_en TEXT DEFAULT '', category_id BIGINT, unit_name TEXT DEFAULT 'পিস', barcode TEXT DEFAULT '', purchase_price_poisha BIGINT DEFAULT 0, sale_price_poisha BIGINT DEFAULT 0, wholesale_price_poisha BIGINT DEFAULT 0, stock_qty NUMERIC DEFAULT 0, min_stock NUMERIC DEFAULT 5, expiry_date BIGINT, supplier_id BIGINT, is_active BOOLEAN DEFAULT TRUE, created_at BIGINT NOT NULL, updated_at BIGINT);
CREATE TABLE IF NOT EXISTS public.customers (id BIGINT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT, credit_limit_poisha BIGINT DEFAULT 0, is_active BOOLEAN DEFAULT TRUE, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.sales (id BIGINT PRIMARY KEY, invoice_no TEXT NOT NULL, customer_id BIGINT, customer_name TEXT, sale_date BIGINT NOT NULL, subtotal_poisha BIGINT NOT NULL, discount_poisha BIGINT DEFAULT 0, vat_poisha BIGINT DEFAULT 0, total_poisha BIGINT NOT NULL, paid_amount_poisha BIGINT NOT NULL, due_amount_poisha BIGINT DEFAULT 0, payment_method TEXT DEFAULT 'cash', user_id TEXT DEFAULT 'owner', note TEXT, is_returned BOOLEAN DEFAULT FALSE, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.sale_items (id BIGINT PRIMARY KEY, sale_id BIGINT, product_id BIGINT, product_name TEXT NOT NULL, unit_name TEXT DEFAULT 'পিস', qty NUMERIC NOT NULL, unit_price_poisha BIGINT NOT NULL, purchase_price_at_sale_poisha BIGINT DEFAULT 0, discount_poisha BIGINT DEFAULT 0, line_total_poisha BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.customer_ledger (id BIGINT PRIMARY KEY, customer_id BIGINT, ref_type TEXT NOT NULL, ref_id BIGINT, debit_poisha BIGINT DEFAULT 0, credit_poisha BIGINT DEFAULT 0, note TEXT, entry_date BIGINT NOT NULL, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.expenses (id BIGINT PRIMARY KEY, category_id BIGINT DEFAULT 1, category_name TEXT NOT NULL, amount_poisha BIGINT NOT NULL, note TEXT, expense_date BIGINT NOT NULL, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.suppliers (id BIGINT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, company TEXT, address TEXT, is_active BOOLEAN DEFAULT TRUE, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.purchases (id BIGINT PRIMARY KEY, invoice_no TEXT NOT NULL, supplier_id BIGINT, supplier_name TEXT, purchase_date BIGINT NOT NULL, total_poisha BIGINT NOT NULL, paid_amount_poisha BIGINT NOT NULL, due_amount_poisha BIGINT DEFAULT 0, note TEXT, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.purchase_items (id BIGINT PRIMARY KEY, purchase_id BIGINT, product_id BIGINT, product_name TEXT NOT NULL, qty NUMERIC NOT NULL, unit_price_poisha BIGINT NOT NULL, line_total_poisha BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.stock_adjustments (id BIGINT PRIMARY KEY, product_id BIGINT, product_name TEXT NOT NULL, qty_change NUMERIC NOT NULL, reason TEXT NOT NULL, note TEXT, created_at BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS public.app_config (config_key TEXT PRIMARY KEY, config_value TEXT NOT NULL, updated_at BIGINT);
        """.trimIndent()

        AlertDialog(
            onDismissRequest = { showSqlSchemaDialog = false },
            icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("সুপাবেস ডাটাবেস স্ক্রিপ্ট", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "আপনার ব্যক্তিগত Supabase ড্যাশবোর্ডে গিয়ে SQL Editor-এ নিচের স্ক্রিপ্টটি রান করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        shape = RoundedCornerShape(Radius.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = schemaSql, fontSize = 10.sp, maxLines = 8, modifier = Modifier.padding(Spacing.sm))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, schemaSql, "SQL স্ক্রিপ্ট কপি করা হয়েছে!")
                        showSqlSchemaDialog = false
                    },
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("স্ক্রিপ্ট কপি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSqlSchemaDialog = false }) { Text("বন্ধ করুন") }
            }
        )
    }

    if (showDemoActivationDialog) {
        AlertDialog(
            onDismissRequest = { showDemoActivationDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Dokan-Pro লাইসেন্স সক্রিয় করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Webix Solution ওয়েবসাইট থেকে ক্রয়কৃত ইমেইলটি লিখুন। সক্রিয় হওয়ার সাথে সাথে সমস্ত ডামি ডাটা স্বয়ংক্রিয়ভাবে মুছে সম্পূর্ণ ফ্রেশ ডেটাবেস চালু হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DokanTextField(
                        value = demoActivationEmail,
                        onValueChange = { demoActivationEmail = it },
                        label = "ইমেইল",
                        placeholder = "customer@gmail.com",
                        keyboardType = KeyboardType.Email
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (demoActivationEmail.isNotBlank()) {
                            showDemoActivationDialog = false
                            viewModel.activateApp(demoActivationEmail.trim())
                        }
                    },
                    enabled = demoActivationEmail.isNotBlank(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("সক্রিয় করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoActivationDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

// ==============================================================================
// HELPER COMPOSABLES
// ==============================================================================
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content
        )
    }
}

@Composable
private fun SectionHeaderWithIcon(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    helperText: String? = null,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Spacing.md)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!helperText.isNullOrBlank()) {
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        control()
    }
}

@Composable
private fun ThemeTile(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.border
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.dokanColors.surfaceAlt

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier.height(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun brandSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DokanPro", text))
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
