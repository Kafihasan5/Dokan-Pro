package com.example.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.Category
import com.example.ui.PaponViewModel

@Composable
fun CategoryUnitManagerDialog(
    viewModel: PaponViewModel,
    initialTab: Int = 0, // 0 = Categories, 1 = Units
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()

    // Dialog states for Category
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    // Dialog states for Unit
    var showUnitDialog by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf<String?>(null) }
    var unitToDelete by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ক্যাটাগরি ও পরিমাপের একক",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "যোগ, সম্পাদনা ও মুছে ফেলার নিয়ন্ত্রণ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("ক্যাটাগরি (${categories.size})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("পরিমাপের একক (${units.size})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content according to selected tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // Category Management
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "সকল ক্যাটাগরি তালিকা",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        editingCategory = null
                                        showCategoryDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("নতুন ক্যাটাগরি", fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categories, key = { it.id }) { cat ->
                                    val isDefaultAll = cat.id == 1L
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        cat.nameBn,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    if (isDefaultAll) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                "সিস্টেম",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (cat.nameEn.isNotBlank() && !isDefaultAll) {
                                                    Text(
                                                        cat.nameEn,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (!isDefaultAll) {
                                                IconButton(
                                                    onClick = {
                                                        editingCategory = cat
                                                        showCategoryDialog = true
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "সম্পাদনা",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        categoryToDelete = cat
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "মুছুন",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Units Management
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "পরিমাপের একক তালিকা",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        editingUnit = null
                                        showUnitDialog = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("নতুন একক", fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(units, key = { it }) { unitName ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.secondaryContainer,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Scale,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Text(
                                                unitName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(
                                                onClick = {
                                                    editingUnit = unitName
                                                    showUnitDialog = true
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "সম্পাদনা",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    unitToDelete = unitName
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "মুছুন",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TextButton(
                                        onClick = {
                                            viewModel.resetDefaultUnits()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ডিফল্ট এককসমূহে পুনরায় সেট করুন", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("সম্পন্ন / বন্ধ করুন", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- DIALOG: Add / Edit Category ---
    if (showCategoryDialog) {
        var nameBn by remember { mutableStateOf(editingCategory?.nameBn ?: "") }
        var nameEn by remember { mutableStateOf(editingCategory?.nameEn ?: "") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = {
                Text(
                    if (editingCategory == null) "নতুন ক্যাটাগরি যোগ করুন" else "ক্যাটাগরি সম্পাদনা",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = {
                            nameBn = it
                            isError = false
                        },
                        label = { Text("বাংলা নাম *") },
                        placeholder = { Text("যেমন: কোমল পানীয়, বেকারি") },
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("বাংলা নাম আবশ্যক!", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text("ইংরেজি নাম (ঐচ্ছিক)") },
                        placeholder = { Text("যেমন: Soft Drinks, Bakery") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameBn.isBlank()) {
                            isError = true
                        } else {
                            if (editingCategory == null) {
                                viewModel.addCategory(nameBn.trim(), nameEn.trim())
                            } else {
                                viewModel.updateCategory(
                                    editingCategory!!.copy(
                                        nameBn = nameBn.trim(),
                                        nameEn = nameEn.trim()
                                    )
                                )
                            }
                            showCategoryDialog = false
                        }
                    }
                ) {
                    Text(if (editingCategory == null) "যোগ করুন" else "সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // --- DIALOG: Delete Category Confirmation ---
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("ক্যাটাগরি মুছবেন?") },
            text = {
                Text("আপনি কি নিশ্চিতভাবে \"${cat.nameBn}\" ক্যাটাগরি মুছে ফেলতে চান?\n\nএই ক্যাটাগরির অন্তর্ভুক্ত পণ্যগুলো স্বয়ংক্রিয়ভাবে অন্য ক্যাটাগরিতে স্থানান্তরিত হবে।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.id)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // --- DIALOG: Add / Edit Unit ---
    if (showUnitDialog) {
        var unitNameInput by remember { mutableStateOf(editingUnit ?: "") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUnitDialog = false },
            title = {
                Text(
                    if (editingUnit == null) "নতুন একক যোগ করুন" else "একক সম্পাদনা",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitNameInput,
                        onValueChange = {
                            unitNameInput = it
                            isError = false
                        },
                        label = { Text("পরিমাপের একক *") },
                        placeholder = { Text("যেমন: বোতল, ড্রাম, মিটার, ক্যান") },
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("এককের নাম প্রদান করুন!", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editingUnit != null) {
                        Text(
                            "দ্রষ্টব্য: এই একক পরিবর্তন করলে বিদ্যমান সকল পণ্যে স্বয়ংক্রিয়ভাবে নতুন একক কার্যকর হবে।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = unitNameInput.trim()
                        if (clean.isBlank()) {
                            isError = true
                        } else {
                            if (editingUnit == null) {
                                viewModel.addUnit(clean)
                            } else {
                                viewModel.updateUnit(editingUnit!!, clean)
                            }
                            showUnitDialog = false
                        }
                    }
                ) {
                    Text(if (editingUnit == null) "যোগ করুন" else "সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnitDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // --- DIALOG: Delete Unit Confirmation ---
    unitToDelete?.let { unitName ->
        AlertDialog(
            onDismissRequest = { unitToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("একক মুছবেন?") },
            text = {
                Text("আপনি কি নিশ্চিতভাবে \"${unitName}\" পরিমাপের এককটি তালিকা থেকে মুছে ফেলতে চান?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUnit(unitName)
                        unitToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { unitToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
