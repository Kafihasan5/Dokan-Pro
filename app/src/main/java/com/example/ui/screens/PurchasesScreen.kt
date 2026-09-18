package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Product
import com.example.data.entity.Purchase
import com.example.data.entity.PurchaseItem
import com.example.data.entity.Supplier
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.Formatters

private val SupplierAvatarPalette = listOf(
    Color(0xFF0E9F6E), // Brand green
    Color(0xFF2563EB), // Blue
    Color(0xFFD97706), // Amber
    Color(0xFF7C3AED), // Purple
    Color(0xFFDB2777), // Pink
    Color(0xFF0891B2)  // Cyan
)

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

    var showAddPurchaseSheet by remember { mutableStateOf(false) }
    var showAddSupplierSheet by remember { mutableStateOf(false) }
    var purchaseToDelete by remember { mutableStateOf<Purchase?>(null) }
    var purchaseToPayDue by remember { mutableStateOf<Purchase?>(null) }

    DokanScreenScaffold(
        title = "ক্রয় ও সাপ্লায়ার",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAddSupplierSheet = true }) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "সাপ্লায়ার যোগ",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = { showAddPurchaseSheet = true },
                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = null) },
                text = { Text("নতুন ক্রয় চালান", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    ) {
        // 1) Single surfaceAlt summary strip at the top
        Surface(
            color = MaterialTheme.dokanColors.surfaceAlt,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "নিবন্ধিত সাপ্লায়ার",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(suppliers.size.toString()) else suppliers.size} জন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "মোট ক্রয় চালান",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(purchases.size.toString()) else purchases.size} টি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.dokanColors.border)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // History Section Header
        SectionHeader(
            title = "ক্রয় চালানের ইতিহাস",
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
        )

        // Purchases List or EmptyState
        if (purchases.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.AddShoppingCart,
                    title = "কোনো ক্রয় চালান এন্ট্রি করা হয়নি",
                    message = "নতুন ক্রয় চালান তৈরি করতে নিচের বাটনে চাপ দিন।",
                    actionLabel = "নতুন ক্রয় চালান",
                    onAction = { showAddPurchaseSheet = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.xs,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(purchases, key = { it.id }) { purchase ->
                    PurchaseCard(
                        purchase = purchase,
                        config = config,
                        onPayDueClick = { purchaseToPayDue = purchase },
                        onDeleteClick = { purchaseToDelete = purchase }
                    )
                }
            }
        }
    }

    // Pay Purchase Due Dialog
    purchaseToPayDue?.let { purchase ->
        PayPurchaseDueDialog(
            purchase = purchase,
            config = config,
            onDismiss = { purchaseToPayDue = null },
            onConfirmPay = { amountPoisha, note ->
                viewModel.payPurchaseDue(purchase.id, amountPoisha, note) {
                    purchaseToPayDue = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    purchaseToDelete?.let { purchase ->
        DokanConfirmDialog(
            title = "ক্রয় রেকর্ড মুছুন",
            message = "চালান নং ${purchase.invoiceNo}-এর রেকর্ডটি মুছে ফেলতে চান?",
            confirmLabel = "মুছুন",
            onConfirm = {
                viewModel.deletePurchase(purchase.id)
                purchaseToDelete = null
            },
            onDismiss = { purchaseToDelete = null },
            isDestructive = true
        )
    }

    // Modal Bottom Sheet: Add Purchase
    if (showAddPurchaseSheet) {
        AddPurchaseBottomSheet(
            suppliers = suppliers,
            products = products,
            config = config,
            onDismiss = { showAddPurchaseSheet = false },
            onQuickCreateSupplier = { newSup, onDone ->
                viewModel.saveSupplierAndReturn(newSup, onDone)
            },
            onQuickCreateProduct = { newProd, onDone ->
                viewModel.saveProductAndReturn(newProd, onDone)
            },
            onSave = { purchase, items ->
                viewModel.recordPurchase(purchase, items) {
                    showAddPurchaseSheet = false
                }
            }
        )
    }

    // Modal Bottom Sheet: Add Supplier
    if (showAddSupplierSheet) {
        AddSupplierBottomSheet(
            onDismiss = { showAddSupplierSheet = false },
            onSave = { sup ->
                viewModel.saveSupplier(sup) {
                    showAddSupplierSheet = false
                }
            }
        )
    }
}

// ==============================================================================
// PURCHASE ROW CARD
// ==============================================================================
@Composable
private fun PurchaseCard(
    purchase: Purchase,
    config: ShopConfig,
    onPayDueClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val avatarColor = remember(purchase.supplierId, purchase.supplierName) {
        val hash = if (purchase.supplierId > 0) purchase.supplierId.hashCode() else purchase.supplierName.hashCode()
        SupplierAvatarPalette[kotlin.math.abs(hash) % SupplierAvatarPalette.size]
    }
    val firstLetter = purchase.supplierName.trim().firstOrNull()?.toString() ?: "স"

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.md))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Supplier Avatar Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstLetter,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = purchase.supplierName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${purchase.invoiceNo} • ${Formatters.formatDateTime(purchase.purchaseDate, config.useBengaliNumerals)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // Amount & Status Chip
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatMoney(purchase.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                    style = amountTextStyle(17.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                if (purchase.dueAmountPoisha > 0) {
                    Surface(
                        onClick = onPayDueClick,
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.warningContainer,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.warning.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "বকেয়া পরিশোধ",
                                tint = MaterialTheme.dokanColors.warning,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "বাকি ${Formatters.formatMoney(purchase.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)} • পরিশোধ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.warning
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.successContainer
                    ) {
                        Text(
                            text = "পরিশোধিত",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.dokanColors.success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.xs))

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "ক্রয় রেকর্ড মুছুন",
                    tint = MaterialTheme.dokanColors.danger.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==============================================================================
// PURCHASE DRAFT ITEM DATA CLASS & ROW COMPONENT
// ==============================================================================
private data class PurchaseDraftItem(
    val product: Product,
    val qty: Double = 1.0,
    val unitPricePoisha: Long = product.purchasePricePoisha
) {
    val lineTotalPoisha: Long
        get() = (qty * unitPricePoisha).toLong()
}

@Composable
private fun PurchaseDraftItemRow(
    index: Int,
    item: PurchaseDraftItem,
    config: ShopConfig,
    onQtyChange: (Double) -> Unit,
    onPriceChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    var qtyString by remember(item.product.id) {
        mutableStateOf(if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else item.qty.toString())
    }
    var priceString by remember(item.product.id) {
        mutableStateOf(
            if (item.unitPricePoisha % 100 == 0L) (item.unitPricePoisha / 100).toString()
            else String.format(java.util.Locale.US, "%.2f", item.unitPricePoisha / 100.0)
        )
    }

    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm)
        ) {
            // Row 1: Item # + Product Name + Unit + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Column {
                        Text(
                            text = item.product.nameBn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.product.barcode.isNullOrBlank()) {
                            Text(
                                text = "বারকোড: ${item.product.barcode}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "মুছুন",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Quantity Controls + Price Controls + Subtotal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity input with +/- buttons (Direct manual entry)
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "পরিমাণ (${item.product.unitName})",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = {
                                val currentQ = item.qty
                                val step = if (currentQ <= 1.0) 0.1 else 1.0
                                val newQ = (currentQ - step).coerceAtLeast(0.001)
                                val rounded = kotlin.math.round(newQ * 1000) / 1000.0
                                qtyString = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
                                onQtyChange(rounded)
                            },
                            shape = RoundedCornerShape(Radius.xs),
                            color = MaterialTheme.dokanColors.surfaceAlt,
                            border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Remove, contentDescription = "কমান", modifier = Modifier.size(14.dp))
                            }
                        }

                        OutlinedTextField(
                            value = qtyString,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { it.isDigit() || it == '.' }
                                val parts = clean.split(".")
                                val sanitized = if (parts.size > 2) parts[0] + "." + parts.drop(1).joinToString("") else clean
                                qtyString = sanitized
                                sanitized.toDoubleOrNull()?.let { parsed ->
                                    if (parsed > 0.0) onQtyChange(parsed)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(Radius.xs),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Surface(
                            onClick = {
                                val currentQ = item.qty
                                val step = if (currentQ < 1.0) 0.1 else 1.0
                                val newQ = currentQ + step
                                val rounded = kotlin.math.round(newQ * 1000) / 1000.0
                                qtyString = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
                                onQtyChange(rounded)
                            },
                            shape = RoundedCornerShape(Radius.xs),
                            color = MaterialTheme.dokanColors.surfaceAlt,
                            border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "বাড়ান", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Purchase price input (Manual entry)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ক্রয় দর (৳)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    OutlinedTextField(
                        value = priceString,
                        onValueChange = { newVal ->
                            val clean = newVal.filter { it.isDigit() || it == '.' }
                            val parts = clean.split(".")
                            val sanitized = if (parts.size > 2) parts[0] + "." + parts.drop(1).joinToString("") else clean
                            priceString = sanitized
                            sanitized.toDoubleOrNull()?.let { dbl ->
                                onPriceChange((dbl * 100).toLong())
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(Radius.xs),
                        prefix = { Text("৳ ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // Line Total
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text(
                        text = "মোট",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(14.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==============================================================================
// ADD PURCHASE BOTTOM SHEET (ModalBottomSheet with Multi-Product Cart & Scanner)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPurchaseBottomSheet(
    suppliers: List<Supplier>,
    products: List<Product>,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onQuickCreateSupplier: (Supplier, (Supplier) -> Unit) -> Unit,
    onQuickCreateProduct: (Product, (Product) -> Unit) -> Unit,
    onSave: (Purchase, List<PurchaseItem>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }
    var showQuickAddProduct by remember { mutableStateOf(false) }
    var quickAddBarcode by remember { mutableStateOf("") }

    var draftItems by remember { mutableStateOf<List<PurchaseDraftItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isScannerOpen by remember { mutableStateOf(true) }
    var isProductDropdownExpanded by remember { mutableStateOf(false) }
    var scanFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var paidAmountText by remember { mutableStateOf("") }

    fun addOrSelectProduct(product: Product) {
        val existingIdx = draftItems.indexOfFirst { it.product.id == product.id }
        if (existingIdx >= 0) {
            scanFeedbackMessage = "⚠️ ${product.nameBn} ইতিমধ্যেই চালানে যোগ করা আছে (পরিমাণ নিচে লিখুন)"
            return
        }
        draftItems = draftItems + PurchaseDraftItem(
            product = product,
            qty = 1.0,
            unitPricePoisha = product.purchasePricePoisha
        )
        scanFeedbackMessage = "✓ ${product.nameBn} চালানে যুক্ত হয়েছে (পরিমাণ নিচে লিখুন)"
    }

    fun updateQty(index: Int, newQty: Double) {
        if (index !in draftItems.indices) return
        val updated = draftItems.toMutableList()
        val safeQty = newQty.coerceAtLeast(0.0)
        updated[index] = updated[index].copy(qty = kotlin.math.round(safeQty * 1000) / 1000.0)
        draftItems = updated
    }

    fun updatePrice(index: Int, newPricePoisha: Long) {
        if (index !in draftItems.indices) return
        val updated = draftItems.toMutableList()
        updated[index] = updated[index].copy(unitPricePoisha = newPricePoisha.coerceAtLeast(0L))
        draftItems = updated
    }

    fun removeItem(index: Int) {
        if (index !in draftItems.indices) return
        val updated = draftItems.toMutableList()
        updated.removeAt(index)
        draftItems = updated
    }

    val totalPoisha = remember(draftItems) {
        draftItems.sumOf { it.lineTotalPoisha }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Column {
                        Text(
                            text = "নতুন পণ্য ক্রয় এন্ট্রি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "একই চালানে একাধিক পণ্য যুক্ত করুন",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বাতিল")
                }
            }

            HorizontalDivider(color = MaterialTheme.dokanColors.border)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Section 1: সাপ্লায়ার নির্বাচন
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "১. সাপ্লায়ার নির্বাচন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showQuickAddSupplier = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ নতুন সাপ্লায়ার", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    var supMenuExpanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { supMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Column {
                                    Text(
                                        text = selectedSupplier?.name ?: "সাপ্লায়ার নির্বাচন করুন",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    selectedSupplier?.company?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = supMenuExpanded,
                            onDismissRequest = { supMenuExpanded = false }
                        ) {
                            suppliers.forEach { sup ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(sup.name, fontWeight = FontWeight.Bold)
                                            sup.company?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                                        }
                                    },
                                    onClick = {
                                        selectedSupplier = sup
                                        supMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 2: পণ্য অনুসন্ধান ও বারকোড স্ক্যান
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "২. চালানে পণ্য যোগ করুন",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Barcode camera toggle
                            Surface(
                                onClick = { isScannerOpen = !isScannerOpen },
                                shape = RoundedCornerShape(Radius.pill),
                                color = if (isScannerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, if (isScannerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isScannerOpen) Icons.Default.VideocamOff else Icons.Default.QrCodeScanner,
                                        contentDescription = "স্ক্যানার",
                                        tint = if (isScannerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isScannerOpen) "ক্যামেরা বন্ধ" else "বারকোড স্ক্যান",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isScannerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Quick add product
                            Surface(
                                onClick = {
                                    quickAddBarcode = ""
                                    showQuickAddProduct = true
                                },
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.dokanColors.surfaceAlt,
                                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+ নতুন পণ্য",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Compact camera scanner frame
                    if (isScannerOpen) {
                        CompactCameraBarcodeScanner(
                            currentBarcode = "",
                            existingProducts = products,
                            config = config,
                            onBarcodeScanned = { barcode ->
                                val trimmed = barcode.trim()
                                val found = products.find { it.barcode?.trim().equals(trimmed, ignoreCase = true) }
                                if (found != null) {
                                    addOrSelectProduct(found)
                                } else {
                                    scanFeedbackMessage = "বারকোড/QR: $trimmed (তালিকায় নেই)"
                                    quickAddBarcode = trimmed
                                }
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    scanFeedbackMessage?.let { msg ->
                        val isSuccess = msg.startsWith("✓")
                        val isWarning = msg.startsWith("⚠️")
                        Surface(
                            shape = RoundedCornerShape(Radius.xs),
                            color = when {
                                isSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                isWarning -> MaterialTheme.dokanColors.warning.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isSuccess -> MaterialTheme.colorScheme.primary
                                        isWarning -> MaterialTheme.dokanColors.warning
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!isSuccess && !isWarning && quickAddBarcode.isNotBlank()) {
                                    TextButton(
                                        onClick = { showQuickAddProduct = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("+ নতুন তৈরি করুন", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Product Search Bar with Dropdown Toggle
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            if (it.isNotBlank()) {
                                isProductDropdownExpanded = true
                            }
                        },
                        placeholder = { Text("পণ্য খুঁজুন বা ড্রপডাউন থেকে নির্বাচন করুন...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "সার্চ", tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "মুছুন", modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { isProductDropdownExpanded = !isProductDropdownExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isProductDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "ড্রপডাউন",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.sm),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.dokanColors.surfaceAlt,
                            unfocusedContainerColor = MaterialTheme.dokanColors.surfaceAlt
                        )
                    )

                    // Dropdown button if not expanded and search is blank
                    if (!isProductDropdownExpanded && searchQuery.isBlank()) {
                        Surface(
                            onClick = { isProductDropdownExpanded = true },
                            shape = RoundedCornerShape(Radius.xs),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "পণ্য ড্রপডাউন তালিকা দেখুন (${products.size}টি পণ্য)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Product Dropdown & Multi-add Selection List
                    if (isProductDropdownExpanded || searchQuery.isNotBlank()) {
                        val filteredProducts = remember(searchQuery, products) {
                            if (searchQuery.isBlank()) {
                                products
                            } else {
                                val q = searchQuery.trim()
                                products.filter {
                                    it.nameBn.contains(q, ignoreCase = true) ||
                                    it.nameEn.contains(q, ignoreCase = true) ||
                                    it.barcode?.contains(q) == true
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Spacing.xs)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (searchQuery.isBlank()) "পণ্য তালিকা (${filteredProducts.size}টি) - ট্যাপ করে যোগ করুন:" else "খোঁজার ফলাফল (${filteredProducts.size}টি):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            isProductDropdownExpanded = false
                                            searchQuery = ""
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "বন্ধ", modifier = Modifier.size(14.dp))
                                    }
                                }

                                if (filteredProducts.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.sm),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (searchQuery.isBlank()) "কোনো পণ্য পাওয়া যায়নি" else "\"$searchQuery\" নামে কোনো পণ্য নেই",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { showQuickAddProduct = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("+ নতুন পণ্য তৈরি করুন", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        filteredProducts.forEach { prod ->
                                            val isAdded = draftItems.any { it.product.id == prod.id }
                                            Surface(
                                                onClick = {
                                                    addOrSelectProduct(prod)
                                                    // Keeps dropdown open so multiple products can be added!
                                                },
                                                shape = RoundedCornerShape(Radius.xs),
                                                color = if (isAdded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.dokanColors.surfaceAlt,
                                                border = if (isAdded) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = prod.nameBn,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "মজুদ: ${Formatters.formatQty(prod.stockQty, prod.unitName, config.useBengaliNumerals)} • ক্রয়দর: ${Formatters.formatMoney(prod.purchasePricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (isAdded) {
                                                        Surface(
                                                            shape = RoundedCornerShape(Radius.pill),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(start = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "✓ যুক্ত",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(Radius.pill),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                            modifier = Modifier.padding(start = 6.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = "যোগ",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text(
                                                                    text = "যোগ",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: চালানে যুক্ত পণ্যসমূহ
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "৩. চালানের মালামাল (${draftItems.size}টি আইটেম):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (draftItems.isNotEmpty()) {
                            TextButton(
                                onClick = { draftItems = emptyList() },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("সব মুছুন", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (draftItems.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.dokanColors.border.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = "চালানে এখনও কোনো পণ্য যোগ করা হয়নি। উপরের ড্রপডাউন বা স্ক্যানার দিয়ে যোগ করুন।",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            draftItems.forEachIndexed { idx, item ->
                                PurchaseDraftItemRow(
                                    index = idx,
                                    item = item,
                                    config = config,
                                    onQtyChange = { newQ -> updateQty(idx, newQ) },
                                    onPriceChange = { newP -> updatePrice(idx, newP) },
                                    onRemove = { removeItem(idx) }
                                )
                            }
                        }
                    }
                }

                // Section 4: চালান বিল ও পেমেন্ট
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "৪. চালান বিল ও পরিশোধ বিবরণ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "মোট পণ্য সংখ্যা:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(draftItems.size.toString()) else draftItems.size} টি আইটেম",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "চালানের মোট বিল:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = Formatters.formatMoney(totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                    style = amountTextStyle(18.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    DokanTextField(
                        value = paidAmountText,
                        onValueChange = { paidAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "পরিশোধিত টাকা (বাকি থাকলে কম লিখুন)",
                        placeholder = "যেমন: ${(totalPoisha / 100.0)}",
                        keyboardType = KeyboardType.Decimal,
                        prefix = { Text("৳ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    )

                    val paidAmountVal = (paidAmountText.toDoubleOrNull()?.times(100))?.toLong() ?: totalPoisha
                    val dueAmountVal = (totalPoisha - paidAmountVal).coerceAtLeast(0L)

                    if (dueAmountVal > 0) {
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.dokanColors.warning.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.dokanColors.warning.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.dokanColors.warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "সাপ্লায়ারের বকেয়া হিসেবে জমা হবে:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.dokanColors.warning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = Formatters.formatMoney(dueAmountVal, config.useBengaliNumerals, config.currencySymbol),
                                    style = amountTextStyle(14.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.dokanColors.warning
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Save Action
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DokanSecondaryButton(
                        text = "বাতিল",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    val validItems = draftItems.filter { it.qty > 0.0 }

                    DokanPrimaryButton(
                        text = if (validItems.isEmpty()) "চালান সংরক্ষণ" else "চালান সংরক্ষণ (${validItems.size}টি)",
                        onClick = {
                            if (selectedSupplier != null && validItems.isNotEmpty() && totalPoisha > 0) {
                                val paid = (paidAmountText.toDoubleOrNull()?.times(100))?.toLong() ?: totalPoisha
                                val due = (totalPoisha - paid).coerceAtLeast(0L)

                                val purchase = Purchase(
                                    invoiceNo = "PUR-${System.currentTimeMillis() % 1000000}",
                                    supplierId = selectedSupplier!!.id,
                                    supplierName = selectedSupplier!!.name,
                                    totalPoisha = totalPoisha,
                                    paidAmountPoisha = paid,
                                    dueAmountPoisha = due
                                )

                                val items = validItems.map { draft ->
                                    PurchaseItem(
                                        purchaseId = 0,
                                        productId = draft.product.id,
                                        productName = draft.product.nameBn,
                                        qty = draft.qty,
                                        unitPricePoisha = draft.unitPricePoisha,
                                        lineTotalPoisha = draft.lineTotalPoisha
                                    )
                                }

                                onSave(purchase, items)
                            }
                        },
                        enabled = totalPoisha > 0 && selectedSupplier != null && validItems.isNotEmpty(),
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }

        if (showQuickAddSupplier) {
            QuickAddSupplierDialog(
                onDismiss = { showQuickAddSupplier = false },
                onSave = { newSup ->
                    onQuickCreateSupplier(newSup) { created ->
                        selectedSupplier = created
                        showQuickAddSupplier = false
                    }
                }
            )
        }

        if (showQuickAddProduct) {
            QuickAddProductDialog(
                initialBarcode = quickAddBarcode,
                onDismiss = {
                    showQuickAddProduct = false
                    quickAddBarcode = ""
                },
                onSave = { newProd ->
                    onQuickCreateProduct(newProd) { created ->
                        addOrSelectProduct(created)
                        showQuickAddProduct = false
                        quickAddBarcode = ""
                    }
                }
            )
        }
    }
}

// ==============================================================================
// ADD SUPPLIER BOTTOM SHEET (ModalBottomSheet)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSupplierBottomSheet(
    onDismiss: () -> Unit,
    onSave: (Supplier) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    val isValid = name.isNotBlank() && phone.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "নতুন সাপ্লায়ার যোগ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বাতিল")
                }
            }

            HorizontalDivider(color = MaterialTheme.dokanColors.border)

            DokanTextField(
                value = name,
                onValueChange = { name = it },
                label = "সাপ্লায়ারের নাম *",
                placeholder = "যেমন: মেসার্স রহিম ট্রেডার্স"
            )

            DokanTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "মোবাইল নম্বর *",
                placeholder = "০১৭xxxxxxxx",
                keyboardType = KeyboardType.Phone
            )

            DokanTextField(
                value = company,
                onValueChange = { company = it },
                label = "প্রতিষ্ঠান / কোম্পানি (ঐচ্ছিক)",
                placeholder = "কোম্পানির নাম"
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                DokanSecondaryButton(
                    text = "বাতিল",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )

                DokanPrimaryButton(
                    text = "যোগ করুন",
                    onClick = {
                        if (isValid) {
                            onSave(Supplier(name = name.trim(), phone = phone.trim(), company = company.trim().ifBlank { null }))
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

// ==============================================================================
// PAY PURCHASE DUE DIALOG (সাপ্লায়ার বকেয়া পরিশোধ)
// ==============================================================================
@Composable
private fun PayPurchaseDueDialog(
    purchase: Purchase,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onConfirmPay: (amountPoisha: Long, note: String?) -> Unit
) {
    val defaultAmountTaka = (purchase.dueAmountPoisha / 100.0).toString()
    var amountText by remember { mutableStateOf(defaultAmountTaka) }
    var noteText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "সাপ্লায়ার বকেয়া পরিশোধ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.dokanColors.surfaceAlt,
                    border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Text(
                            text = "সাপ্লায়ার: ${purchase.supplierName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "চালান নং: ${purchase.invoiceNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "মোট বিল:",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = Formatters.formatMoney(purchase.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "বর্তমান বকেয়া:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.dokanColors.warning
                            )
                            Text(
                                text = Formatters.formatMoney(purchase.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.warning
                            )
                        }
                    }
                }

                DokanTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { c -> c.isDigit() || c == '.' }
                        errorMessage = null
                    },
                    label = "পরিশোধের পরিমাণ (৳) *",
                    keyboardType = KeyboardType.Decimal,
                    prefix = { Text("৳ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    isError = errorMessage != null,
                    errorText = errorMessage
                )

                DokanTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = "পেমেন্ট মন্তব্য (ঐচ্ছিক)",
                    placeholder = "যেমন: ক্যাশ পরিশোধ"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountTaka = amountText.toDoubleOrNull() ?: 0.0
                    val amountPoisha = (amountTaka * 100).toLong()
                    if (amountPoisha <= 0) {
                        errorMessage = "সঠিক পরিমাণ লিখুন"
                        return@Button
                    }
                    if (amountPoisha > purchase.dueAmountPoisha) {
                        errorMessage = "বকেয়ার চেয়ে বেশি পরিশোধ করা যাবে না"
                        return@Button
                    }
                    onConfirmPay(amountPoisha, noteText.trim().ifBlank { null })
                },
                shape = RoundedCornerShape(Radius.pill)
            ) {
                Text("পরিশোধ নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

// ==============================================================================
// QUICK ADD SUPPLIER DIALOG (ইনলাইন দ্রুত সাপ্লায়ার তৈরি)
// ==============================================================================
@Composable
private fun QuickAddSupplierDialog(
    onDismiss: () -> Unit,
    onSave: (Supplier) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("নতুন সাপ্লায়ার যোগ করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                DokanTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = "সাপ্লায়ারের নাম *",
                    isError = nameError,
                    errorText = if (nameError) "সাপ্লায়ারের নাম আবশ্যক" else null
                )
                DokanTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "মোবাইল নম্বর",
                    keyboardType = KeyboardType.Phone
                )
                DokanTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = "কোম্পানির নাম (ঐচ্ছিক)"
                )
                DokanTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "ঠিকানা (ঐচ্ছিক)"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    onSave(
                        Supplier(
                            name = name.trim(),
                            phone = phone.trim(),
                            company = company.trim().ifBlank { null },
                            address = address.trim().ifBlank { null }
                        )
                    )
                },
                shape = RoundedCornerShape(Radius.pill)
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

// ==============================================================================
// QUICK ADD PRODUCT DIALOG (ইনলাইন দ্রুত পণ্য তৈরি)
// ==============================================================================
@Composable
private fun QuickAddProductDialog(
    initialBarcode: String = "",
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
    var barcodeText by remember { mutableStateOf(initialBarcode) }
    var purchasePriceText by remember { mutableStateOf("") }
    var salePriceText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("পিস") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("নতুন পণ্য যোগ করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                DokanTextField(
                    value = nameBn,
                    onValueChange = { nameBn = it; nameError = false },
                    label = "পণ্যের নাম (বাংলা) *",
                    isError = nameError,
                    errorText = if (nameError) "পণ্যের নাম আবশ্যক" else null
                )

                DokanTextField(
                    value = barcodeText,
                    onValueChange = { barcodeText = it },
                    label = "বারকোড (ঐচ্ছিক)",
                    placeholder = "যেমন: 890123456",
                    keyboardType = KeyboardType.Text,
                    leadingIcon = Icons.Default.QrCode
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    DokanTextField(
                        value = purchasePriceText,
                        onValueChange = { purchasePriceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "ক্রয়দর (৳) *",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    DokanTextField(
                        value = salePriceText,
                        onValueChange = { salePriceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "বিক্রয়দর (৳) *",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    DokanTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = "ক্যাটাগরি",
                        modifier = Modifier.weight(1f)
                    )
                    DokanTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "একক (কেজি/পিস)",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameBn.trim().isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    val pPriceTaka = purchasePriceText.toDoubleOrNull() ?: 0.0
                    val sPriceTaka = salePriceText.toDoubleOrNull() ?: pPriceTaka
                    onSave(
                        Product(
                            nameBn = nameBn.trim(),
                            barcode = barcodeText.trim(),
                            purchasePricePoisha = (pPriceTaka * 100).toLong(),
                            salePricePoisha = (sPriceTaka * 100).toLong(),
                            categoryId = 1,
                            unitName = unit.trim().ifBlank { "পিস" },
                            stockQty = 0.0
                        )
                    )
                },
                shape = RoundedCornerShape(Radius.pill)
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
