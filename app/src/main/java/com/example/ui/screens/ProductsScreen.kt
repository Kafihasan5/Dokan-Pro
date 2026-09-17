package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Category
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.Category
import com.example.data.entity.Product
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.Formatters
import com.example.ui.components.DokanSkeletonList
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()
    val totalStockSaleValue by viewModel.totalStockSaleValuePoisha.collectAsState()
    val totalStockPurchaseValue by viewModel.totalStockPurchaseValuePoisha.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(1L) }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var showSearchBarcodeScanner by remember { mutableStateOf(false) }

    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var productForStockAdjust by remember { mutableStateOf<Product?>(null) }
    var showCategoryUnitManager by remember { mutableStateOf(false) }
    var initialManageTab by remember { mutableStateOf(0) }
    var isFirstLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        isFirstLoading = false
    }

    val filteredProducts = remember(products, searchQuery, selectedCatId, showOnlyLowStock) {
        products.filter { prod ->
            val matchLow = !showOnlyLowStock || (prod.stockQty <= prod.minStock)
            val matchCat = (selectedCatId == 1L || prod.categoryId == selectedCatId)
            val matchSearch = searchQuery.isBlank() ||
                    prod.nameBn.contains(searchQuery, ignoreCase = true) ||
                    prod.nameEn.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery)
            matchLow && matchCat && matchSearch
        }
    }

    var isManualRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isAddingNew = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("নতুন পণ্য", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .testTag("add_product_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isManualRefreshing,
            onRefresh = {
                isManualRefreshing = true
                coroutineScope.launch {
                    viewModel.syncToSupabase(silent = true)
                    kotlinx.coroutines.delay(600)
                    isManualRefreshing = false
                }
            },
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1) Compact Stock-Value Summary Strip at Top
                ProductSummaryStrip(
                    totalSaleValue = totalStockSaleValue,
                    totalPurchaseValue = totalStockPurchaseValue,
                    itemCount = products.size,
                    config = config
                )

                // 2) Search Bar, Low Stock Filter & Category Entry
                ProductFilterHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    showOnlyLowStock = showOnlyLowStock,
                    onToggleLowStock = { showOnlyLowStock = it },
                    onOpenCategoryManager = {
                        initialManageTab = 0
                        showCategoryUnitManager = true
                    },
                    onScanBarcode = { showSearchBarcodeScanner = true }
                )

                // Category Chips Row (Preserved for seamless category filtering)
                if (categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCatId == cat.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCatId = cat.id },
                                label = { Text(cat.nameBn, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(Radius.pill),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // 3 & 6) Product List or Empty State
                if (isFirstLoading && products.isEmpty()) {
                    DokanSkeletonList(
                        itemCount = 6,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else if (products.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Default.Inventory2,
                            title = "দোকানে কোনো পণ্য নেই (খালি দোকান)",
                            message = "নিচের বাটনে চাপ দিয়ে আপনার আসল পণ্য যোগ করুন।",
                            actionLabel = "প্রথম পণ্য যোগ করুন",
                            onAction = { isAddingNew = true }
                        )
                    }
                } else if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "অনুসন্ধানে কোনো পণ্য পাওয়া যায়নি",
                            message = "অন্য নাম বা বারকোড দিয়ে চেষ্টা করুন।"
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
                            top = Spacing.sm,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            SwipeableProductCard(
                                product = product,
                                categoryName = categories.find { it.id == product.categoryId }?.nameBn ?: "",
                                config = config,
                                onEdit = { productToEdit = product },
                                onAdjustStock = { productForStockAdjust = product }
                            )
                        }
                    }
                }
            }
        }
    }

    // 5) Add or Edit Product ModalBottomSheet (Full-height grouped)
    if (isAddingNew || productToEdit != null) {
        ProductFormBottomSheet(
            initialProduct = productToEdit,
            categories = categories,
            units = units,
            onDismiss = {
                isAddingNew = false
                productToEdit = null
            },
            onSave = { savedProduct ->
                viewModel.saveProduct(savedProduct) {
                    isAddingNew = false
                    productToEdit = null
                }
            },
            onManageCategories = {
                initialManageTab = 0
                showCategoryUnitManager = true
            },
            onManageUnits = {
                initialManageTab = 1
                showCategoryUnitManager = true
            }
        )
    }

    // Stock Adjustment Dialog
    productForStockAdjust?.let { product ->
        StockAdjustmentDialog(
            product = product,
            config = config,
            onDismiss = { productForStockAdjust = null },
            onConfirm = { qtyChange, reason, note ->
                viewModel.adjustStock(product.id, product.nameBn, qtyChange, reason, note)
                productForStockAdjust = null
            }
        )
    }

    // Category / Unit Manager Dialog
    if (showCategoryUnitManager) {
        CategoryUnitManagerDialog(
            viewModel = viewModel,
            initialTab = initialManageTab,
            onDismiss = { showCategoryUnitManager = false }
        )
    }

    // Barcode Search Scanner Dialog
    if (showSearchBarcodeScanner) {
        CameraBarcodeScannerDialog(
            title = "পণ্য খুঁজুন",
            confirmText = "খুঁজুন",
            onDismiss = { showSearchBarcodeScanner = false },
            onBarcodeScanned = { code ->
                searchQuery = code
                showSearchBarcodeScanner = false
            }
        )
    }
}

