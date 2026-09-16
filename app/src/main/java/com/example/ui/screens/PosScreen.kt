package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.Customer
import com.example.data.entity.Product
import com.example.ui.CartItem
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.AnimatedAmount
import com.example.ui.components.DokanPrimaryButton
import com.example.ui.components.DokanTextField
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChipRow
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.amountTextStyle
import com.example.ui.theme.dokanColors
import com.example.ui.theme.softShadow
import com.example.util.Formatters
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.posSearchQuery.collectAsState()
    val heldCarts by viewModel.heldCarts.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var showLooseItemDialog by remember { mutableStateOf(false) }
    var showBarcodeDialog by remember { mutableStateOf(false) }
    var productForQuantityDialog by remember { mutableStateOf<Product?>(null) }
    var showHeldCartsDialog by remember { mutableStateOf(false) }

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
    val discountPoisha by viewModel.discountPoisha.collectAsState()
    val vat = if (config.vatEnabled) ((subtotalPoisha - discountPoisha) * (config.vatPercentage / 100.0)).toLong() else 0L
    val grandTotal = (subtotalPoisha - discountPoisha + vat).coerceAtLeast(0)

    var showCartModalSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Sticky Top Search Bar (56dp tall, Radius.pill, with 44dp icon buttons)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        // Sticky search pill
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = MaterialTheme.dokanColors.surfaceAlt,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
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
                                    onValueChange = { viewModel.setPosSearchQuery(it) },
                                    placeholder = { Text("পণ্য খুঁজুন বা বারকোড...", style = MaterialTheme.typography.bodyMedium) },
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
                                        onClick = { viewModel.setPosSearchQuery("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "পরিষ্কার", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Barcode scan 44dp icon button
                        IconButton(
                            onClick = { showBarcodeDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(MaterialTheme.dokanColors.surfaceAlt)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "বারকোড স্ক্যান",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Loose item / খোলা পণ্য 44dp icon button
                        IconButton(
                            onClick = { showLooseItemDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "খোলা পণ্য",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Held Carts indicator button
                        if (heldCarts.isNotEmpty()) {
                            IconButton(
                                onClick = { showHeldCartsDialog = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(MaterialTheme.dokanColors.warningContainer.copy(alpha = 0.3f))
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text(heldCarts.size.toString(), style = MaterialTheme.typography.labelSmall) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PauseCircle,
                                        contentDescription = "হোল্ড কার্ট",
                                        tint = MaterialTheme.dokanColors.warning,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Cart shortcut icon button with badge
                        if (cartItems.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showCartModalSheet = true
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (config.useBengaliNumerals) Formatters.toBengaliDigits(cartItems.size.toString()) else cartItems.size.toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = "কার্ট",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    // Category chips using FilterChipRow
                    val categoryNames = categories.map { it.nameBn }
                    val selectedCategoryName = categories.find { it.id == selectedCatId }?.nameBn ?: (categoryNames.firstOrNull() ?: "সব")
                    FilterChipRow(
                        options = categoryNames,
                        selected = selectedCategoryName,
                        onSelect = { selectedName ->
                            val cat = categories.find { it.nameBn == selectedName }
                            if (cat != null) {
                                viewModel.selectCategory(cat.id)
                            }
                        }
                    )
                }
            }

            // Products 2-Column Grid
            if (filteredProducts.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "কোনো পণ্য পাওয়া যায়নি",
                    message = "অন্য নামে খুঁজুন অথবা নতুন পণ্য যোগ করুন।"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = Spacing.md,
                        end = Spacing.md,
                        top = Spacing.md,
                        bottom = 160.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val inCartQty = cartItems.find { it.productId == product.id }?.qty ?: 0.0

                        PosProductGridCard(
                            product = product,
                            inCartQty = inCartQty,
                            config = config,
                            onTap = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        }

        // Floating Cart Checkout Button floating safely above FloatingNavBar
        AnimatedVisibility(
            visible = cartItems.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it * 2 }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp, start = Spacing.md, end = Spacing.md)
        ) {
            FloatingCartCheckoutButton(
                itemCount = cartItems.size,
                totalQty = totalItemsCount,
                grandTotalPoisha = grandTotal,
                config = config,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showCartModalSheet = true
                }
            )
        }
    }

    // Modal Bottom Sheet for Cart & Bill Checkout
    if (showCartModalSheet && cartItems.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showCartModalSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            PosCartModalSheetContent(
                viewModel = viewModel,
                config = config,
                customers = customers,
                cartItems = cartItems,
                subtotalPoisha = subtotalPoisha,
                totalItemsCount = totalItemsCount,
                onDismiss = { showCartModalSheet = false },
                onHoldCart = {
                    viewModel.holdCurrentCart()
                    showCartModalSheet = false
                },
                onClearCart = {
                    viewModel.clearCart()
                    showCartModalSheet = false
                }
            )
        }
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

// ==============================================================================
// 1. POS PRODUCT GRID CARD (2-Column, Radius.md, Bounce on Tap)
// ==============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosProductGridCard(
    product: Product,
    inCartQty: Double,
    config: ShopConfig,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pos_card_scale"
    )

    val localFile = remember(product.localImagePath) {
        if (!product.localImagePath.isNullOrBlank()) {
            val f = File(product.localImagePath)
            if (f.exists()) f else null
        } else null
    }

    val isLowStock = product.stockQty <= product.minStock

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .softShadow(1, RoundedCornerShape(Radius.md))
            .border(
                width = if (inCartQty > 0) 1.5.dp else 1.dp,
                color = if (inCartQty > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Radius.md)
            )
            .clip(RoundedCornerShape(Radius.md))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap,
                onLongClick = onLongTap
            )
            .defaultMinSize(minHeight = 44.dp)
            .testTag("pos_product_${product.id}"),
        shape = RoundedCornerShape(Radius.md),
        color = if (inCartQty > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Product Image or Coloured initial-letter tile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(MaterialTheme.dokanColors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                if (localFile != null) {
                    AsyncImage(
                        model = localFile,
                        contentDescription = product.nameBn,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xs)
                    )
                } else {
                    // Coloured initial-letter tile
                    val initialLetter = product.nameBn.firstOrNull()?.toString() ?: "প"
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialLetter,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // In-Cart Badge Indicator
                if (inCartQty > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.xs)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = Formatters.formatQty(inCartQty, "", config.useBengaliNumerals).trim(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Product Information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
            ) {
                Text(
                    text = product.nameBn,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol),
                    style = amountTextStyle(16.sp),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLowStock) MaterialTheme.dokanColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isLowStock) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ==============================================================================
// 2. FLOATING CART CHECKOUT BUTTON (ABOVE NAV BAR)
// ==============================================================================
@Composable
fun FloatingCartCheckoutButton(
    itemCount: Int,
    totalQty: Double,
    grandTotalPoisha: Long,
    config: ShopConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cart_btn_scale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.30f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side: Cart Icon Badge + Total & Item count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "কার্ট",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Column {
                    Text(
                        text = Formatters.formatMoney(grandTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(18.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(itemCount.toString()) else itemCount} পদ (${Formatters.formatQty(totalQty, "পণ্য", config.useBengaliNumerals)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.90f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.xs))

            // Right Side: Action Pill "বিল সম্পন্ন করুন" with Arrow
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = Color.White.copy(alpha = 0.22f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "বিল সম্পন্ন করুন",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 3. POS CART MODAL BOTTOM SHEET CONTENT
// ==============================================================================
@Composable
private fun PosCartModalSheetContent(
    viewModel: PaponViewModel,
    config: ShopConfig,
    customers: List<Customer>,
    cartItems: List<CartItem>,
    subtotalPoisha: Long,
    totalItemsCount: Double,
    onDismiss: () -> Unit,
    onHoldCart: () -> Unit,
    onClearCart: () -> Unit
) {
    val selectedCustId by viewModel.selectedCustomerId.collectAsState()
    val selectedPaymentMethod by viewModel.selectedPaymentMethod.collectAsState()
    val discountPoisha by viewModel.discountPoisha.collectAsState()
    val cashTenderedPoisha by viewModel.cashTenderedPoisha.collectAsState()

    val vat = if (config.vatEnabled) ((subtotalPoisha - discountPoisha) * (config.vatPercentage / 100.0)).toLong() else 0L
    val grandTotal = (subtotalPoisha - discountPoisha + vat).coerceAtLeast(0)
    val changeReturnPoisha = if (cashTenderedPoisha > grandTotal) cashTenderedPoisha - grandTotal else 0L

    var showCustomerPicker by remember { mutableStateOf(false) }
    val view = LocalView.current

    if (cartItems.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        // Modal Sheet Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "কার্ট ও বিল",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(cartItems.size.toString()) else cartItems.size} পদ (${Formatters.formatQty(totalItemsCount, "পণ্য", config.useBengaliNumerals)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onHoldCart,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = MaterialTheme.dokanColors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "হোল্ড",
                        color = MaterialTheme.dokanColors.warning,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onClearCart,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.dokanColors.danger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "খালি",
                        color = MaterialTheme.dokanColors.danger,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "বন্ধ করুন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = 32.dp)
        ) {
            // Cart Items Header
            Text(
                text = "ব্যাগে থাকা পণ্যসমূহ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xs)
            )

            // Cart Items List
            cartItems.forEach { item ->
                PosCartItemRow(
                    item = item,
                    config = config,
                    onQtyChange = { newQty ->
                        if (newQty <= 0) {
                            viewModel.removeCartItem(item.productId)
                        } else {
                            viewModel.updateCartItemQty(item.productId, newQty)
                        }
                    },
                    onRemove = { viewModel.removeCartItem(item.productId) }
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Summary Block on surfaceAlt
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.sm),
                color = MaterialTheme.dokanColors.surfaceAlt
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট মূল্য:", style = MaterialTheme.typography.bodyMedium)
                        Text(Formatters.formatMoney(subtotalPoisha, config.useBengaliNumerals, config.currencySymbol), style = MaterialTheme.typography.bodyMedium)
                    }

                    if (discountPoisha > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ছাড়:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.dokanColors.danger)
                            Text("-${Formatters.formatMoney(discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.dokanColors.danger)
                        }
                    }

                    if (config.vatEnabled && vat > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ভ্যাট (${config.vatPercentage}%):", style = MaterialTheme.typography.bodyMedium)
                            Text(Formatters.formatMoney(vat, config.useBengaliNumerals, config.currencySymbol), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সর্বমোট প্রদেয়:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AnimatedAmount(
                            value = grandTotal,
                            style = amountTextStyle(26.sp),
                            color = MaterialTheme.colorScheme.primary,
                            useBengaliNumerals = config.useBengaliNumerals,
                            currencySymbol = config.currencySymbol
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // 3-Segment Payment Selector: নগদ / বিকাশ-নগদ / বাকি
            Payment3SegmentSelector(
                selectedMethod = selectedPaymentMethod,
                onSelectMethod = { viewModel.selectPaymentMethod(it) }
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Dynamic view based on payment method
            if (selectedPaymentMethod == "cash") {
                CashTenderSection(
                    grandTotal = grandTotal,
                    cashTenderedPoisha = cashTenderedPoisha,
                    changeReturnPoisha = changeReturnPoisha,
                    config = config,
                    onSetCash = { viewModel.setCashTendered(it) }
                )
            } else if (selectedPaymentMethod == "due") {
                val selectedCustomer = customers.find { it.id == selectedCustId }
                Surface(
                    onClick = { showCustomerPicker = true },
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.dokanColors.surfaceAlt,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = selectedCustomer?.name ?: "গ্রাহক নির্বাচন করুন (আবশ্যক)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedCustomer != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.dokanColors.danger
                            )
                        }
                        Text(
                            text = if (selectedCustomer == null) "বাছাই করুন" else "পরিবর্তন",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // বিল সম্পন্ন করুন Button (56dp, full width, Radius.md)
            DokanPrimaryButton(
                text = if (selectedPaymentMethod == "due") "বাকিতে বিল সম্পন্ন করুন" else "বিল সম্পন্ন করুন",
                onClick = {
                    viewModel.checkoutSale(
                        onSuccess = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onDismiss()
                        },
                        onError = { err -> viewModel.showToast(err) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelect = { cust ->
                viewModel.selectCustomer(cust?.id)
                showCustomerPicker = false
            },
            onAddNew = { name, phone, initialDuePoisha ->
                viewModel.saveCustomer(
                    Customer(name = name, phone = phone),
                    initialDuePoisha = initialDuePoisha
                ) { newId ->
                    viewModel.selectCustomer(newId)
                    showCustomerPicker = false
                }
            }
        )
    }
}

// ==============================================================================
// 3. CART ITEM ROW WITH 40dp STEPPERS
// ==============================================================================
@Composable
private fun PosCartItemRow(
    item: CartItem,
    config: ShopConfig,
    onQtyChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    val view = LocalView.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.xs)),
        shape = RoundedCornerShape(Radius.xs),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)} × ${Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Minus stepper (40dp target)
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onQtyChange(item.qty - 1.0)
                    },
                    shape = RoundedCornerShape(Radius.xs),
                    color = MaterialTheme.dokanColors.surfaceAlt,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "কমান", modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = Formatters.formatQty(item.qty, "", config.useBengaliNumerals).trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.sm)
                )

                // Plus stepper (40dp target)
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onQtyChange(item.qty + 1.0)
                    },
                    shape = RoundedCornerShape(Radius.xs),
                    color = MaterialTheme.dokanColors.surfaceAlt,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "বাড়ান", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                Text(
                    text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                    style = amountTextStyle(15.sp),
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = MaterialTheme.dokanColors.danger, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ==============================================================================
// 4. 3-SEGMENT PAYMENT SELECTOR (নগদ / বিকাশ-নগদ / বাকি)
// ==============================================================================
@Composable
private fun Payment3SegmentSelector(
    selectedMethod: String,
    onSelectMethod: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.dokanColors.surfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val segments = listOf(
            Triple("cash", "নগদ", Icons.Default.Payments),
            Triple("mfs", "বিকাশ-নগদ", Icons.Default.PhoneAndroid),
            Triple("due", "বাকি", Icons.Default.MenuBook)
        )

        segments.forEach { (id, title, icon) ->
            val isSelected = id == selectedMethod
            Surface(
                onClick = { onSelectMethod(id) },
                shape = RoundedCornerShape(Radius.pill),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 5. CASH TENDER SECTION & ANIMATED CHANGE RETURN
// ==============================================================================
@Composable
private fun CashTenderSection(
    grandTotal: Long,
    cashTenderedPoisha: Long,
    changeReturnPoisha: Long,
    config: ShopConfig,
    onSetCash: (Long) -> Unit
) {
    val grandTotalTaka = (grandTotal / 100).coerceAtLeast(0)
    var cashInputText by remember(cashTenderedPoisha) {
        mutableStateOf(if (cashTenderedPoisha > 0) (cashTenderedPoisha / 100).toString() else "")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.dokanColors.surfaceAlt)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Header with Title & Reset action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "নগদ গ্রহণ ও ফেরত টাকার হিসাব",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (cashTenderedPoisha > 0) {
                TextButton(
                    onClick = {
                        cashInputText = ""
                        onSetCash(0L)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "রিসেট",
                        tint = MaterialTheme.dokanColors.danger,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "মুছুন",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.dokanColors.danger
                    )
                }
            }
        }

        // Input Box for Customer Tendered Amount
        DokanTextField(
            value = cashInputText,
            onValueChange = { input ->
                val normalized = Formatters.fromBengaliDigits(input).filter { it.isDigit() || it == '.' }
                cashInputText = normalized
                val amountDouble = normalized.toDoubleOrNull() ?: 0.0
                onSetCash((amountDouble * 100).toLong())
            },
            label = "গ্রাহকের দেওয়া টাকা / নোট (৳)",
            placeholder = if (grandTotalTaka > 0) "যেমন: $grandTotalTaka বা ১০০০" else "টাকার পরিমাণ লিখুন"
        )

        // Note Chips Row
        val noteOptions = remember(grandTotalTaka) {
            val list = mutableListOf<Long>()
            if (grandTotalTaka > 0) {
                list.add(grandTotalTaka)
            }
            val standardNotes = listOf(50L, 100L, 200L, 500L, 1000L)
            standardNotes.filter { it >= grandTotalTaka }.forEach { list.add(it) }

            val next50 = ((grandTotalTaka + 49) / 50) * 50
            if (next50 > grandTotalTaka) list.add(next50)
            val next100 = ((grandTotalTaka + 99) / 100) * 100
            if (next100 > grandTotalTaka) list.add(next100)

            list.distinct().sorted().take(5)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            noteOptions.forEach { amt ->
                val isExact = amt == grandTotalTaka
                val isSelected = (cashTenderedPoisha / 100) == amt
                val labelText = if (isExact) {
                    "হুবহু ${Formatters.formatMoney(amt * 100, config.useBengaliNumerals, config.currencySymbol)}"
                } else {
                    Formatters.formatMoney(amt * 100, config.useBengaliNumerals, config.currencySymbol)
                }

                Surface(
                    onClick = {
                        cashInputText = amt.toString()
                        onSetCash(amt * 100)
                    },
                    shape = RoundedCornerShape(Radius.pill),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isExact -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected || isExact) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isExact -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 6.dp)
                    )
                }
            }
        }

        // Live Change Due Card
        if (cashTenderedPoisha > 0) {
            when {
                cashTenderedPoisha > grandTotal -> {
                    val change = cashTenderedPoisha - grandTotal
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column {
                                    Text(
                                        text = "গ্রাহককে ফেরত দিন",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                    Text(
                                        text = "নগদ ফেরত প্রদান আবশ্যক",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }

                            AnimatedContent(
                                targetState = change,
                                transitionSpec = { slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() },
                                label = "change_return_live"
                            ) { targetChange ->
                                Text(
                                    text = Formatters.formatMoney(targetChange, config.useBengaliNumerals, config.currencySymbol),
                                    style = amountTextStyle(22.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }

                cashTenderedPoisha == grandTotal -> {
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.dokanColors.success,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "হুবহু পরিশোধ (কোনো ফেরত নেই)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.dokanColors.success
                            )
                        }
                    }
                }

                cashTenderedPoisha < grandTotal -> {
                    val remaining = grandTotal - cashTenderedPoisha
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "কম দেওয়া হয়েছে",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Text(
                                text = "বাকি: ${Formatters.formatMoney(remaining, config.useBengaliNumerals, config.currencySymbol)}",
                                style = amountTextStyle(15.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 6. SUPPORTING DIALOGS (RESTYLED WITH Radius.lg & 20dp PADDING)
// ==============================================================================
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
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("খোলা পণ্য যোগ করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                DokanTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "পণ্যের নাম (যেমন: খোলা চিনি)"
                )
                DokanTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "একক মূল্য (৳)"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DokanTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = "পরিমাণ",
                        modifier = Modifier.weight(1f)
                    )
                    DokanTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "একক",
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
                },
                shape = RoundedCornerShape(Radius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("কার্টে যোগ করুন", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun BarcodeEntryDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    var barcodeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("বারকোড স্ক্যানার", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text("ক্যামেরা স্ক্যান অথবা সরাসরি বারকোড নম্বর লিখুন:", style = MaterialTheme.typography.bodyMedium)
                DokanTextField(
                    value = barcodeText,
                    onValueChange = { barcodeText = it },
                    label = "বারকোড নম্বর (যেমন: 8901001)"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf("8901001", "8901005", "8901016").forEach { code ->
                        AssistChip(
                            onClick = { barcodeText = code },
                            label = { Text(code, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(Radius.pill)
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
                },
                shape = RoundedCornerShape(Radius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("যোগ করুন", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

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
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("${product.nameBn} - পরিমাণ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "একক প্রতি মূল্য: ${Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${product.unitName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                DokanTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "পরিমাণ (${product.unitName})"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(0.25, 0.5, 1.0, 2.0, 5.0).forEach { weight ->
                        AssistChip(
                            onClick = { qtyText = weight.toString() },
                            label = { Text(Formatters.formatQty(weight, "", config.useBengaliNumerals).trim(), style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(Radius.pill)
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
                },
                shape = RoundedCornerShape(Radius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("কার্টে যোগ করুন", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun HeldCartsDialog(
    heldCarts: List<List<CartItem>>,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onRestore: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        title = { Text("হোল্ড করা কার্টসমূহ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(heldCarts.indices.toList()) { index ->
                    val cart = heldCarts[index]
                    val total = cart.sumOf { it.lineTotalPoisha }
                    Surface(
                        onClick = { onRestore(index) },
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("কার্ট #${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${cart.size} টি পদ • ${Formatters.formatMoney(total, config.useBengaliNumerals, config.currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { onRestore(index) },
                                shape = RoundedCornerShape(Radius.xs),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("পুনরুদ্ধার", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন", style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
fun CustomerPickerDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSelect: (Customer?) -> Unit,
    onAddNew: (String, String, Long) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newInitialDue by remember { mutableStateOf("") }

    val filtered = customers.filter {
        it.name.contains(search, ignoreCase = true) || it.phone.contains(search)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        title = { Text(if (isAddingNew) "নতুন কাস্টমার যোগ" else "কাস্টমার নির্বাচন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            if (isAddingNew) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DokanTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = "কাস্টমারের নাম *"
                    )
                    DokanTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = "মোবাইল নম্বর *"
                    )
                    DokanTextField(
                        value = newInitialDue,
                        onValueChange = { newInitialDue = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "পূর্বের বাকি / আগের বকেয়া (৳)",
                        placeholder = "০ (যদি থাকে)"
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DokanTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "নাম বা ফোন নম্বর দিয়ে খুঁজুন..."
                    )

                    Button(
                        onClick = { isAddingNew = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.sm),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নতুন কাস্টমার যুক্ত করুন", style = MaterialTheme.typography.labelLarge)
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        item {
                            Surface(
                                onClick = { onSelect(null) },
                                shape = RoundedCornerShape(Radius.xs),
                                color = MaterialTheme.dokanColors.surfaceAlt,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("সাধারণ ক্রেতা (নগদ)", modifier = Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        items(filtered) { c ->
                            Surface(
                                onClick = { onSelect(c) },
                                shape = RoundedCornerShape(Radius.xs),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.xs))
                            ) {
                                Column(modifier = Modifier.padding(Spacing.sm)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            val dueTk = newInitialDue.toDoubleOrNull() ?: 0.0
                            val duePoisha = (dueTk * 100).toLong()
                            onAddNew(newName.trim(), newPhone.trim(), duePoisha)
                            isAddingNew = false
                        }
                    },
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("যোগ করুন", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", style = MaterialTheme.typography.labelLarge) }
        }
    )
}
