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
// ADD PURCHASE BOTTOM SHEET (ModalBottomSheet with sections)
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
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var showQuickAddSupplier by remember { mutableStateOf(false) }
    var showQuickAddProduct by remember { mutableStateOf(false) }

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
                .fillMaxHeight(0.9f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "নতুন পণ্য ক্রয় এন্ট্রি",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বাতিল")
                }
            }

            HorizontalDivider(color = MaterialTheme.dokanColors.border)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section 1: সাপ্লায়ার ও পণ্য
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "সাপ্লায়ার ও পণ্য")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সাপ্লায়ার নির্বাচন:", style = MaterialTheme.typography.labelSmall)
                        TextButton(
                            onClick = { showQuickAddSupplier = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
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
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedSupplier?.name ?: "সাপ্লায়ার নেই",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = supMenuExpanded,
                            onDismissRequest = { supMenuExpanded = false }
                        ) {
                            suppliers.forEach { sup ->
                                DropdownMenuItem(
                                    text = { Text(sup.name) },
                                    onClick = {
                                        selectedSupplier = sup
                                        supMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("পণ্য নির্বাচন:", style = MaterialTheme.typography.labelSmall)
                        TextButton(
                            onClick = { showQuickAddProduct = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ নতুন পণ্য", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    var prodMenuExpanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { prodMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedProduct?.nameBn ?: "পণ্য নেই",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = prodMenuExpanded,
                            onDismissRequest = { prodMenuExpanded = false }
                        ) {
                            products.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text(prod.nameBn) },
                                    onClick = {
                                        selectedProduct = prod
                                        unitPriceText = (prod.purchasePricePoisha / 100.0).toString()
                                        prodMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Section 2: পরিমাণ ও দর
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "পরিমাণ ও দর")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        DokanTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "ক্রয় পরিমাণ",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )

                        DokanTextField(
                            value = unitPriceText,
                            onValueChange = { unitPriceText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "ক্রয় দর (৳)",
                            keyboardType = KeyboardType.Decimal,
                            prefix = { Text("৳ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "মোট চালানের মূল্য:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = Formatters.formatMoney(totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = amountTextStyle(18.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Section 3: পেমেন্ট
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "পেমেন্ট")

                    DokanTextField(
                        value = paidAmountText,
                        onValueChange = { paidAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "পরিশোধিত টাকা (বাকি থাকলে কম লিখুন)",
                        placeholder = "যেমন: ${(totalPoisha / 100.0)}",
                        keyboardType = KeyboardType.Decimal,
                        prefix = { Text("৳ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    )
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
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DokanSecondaryButton(
                        text = "বাতিল",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    DokanPrimaryButton(
                        text = "চালান সংরক্ষণ",
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
                        enabled = totalPoisha > 0 && selectedSupplier != null && selectedProduct != null,
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
                onDismiss = { showQuickAddProduct = false },
                onSave = { newProd ->
                    onQuickCreateProduct(newProd) { created ->
                        selectedProduct = created
                        unitPriceText = (created.purchasePricePoisha / 100.0).toString()
                        showQuickAddProduct = false
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
                            phone = phone.trim().ifBlank { null },
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
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var nameBn by remember { mutableStateOf("") }
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
