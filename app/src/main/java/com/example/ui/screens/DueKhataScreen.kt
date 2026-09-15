package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.Customer
import com.example.data.entity.CustomerLedger
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueKhataScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val totalDue by viewModel.totalDue.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomerForDetail by remember { mutableStateOf<Customer?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    val filteredCustomers = remember(customers, searchQuery) {
        customers.filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("নতুন কাস্টমার") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("add_customer_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header & Total Due Banner
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "বাকি খাতা (কাস্টমার লেজার)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusDanger.copy(alpha = 0.09f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "দোকানের মোট বাকি পাওনা",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatMoney(totalDue, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusDanger
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StatusDanger.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = StatusDanger)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("কাস্টমারের নাম বা মোবাইল দিয়ে খুঁজুন...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Customer List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCustomers, key = { it.id }) { customer ->
                    CustomerDueItemCard(
                        customer = customer,
                        viewModel = viewModel,
                        config = config,
                        onClick = { selectedCustomerForDetail = customer }
                    )
                }
            }
        }
    }

    // Customer Detail Bottom Sheet
    selectedCustomerForDetail?.let { customer ->
        CustomerDetailBottomSheet(
            customer = customer,
            viewModel = viewModel,
            config = config,
            onDismiss = { selectedCustomerForDetail = null }
        )
    }

    // Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { newCust ->
                viewModel.saveCustomer(newCust) {
                    showAddCustomerDialog = false
                }
            }
        )
    }
}

