package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.BackupLog
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
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

    var isAutoBackupEnabled by remember { mutableStateOf(true) }
    var isWifiOnly by remember { mutableStateOf(true) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showClearDummyConfirmDialog by remember { mutableStateOf(false) }
    var showSelectiveDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }

    // Selective Deletion Filters
    var selClearSales by remember { mutableStateOf(true) }
    var selClearProducts by remember { mutableStateOf(false) }
    var selClearCustomers by remember { mutableStateOf(false) }
    var selClearExpenses by remember { mutableStateOf(true) }
    var selClearPurchases by remember { mutableStateOf(false) }
    var selTimeFilter by remember { mutableStateOf("all") } // "all", "1m", "3m", "6m", "1y"


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("গুগল ড্রাইভ ও ব্যাকআপ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "পিছনে যান")
                    }
                }
            )
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
            // Offline First & Cloud Sync Status Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("অফলাইন-ফার্স্ট নিরাপত্তা", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("ইন্টারনেট ছাড়াও সব ডেটা ফোনে সংরক্ষিত", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(14.dp))

                        Text("ব্যাকআপ সংরক্ষণ ও রিস্টোর মাধ্যম:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        // Google Drive Backup Button
                        Button(
                            onClick = {
                                viewModel.createBackup { jsonString ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, jsonString)
                                        putExtra(Intent.EXTRA_TITLE, "DokanPro_Backup_${System.currentTimeMillis()}.json")
                                        type = "application/json"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Google Drive এ ব্যাকআপ সংরক্ষণ করুন"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Drive এ ব্যাকআপ সংরক্ষণ করুন")
                        }

                        // Local Storage Backup Button
                        OutlinedButton(
                            onClick = {
                                viewModel.createBackup { jsonString ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, jsonString)
                                        type = "application/json"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "ফোন মেমোরি / লোকাল ফাইলে সংরক্ষণ করুন"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("লোকাল ব্যাকআপ (মেমোরি / ফাইল)")
                        }

                        // Restore Backup Button
                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ব্যাকআপ ফাইল থেকে রিস্টোর করুন")
                        }
                    }
                }
            }

            // Supabase Cloud Realtime Two-Way Sync
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("রিয়েলটাইম অটো-ব্যাকআপ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        text = if (isSyncing) "হালনাগাদ হচ্ছে..." else "সবসময় সক্রিয় ও সংরক্ষিত",
                                        fontSize = 12.sp,
                                        color = if (isSyncing) MaterialTheme.colorScheme.primary else StatusSuccess
                                    )
                                }
                            }
                        }

                        Text(
                            text = "প্রতিটি বিক্রি, পণ্যের হিসাব ও বাকি খাতা তাৎক্ষণিকভাবে সুরক্ষিত থাকে। কোনো কারণে ইন্টারনেট বিচ্ছিন্ন থাকলেও চিন্তা নেই—ইন্টারনেট আসার সাথে সাথেই সম্পূর্ণ ডেটা স্বয়ংক্রিয়ভাবে হালনাগাদ ও ব্যাকআপ হয়ে যায়।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (lastSyncTime != null) {
                            Text(
                                text = "সর্বশেষ ব্যাকআপ: ${Formatters.formatBengaliTime(lastSyncTime!!)}",
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
                            Text(if (isSyncing) "হালনাগাদ হচ্ছে..." else "এখনই ডেটা রিফ্রেশ ও ব্যাকআপ নিন")
                        }
                    }
                }
            }

            // Auto Backup Configuration
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
                                Text("ওয়াইফাই বা ডেটা পেলেই ড্রাইভ ব্যাকআপ হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // 1-Click Data Removal & Granular Filter Selection
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
                        Text("১-ক্লিকে ডেটা পরিষ্কার ও ফিল্টারিং", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "দোকানের হিসাব ফ্রেশ করতে ১-ক্লিকে নির্দিষ্ট হিসাব বা সকল ডামি ডেটা ফিল্টার করে পরিষ্কার করতে পারেন।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Selective Filter Removal Button
                        Button(
                            onClick = { showSelectiveDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FilterAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ফিল্টার করে বাছাইকৃত ডেটা মুছুন")
                        }

                        // Complete 1-Click Clear
                        OutlinedButton(
                            onClick = { showClearDummyConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("১-ক্লিকে সব ডেটা সম্পূর্ণ মুছুন")
                        }

                        // Reload Sample Data
                        OutlinedButton(
                            onClick = { showResetConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("২০টি স্যাম্পল মুদি পণ্য ও ডামি হিসাব লোড করুন")
                        }
                    }
                }
            }

            // Backup History
            item {
                Text(
                    text = "পূর্ববর্তী ব্যাকআপ লগ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (backupLogs.isEmpty()) {
                item {
                    Text("এখনো কোনো ব্যাকআপ লগ তৈরি হয়নি", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(backupLogs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(log.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "${log.recordCount} রেকর্ড • ${(log.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "সফল ✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusSuccess
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDummyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDummyConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDanger) },
            title = { Text("সব ডামি ডেটা মুছে ফেলতে চান?") },
            text = { Text("দোকানের সকল স্যাম্পল পণ্য, বিক্রির রেকর্ড, কাস্টমার তালিকা ও বাকি খাতার এন্ট্রি সম্পূর্ণ মুছে যাবে। আপনার দোকান সম্পূর্ণ ফ্রেশ ও খালি হয়ে যাবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllDummyData {
                            showClearDummyConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("হ্যাঁ, সব ডামি ডেটা মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDummyConfirmDialog = false }) { Text("বাতিল") }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("ডেটা রিসেট নিশ্চিতকরণ") },
            text = { Text("আপনি কি নিশ্চিত? এতে বর্তমান সকল পরীক্ষামূলক ডেটা মুছে যাবে এবং প্রাথমিক ২০টি মুদি পণ্য ও ক্যাটাগরি নতুন করে লোড হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("লোড করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Selective Deletion Dialog
    if (showSelectiveDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showSelectiveDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StatusDanger) },
            title = { Text("ফিল্টার করে ডেটা মুছে ফেলুন") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("কোন কোন ডেটা মুছতে চান তা নির্বাচন করুন:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearSales, onCheckedChange = { selClearSales = it })
                        Text("বিক্রয় ইতিহাস ও সকল ইনভয়েস", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearProducts, onCheckedChange = { selClearProducts = it })
                        Text("সকল পণ্য ও স্টক তালিকা", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearCustomers, onCheckedChange = { selClearCustomers = it })
                        Text("কাস্টমার তালিকা ও বাকি খাতা", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearExpenses, onCheckedChange = { selClearExpenses = it })
                        Text("দোকানের খরচের হিসাব", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selClearPurchases, onCheckedChange = { selClearPurchases = it })
                        Text("পাইকারি মালামাল ক্রয়ের হিসাব", fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("সময়কাল ফিল্টার:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selTimeFilter == "all",
                            onClick = { selTimeFilter = "all" },
                            label = { Text("সব সময়", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selTimeFilter == "1m",
                            onClick = { selTimeFilter = "1m" },
                            label = { Text("১ মাস পূর্বের", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selTimeFilter == "3m",
                            onClick = { selTimeFilter = "3m" },
                            label = { Text("৩ মাস পূর্বের", fontSize = 11.sp) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("বাছাইকৃত ডেটা মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectiveDeleteDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Restore Backup Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("ব্যাকআপ ফাইল থেকে রিস্টোর") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("সংরক্ষিত ব্যাকআপ JSON কোডটি নিচে পেস্ট করুন:", fontSize = 13.sp)
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
                    enabled = restoreJsonText.isNotBlank()
                ) {
                    Text("রিস্টোর সম্পন্ন করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("বাতিল") }
            }
        )
    }
}
