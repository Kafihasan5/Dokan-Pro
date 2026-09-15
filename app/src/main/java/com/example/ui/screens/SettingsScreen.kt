package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
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

            // Section 5: Customer Online Cloud Sync (Supabase Setup)
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
                                    imageVector = if (isCustomerCloudConfigured) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (isCustomerCloudConfigured) StatusSuccess else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("অনলাইন ক্লাউড সিঙ্ক (Supabase)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCustomerCloudConfigured) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isCustomerCloudConfigured) "সংযুক্ত ✓" else "সেটআপ করা নেই",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCustomerCloudConfigured) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "আপনার নিজস্ব Supabase প্রজেক্টের URL এবং API Key এখানে সেভ করে অ্যাপের সমস্ত পণ্য, বিক্রি, বাকি ও খরচের হিসাব অনলাইনে নিরাপদে ব্যাকআপ ও যেকোনো ডিভাইসে রিস্টোর করতে পারেন।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = cloudUrlInput,
                            onValueChange = {
                                cloudUrlInput = it
                                cloudTestMessage = null
                            },
                            label = { Text("Supabase Project URL") },
                            placeholder = { Text("https://xxxx.supabase.co") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = cloudKeyInput,
                            onValueChange = {
                                cloudKeyInput = it
                                cloudTestMessage = null
                            },
                            label = { Text("Supabase Secret / Anon API Key") },
                            placeholder = { Text("eyJhbGciOi...") },
                            singleLine = true,
                            visualTransformation = if (isCloudKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCloudKeyVisible = !isCloudKeyVisible }) {
                                    Icon(
                                        if (isCloudKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (cloudTestMessage != null) {
                            Text(
                                text = cloudTestMessage ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cloudTestMessage?.contains("সফল") == true) StatusSuccess else StatusDanger
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveCustomerCloudConfig(cloudUrlInput, cloudKeyInput)
                                },
                                enabled = cloudUrlInput.isNotBlank() && cloudKeyInput.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সংরক্ষণ করুন", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    isTestingCloud = true
                                    cloudTestMessage = "টেস্ট করা হচ্ছে..."
                                    viewModel.testCustomerCloudConnection(cloudUrlInput, cloudKeyInput) { ok, msg ->
                                        isTestingCloud = false
                                        cloudTestMessage = msg
                                    }
                                },
                                enabled = !isTestingCloud && cloudUrlInput.isNotBlank() && cloudKeyInput.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTestingCloud) "পরীক্ষা..." else "কানেকশন টেস্ট", fontSize = 13.sp)
                            }
                        }

                        if (isCustomerCloudConfigured) {
                            Divider()

                            if (lastSyncTime != null) {
                                Text(
                                    text = "সর্বশেষ ক্লাউড ব্যাকআপ: ${com.example.util.Formatters.formatBengaliTime(lastSyncTime!!)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.backupToCustomerCloud { _, _ -> } },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSyncing) "সেভ..." else "ক্লাউড ব্যাকআপ", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showCloudRestoreConfirmDialog = true },
                                    enabled = !isSyncing,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ক্লাউড রিস্টোর", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCustomerCloudConfig {
                                        cloudUrlInput = ""
                                        cloudKeyInput = ""
                                        cloudTestMessage = null
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ক্লাউড ডিসকানেক্ট করুন", fontSize = 12.sp)
                            }
                        }

                        TextButton(
                            onClick = { showSqlSchemaDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সুপাবেস ডাটাবেস স্ক্রিপ্ট (SQL Setup)", fontSize = 12.sp)
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

            // Section: Software License & Activation Information
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text("সফটওয়্যার লাইসেন্স ও অ্যাক্টিভেশন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Webix Solution Verified License",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        val lic = licenseInfo
                        if (lic != null) {
                            Text("নিবন্ধিত ইমেইল: ${lic.email}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("ডিভাইস আইডি: ${lic.deviceId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "লাইসেন্স মেয়াদ: ${if (lic.isLifetime) "আজীবন (Lifetime)" else lic.expiresAt ?: "অ্যাক্টিভ"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("স্ট্যাটাস: সক্রিয় (Active & Verified)", fontSize = 12.sp, color = StatusSuccess, fontWeight = FontWeight.Bold)
                        } else if (isDemoMode) {
                            val totalSeconds = (remainingDemoMillis / 1000).coerceAtLeast(0)
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            val timeFormatted = String.format(java.util.Locale.ENGLISH, "%02d:%02d", minutes, seconds)
                            val bengaliTime = com.example.util.Formatters.toBengaliDigits(timeFormatted)

                            Text("স্ট্যাটাস: ১ ঘণ্টার ফ্রি ডেমো মোড (বাকি: $bengaliTime মিনিট)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("ডিভাইস আইডি: ${viewModel.getDeviceId()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "বর্তমানে ৭ দিনের ডামি ডাটা সংযুক্ত আছে। নিয়মিত ব্যবহারের জন্য ওয়েবসাইট থেকে আজীবন লাইসেন্স সংগ্রহ করে (৳৪৯০) আপনার ইমেইল দিয়ে সক্রিয় করুন। সক্রিয় করার সাথে সাথে সমস্ত ডামি ডাটা মুছে ফ্রেশ শপ ডেটাবেস প্রস্তুত হবে।",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://webixsolution.store/product/dokan-pro"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("লাইসেন্স কিনুন (৳৪৯০)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showDemoActivationDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("অ্যাক্টিভ করুন", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text("ডিভাইস আইডি: ${viewModel.getDeviceId()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("স্ট্যাটাস: সক্রিয় (অফলাইন লোকাল মোড)", fontSize = 12.sp, color = StatusSuccess)
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
                                onClick = { viewModel.checkForUpdates(silent = false) },
                                enabled = !isCheckingUpdate
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("যাচাই হচ্ছে...")
                                } else {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("আপডেট চেক")
                                }
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
                                            viewModel.openUpdateDialog()
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
                        "সতর্কতা: ফোন থেকে সকল পণ্য, বিক্রির রেকর্ড, কাস্টমার তালিকা, বাকি খাতা ও খরচের সমস্ত হিসাব স্থায়ীভাবে মুছে যাবে। এটি আর ফিরিয়ে আনা সম্ভব নয়!\n\nমুছে ফেলতে পাসওয়ার্ড লিখুন (dokanpro):",
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

    if (showCategoryUnitManager) {
        CategoryUnitManagerDialog(
            viewModel = viewModel,
            initialTab = initialManageTab,
            onDismiss = { showCategoryUnitManager = false }
        )
    }

    if (showCloudRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("ক্লাউড থেকে রিস্টোর করতে চান?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "আপনার Supabase ক্লাউডে সংরক্ষিত পণ্য, বিক্রি, বাকি খাতা ও খরচের হিসাব বর্তমান ফোনে লোড ও সিঙ্ক হবে। আপনি কি রিস্টোর করতে চান?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudRestoreConfirmDialog = false
                        viewModel.restoreFromCustomerCloud { _, _ -> }
                    }
                ) {
                    Text("হ্যাঁ, ক্লাউড থেকে রিস্টোর করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudRestoreConfirmDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    if (showSqlSchemaDialog) {
        val schemaSql = """
-- Dokan Pro Customer Database Tables
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "আপনার ব্যক্তিগত Supabase ড্যাশবোর্ডে (supabase.com) গিয়ে SQL Editor-এ নিচের স্ক্রিপ্টটি পেস্ট করে Run করুন। সম্পূর্ণ স্ক্রিপ্টটি প্রজেক্টের customer_supabase_schema.sql ফাইলে দেওয়া আছে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = schemaSql,
                            fontSize = 10.sp,
                            maxLines = 8,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Supabase Schema", schemaSql))
                        viewModel.showToast("SQL স্ক্রিপ্ট কপি করা হয়েছে!")
                        showSqlSchemaDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("স্ক্রিপ্ট কপি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSqlSchemaDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    if (showDemoActivationDialog) {
        AlertDialog(
            onDismissRequest = { showDemoActivationDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Dokan-Pro লাইসেন্স সক্রিয় করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Webix Solution ওয়েবসাইট থেকে ক্রয়কৃত ইমেইলটি লিখুন। সক্রিয় হওয়ার সাথে সাথে সমস্ত ডামি ডাটা স্বয়ংক্রিয়ভাবে মুছে সম্পূর্ণ ফ্রেশ ডেটাবেস চালু হবে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = demoActivationEmail,
                        onValueChange = { demoActivationEmail = it },
                        placeholder = { Text("customer@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
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
                    enabled = demoActivationEmail.isNotBlank()
                ) {
                    Text("সক্রিয় করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoActivationDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