@Composable
fun CustomerDueItemCard(
    customer: Customer,
    viewModel: PaponViewModel,
    config: ShopConfig,
    onClick: () -> Unit
) {
    val balance by viewModel.getCustomerBalanceFlow(customer.id).collectAsState(initial = 0L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("customer_card_${customer.id}"),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = customer.phone,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!customer.address.isNullOrBlank()) {
                        Text(
                            text = customer.address,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "বাকি:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (balance > 0) StatusDanger else StatusSuccess
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailBottomSheet(
    customer: Customer,
    viewModel: PaponViewModel,
    config: ShopConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val balance by viewModel.getCustomerBalanceFlow(customer.id).collectAsState(initial = 0L)
    val ledgerItems by viewModel.getCustomerLedgerFlow(customer.id).collectAsState(initial = emptyList())

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewCaption by remember { mutableStateOf("") }
    var collectedReceiptInfo by remember { mutableStateOf<CollectedReceiptInfo?>(null) }

    fun sendWhatsAppReminder() {
        val msg = "শ্রদ্ধেয় ${customer.name},\n${config.shopName}-এ আপনার বকেয়া বাকি রয়েছে ${Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol)}। অনুগ্রহ করে সুবিধাজনক সময়ে পরিশোধের অনুরোধ রইল।\n- ধন্যবাদ, ${config.shopName}"
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=+88${customer.phone}&text=${Uri.encode(msg)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            viewModel.showToast("WhatsApp অ্যাপ পাওয়া যায়নি")
        }
    }

    fun shareDueStatementImage() {
        val bmp = InvoiceImageHelper.generateDueStatementBitmap(context, config, customer, ledgerItems, balance)
        val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "statement_${customer.name}")
        val caption = InvoiceImageHelper.buildDueStatementCaption(config, customer, balance)
        InvoiceImageHelper.shareToWhatsApp(context, uri, customer.phone, caption)
    }

    fun saveDueStatementImage() {
        val bmp = InvoiceImageHelper.generateDueStatementBitmap(context, config, customer, ledgerItems, balance)
        InvoiceImageHelper.saveBitmapToGallery(context, bmp, "statement_${customer.name}")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp)
        ) {
            // Customer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "মোবাইল: ${customer.phone}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusDanger.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("মোট বাকি", fontSize = 11.sp, color = StatusDanger)
                        Text(
                            text = Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusDanger
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: বাকি আদায়, WhatsApp তাগাদা, সরাসরি কল
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("বাকি আদায়")
                }

                Button(
                    onClick = { sendWhatsAppReminder() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("তাগাদা", color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                        context.startActivity(callIntent)
                    },
                    modifier = Modifier.weight(0.8f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Digital Due Statement Image Slip Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "বাকি খাতার স্লিপ ছবি (PNG)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = {
                                val bmp = InvoiceImageHelper.generateDueStatementBitmap(context, config, customer, ledgerItems, balance)
                                previewBitmap = bmp
                                previewCaption = InvoiceImageHelper.buildDueStatementCaption(config, customer, balance)
                                showPreviewDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিভিউ", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { shareDueStatementImage() },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp স্লিপ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { saveDueStatementImage() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ছবি সেভ", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "লেনদেন খতিয়ান (লেজার)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                var showDeleteConfirm by remember { mutableStateOf(false) }

                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("কাস্টমার মুছুন", fontSize = 12.sp)
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("কাস্টমার মুছবেন?") },
                        text = { Text("${customer.name}-এর সকল তথ্য ও বাকি খাতার রেকর্ড মুছে ফেলা হবে।") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    viewModel.deleteCustomer(customer.id) {
                                        onDismiss()
                                    }
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

            Spacer(modifier = Modifier.height(8.dp))

            if (ledgerItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("কোনো লেনদেন রেকর্ড নেই", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ledgerItems) { item ->
                        LedgerItemRow(item = item, config = config)
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        DueCollectionDialog(
            customerName = customer.name,
            currentDue = balance,
            config = config,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amountPoisha, note ->
                val prevDue = balance
                val remDue = (balance - amountPoisha).coerceAtLeast(0L)
                viewModel.collectDuePayment(customer.id, amountPoisha, note) {
                    showPaymentDialog = false
                    collectedReceiptInfo = CollectedReceiptInfo(
                        amountPoisha = amountPoisha,
                        previousDuePoisha = prevDue,
                        remainingDuePoisha = remDue,
                        paymentMethod = "নগদ",
                        note = note
                    )
                }
            }
        )
    }

    // Payment Collection Success Voucher Dialog
    collectedReceiptInfo?.let { info ->
        PaymentSuccessReceiptDialog(
            customer = customer,
            info = info,
            config = config,
            onDismiss = { collectedReceiptInfo = null }
        )
    }

    // Due Statement Slip Preview Dialog
    if (showPreviewDialog && previewBitmap != null) {
        StatementPreviewDialog(
            bitmap = previewBitmap!!,
            title = "বাকি খাতার স্লিপ প্রিভিউ",
            onDismiss = { showPreviewDialog = false },
            onWhatsApp = {
                val uri = InvoiceImageHelper.saveBitmapToCache(context, previewBitmap!!, "statement_${customer.name}")
                InvoiceImageHelper.shareToWhatsApp(context, uri, customer.phone, previewCaption)
                showPreviewDialog = false
            },
            onSave = {
                InvoiceImageHelper.saveBitmapToGallery(context, previewBitmap!!, "statement_${customer.name}")
            }
        )
    }
}

@Composable
fun LedgerItemRow(item: CustomerLedger, config: ShopConfig) {
    val isPayment = item.creditPoisha > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isPayment) "জমা / আদায়" else "বাকি ক্রয়",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isPayment) StatusSuccess else StatusDanger
                )
                Text(
                    text = item.note ?: Formatters.formatDateTime(item.entryDate, config.useBengaliNumerals),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (isPayment)
                    "+${Formatters.formatMoney(item.creditPoisha, config.useBengaliNumerals, config.currencySymbol)}"
                else
                    "-${Formatters.formatMoney(item.debitPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isPayment) StatusSuccess else StatusDanger
            )
        }
    }
}