// ==============================================================================
// 1. SUMMARY STRIP: Compact stock-value summary on surfaceAlt bar
// ==============================================================================
@Composable
private fun ProductSummaryStrip(
    totalSaleValue: Long,
    totalPurchaseValue: Long,
    itemCount: Int,
    config: ShopConfig
) {
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
                // Stock Sale Value
                Column {
                    Text(
                        text = "মোট স্টক মূল্য (বিক্রয়দর)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatMoney(totalSaleValue, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(18.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ক্রয়মূল্য: ${Formatters.formatMoney(totalPurchaseValue, config.useBengaliNumerals, config.currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Item Count & Potential Profit
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "আইটেম সংখ্যা",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(itemCount.toString()) else itemCount} টি",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val potential = (totalSaleValue - totalPurchaseValue).coerceAtLeast(0)
                    Text(
                        text = "সম্ভাব্য লাভ: +${Formatters.formatMoney(potential, config.useBengaliNumerals, config.currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.dokanColors.success
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.dokanColors.border
            )
        }
    }
}

// ==============================================================================
// 2. SEARCH PILL & FILTER BAR: 52dp search pill + segmented toggle
// ==============================================================================
@Composable
private fun ProductFilterHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showOnlyLowStock: Boolean,
    onToggleLowStock: (Boolean) -> Unit,
    onOpenCategoryManager: () -> Unit,
    onScanBarcode: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        // 52dp Search Pill
        Surface(
            shape = RoundedCornerShape(Radius.pill),
            color = MaterialTheme.dokanColors.surfaceAlt,
            border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
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
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "পণ্যের নাম বা বারকোড দিয়ে খুঁজুন...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "মুছুন",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onScanBarcode,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "ক্যামেরা দিয়ে বারকোড স্ক্যান করুন",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Filter strip: Segmented toggle for সব / স্টক কম + Category Manager Icon Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented toggle
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = MaterialTheme.dokanColors.surfaceAlt,
                border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "সব"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (!showOnlyLowStock) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onToggleLowStock(false) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "সব",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (!showOnlyLowStock) FontWeight.Bold else FontWeight.Medium,
                            color = if (!showOnlyLowStock) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // "স্টক কম"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (showOnlyLowStock) MaterialTheme.dokanColors.warningContainer else Color.Transparent)
                            .clickable { onToggleLowStock(true) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "স্টক কম ⚠️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (showOnlyLowStock) FontWeight.Bold else FontWeight.Medium,
                            color = if (showOnlyLowStock) MaterialTheme.dokanColors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ⚙️ ক্যাটাগরি ও একক Icon Button
            Surface(
                onClick = onOpenCategoryManager,
                shape = RoundedCornerShape(Radius.pill),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "ক্যাটাগরি ও একক",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "ক্যাটাগরি ও একক",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 3 & 4. SWIPEABLE PRODUCT CARD (SwipeToDismissBox + Tap fallback)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableProductCard(
    product: Product,
    categoryName: String,
    config: ShopConfig,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit
) {
    val isLowStock = product.stockQty <= product.minStock

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false // Non-dismissing mode
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onAdjustStock()
                    false // Non-dismissing mode
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSwipingRight = direction == SwipeToDismissBoxValue.StartToEnd
            val isSwipingLeft = direction == SwipeToDismissBoxValue.EndToStart

            val bgColor = when {
                isSwipingRight -> MaterialTheme.colorScheme.primaryContainer
                isSwipingLeft -> MaterialTheme.dokanColors.warningContainer
                else -> Color.Transparent
            }
            val contentColor = when {
                isSwipingRight -> MaterialTheme.colorScheme.primary
                isSwipingLeft -> MaterialTheme.dokanColors.warning
                else -> Color.Transparent
            }
            val alignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd
            val icon = if (isSwipingRight) Icons.Default.Edit else Icons.Default.Tune
            val text = if (isSwipingRight) "সম্পাদনা" else "স্টক সমন্বয়"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(bgColor)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = alignment
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    ) {
        // Product Card Content (Card tap preserved)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .softShadow(1, RoundedCornerShape(Radius.md))
                .clip(RoundedCornerShape(Radius.md))
                .clickable { onEdit() },
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Warning-coloured 3dp left stripe for low stock
                if (isLowStock) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.dokanColors.warning)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 56dp square product thumbnail with initial letter tile fallback
                    ProductThumbnail(
                        imagePath = product.localImagePath,
                        size = 56.dp,
                        shape = RoundedCornerShape(Radius.sm),
                        contentScale = ContentScale.Fit,
                        productName = product.nameBn
                    )

                    Spacer(modifier = Modifier.width(Spacing.md))

                    // Middle details: name, category, unit, barcode, low-stock chip
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.nameBn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val metaText = buildString {
                            if (categoryName.isNotBlank()) append(categoryName)
                            if (product.unitName.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(product.unitName)
                            }
                        }
                        if (metaText.isNotBlank()) {
                            Text(
                                text = metaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isLowStock) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.dokanColors.warningContainer
                            ) {
                                Text(
                                    text = "স্টক কম ⚠️",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.dokanColors.warning,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    // Right column: Sale price & Stock quantity
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(17.sp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Text(
                            text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLowStock) MaterialTheme.dokanColors.danger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 5. ADD / EDIT PRODUCT FULL-HEIGHT ModalBottomSheet
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormBottomSheet(
    initialProduct: Product?,
    categories: List<Category>,
    units: List<String>,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onManageCategories: () -> Unit,
    onManageUnits: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentImagePath by remember { mutableStateOf(initialProduct?.localImagePath) }
    var isImageRemoved by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            isImageRemoved = false
        }
    }

    var nameBn by remember { mutableStateOf(initialProduct?.nameBn ?: "") }
    var nameEn by remember { mutableStateOf(initialProduct?.nameEn ?: "") }
    var selectedCatId by remember {
        mutableStateOf(initialProduct?.categoryId ?: (categories.firstOrNull { it.id != 1L }?.id ?: 2L))
    }
    var unitName by remember {
        mutableStateOf(initialProduct?.unitName ?: (units.firstOrNull() ?: "কেজি"))
    }
    var purchasePrice by remember {
        mutableStateOf(if (initialProduct != null) (initialProduct.purchasePricePoisha / 100.0).toString() else "")
    }
    var salePrice by remember {
        mutableStateOf(if (initialProduct != null) (initialProduct.salePricePoisha / 100.0).toString() else "")
    }
    var stockQty by remember {
        mutableStateOf(initialProduct?.stockQty?.toString() ?: "0")
    }
    var minStock by remember {
        mutableStateOf(initialProduct?.minStock?.toString() ?: "5")
    }
    var barcode by remember {
        mutableStateOf(initialProduct?.barcode ?: "")
    }

    val isValid = nameBn.isNotBlank() && (salePrice.toDoubleOrNull() ?: 0.0) > 0

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
                Text(
                    text = if (initialProduct == null) "নতুন পণ্য যোগ করুন" else "পণ্য সম্পাদনা",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                }
            }

            HorizontalDivider(color = MaterialTheme.dokanColors.border)

            // Scrollable 3 Sections
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // --------------------------------------------------------------
                // TOP SECTION: বারকোড স্ক্যানার (লাইভ ক্যামেরা)
                // --------------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "বারকোড স্ক্যানার ও কোড")

                    CompactCameraBarcodeScanner(
                        currentBarcode = barcode,
                        onBarcodeScanned = { scannedCode ->
                            barcode = scannedCode
                        }
                    )

                    DokanTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = "বারকোড নম্বর",
                        placeholder = "ক্যামেরায় স্ক্যান করুন বা লিখুন",
                        keyboardType = KeyboardType.Text,
                        leadingIcon = Icons.Default.QrCodeScanner,
                        trailingIcon = if (barcode.isNotEmpty()) {
                            {
                                IconButton(onClick = { barcode = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "মুছুন",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null
                    )
                }

                // --------------------------------------------------------------
                // SECTION 1: পণ্যের পরিচয়
                // --------------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "পণ্যের পরিচয়")

                    // Image Picker
                    val hasImage = (selectedImageUri != null) || (!isImageRemoved && !currentImagePath.isNullOrBlank() && File(currentImagePath!!).exists())
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.dokanColors.surfaceAlt,
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (hasImage) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val modelToLoad: Any? = selectedImageUri ?: currentImagePath?.let { File(it) }
                                        AsyncImage(
                                            model = modelToLoad,
                                            contentDescription = "পণ্যের ছবি",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(2.dp)
                                        )

                                        Spacer(modifier = Modifier.width(Spacing.md))

                                        Column {
                                            Text(
                                                text = "পণ্যের ছবি যুক্ত আছে",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "শুধুমাত্র ফোনে থাকবে (অফলাইন)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { photoPickerLauncher.launch("image/*") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "ছবি পরিবর্তন",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedImageUri = null
                                                isImageRemoved = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "ছবি মুছুন",
                                                tint = MaterialTheme.dokanColors.danger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.lg),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Column {
                                            Text(
                                                text = "পণ্যের ছবি যোগ করুন (গ্যালারি)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "ছবিটি শুধুমাত্র আপনার ফোনে থাকবে (অফলাইন)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bengali Name
                    DokanTextField(
                        value = nameBn,
                        onValueChange = { nameBn = it },
                        label = "বাংলা নাম *",
                        placeholder = "যেমন: মিনিকেট চাল"
                    )


                    // Category Selection
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ক্যাটাগরি নির্বাচন:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onManageCategories,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ম্যানেজ/যোগ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(categories.filter { it.id != 1L }) { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = { selectedCatId = cat.id },
                                    label = { Text(cat.nameBn, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(Radius.pill)
                                )
                            }
                        }
                    }

                    // Unit Selection
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "একক নির্বাচন:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onManageUnits,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ম্যানেজ/যোগ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(units) { u ->
                                FilterChip(
                                    selected = unitName == u,
                                    onClick = { unitName = u },
                                    label = { Text(u, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(Radius.pill)
                                )
                            }
                        }
                    }
                }

                // --------------------------------------------------------------
                // SECTION 2: দাম ও স্টক
                // --------------------------------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "দাম ও স্টক")

                    // Prices with ৳ prefix and decimal keyboard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        DokanTextField(
                            value = purchasePrice,
                            onValueChange = { purchasePrice = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "ক্রয় মূল্য (৳)",
                            keyboardType = KeyboardType.Decimal,
                            prefix = {
                                Text(
                                    text = "৳ ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DokanTextField(
                            value = salePrice,
                            onValueChange = { salePrice = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "বিক্রয় মূল্য (৳) *",
                            keyboardType = KeyboardType.Decimal,
                            prefix = {
                                Text(
                                    text = "৳ ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Stock & Min Stock Alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        DokanTextField(
                            value = stockQty,
                            onValueChange = { stockQty = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "বর্তমান স্টক",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )

                        DokanTextField(
                            value = minStock,
                            onValueChange = { minStock = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "সতর্ক স্টক লিমিট",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            }

            // Bottom Action Strip
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
                        text = "সংরক্ষণ করুন",
                        onClick = {
                            if (isValid) {
                                val sPricePoisha = ((salePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                val pPricePoisha = ((purchasePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                val stock = stockQty.toDoubleOrNull() ?: 0.0
                                val minStk = minStock.toDoubleOrNull() ?: 5.0

                                val finalImagePath = when {
                                    selectedImageUri != null -> {
                                        ImageStorageHelper.deleteProductImage(currentImagePath)
                                        ImageStorageHelper.saveProductImage(context, selectedImageUri!!)
                                    }
                                    isImageRemoved -> {
                                        ImageStorageHelper.deleteProductImage(currentImagePath)
                                        null
                                    }
                                    else -> currentImagePath
                                }

                                val updated = (initialProduct ?: Product(nameBn = nameBn)).copy(
                                    nameBn = nameBn.trim(),
                                    nameEn = nameEn.trim(),
                                    categoryId = selectedCatId,
                                    unitName = unitName,
                                    purchasePricePoisha = pPricePoisha,
                                    salePricePoisha = sPricePoisha,
                                    stockQty = stock,
                                    minStock = minStk,
                                    barcode = barcode.trim(),
                                    localImagePath = finalImagePath,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(updated)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// STOCK ADJUSTMENT DIALOG
// ==============================================================================
@Composable
private fun StockAdjustmentDialog(
    product: Product,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String?) -> Unit
) {
    var isAddition by remember { mutableStateOf(true) }
    var qtyChangeText by remember { mutableStateOf("1") }
    var selectedReason by remember { mutableStateOf("স্টক বৃদ্ধি/যোগ") }
    var note by remember { mutableStateOf("") }

    val reasons = if (isAddition) {
        listOf("স্টক বৃদ্ধি/যোগ", "ক্রয় সমন্বয়", "অন্যান্য")
    } else {
        listOf("নষ্ট/ড্যামেজ", "চুরি/হারিয়ে যাওয়া", "গণনা ভুল", "অন্যান্য")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "স্টক সমন্বয়: ${product.nameBn}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "বর্তমান স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Toggle Add vs Deduct
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true; selectedReason = "স্টক বৃদ্ধি/যোগ" },
                        label = { Text("+ যোগ করুন", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(Radius.pill),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false; selectedReason = "নষ্ট/ড্যামেজ" },
                        label = { Text("- কমান", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(Radius.pill),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.dokanColors.dangerContainer,
                            selectedLabelColor = MaterialTheme.dokanColors.danger
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                DokanTextField(
                    value = qtyChangeText,
                    onValueChange = { qtyChangeText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "সমন্বয়ের পরিমাণ (${product.unitName})",
                    keyboardType = KeyboardType.Decimal
                )

                Column {
                    Text(
                        text = "কারণ নির্বাচন:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(reasons) { r ->
                            FilterChip(
                                selected = selectedReason == r,
                                onClick = { selectedReason = r },
                                label = { Text(r, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(Radius.pill)
                            )
                        }
                    }
                }

                DokanTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "মন্তব্য / নোট (ঐচ্ছিক)"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qtyChangeText.toDoubleOrNull() ?: 0.0
                    if (q > 0) {
                        val finalChange = if (isAddition) q else -q
                        onConfirm(finalChange, selectedReason, note.ifBlank { null })
                    }
                },
                enabled = (qtyChangeText.toDoubleOrNull() ?: 0.0) > 0,
                shape = RoundedCornerShape(Radius.md)
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
        shape = RoundedCornerShape(Radius.lg)
    )
}
