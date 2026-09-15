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
                        onDeleteClick = { purchaseToDelete = purchase }
                    )
                }
            }
        }
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
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.dokanColors.warningContainer
                    ) {
                        Text(
                            text = "বাকি ${Formatters.formatMoney(purchase.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.dokanColors.warning,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
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
    onSave: (Purchase, List<PurchaseItem>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

                    Text("সাপ্লায়ার নির্বাচন:", style = MaterialTheme.typography.labelSmall)
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

                    Text("পণ্য নির্বাচন:", style = MaterialTheme.typography.labelSmall)
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