// Due Collection Dialog
@Composable
fun DueCollectionDialog(
    customerName: String,
    currentDue: Long,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onConfirm: (Long, String?) -> Unit
) {
    var amountText by remember { mutableStateOf(if (currentDue > 0) (currentDue / 100).toString() else "") }
    var note by remember { mutableStateOf("নগদে বাকি আদায়") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("বাকি আদায়: $customerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "বর্তমান বাকি: ${Formatters.formatMoney(currentDue, config.useBengaliNumerals, config.currencySymbol)}",
                    fontWeight = FontWeight.SemiBold,
                    color = StatusDanger
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("আদায়কৃত টাকা (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick amount chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(500L, 1000L, currentDue / 100).distinct().forEach { amt ->
                        AssistChip(
                            onClick = { amountText = amt.toString() },
                            label = { Text("৳$amt", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("বিবরণ / নোট") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amountText.toDoubleOrNull() ?: 0.0
                    if (a > 0) {
                        onConfirm((a * 100).toLong(), note.ifBlank { null })
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("আদায় নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// Add Customer Dialog
@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("5000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন কাস্টমার যোগ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("কাস্টমারের নাম *") },
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
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ঠিকানা (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it.filter { c -> c.isDigit() } },
                    label = { Text("সর্বোচ্চ বাকি লিমিট (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val limitPoisha = ((creditLimitText.toLongOrNull() ?: 5000L) * 100)
                        onSave(
                            Customer(
                                name = name.trim(),
                                phone = phone.trim(),
                                address = address.trim().ifBlank { null },
                                creditLimitPoisha = limitPoisha
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// Data class for collected payment receipt details
data class CollectedReceiptInfo(
    val amountPoisha: Long,
    val previousDuePoisha: Long,
    val remainingDuePoisha: Long,
    val paymentMethod: String = "নগদ",
    val note: String? = null
)

// Payment Success Voucher Dialog
@Composable
fun PaymentSuccessReceiptDialog(
    customer: Customer,
    info: CollectedReceiptInfo,
    config: ShopConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showPreview by remember { mutableStateOf(false) }
    var receiptBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun getOrGenBitmap(): Bitmap {
        if (receiptBitmap != null && !receiptBitmap!!.isRecycled) return receiptBitmap!!
        val bmp = InvoiceImageHelper.generatePaymentReceiptBitmap(
            context = context,
            config = config,
            customer = customer,
            amountPoisha = info.amountPoisha,
            previousDuePoisha = info.previousDuePoisha,
            remainingDuePoisha = info.remainingDuePoisha,
            paymentMethod = info.paymentMethod,
            note = info.note
        )
        receiptBitmap = bmp
        return bmp
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("টাকা জমা সফল হয়েছে!", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("সম্মানিত ক্রেতা: ${customer.name}", fontWeight = FontWeight.SemiBold)
                Text(
                    "আদায়কৃত টাকা: ${Formatters.formatMoney(info.amountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    fontWeight = FontWeight.Bold,
                    color = StatusSuccess,
                    fontSize = 16.sp
                )
                Text(
                    "অবশিষ্ট বকেয়া: ${Formatters.formatMoney(info.remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    color = if (info.remainingDuePoisha > 0) StatusDanger else StatusSuccess
                )

                Spacer(modifier = Modifier.height(4.dp))
                Divider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "ক্রেতাকে ডিজিটাল টাকা জমার ভাউচার ছবি পাঠাবেন?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = getOrGenBitmap()
                            val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "receipt_${customer.name}")
                            val caption = InvoiceImageHelper.buildPaymentReceiptCaption(
                                config = config,
                                customer = customer,
                                amountPoisha = info.amountPoisha,
                                previousDuePoisha = info.previousDuePoisha,
                                remainingDuePoisha = info.remainingDuePoisha
                            )
                            InvoiceImageHelper.shareToWhatsApp(context, uri, customer.phone, caption)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val bmp = getOrGenBitmap()
                            InvoiceImageHelper.saveBitmapToGallery(context, bmp, "receipt_${customer.name}")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ছবি সেভ", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("সম্পন্ন")
            }
        }
    )
}

// Statement Preview Dialog
@Composable
fun StatementPreviewDialog(
    bitmap: Bitmap,
    title: String = "ছবি প্রিভিউ",
    onDismiss: () -> Unit,
    onWhatsApp: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "স্লিপ ছবি",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("হোয়াটসঅ্যাপ", color = Color.White)
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সেভ করুন")
                    }
                }
            }
        }
    }
}
