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
import androidx.compose.ui.platform.LocalContext
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
    val customerUrl by viewModel.customerSupabaseUrl.collectAsState()
    val customerKey by viewModel.customerSupabaseKey.collectAsState()
    val isCustomerCloudConfigured by viewModel.isCustomerCloudConfigured.collectAsState()

    var isAutoBackupEnabled by remember { mutableStateOf(true) }
    var isWifiOnly by remember { mutableStateOf(true) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClearDummyConfirmDialog by remember { mutableStateOf(false) }
    var showSelectiveDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCloudConfigDialog by remember { mutableStateOf(false) }
    var showCloudRestoreConfirmDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }

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
            // CARD 2: অনলাইন ক্লাউড ব্যাকআপ (Customer Supabase)
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
                                            if (isCustomerCloudConfigured) MaterialTheme.dokanColors.successContainer
                                            else MaterialTheme.dokanColors.surfaceAlt
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCustomerCloudConfigured) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.md))
                                Column {
                                    Text(
                                        text = "অনলাইন ক্লাউড ব্যাকআপ",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isCustomerCloudConfigured) "সুপাবেস ক্লাউড সংযুক্ত" else "ক্লাউড সেটআপ করা নেই",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.successContainer else MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success.copy(alpha = 0.3f) else MaterialTheme.dokanColors.border)
                            ) {
                                Text(
                                    text = if (isCustomerCloudConfigured) "সংযুক্ত ✓" else "অফলাইন",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCustomerCloudConfigured) MaterialTheme.dokanColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (isCustomerCloudConfigured) {
                            Text(
                                text = "প্রজেক্ট: $customerUrl",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (lastSyncTime != null) {
                                Text(
                                    text = "সর্বশেষ ক্লাউড ব্যাকআপ: ${Formatters.formatBengaliTime(lastSyncTime!!)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            DokanPrimaryButton(
                                text = "এখনই ক্লাউডে ব্যাকআপ নিন (Cloud Push)",
                                onClick = { viewModel.backupToCustomerCloud { _, _ -> } },
                                isLoading = isSyncing,
                                enabled = !isSyncing
                            )

                            DokanSecondaryButton(
                                text = "ক্লাউড থেকে রিস্টোর করুন (Cloud Pull)",
                                onClick = { showCloudRestoreConfirmDialog = true },
                                enabled = !isSyncing
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                DokanSecondaryButton(
                                    text = "কানেকশন টেস্ট",
                                    onClick = { viewModel.testCustomerCloudConnection(customerUrl, customerKey) { _, _ -> } },
                                    modifier = Modifier.weight(1f)
                                )

                                DokanSecondaryButton(
                                    text = "ক্লাউড সেটিংস",
                                    onClick = { showCloudConfigDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Text(
                                text = "আপনার নিজস্ব Supabase প্রজেক্ট কানেক্ট করে অনলাইনে সমস্ত পণ্য, বিক্রি, বাকি ও খরচের হিসাব ক্লাউডে ব্যাকআপ রাখুন এবং যেকোনো ফোন থেকে রিস্টোর করুন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            DokanPrimaryButton(
                                text = "Supabase ক্লাউড সেটআপ করুন",
                                onClick = { showCloudConfigDialog = true }
                            )
                        }
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

    if (showCloudConfigDialog) {
        var inputUrl by remember { mutableStateOf(customerUrl) }
        var inputKey by remember { mutableStateOf(customerKey) }
        var isKeyVisible by remember { mutableStateOf(false) }
        var testMsg by remember { mutableStateOf<String?>(null) }
        var isTesting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCloudConfigDialog = false },
            icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("সুপাবেস ক্লাউড সেটিংস", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "আপনার ব্যক্তিগত Supabase প্রজেক্টের URL ও API Key দিন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DokanTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = "Supabase Project URL",
                        placeholder = "https://xxxx.supabase.co"
                    )

                    DokanTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = "Supabase API / Secret Key",
                        placeholder = "eyJhbGciOi...",
                        keyboardType = KeyboardType.Password,
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    if (testMsg != null) {
                        Text(
                            text = testMsg ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (testMsg?.contains("সফল") == true) MaterialTheme.dokanColors.success else MaterialTheme.dokanColors.danger
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        DokanSecondaryButton(
                            text = if (isTesting) "পরীক্ষা হচ্ছে..." else "কানেকশন টেস্ট",
                            onClick = {
                                isTesting = true
                                testMsg = "টেস্ট করা হচ্ছে..."
                                viewModel.testCustomerCloudConnection(inputUrl, inputKey) { _, msg ->
                                    isTesting = false
                                    testMsg = msg
                                }
                            },
                            enabled = !isTesting && inputUrl.isNotBlank() && inputKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        )

                        if (isCustomerCloudConfigured) {
                            DokanDangerButton(
                                text = "ডিসকানেক্ট",
                                onClick = {
                                    viewModel.clearCustomerCloudConfig {
                                        showCloudConfigDialog = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCustomerCloudConfig(inputUrl, inputKey) {
                            showCloudConfigDialog = false
                        }
                    },
                    enabled = inputUrl.isNotBlank() && inputKey.isNotBlank(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudConfigDialog = false }) { Text("বাতিল") }
            }
        )
    }

    if (showCloudRestoreConfirmDialog) {
        DokanConfirmDialog(
            title = "ক্লাউড থেকে রিস্টোর করতে চান?",
            message = "আপনার Supabase ক্লাউডে সংরক্ষিত পণ্য, বিক্রি, বাকি খাতা ও খরচের হিসাব বর্তমান ফোনে লোড ও সিঙ্ক হবে। এতে অফলাইনে করা কোনো অমিল ডেটা ক্লাউডের সাথে সমন্বয় হবে। আপনি কি রিস্টোর করতে চান?",
            confirmLabel = "হ্যাঁ, ক্লাউড থেকে রিস্টোর করুন",
            onConfirm = {
                showCloudRestoreConfirmDialog = false
                viewModel.restoreFromCustomerCloud { _, _ -> }
            },
            onDismiss = { showCloudRestoreConfirmDialog = false }
        )
    }
}
