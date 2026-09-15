package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.entity.Product
import com.example.data.entity.Purchase
import com.example.data.entity.PurchaseItem
import com.example.data.entity.Supplier
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val purchases by viewModel.purchases.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val products by viewModel.products.collectAsState()

    var showAddPurchaseDialog by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ক্রয় ও সাপ্লায়ার") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "পিছনে যান")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSupplierDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "সাপ্লায়ার যোগ")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPurchaseDialog = true },
                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = null) },
                text = { Text("নতুন ক্রয় চালান") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Suppliers Summary Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("নিবন্ধিত সাপ্লায়ার", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(suppliers.size.toString()) else suppliers.size} জন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("মোট ক্রয় চালান", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(purchases.size.toString()) else purchases.size} টি",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Text(
                text = "ক্রয় চালানের ইতিহাস",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (purchases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("কোনো ক্রয় চালান এন্ট্রি করা হয়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(purchases, key = { it.id }) { pur ->
                        PurchaseItemCard(
                            purchase = pur,
                            config = config,
                            onDelete = { viewModel.deletePurchase(pur.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onDismiss = { showAddSupplierDialog = false },
            onSave = { sup ->
                viewModel.saveSupplier(sup) {
                    showAddSupplierDialog = false
                }
            }
        )
    }

    if (showAddPurchaseDialog) {
        AddPurchaseDialog(
            suppliers = suppliers,
            products = products,
            config = config,
            onDismiss = { showAddPurchaseDialog = false },
            onSave = { purchase, items ->
                viewModel.recordPurchase(purchase, items) {
                    showAddPurchaseDialog = false
                }
            }
        )
    }
}

@Composable
fun PurchaseItemCard(
    purchase: Purchase,
    config: ShopConfig,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(purchase.invoiceNo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(purchase.supplierName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    Formatters.formatDateTime(purchase.purchaseDate, config.useBengaliNumerals),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formatters.formatMoney(purchase.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (purchase.dueAmountPoisha > 0) {
                        Text(
                            "বাকি: ${Formatters.formatMoney(purchase.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusDanger
                        )
                    } else {
                        Text("পরিশোধিত", fontSize = 11.sp, color = StatusSuccess)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Purchase",
                        tint = StatusDanger.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ক্রয় রেকর্ড মুছুন") },
            text = { Text("চালান নং ${purchase.invoiceNo}-এর রেকর্ডটি মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) {
                    Text("মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun AddSupplierDialog(
    onDismiss: () -> Unit,
    onSave: (Supplier) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন সাপ্লায়ার যোগ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("সাপ্লায়ারের নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("প্রতিষ্ঠান / কোম্পানি (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onSave(Supplier(name = name.trim(), phone = phone.trim(), company = company.trim().ifBlank { null }))
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun AddPurchaseDialog(
    suppliers: List<Supplier>,
    products: List<Product>,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onSave: (Purchase, List<PurchaseItem>) -> Unit
) {
    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var qtyText by remember { mutableStateOf("10") }
    var unitPriceText by remember {
        mutableStateOf(if (products.isNotEmpty()) (products.first().purchasePricePoisha / 100.0).toString() else "50")
    }
    var paidAmountText by remember { mutableStateOf("") }

    val totalPoisha = remember(qtyText, unitPriceText) {
        val q = qtyText.toDoubleOrNull() ?: 0.0
        val p = unitPriceText.toDoubleOrNull() ?: 0.0
        ((q * p) * 100).toLong()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন পণ্য ক্রয় এন্ট্রি") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("সাপ্লায়ার নির্বাচন:", fontSize = 12.sp)
                var supExpanded by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { supExpanded = true }
                ) {
                    Text(
                        text = selectedSupplier?.name ?: "সাপ্লায়ার নেই",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text("পণ্য নির্বাচন:", fontSize = 12.sp)
                var prodExpanded by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { prodExpanded = true }
                ) {
                    Text(
                        text = selectedProduct?.nameBn ?: "পণ্য নেই",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("ক্রয় পরিমাণ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unitPriceText,
                        onValueChange = { unitPriceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("ক্রয় দর (৳)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "মোট চালানের মূল্য: ${Formatters.formatMoney(totalPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("পরিশোধিত টাকা (বাকি থাকলে কম লিখুন)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedSupplier != null && selectedProduct != null && totalPoisha > 0) {
                        val paid = ((paidAmountText.toDoubleOrNull() ?: (totalPoisha / 100.0)) * 100).toLong()
                        val due = (totalPoisha - paid).coerceAtLeast(0)

                        val purchase = Purchase(
                            invoiceNo = "PUR-${System.currentTimeMillis() % 100000}",
                            supplierId = selectedSupplier!!.id,
                            supplierName = selectedSupplier!!.name,
                            totalPoisha = totalPoisha,
                            paidAmountPoisha = paid,
                            dueAmountPoisha = due
                        )

                        val item = PurchaseItem(
                            purchaseId = 0,
                            productId = selectedProduct!!.id,
                            productName = selectedProduct!!.nameBn,
                            qty = qtyText.toDoubleOrNull() ?: 1.0,
                            unitPricePoisha = ((unitPriceText.toDoubleOrNull() ?: 0.0) * 100).toLong(),
                            lineTotalPoisha = totalPoisha
                        )

                        onSave(purchase, listOf(item))
                    }
                },
                enabled = totalPoisha > 0 && selectedSupplier != null && selectedProduct != null
            ) {
                Text("চালান সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
