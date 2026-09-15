package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Customer
import com.example.data.entity.Product
import com.example.ui.CartItem
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PosScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.posSearchQuery.collectAsState()
    val heldCarts by viewModel.heldCarts.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var showCartSheet by remember { mutableStateOf(false) }
    var showLooseItemDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var productForQuantityDialog by remember { mutableStateOf<Product?>(null) }
    var showHeldCartsDialog by remember { mutableStateOf(false) }

    // Filter products by category and search
    val filteredProducts = remember(products, selectedCatId, searchQuery) {
        products.filter { prod ->
            val matchCat = (selectedCatId == 1L || prod.categoryId == selectedCatId)
            val matchQuery = searchQuery.isBlank() ||
                    prod.nameBn.contains(searchQuery, ignoreCase = true) ||
                    prod.nameEn.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery)
            matchCat && matchQuery
        }
    }

    val totalItemsCount = cartItems.sumOf { it.qty }
    val subtotalPoisha = cartItems.sumOf { it.lineTotalPoisha }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header / Search Bar & Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setPosSearchQuery(it) },
                            placeholder = { Text("পণ্য খুঁজুন বা বারকোড...", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setPosSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )

                        // Barcode Scan Button
                        IconButton(
                            onClick = { showBarcodeDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "বারকোড স্ক্যান",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Loose Item / খোলা পণ্য Button
                        IconButton(
                            onClick = { showLooseItemDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "খোলা পণ্য",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Held Carts indicator button
                        if (heldCarts.isNotEmpty()) {
                            IconButton(
                                onClick = { showHeldCartsDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StatusWarning.copy(alpha = 0.18f))
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text(heldCarts.size.toString()) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PauseCircle,
                                        contentDescription = "হোল্ড কার্ট",
                                        tint = StatusWarning
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Categories Tab Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat.id == selectedCatId
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectCategory(cat.id) },
                                label = { Text(cat.nameBn, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Products Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val inCartQty = cartItems.find { it.productId == product.id }?.qty ?: 0.0

                    ProductPosCard(
                        product = product,
                        inCartQty = inCartQty,
                        config = config,
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.addProductToCart(product, 1.0)
                        },
                        onLongTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            productForQuantityDialog = product
                        }
                    )
                }
            }
        }

        // Sticky Bottom Cart Bar (64dp pill above bottom)
        AnimatedVisibility(
            visible = cartItems.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 76.dp, start = 12.dp, end = 12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showCartSheet = true }
                    .testTag("sticky_cart_bar"),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "${Formatters.formatMoney(subtotalPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(cartItems.size.toString()) else cartItems.size} পদ (${Formatters.formatQty(totalItemsCount, "পণ্য", config.useBengaliNumerals)})",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "বিল সম্পন্ন করুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Full Cart & Checkout
    if (showCartSheet) {
        CartCheckoutBottomSheet(
            viewModel = viewModel,
            config = config,
            customers = customers,
            onDismiss = { showCartSheet = false },
            onComplete = {
                showCartSheet = false
            }
        )
    }

    // Modal: Loose Item Add
    if (showLooseItemDialog) {
        LooseItemDialog(
            onDismiss = { showLooseItemDialog = false },
            onAdd = { name, pricePoisha, qty, unit ->
                viewModel.addCustomItemToCart(name, pricePoisha, qty, unit)
                showLooseItemDialog = false
            }
        )
    }

    // Modal: Barcode Entry / Scanner
    if (showBarcodeDialog) {
        BarcodeEntryDialog(
            onDismiss = { showBarcodeDialog = false },
            onBarcodeScanned = { barcode ->
                val found = products.find { it.barcode == barcode }
                if (found != null) {
                    viewModel.addProductToCart(found, 1.0)
                    viewModel.showToast("${found.nameBn} যোগ করা হয়েছে")
                } else {
                    viewModel.showToast("বারকোড খুঁজে পাওয়া যায়নি: $barcode")
                }
                showBarcodeDialog = false
            }
        )
    }

    // Modal: Quick Weight / Decimal Quantity Keypad
    productForQuantityDialog?.let { product ->
        WeightQuantityDialog(
            product = product,
            config = config,
            onDismiss = { productForQuantityDialog = null },
            onConfirm = { qty ->
                viewModel.addProductToCart(product, qty)
                productForQuantityDialog = null
            }
        )
    }

    // Modal: Held Carts
    if (showHeldCartsDialog) {
        HeldCartsDialog(
            heldCarts = heldCarts,
            config = config,
            onDismiss = { showHeldCartsDialog = false },
            onRestore = { index ->
                viewModel.restoreHeldCart(index)
                showHeldCartsDialog = false
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductPosCard(
    product: Product,
    inCartQty: Double,
    config: ShopConfig,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap
            )
            .testTag("pos_product_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (inCartQty > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (inCartQty > 0) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column {
                Text(
                    text = product.nameBn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                        fontSize = 11.sp,
                        color = if (product.stockQty <= product.minStock) StatusDanger else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "চাপুন: পরিমাণ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // In-Cart Badge indicator
            if (inCartQty > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = Formatters.formatQty(inCartQty, "", config.useBengaliNumerals).trim(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartCheckoutBottomSheet(
    viewModel: PaponViewModel,
    config: ShopConfig,
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val selectedCustId by viewModel.selectedCustomerId.collectAsState()
    val selectedPaymentMethod by viewModel.selectedPaymentMethod.collectAsState()
    val discountPoisha by viewModel.discountPoisha.collectAsState()
    val cashTenderedPoisha by viewModel.cashTenderedPoisha.collectAsState()

    val subtotal = cartItems.sumOf { it.lineTotalPoisha }
    val vat = if (config.vatEnabled) ((subtotal - discountPoisha) * (config.vatPercentage / 100.0)).toLong() else 0L
    val grandTotal = (subtotal - discountPoisha + vat).coerceAtLeast(0)

    val changeReturnPoisha = if (cashTenderedPoisha > grandTotal) cashTenderedPoisha - grandTotal else 0L

    var showCustomerPicker by remember { mutableStateOf(false) }
    var discountInput by remember { mutableStateOf("") }
    var cashInput by remember { mutableStateOf("") }

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
                .padding(bottom = 32.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "বিক্রয় ও পেমেন্ট",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${cartItems.size} টি পদ)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    TextButton(onClick = { viewModel.holdCurrentCart(); onDismiss() }) {
                        Text("হোল্ড করুন", color = StatusWarning)
                    }
                    TextButton(onClick = { viewModel.clearCart(); onDismiss() }) {
                        Text("খালি করুন", color = StatusDanger)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cart Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems) { item ->
                    CartItemRow(
                        item = item,
                        config = config,
                        onQtyChange = { newQty -> viewModel.updateCartItemQty(item.productId, newQty) },
                        onRemove = { viewModel.removeCartItem(item.productId) }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp))

            // Customer Selection Chip/Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val selectedCustomer = customers.find { it.id == selectedCustId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedCustomer?.name ?: "সাধারণ ক্রেতা (নগদ)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                TextButton(onClick = { showCustomerPicker = true }) {
                    Text(if (selectedCustomer == null) "কাস্টমার যোগ" else "পরিবর্তন")
                }
            }

            // Payment Methods Toggle: নগদ, বিকাশ/নগদ, কার্ড, বাকি
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethodButton("cash", "নগদ", Icons.Default.Payments, selectedPaymentMethod) {
                    viewModel.selectPaymentMethod("cash")
                }
                PaymentMethodButton("mfs", "বিকাশ/নগদ", Icons.Default.PhoneAndroid, selectedPaymentMethod) {
                    viewModel.selectPaymentMethod("mfs")
                }
                PaymentMethodButton("due", "বাকি", Icons.Default.MenuBook, selectedPaymentMethod) {
                    viewModel.selectPaymentMethod("due")
                }
            }

            // If cash selected, show quick tender buttons
            if (selectedPaymentMethod == "cash") {
                val grandTotalTaka = grandTotal / 100
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        grandTotalTaka,
                        grandTotalTaka + 50,
                        grandTotalTaka + 100,
                        500L,
                        1000L
                    ).distinct().take(4).forEach { amt ->
                        OutlinedButton(
                            onClick = {
                                viewModel.setCashTendered(amt * 100)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${config.currencySymbol}${if (config.useBengaliNumerals) Formatters.toBengaliDigits(amt.toString()) else amt}",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (changeReturnPoisha > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ফেরত দিতে হবে:", color = StatusSuccess, fontWeight = FontWeight.Bold)
                        Text(
                            Formatters.formatMoney(changeReturnPoisha, config.useBengaliNumerals, config.currencySymbol),
                            color = StatusSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Calculation Summary Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট মূল্য:", fontSize = 14.sp)
                        Text(Formatters.formatMoney(subtotal, config.useBengaliNumerals, config.currencySymbol), fontSize = 14.sp)
                    }

                    if (discountPoisha > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ছাড়:", fontSize = 14.sp, color = StatusDanger)
                            Text("-${Formatters.formatMoney(discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", fontSize = 14.sp, color = StatusDanger)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সর্বমোট প্রদেয়:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            Formatters.formatMoney(grandTotal, config.useBengaliNumerals, config.currencySymbol),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Checkout CTA Button
            Button(
                onClick = {
                    viewModel.checkoutSale(
                        onSuccess = {
                            onComplete()
                        },
                        onError = { err ->
                            viewModel.showToast(err)
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("checkout_confirm_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPaymentMethod == "due") "বাকিতে বিক্রয় নিশ্চিত করুন" else "পেমেন্ট নিশ্চিত করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = { cust ->
                viewModel.selectCustomer(cust?.id)
                showCustomerPicker = false
            },
            onAddNew = { name, phone ->
                viewModel.saveCustomer(Customer(name = name, phone = phone)) {
                    // Created
                }
            }
        )
    }
}

@Composable
fun RowScope.PaymentMethodButton(
    methodId: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentMethod: String,
    onClick: () -> Unit
) {
    val isSelected = methodId == currentMethod
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    config: ShopConfig,
    onQtyChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = "${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)} × ${Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // - Button
            IconButton(
                onClick = { onQtyChange(item.qty - 1.0) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "কমান", modifier = Modifier.size(16.dp))
            }

            Text(
                text = Formatters.formatQty(item.qty, "", config.useBengaliNumerals).trim(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // + Button
            IconButton(
                onClick = { onQtyChange(item.qty + 1.0) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "বাড়ান", modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = StatusDanger, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Dialog for Loose Item
@Composable
fun LooseItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Long, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("পিস") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("খোলা পণ্য যোগ করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("পণ্যের নাম (যেমন: খোলা চিনি)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("একক মূল্য (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("পরিমাণ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("একক (কেজি/পিস)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pDouble = priceText.toDoubleOrNull() ?: 0.0
                    val qDouble = qtyText.toDoubleOrNull() ?: 1.0
                    if (pDouble > 0 && qDouble > 0) {
                        onAdd(name, (pDouble * 100).toLong(), qDouble, unit)
                    }
                }
            ) {
                Text("কার্টে যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// Dialog for Barcode Simulation
@Composable
fun BarcodeEntryDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    var barcodeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("বারকোড স্ক্যানার") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ক্যামেরা স্ক্যান অথবা সরাসরি বারকোড নম্বর লিখুন:", fontSize = 13.sp)
                OutlinedTextField(
                    value = barcodeText,
                    onValueChange = { barcodeText = it },
                    label = { Text("বারকোড নম্বর (যেমন: 8901001)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("দ্রুত ডেমো কোডসমূহ:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("8901001", "8901005", "8901016").forEach { code ->
                        AssistChip(
                            onClick = { barcodeText = code },
                            label = { Text(code, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (barcodeText.isNotBlank()) {
                        onBarcodeScanned(barcodeText.trim())
                    }
                }
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// Decimal / Weight Keypad Dialog (Section 5.2 Decimal quantities)
@Composable
fun WeightQuantityDialog(
    product: Product,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var qtyText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${product.nameBn} - পরিমাণ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("একক প্রতি মূল্য: ${Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${product.unitName}", fontSize = 13.sp)

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("পরিমাণ (${product.unitName})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("দ্রুত নির্বাচন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.25, 0.5, 1.0, 2.0, 5.0).forEach { weight ->
                        AssistChip(
                            onClick = { qtyText = weight.toString() },
                            label = { Text(Formatters.formatQty(weight, "", config.useBengaliNumerals).trim(), fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qtyText.toDoubleOrNull() ?: 1.0
                    if (q > 0) onConfirm(q)
                }
            ) {
                Text("কার্টে যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// Held Carts Dialog
@Composable
fun HeldCartsDialog(
    heldCarts: List<List<CartItem>>,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onRestore: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("হোল্ড করা কার্টসমূহ") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(heldCarts.indices.toList()) { index ->
                    val cart = heldCarts[index]
                    val total = cart.sumOf { it.lineTotalPoisha }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRestore(index) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("কার্ট #${index + 1}", fontWeight = FontWeight.Bold)
                                Text("${cart.size} টি পদ • ${Formatters.formatMoney(total, config.useBengaliNumerals, config.currencySymbol)}")
                            }
                            Button(onClick = { onRestore(index) }) {
                                Text("পুনরুদ্ধার")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

// Customer Picker Dialog
@Composable
fun CustomerPickerDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSelect: (Customer?) -> Unit,
    onAddNew: (String, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val filtered = customers.filter {
        it.name.contains(search, ignoreCase = true) || it.phone.contains(search)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAddingNew) "নতুন কাস্টমার যোগ" else "কাস্টমার নির্বাচন") },
        text = {
            if (isAddingNew) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("কাস্টমারের নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("মোবাইল নম্বর") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("নাম বা ফোন নম্বর দিয়ে খুঁজুন...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { isAddingNew = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নতুন কাস্টমার যুক্ত করুন")
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(null) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("সাধারণ ক্রেতা (নগদ)", modifier = Modifier.padding(12.dp))
                            }
                        }

                        items(filtered) { c ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(c) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    Text(c.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isAddingNew) {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            onAddNew(newName, newPhone)
                            isAddingNew = false
                        }
                    }
                ) {
                    Text("যোগ করুন")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
