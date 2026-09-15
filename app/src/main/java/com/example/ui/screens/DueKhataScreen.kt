package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.Customer
import com.example.data.entity.CustomerLedger
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.AnimatedAmount
import com.example.ui.components.DokanConfirmDialog
import com.example.ui.components.DokanSkeletonList
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanTextField
import com.example.ui.components.EmptyState
import com.example.ui.components.SectionHeader
import com.example.ui.theme.Brand900
import com.example.ui.theme.Gold500
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.amountTextStyle
import com.example.ui.theme.dokanColors
import com.example.ui.theme.softShadow
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper
import kotlin.math.abs

// Deterministic 6-colour avatar palette
private val CustomerAvatarPalette = listOf(
    Color(0xFF0E9F6E), // Emerald
    Color(0xFF0284C7), // Sky Blue
    Color(0xFF8B5CF6), // Purple
    Color(0xFFD97706), // Amber
    Color(0xFFEC4899), // Pink
    Color(0xFF14B8A6)  // Teal
)

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
    var customerForPaymentDialog by remember { mutableStateOf<Customer?>(null) }
    var customerBalanceForPayment by remember { mutableStateOf(0L) }
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var isFirstLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        isFirstLoading = false
    }

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
                text = { Text("নতুন কাস্টমার", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier
                    .padding(bottom = 80.dp)
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
            // Header: Total Due Ledger Banner & Search Pill
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    // 1) TOTAL DUE HEADER with Brand900 & subtle repeating diagonal canvas lines
                    TotalDueLedgerBanner(
                        totalDue = totalDue,
                        customersCount = customers.size,
                        config = config
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 2) SEARCH (52dp pill on surfaceAlt)
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.pill))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("কাস্টমারের নাম বা মোবাইল দিয়ে খুঁজুন...", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "পরিষ্কার", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Customer List
            if (isFirstLoading && customers.isEmpty()) {
                DokanSkeletonList(
                    itemCount = 6,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else if (filteredCustomers.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.MenuBook,
                    title = if (searchQuery.isNotBlank()) "কোনো কাস্টমার মেলেনি" else "এখনো কোনো কাস্টমার নেই",
                    message = "নতুন কাস্টমার যোগ করতে নিচের বাটনে চাপ দিন।"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.md,
                        bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        CustomerDueCardItem(
                            customer = customer,
                            viewModel = viewModel,
                            config = config,
                            onCollectDue = { bal ->
                                customerBalanceForPayment = bal
                                customerForPaymentDialog = customer
                            },
                            onSendReminder = { bal ->
                                val msg = "শ্রদ্ধেয় ${customer.name},\n${config.shopName}-এ আপনার বকেয়া বাকি রয়েছে ${Formatters.formatMoney(bal, config.useBengaliNumerals, config.currencySymbol)}। অনুগ্রহ করে সুবিধাজনক সময়ে পরিশোধের অনুরোধ রইল।\n- ধন্যবাদ, ${config.shopName}"
                                val uri = Uri.parse("https://api.whatsapp.com/send?phone=+88${customer.phone}&text=${Uri.encode(msg)}")
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: Exception) {
                                    viewModel.showToast("WhatsApp অ্যাপ পাওয়া যায়নি")
                                }
                            },
                            onOpenLedger = {
                                selectedCustomerForDetail = customer
                            },
                            onLongPressDelete = {
                                customerToDelete = customer
                            }
                        )
                    }
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

    // Quick Payment Collection Dialog from Customer Card
    customerForPaymentDialog?.let { customer ->
        DueCollectionDialog(
            customerName = customer.name,
            currentDue = customerBalanceForPayment,
            config = config,
            onDismiss = { customerForPaymentDialog = null },
            onConfirm = { amountPoisha, note ->
                viewModel.collectDuePayment(customer.id, amountPoisha, note) {
                    customerForPaymentDialog = null
                }
            }
        )
    }

    // Delete Customer Confirmation Dialog
    customerToDelete?.let { cust ->
        DokanConfirmDialog(
            title = "কাস্টমার মুছবেন?",
            message = "${cust.name}-এর সকল তথ্য ও বাকি খাতার রেকর্ড মুছে ফেলা হবে।",
            confirmLabel = "মুছুন",
            isDestructive = true,
            onConfirm = {
                val id = cust.id
                customerToDelete = null
                viewModel.deleteCustomer(id) {}
            },
            onDismiss = { customerToDelete = null }
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

// ==============================================================================
// 1. TOTAL DUE LEDGER BANNER (Brand900 + Canvas Diagonal Texture + Gold500 Figure)
// ==============================================================================
@Composable
private fun TotalDueLedgerBanner(
    totalDue: Long,
    customersCount: Int,
    config: ShopConfig
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(2, RoundedCornerShape(Radius.lg)),
        shape = RoundedCornerShape(Radius.lg),
        color = Brand900
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle repeating diagonal line pattern drawn with Canvas at 4% white
            Canvas(modifier = Modifier.matchParentSize()) {
                val step = 16.dp.toPx()
                val stroke = 1.dp.toPx()
                val lineColor = Color.White.copy(alpha = 0.04f)
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = stroke
                    )
                    x += step
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                // Label in labelMedium at 70% white
                Text(
                    text = "দোকানের মোট বাকি পাওনা",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.70f)
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Outstanding in amountTextStyle(34.sp) in Gold500
                AnimatedAmount(
                    value = totalDue,
                    style = amountTextStyle(34.sp),
                    color = Gold500,
                    useBengaliNumerals = config.useBengaliNumerals,
                    currencySymbol = config.currencySymbol
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Sub-row showing how many customers owe money
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Gold500)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(customersCount.toString()) else customersCount} জন গ্রাহকের কাছে বাকি পাওনা রয়েছে",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 2. CUSTOMER DUE ITEM CARD (Deterministic 6-Colour Avatar + Inline Expand)
// ==============================================================================
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CustomerDueCardItem(
    customer: Customer,
    viewModel: PaponViewModel,
    config: ShopConfig,
    onCollectDue: (Long) -> Unit,
    onSendReminder: (Long) -> Unit,
    onOpenLedger: () -> Unit,
    onLongPressDelete: () -> Unit
) {
    val balance by viewModel.getCustomerBalanceFlow(customer.id).collectAsState(initial = 0L)
    var isExpanded by remember { mutableStateOf(false) }

    // Avatar colour from 6-color palette
    val avatarColor = remember(customer.id) {
        val idx = abs((customer.id % CustomerAvatarPalette.size).toInt())
        CustomerAvatarPalette[idx]
    }

    val creditLimit = customer.creditLimitPoisha.coerceAtLeast(1L)
    val limitFraction = (balance.toFloat() / creditLimit.toFloat()).coerceIn(0f, 1f)
    val isOverLimit = balance > customer.creditLimitPoisha

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            .softShadow(1, RoundedCornerShape(Radius.md))
            .clip(RoundedCornerShape(Radius.md))
            .combinedClickable(
                onClick = { isExpanded = !isExpanded },
                onLongClick = onLongPressDelete
            )
            .testTag("customer_card_${customer.id}"),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: 44dp circular avatar with Bengali first letter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.md))

                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Due Amount in amountTextStyle(18.sp) in danger colour
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(18.sp),
                        color = if (balance > 0) MaterialTheme.dokanColors.danger else MaterialTheme.dokanColors.success
                    )

                    // Thin progress bar showing due against creditLimitPoisha
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (isOverLimit) 1f else limitFraction },
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.pill)),
                        color = if (isOverLimit) MaterialTheme.dokanColors.danger else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Inline Expansion (3 compact action buttons: বাকি আদায়, তাগাদা, লেনদেন খতিয়ান)
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Action 1: বাকি আদায়
                        Button(
                            onClick = { onCollectDue(balance) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("বাকি আদায়", style = MaterialTheme.typography.labelMedium)
                        }

                        // Action 2: তাগাদা (WhatsApp)
                        Button(
                            onClick = { onSendReminder(balance) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Text("তাগাদা", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }

                        // Action 3: লেনদেন খতিয়ান
                        OutlinedButton(
                            onClick = onOpenLedger,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(38.dp),
                            shape = RoundedCornerShape(Radius.sm)
                        ) {
                            Text("খতিয়ান", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 3. CUSTOMER DETAIL & TIMELINE LEDGER BOTTOM SHEET
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDetailBottomSheet(
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
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun sendWhatsAppReminder() {
        val msg = "শ্রদ্ধেয় ${customer.name},\n${config.shopName}-এ আপনার বকেয়া বাকি রয়েছে ${Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol)}। অনুগ্রহ করে সুবিধাজনক সময়ে পরিশোধের অনুরোধ রইল।\n- ধন্যবাদ, ${config.shopName}"
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=+88${customer.phone}&text=${Uri.encode(msg)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
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
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.dokanColors.dangerContainer.copy(alpha = 0.2f),
                    modifier = Modifier.padding(Spacing.xs)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text("মোট বাকি", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.dokanColors.danger)
                        Text(
                            text = Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(18.sp),
                            color = MaterialTheme.dokanColors.danger
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Button(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("বাকি আদায়", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = { sendWhatsAppReminder() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("তাগাদা", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = {
                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                        context.startActivity(callIntent)
                    },
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.weight(0.8f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "কল করুন", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Digital Due Statement Slip Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.sm),
                color = MaterialTheme.dokanColors.surfaceAlt
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("বাকি খাতার স্লিপ ছবি (PNG)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                val bmp = InvoiceImageHelper.generateDueStatementBitmap(context, config, customer, ledgerItems, balance)
                                previewBitmap = bmp
                                previewCaption = InvoiceImageHelper.buildDueStatementCaption(config, customer, balance)
                                showPreviewDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিভিউ", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Button(
                            onClick = { shareDueStatementImage() },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(Radius.xs),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp স্লিপ", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { saveDueStatementImage() },
                            shape = RoundedCornerShape(Radius.xs),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ছবি সেভ", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "লেনদেন খতিয়ান (লেজার)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { showDeleteConfirm = true }
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.dokanColors.danger, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("কাস্টমার মুছুন", color = MaterialTheme.dokanColors.danger, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 5) LEDGER TIMELINE VIEW
            if (ledgerItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.MenuBook,
                    title = "কোনো লেনদেন রেকর্ড নেই",
                    message = "এই কাস্টমারের কোনো বাকি বা জমার রেকর্ড নেই।"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(ledgerItems) { item ->
                        TimelineLedgerItemRow(item = item, config = config)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DokanConfirmDialog(
            title = "কাস্টমার মুছবেন?",
            message = "${customer.name}-এর সকল তথ্য ও বাকি খাতার রেকর্ড মুছে ফেলা হবে।",
            confirmLabel = "মুছুন",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteCustomer(customer.id) {
                    onDismiss()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
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

    collectedReceiptInfo?.let { info ->
        PaymentSuccessReceiptDialog(
            customer = customer,
            info = info,
            config = config,
            onDismiss = { collectedReceiptInfo = null }
        )
    }

    // 6) Digital Slip Preview Sheet with paper frame
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

// ==============================================================================
// 4. TIMELINE LEDGER ITEM ROW (Vertical hairline + 10dp dot + running balance)
// ==============================================================================
@Composable
private fun TimelineLedgerItemRow(item: CustomerLedger, config: ShopConfig) {
    val isPayment = item.creditPoisha > 0
    val dotColor = if (isPayment) MaterialTheme.dokanColors.success else MaterialTheme.dokanColors.danger

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical timeline bar with 10dp dot
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        // Note and Date on the left
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isPayment) "জমা / আদায়" else "বাকি ক্রয়",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
            Text(
                text = item.note ?: Formatters.formatDateTime(item.entryDate, config.useBengaliNumerals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Amount right-aligned
        Text(
            text = if (isPayment)
                "+${Formatters.formatMoney(item.creditPoisha, config.useBengaliNumerals, config.currencySymbol)}"
            else
                "-${Formatters.formatMoney(item.debitPoisha, config.useBengaliNumerals, config.currencySymbol)}",
            style = amountTextStyle(15.sp),
            color = dotColor
        )
    }
}

// ==============================================================================
// 5. DIGITAL STATEMENT PREVIEW SHEET (Paper-like frame + Pinned bottom buttons)
// ==============================================================================
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
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Paper-like frame (2dp border, Radius.sm, soft shadow)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .softShadow(2, RoundedCornerShape(Radius.sm))
                        .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "স্লিপ ছবি",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Pinned Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Button(
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(Radius.sm)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.sm)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ছবি সেভ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 6. DUE COLLECTION & ADD CUSTOMER DIALOGS
// ==============================================================================
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
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("বাকি আদায়: $customerName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "বর্তমান বাকি: ${Formatters.formatMoney(currentDue, config.useBengaliNumerals, config.currencySymbol)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.dokanColors.danger
                )

                DokanTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "আদায়কৃত টাকা (৳)"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(500L, 1000L, currentDue / 100).distinct().forEach { amt ->
                        AssistChip(
                            onClick = { amountText = amt.toString() },
                            label = { Text("৳$amt", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(Radius.pill)
                        )
                    }
                }

                DokanTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "বিবরণ / নোট"
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
                shape = RoundedCornerShape(Radius.sm),
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("আদায় নিশ্চিত করুন", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

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
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("নতুন কাস্টমার যোগ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DokanTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "কাস্টমারের নাম *"
                )

                DokanTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "মোবাইল নম্বর *"
                )

                DokanTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "ঠিকানা (ঐচ্ছিক)"
                )

                DokanTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it.filter { c -> c.isDigit() } },
                    label = "সর্বোচ্চ বাকি লিমিট (৳)"
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
                shape = RoundedCornerShape(Radius.sm),
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("সংরক্ষণ করুন", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

data class CollectedReceiptInfo(
    val amountPoisha: Long,
    val previousDuePoisha: Long,
    val remainingDuePoisha: Long,
    val paymentMethod: String = "নগদ",
    val note: String? = null
)

@Composable
fun PaymentSuccessReceiptDialog(
    customer: Customer,
    info: CollectedReceiptInfo,
    config: ShopConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
        shape = RoundedCornerShape(Radius.lg),
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.dokanColors.success,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("টাকা জমা সফল হয়েছে!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("সম্মানিত ক্রেতা: ${customer.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "আদায়কৃত টাকা: ${Formatters.formatMoney(info.amountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    style = amountTextStyle(16.sp),
                    color = MaterialTheme.dokanColors.success
                )
                Text(
                    text = "অবশিষ্ট বকেয়া: ${Formatters.formatMoney(info.remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (info.remainingDuePoisha > 0) MaterialTheme.dokanColors.danger else MaterialTheme.dokanColors.success
                )

                Spacer(modifier = Modifier.height(Spacing.xs))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = "ক্রেতাকে ডিজিটাল টাকা জমার ভাউচার ছবি পাঠাবেন?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                        shape = RoundedCornerShape(Radius.xs),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val bmp = getOrGenBitmap()
                            InvoiceImageHelper.saveBitmapToGallery(context, bmp, "receipt_${customer.name}")
                        },
                        shape = RoundedCornerShape(Radius.xs),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ছবি সেভ", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("সম্পন্ন", style = MaterialTheme.typography.labelLarge) }
        }
    )
}
