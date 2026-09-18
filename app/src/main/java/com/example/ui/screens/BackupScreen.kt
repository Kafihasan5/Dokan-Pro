package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
fun BackupScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backupLogs by viewModel.backupLogs.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val firebaseSyncStatus by viewModel.firebaseSyncStatus.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var shopCodeInput by remember(config.firebaseShopCode) {
        mutableStateOf(config.firebaseShopCode.ifBlank { viewModel.getDefaultShopCode() })
    }
    val isOwner = config.userRole == "owner"
    var isAutoBackupEnabled by remember { mutableStateOf(true) }
    var isWifiOnly by remember { mutableStateOf(true) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClearDummyConfirmDialog by remember { mutableStateOf(false) }
    var showSelectiveDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }

    var showOwnerChangeCodeDialog by remember { mutableStateOf(false) }
    var ownerTargetCodeInput by remember { mutableStateOf("") }
    var ownerMasterPinInput by remember { mutableStateOf("") }
    var isRestoringCloud by remember { mutableStateOf(false) }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }
    var staffShopInput by remember { mutableStateOf("") }
    var staffPinInput by remember { mutableStateOf("") }
    var staffNameInput by remember { mutableStateOf("") }
    var isJoiningStaff by remember { mutableStateOf(false) }

    // Selective Deletion Filters
    var selClearSales by remember { mutableStateOf(true) }
    var selClearProducts by remember { mutableStateOf(false) }
    var selClearCustomers by remember { mutableStateOf(false) }
    var selClearExpenses by remember { mutableStateOf(true) }
    var selClearPurchases by remember { mutableStateOf(false) }
    var selTimeFilter by remember { mutableStateOf("all") }

    DokanScreenScaffold(
        title = "ব্যাকআপ ও রিস্টোর",
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
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // ==============================================================
            // CARD 0: গুগল ফায়ারবেস লাইভ সিঙ্ক (Firebase Realtime Database)
            // ==============================================================
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(1, RoundedCornerShape(Radius.lg))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (firebaseSyncStatus.isConnected) MaterialTheme.dokanColors.successContainer
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = if (firebaseSyncStatus.isConnected) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.md))
                                Column {
                                    Text(
                                        text = "গুগল ক্লাউড লাইভ সিঙ্ক",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "মালিক ও কর্মচারীর ফোন অটো সিঙ্ক (আজীবন ফ্রি)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = if (firebaseSyncStatus.isConnected) MaterialTheme.dokanColors.successContainer else MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, if (firebaseSyncStatus.isConnected) MaterialTheme.dokanColors.success.copy(alpha = 0.3f) else MaterialTheme.dokanColors.border)
                            ) {
                                Text(
                                    text = if (firebaseSyncStatus.isConnected) "🟢 লাইভ সিঙ্ক সক্রিয়" else "⚪ অফলাইন",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (firebaseSyncStatus.isConnected) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Zero Data Loss Guarantee Banner
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = "লোকাল ডাটা ১০০% সুরক্ষিত: বর্তমান কোনো ডাটা মুছবে না। ফোনের সব পণ্য ও হিসাব রেখে স্বয়ংক্রিয়ভাবে ক্লাউডে লাইভ সিঙ্ক হবে।",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Active Mode Banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "এই ডিভাইসের মোড:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, if (isOwner) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.dokanColors.border)
                            ) {
                                Text(
                                    text = if (isOwner) "👑 দোকান মালিক (Owner)" else "👤 কর্মচারী (Staff)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.dokanColors.border)

                        if (isOwner) {
                            // OWNER VIEW
                            Text(
                                text = "আপনার দোকান কোড (Shop Code):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(Radius.md),
                                color = MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = shopCodeInput,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(shopCodeInput))
                                                viewModel.showToast("দোকান কোড কপি হয়েছে! কর্মচারীকে পাঠান।")
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "কপি করুন",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "দোকান প্রো অ্যাপে যুক্ত হতে আমার দোকান কোড: $shopCodeInput এবং স্টাফ পিন: ${config.staffPin.ifBlank { "0000" }}"
                                                    )
                                                    type = "text/plain"
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(shareIntent, "দোকান কোড শেয়ার করুন")
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "শেয়ার করুন",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        ownerTargetCodeInput = shopCodeInput
                                        ownerMasterPinInput = ""
                                        restoreErrorMessage = null
                                        showOwnerChangeCodeDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("দোকান কোড পরিবর্তন / নিরাপদ রিস্টোর", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Text(
                                text = "💡 কর্মচারীর ফোনে অ্যাপ ইনস্টল করে এই দোকান কোড এবং স্টাফ পিন (${config.staffPin.ifBlank { "0000" }}) দিলে সরাসরি যুক্ত হয়ে যাবে।",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (firebaseSyncStatus.isConnected) {
                                DokanPrimaryButton(
                                    text = "সকল ডাটা ক্লাউডে সেভ করুন (Push All Data)",
                                    onClick = { viewModel.pushAllDataToFirebase() },
                                    isLoading = isSyncing,
                                    enabled = !isSyncing
                                )

                                DokanSecondaryButton(
                                    text = "ক্লাউড থেকে সমস্ত তথ্য রিস্টোর করুন (Cloud Restore)",
                                    onClick = { viewModel.restoreAllDataFromFirebase() },
                                    isLoading = isSyncing,
                                    enabled = !isSyncing
                                )

                                DokanSecondaryButton(
                                    text = "লাইভ সিঙ্ক সংযোগ বিচ্ছিন্ন করুন",
                                    onClick = { viewModel.disconnectFirebaseShop() }
                                )
                            } else {
                                DokanPrimaryButton(
                                    text = "গুগল ক্লাউড লাইভ সিঙ্ক চালু করুন",
                                    onClick = {
                                        viewModel.connectFirebaseShop(shopCodeInput, "owner")
                                    }
                                )

                                DokanSecondaryButton(
                                    text = "পূর্বের দোকান কোড ও পিন দিয়ে রিস্টোর করুন",
                                    onClick = {
                                        ownerTargetCodeInput = ""
                                        ownerMasterPinInput = ""
                                        restoreErrorMessage = null
                                        showOwnerChangeCodeDialog = true
                                    }
                                )
                            }
                        } else {
                            // STAFF VIEW
                            if (firebaseSyncStatus.isConnected) {
                                Surface(
                                    shape = RoundedCornerShape(Radius.md),
                                    color = MaterialTheme.dokanColors.successContainer.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.dokanColors.success.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Text(
                                            text = "🟢 সংযুক্ত দোকান: ${firebaseSyncStatus.shopCode}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.dokanColors.success
                                        )
                                        Text(
                                            text = "কর্মচারীর নাম: ${config.staffName.ifBlank { "স্টাফ" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "🛡️ স্টাফ মোডে পণ্য তালিকা এবং লাইভ বিক্রি পরিচালিত হচ্ছে। লাভ-ক্ষতি, রিপোর্ট ও বাকির খাতা মালিকের গোপন পিন ছাড়া দেখা সম্ভব নয়।",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                DokanSecondaryButton(
                                    text = "সংযোগ বিচ্ছিন্ন করুন",
                                    onClick = { viewModel.disconnectFirebaseShop() }
                                )
                            } else {
                                Text(
                                    text = "মালিকের দোকানের সাথে কানেক্ট করতে দোকান কোড, স্টাফ পিন ও আপনার নাম দিন:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                DokanTextField(
                                    value = staffShopInput,
                                    onValueChange = { staffShopInput = it.trim() },
                                    label = "দোকান কোড অথবা মালিকের ইমেইল",
                                    placeholder = "যেমন: SHOP-XXXXXX বা owner@gmail.com",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DokanTextField(
                                    value = staffPinInput,
                                    onValueChange = { staffPinInput = it.filter { c -> c.isDigit() }.take(4) },
                                    label = "স্টাফ অ্যাক্সেস পিন (৪ সংখ্যা)",
                                    placeholder = "যেমন: 0000",
                                    keyboardType = KeyboardType.NumberPassword,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DokanTextField(
                                    value = staffNameInput,
                                    onValueChange = { staffNameInput = it },
                                    label = "আপনার নাম (কর্মচারী)",
                                    placeholder = "যেমন: রহিম",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DokanPrimaryButton(
                                    text = if (isJoiningStaff) "যাচাই হচ্ছে..." else "দোকানে যুক্ত হন (Join Shop)",
                                    onClick = {
                                        if (staffShopInput.isBlank() || staffPinInput.isBlank()) {
                                            viewModel.showToast("দোকান কোড এবং স্টাফ পিন আবশ্যক")
                                            return@DokanPrimaryButton
                                        }
                                        isJoiningStaff = true
                                        viewModel.secureJoinAsStaff(staffShopInput, staffPinInput, staffNameInput) { success, msg ->
                                            isJoiningStaff = false
                                            viewModel.showToast(msg)
                                        }
                                    },
                                    isLoading = isJoiningStaff,
                                    enabled = !isJoiningStaff && staffShopInput.isNotBlank() && staffPinInput.isNotBlank()
                                )
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // CARD 1: লোকাল ফাইল ব্যাকআপ (Offline First)
            // ==============================================================
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(1, RoundedCornerShape(Radius.lg))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    text = "লোকাল ফাইল ব্যাকআপ (অফলাইন)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ইন্টারনেট ছাড়াও সব ডেটা ফোনে সংরক্ষিত",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.dokanColors.border)

                        DokanPrimaryButton(
                            text = "লোকাল ব্যাকআপ ফাইল তৈরি করুন",
                            onClick = {
                                viewModel.createBackup { jsonString ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, jsonString)
                                        putExtra(Intent.EXTRA_TITLE, "DokanPro_Backup_${System.currentTimeMillis()}.json")
                                        type = "application/json"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "ফোন মেমোরি / ফাইলে সংরক্ষণ করুন"))
                                }
                            }
                        )

                        DokanSecondaryButton(
                            text = "ব্যাকআপ ফাইল থেকে রিস্টোর করুন",
                            onClick = { showRestoreDialog = true }
                        )
                    }
                }
            }

            // ==============================================================
            // AUTO BACKUP CARD (Visually unchanged per Prompt 11 note)
            // ==============================================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("অটো ব্যাকআপ সেটিংস", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("প্রতিদিন রাত ১২টায় অটো ব্যাকআপ", fontSize = 14.sp)
                                Text("ওয়াইফাই বা ডেটা পেলেই ক্লাউড বা লোকাল ব্যাকআপ হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAutoBackupEnabled,
                                onCheckedChange = { isAutoBackupEnabled = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("শুধুমাত্র Wi-Fi তে ব্যাকআপ", fontSize = 14.sp)
                                Text("মোবাইল ডেটা খরচ রোধ করতে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isWifiOnly,
                                onCheckedChange = { isWifiOnly = it }
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // CARD 3: ডেটা পরিষ্কার ও ফিল্টারিং
            // ==============================================================
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(1, RoundedCornerShape(Radius.lg))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        SectionHeader(title = "১-ক্লিকে ডেটা পরিষ্কার ও ফিল্টারিং")
                        Text(
                            text = "দোকানের হিসাব ফ্রেশ করতে ১-ক্লিকে নির্দিষ্ট হিসাব বা সকল ডামি ডেটা ফিল্টার করে পরিষ্কার করতে পারেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DokanDangerButton(
                            text = "ফিল্টার করে বাছাইকৃত ডেটা মুছুন",
                            onClick = { showSelectiveDeleteDialog = true }
                        )

                        DokanDangerButton(
                            text = "১-ক্লিকে সব ডামি ডেটা সম্পূর্ণ মুছুন",
                            onClick = { showClearDummyConfirmDialog = true }
                        )

                        DokanSecondaryButton(
                            text = "২০টি স্যাম্পল মুদি পণ্য ও ডামি হিসাব লোড করুন",
                            onClick = { showResetConfirmDialog = true }
                        )
                    }
                }
            }

            // ==============================================================
            // BACKUP LOG TIMELINE
            // ==============================================================
            item {
                SectionHeader(title = "পূর্ববর্তী ব্যাকআপ লগ")
            }

            if (backupLogs.isEmpty()) {
                item {
                    Text(
                        text = "এখনো কোনো ব্যাকআপ লগ তৈরি হয়নি",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(backupLogs, key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Success or failure dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.status == "success") MaterialTheme.dokanColors.success
                                        else MaterialTheme.dokanColors.danger
                                    )
                            )

                            Spacer(modifier = Modifier.width(Spacing.md))

                            // File name in monospace style + details in labelSmall
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.fileName,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "${log.recordCount} রেকর্ড • ${(log.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Time right-aligned
                            Text(
                                text = Formatters.formatBengaliTime(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showClearDummyConfirmDialog) {
        DokanConfirmDialog(
            title = "সব ডামি ডেটা মুছে ফেলতে চান?",
            message = "দোকানের সকল স্যাম্পল পণ্য, বিক্রির রেকর্ড, কাস্টমার তালিকা ও বাকি খাতার এন্ট্রি সম্পূর্ণ মুছে যাবে। আপনার দোকান সম্পূর্ণ ফ্রেশ ও খালি হয়ে যাবে।",
            confirmLabel = "হ্যাঁ, সব ডামি ডেটা মুছুন",
            onConfirm = {
                viewModel.clearAllDummyData {
                    showClearDummyConfirmDialog = false
                }
            },
            onDismiss = { showClearDummyConfirmDialog = false },
            isDestructive = true
        )
    }

    if (showResetConfirmDialog) {
        DokanConfirmDialog(
            title = "ডেটা রিসেট নিশ্চিতকরণ",
            message = "আপনি কি নিশ্চিত? এতে বর্তমান সকল পরীক্ষামূলক ডেটা মুছে যাবে এবং প্রাথমিক ২০টি মুদি পণ্য ও ক্যাটাগরি নতুন করে লোড হবে।",
            confirmLabel = "লোড করুন",
            onConfirm = {
                viewModel.resetAllData()
                showResetConfirmDialog = false
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }

    if (showSelectiveDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showSelectiveDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.dokanColors.danger) },
            title = { Text("ফিল্টার করে ডেটা মুছে ফেলুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text("কোন কোন ডেটা মুছতে চান তা নির্বাচন করুন:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearSales, onCheckedChange = { selClearSales = it })
                        Text("বিক্রয় ইতিহাস ও সকল ইনভয়েস", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearProducts, onCheckedChange = { selClearProducts = it })
                        Text("সকল পণ্য ও স্টক তালিকা", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearCustomers, onCheckedChange = { selClearCustomers = it })
                        Text("কাস্টমার তালিকা ও বাকি খাতা", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearExpenses, onCheckedChange = { selClearExpenses = it })
                        Text("দোকানের খরচের হিসাব", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearPurchases, onCheckedChange = { selClearPurchases = it })
                        Text("পাইকারি মালামাল ক্রয়ের হিসাব", style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                    Text("সময়কাল ফিল্টার:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selTimeFilter == "all",
                            onClick = { selTimeFilter = "all" },
                            label = { Text("সব সময়", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = selTimeFilter == "1m",
                            onClick = { selTimeFilter = "1m" },
                            label = { Text("১ মাস পূর্বের", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = selTimeFilter == "3m",
                            onClick = { selTimeFilter = "3m" },
                            label = { Text("৩ মাস পূর্বের", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val beforeTs: Long? = when (selTimeFilter) {
                            "1m" -> System.currentTimeMillis() - 30L * 86400000L
                            "3m" -> System.currentTimeMillis() - 90L * 86400000L
                            "6m" -> System.currentTimeMillis() - 180L * 86400000L
                            "1y" -> System.currentTimeMillis() - 365L * 86400000L
                            else -> null
                        }

                        viewModel.clearSelectiveData(
                            clearSales = selClearSales,
                            clearProducts = selClearProducts,
                            clearCustomers = selClearCustomers,
                            clearExpenses = selClearExpenses,
                            clearPurchases = selClearPurchases,
                            beforeTimestamp = beforeTs
                        ) {
                            showSelectiveDeleteDialog = false
                        }
                    },
                    enabled = selClearSales || selClearProducts || selClearCustomers || selClearExpenses || selClearPurchases,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.dokanColors.danger),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("বাছাইকৃত ডেটা মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectiveDeleteDialog = false }) { Text("বাতিল") }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("ব্যাকআপ ফাইল থেকে রিস্টোর", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("সংরক্ষিত ব্যাকআপ JSON কোডটি নিচে পেস্ট করুন:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        placeholder = { Text("{\"products\": [...], \"sales\": [...]}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreFromBackupJson(restoreJsonText) { success ->
                            if (success) {
                                showRestoreDialog = false
                                restoreJsonText = ""
                            }
                        }
                    },
                    enabled = restoreJsonText.isNotBlank(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("রিস্টোর সম্পন্ন করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("বাতিল") }
            }
        )
    }

    if (showOwnerChangeCodeDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoringCloud) showOwnerChangeCodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("নিরাপদ ক্লাউড রিস্টোর")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "দোকানের যাবতীয় ডেটা ও গোপনীয়তা সুরক্ষায় মালিকের মাস্টার সিকিউরিটি পিন আবশ্যক। সঠিক পিন ব্যতীত কেউ আপনার তথ্য দেখতে পারবে না:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DokanTextField(
                        value = ownerTargetCodeInput,
                        onValueChange = {
                            ownerTargetCodeInput = it
                            restoreErrorMessage = null
                        },
                        label = "দোকান কোড অথবা মালিকের ইমেইল",
                        placeholder = "যেমন: SHOP-XXXXXX বা email@gmail.com",
                        modifier = Modifier.fillMaxWidth()
                    )

                    DokanTextField(
                        value = ownerMasterPinInput,
                        onValueChange = {
                            ownerMasterPinInput = it.filter { c -> c.isDigit() }.take(6)
                            restoreErrorMessage = null
                        },
                        label = "মাস্টার সিকিউরিটি পিন (৪-৬ ডিজিট)",
                        placeholder = "পিন নম্বর লিখুন",
                        keyboardType = KeyboardType.NumberPassword,
                        isError = restoreErrorMessage != null,
                        errorText = restoreErrorMessage,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isRestoringCloud) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = ownerTargetCodeInput.trim()
                        val pin = ownerMasterPinInput.trim()
                        if (input.isBlank()) {
                            restoreErrorMessage = "দোকান কোড অথবা ইমেইল লিখুন"
                            return@Button
                        }
                        if (pin.length < 4) {
                            restoreErrorMessage = "মাস্টার পিন কমপক্ষে ৪ ডিজিট লিখুন"
                            return@Button
                        }
                        isRestoringCloud = true
                        viewModel.secureRestoreOwnerShop(input, pin) { success, msg ->
                            isRestoringCloud = false
                            if (success) {
                                showOwnerChangeCodeDialog = false
                                shopCodeInput = viewModel.shopConfig.value.firebaseShopCode
                            } else {
                                restoreErrorMessage = msg
                            }
                        }
                    },
                    enabled = !isRestoringCloud && ownerTargetCodeInput.isNotBlank() && ownerMasterPinInput.isNotBlank()
                ) {
                    Text(if (isRestoringCloud) "যাচাই ও রিস্টোর হচ্ছে..." else "যাচাই করে রিস্টোর করুন")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOwnerChangeCodeDialog = false },
                    enabled = !isRestoringCloud
                ) {
                    Text("বাতিল")
                }
            }
        )
    }
}
