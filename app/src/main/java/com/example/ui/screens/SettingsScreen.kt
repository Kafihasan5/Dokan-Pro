package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.UpdateDialog
import com.example.ui.components.CategoryUnitManagerDialog
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess

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
    var showInAppSettingsUpdateDialog by remember { mutableStateOf(false) }
    var showCategoryUnitManager by remember { mutableStateOf(false) }
    var initialManageTab by remember { mutableStateOf(0) }

    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("দোকান ও অ্যাপ সেটিংস") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "পিছনে যান")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_settings_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Shop Profile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("দোকানের প্রোফাইল (রসিদে প্রদর্শিত)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("দোকানের নাম") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = shopAddress,
                            onValueChange = { shopAddress = it },
                            label = { Text("দোকানের ঠিকানা") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = shopPhone,
                            onValueChange = { shopPhone = it },
                            label = { Text("মোবাইল নম্বর") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = tagline,
                            onValueChange = { tagline = it },
                            label = { Text("স্লোগান / ট্যাগলাইন") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section 2: Localization & Numerals
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ভাষা ও সংখ্যা প্রদর্শন", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("বাংলা সংখ্যা ব্যবহার (যেমন: ৳১২৫.০০)", fontSize = 14.sp)
                                Text("বন্ধ রাখলে ইংরেজি সংখ্যা (৳125.00) দেখাবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = useBengaliNumerals,
                                onCheckedChange = { useBengaliNumerals = it }
                            )
                        }

                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text("মুদ্রা প্রতীক") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section 3: VAT & Stock Policies
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ভ্যাট ও স্টক নীতি", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ভ্যাট (VAT) সক্রিয় করুন", fontSize = 14.sp)
                                Text("ইনভয়েসে নির্ধারিত হারে ভ্যাট যুক্ত হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = vatEnabled,
                                onCheckedChange = { vatEnabled = it }
                            )
                        }

                        if (vatEnabled) {
                            OutlinedTextField(
                                value = vatPercentageText,
                                onValueChange = { vatPercentageText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("ভ্যাট হার (%)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("নেগেটিভ স্টক অনুমোদন", fontSize = 14.sp)
                                Text("স্টক শূন্য থাকলেও তাৎক্ষণিক বিক্রি চালু রাখা", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = allowNegativeStock,
                                onCheckedChange = { allowNegativeStock = it }
                            )
                        }
                    }
                }
            }

            // Section: Category & Measurement Unit Management
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ক্যাটাগরি ও পরিমাপের একক ব্যবস্থাপনা", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            text = "পণ্যের ক্যাটাগরি এবং বিক্রয় ও ক্রয়ের পরিমাপের একক (যেমন: কেজি, পিস, লিটার, বস্তা) নতুন যোগ, নাম পরিবর্তন বা মুছে ফেলার নিয়ন্ত্রণ।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    initialManageTab = 0
                                    showCategoryUnitManager = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ক্যাটাগরি (${categories.size})", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    initialManageTab = 1
                                    showCategoryUnitManager = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("এককসমূহ (${units.size})", fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = {
                                initialManageTab = 0
                                showCategoryUnitManager = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ক্যাটাগরি ও একক পরিচালনা করুন", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Section 4: Day & Night Mode (Theme & Display)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("থিম ও ডিসপ্লে (ডে ও নাইট মোড)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            "আপনার চোখের আরাম ও দোকানের আলোর সাথে মানানসই মোড নির্বাচন করুন।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = themeMode == "light",
                                onClick = {
                                    themeMode = "light"
                                    viewModel.setThemeMode("light")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("☀️ ডে মোড") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == "dark",
                                onClick = {
                                    themeMode = "dark"
                                    viewModel.setThemeMode("dark")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("🌙 নাইট মোড") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == "system",
                                onClick = {
                                    themeMode = "system"
                                    viewModel.setThemeMode("system")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("📱 সিস্টেম") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 5: Realtime Auto-Backup & Remote Features
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("রিয়েলটাইম অটো-ব্যাকআপ ও ফিচার", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSyncing) MaterialTheme.colorScheme.primaryContainer else StatusSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isSyncing) "হালনাগাদ হচ্ছে..." else "সক্রিয় ও সুরক্ষিত",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSyncing) MaterialTheme.colorScheme.primary else StatusSuccess,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "অনলাইন ডাটাবেসে পণ্যের নাম, দাম, স্টক কিংবা কনফিগারেশন পরিবর্তন করলে অ্যাপ স্বয়ংক্রিয়ভাবে কয়েক সেকেন্ডের মধ্যে আপডেট গ্রহণ করে। অফলাইনে বিক্রি করলেও ইন্টারনেট ফিরে আসামাত্রই সব হিসাব সংরক্ষিত হয়ে যায়।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (lastSyncTime != null) {
                            Text(
                                text = "সর্বশেষ আপডেট: ${com.example.util.Formatters.formatBengaliTime(lastSyncTime!!)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { viewModel.syncToSupabase(silent = false) },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSyncing) "হালনাগাদ হচ্ছে..." else "তথ্য রিফ্রেশ ও রিমোট আপডেট নিন")
                        }
                    }
                }
            }

            // Section 6: Security, PIN & Role
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("নিরাপত্তা ও ইউজার রোল", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Text("বর্তমান অ্যাক্টিভ রোল:", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = userRole == "owner",
                                onClick = { userRole = "owner" },
                                label = { Text("মালিক (Owner)") }
                            )
                            FilterChip(
                                selected = userRole == "staff",
                                onClick = { userRole = "staff" },
                                label = { Text("কর্মচারী (Staff)") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("৪-ডিজিট অ্যাপ পিন লক", fontSize = 14.sp)
                                Text("অ্যাপ চালুতে পিন সুরক্ষা", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = pinEnabled,
                                onCheckedChange = { pinEnabled = it }
                            )
                        }

                        if (pinEnabled) {
                            OutlinedTextField(
                                value = pinCode,
                                onValueChange = { pinCode = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("৪ ডিজিট পিন কোড") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    val updated = config.copy(
                                        pinEnabled = true,
                                        pinCode = pinCode.trim()
                                    )
                                    viewModel.updateShopConfig(updated)
                                    viewModel.lockApp()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("এখনই অ্যাপ লক করুন (Lock Now)")
                            }
                        }
                    }
                }
            }

            // Section: App Version & In-App Update Management
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("অ্যাপ ভার্সন ও আপডেট নিয়ন্ত্রণ", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "বর্তমান সংস্করণ: v${appUpdateInfo.currentVersionName}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "বিল্ড কোড: ${appUpdateInfo.currentVersionCode}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.checkForUpdates(silent = false) }
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("আপডেট চেক")
                            }
                        }

                        // Update Notification Banner
                        if (appUpdateInfo.isUpdateAvailable) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "নতুন আপডেট পাওয়া গেছে: v${appUpdateInfo.latestVersionName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (appUpdateInfo.updateNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "পরিবর্তনসমূহ: ${appUpdateInfo.updateNotes}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            showInAppSettingsUpdateDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("এখনই আপডেট ইনস্টল করুন")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Cloud & Local Factory Reset (Danger Zone)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = StatusDanger
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "সকল ডেটা মুছে ফেলুন (Cloud + Local)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = StatusDanger
                            )
                        }
                        Text(
                            "ক্লাউড (Supabase) এবং ফোন (লোকাল স্টোরেজ) থেকে সকল পণ্য, বিক্রি, কাস্টমার, বাকি খাতা ও খরচের সমস্ত হিসাব সম্পূর্ণ মুছে ফ্রেশ করুন। এটি নিশ্চিত করতে পাসওয়ার্ড প্রয়োজন হবে।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { showWipeAllDataDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ক্লাউড ও লোকাল ডেটা সম্পূর্ণ মুছুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showWipeAllDataDialog) {
        var inputPassword by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isWiping by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isWiping) {
                    showWipeAllDataDialog = false
                }
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusDanger,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "সকল ডেটা সম্পূর্ণ মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    color = StatusDanger
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "সতর্কতা: ক্লাউড সার্ভার (Supabase) এবং ফোন থেকে সকল পণ্য, বিক্রির রেকর্ড, কাস্টমার তালিকা, বাকি খাতা ও খরচের সমস্ত হিসাব স্থায়ীভাবে মুছে যাবে। এটি আর ফিরিয়ে আনা সম্ভব নয়!\n\nমুছে ফেলতে পাসওয়ার্ড লিখুন:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = {
                            inputPassword = it
                            errorMessage = null
                        },
                        label = { Text("নিরাপত্তা পাসওয়ার্ড") },
                        placeholder = { Text("পাসওয়ার্ড লিখুন") },
                        singleLine = true,
                        isError = errorMessage != null,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "পাসওয়ার্ড লুকান" else "পাসওয়ার্ড দেখুন"
                                )
                            }
                        },
                        enabled = !isWiping,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = StatusDanger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isWiping) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = StatusDanger
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("ক্লাউড ও লোকাল ডেটা মোছা হচ্ছে...", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPassword.trim() != "paponshop") {
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    enabled = !isWiping && inputPassword.isNotBlank()
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
            }
        )
    }

    if (showInAppSettingsUpdateDialog && appUpdateInfo.isUpdateAvailable) {
        UpdateDialog(
            updateInfo = com.example.util.AppUpdateInfo(
                versionCode = appUpdateInfo.latestVersionCode,
                versionName = appUpdateInfo.latestVersionName,
                downloadUrl = appUpdateInfo.apkDownloadUrl,
                releaseNotes = appUpdateInfo.updateNotes
            ),
            onDismiss = { showInAppSettingsUpdateDialog = false }
        )
    }

    if (showCategoryUnitManager) {
        CategoryUnitManagerDialog(
            viewModel = viewModel,
            initialTab = initialManageTab,
            onDismiss = { showCategoryUnitManager = false }
        )
    }
}
