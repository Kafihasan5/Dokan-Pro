package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.Category
import com.example.data.entity.Product
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.CategoryUnitManagerDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.Formatters
import com.example.util.ImageStorageHelper
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
    val isSyncing by viewModel.isSyncing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf(1L) }
    var showOnlyLowStock by remember { mutableStateOf(false) }

    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var productForStockAdjust by remember { mutableStateOf<Product?>(null) }
    var showCategoryUnitManager by remember { mutableStateOf(false) }
    var initialManageTab by remember { mutableStateOf(0) }

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
                text = { Text("নতুন পণ্য") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("add_product_fab")
            )
        }
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
                modifier = Modifier.fillMaxSize()
            ) {
            // Header & Search
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "পণ্য ও স্টক তালিকা",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Total Store Stock Value Card (দোকানের মোট পণ্যের মূল্য)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
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
                                    text = "দোকানের মোট স্টক মূল্য (বিক্রয়দর)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatMoney(totalStockSaleValue, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "ক্রয়মূল্য (ইনভেস্ট): ${Formatters.formatMoney(totalStockPurchaseValue, config.useBengaliNumerals, config.currencySymbol)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "আইটেম সংখ্যা",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(products.size.toString()) else products.size} টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val potential = (totalStockSaleValue - totalStockPurchaseValue).coerceAtLeast(0)
                                Text(
                                    text = "সম্ভাব্য লাভ: +${Formatters.formatMoney(potential, config.useBengaliNumerals, config.currencySymbol)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusSuccess
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("পণ্যের নাম বা বারকোড দিয়ে খুঁজুন...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )


                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Low Stock Filter & Category Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOnlyLowStock,
                            onClick = { showOnlyLowStock = !showOnlyLowStock },
                            label = { Text("স্টক কম ⚠️", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StatusWarning.copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFFB45309)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = { selectedCatId = cat.id },
                                    label = { Text(cat.nameBn, fontSize = 12.sp) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        initialManageTab = 0
                                        showCategoryUnitManager = true
                                    },
                                    label = { Text("⚙️ ক্যাটাগরি ও একক", fontSize = 12.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Products Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "মোট পণ্য: ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(filteredProducts.size.toString()) else filteredProducts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Products List
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (products.isEmpty()) "দোকানে কোনো পণ্য নেই (খালি দোকান)" else "অনুসন্ধানে কোনো পণ্য পাওয়া যায়নি",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (products.isEmpty()) "নিচের বাটনে চাপ দিয়ে আপনার আসল পণ্য যোগ করুন।" else "অন্য নাম বা বারকোড দিয়ে চেষ্টা করুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (products.isEmpty()) {
                                Button(
                                    onClick = { isAddingNew = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("প্রথম পণ্য যোগ করুন")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductManagementCard(
                            product = product,
                            config = config,
                            onEdit = { productToEdit = product },
                            onAdjustStock = { productForStockAdjust = product },
                            onDelete = { viewModel.deleteProduct(product.id) }
                        )
                    }
                }
            }
        }
    }
    }


    // Add or Edit Product Dialog
    if (isAddingNew || productToEdit != null) {
        ProductFormDialog(
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

    if (showCategoryUnitManager) {
        CategoryUnitManagerDialog(
            viewModel = viewModel,
            initialTab = initialManageTab,
            onDismiss = { showCategoryUnitManager = false }
        )
    }
}

@Composable
fun ProductManagementCard(
    product: Product,
    config: ShopConfig,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.stockQty <= product.minStock

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductThumbnail(
                    imagePath = product.localImagePath,
                    size = 52.dp,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.nameBn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.nameEn.isNotBlank()) {
                        Text(
                            text = product.nameEn,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (product.barcode.isNotBlank()) {
                        Text(
                            text = "বারকোড: ${product.barcode}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stock Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLowStock) StatusDanger.copy(alpha = 0.12f) else StatusSuccess.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStock) StatusDanger else StatusSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "বিক্রয় মূল্য: ${Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ক্রয় মূল্য: ${Formatters.formatMoney(product.purchasePricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    OutlinedButton(
                        onClick = onAdjustStock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("স্টক সমন্বয়", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    initialProduct: Product?,
    categories: List<Category>,
    units: List<String>,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onManageCategories: () -> Unit,
    onManageUnits: () -> Unit
) {
    val context = LocalContext.current
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
    var selectedCatId by remember { mutableStateOf(initialProduct?.categoryId ?: (categories.firstOrNull { it.id != 1L }?.id ?: 2L)) }
    var unitName by remember { mutableStateOf(initialProduct?.unitName ?: (units.firstOrNull() ?: "কেজি")) }
    var purchasePrice by remember { mutableStateOf(if (initialProduct != null) (initialProduct.purchasePricePoisha / 100.0).toString() else "") }
    var salePrice by remember { mutableStateOf(if (initialProduct != null) (initialProduct.salePricePoisha / 100.0).toString() else "") }
    var stockQty by remember { mutableStateOf(initialProduct?.stockQty?.toString() ?: "0") }
    var minStock by remember { mutableStateOf(initialProduct?.minStock?.toString() ?: "5") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProduct == null) "নতুন পণ্য যোগ করুন" else "পণ্য সম্পাদনা") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // --- PRODUCT IMAGE PICKER ---
                item {
                    val hasImage = (selectedImageUri != null) || (!isImageRemoved && !currentImagePath.isNullOrBlank() && File(currentImagePath!!).exists())

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
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
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = "পণ্যের ছবি যুক্ত আছে",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "শুধুমাত্র ফোনে থাকবে (অফলাইন)",
                                                fontSize = 11.sp,
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
                                                tint = StatusDanger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "পণ্যের ছবি যোগ করুন (গ্যালারি)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "ছবিটি শুধুমাত্র আপনার ফোনে থাকবে (অফলাইন)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = { nameBn = it },
                        label = { Text("বাংলা নাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text("ইংরেজি নাম (ঐচ্ছিক)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ক্যাটাগরি নির্বাচন:", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = onManageCategories,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("ম্যানেজ/যোগ", fontSize = 11.sp)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories.filter { it.id != 1L }) { cat ->
                            FilterChip(
                                selected = selectedCatId == cat.id,
                                onClick = { selectedCatId = cat.id },
                                label = { Text(cat.nameBn, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("একক নির্বাচন:", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = onManageUnits,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("ম্যানেজ/যোগ", fontSize = 11.sp)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(units) { u ->
                            FilterChip(
                                selected = unitName == u,
                                onClick = { unitName = u },
                                label = { Text(u, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = purchasePrice,
                            onValueChange = { purchasePrice = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("ক্রয় মূল্য (৳)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = salePrice,
                            onValueChange = { salePrice = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("বিক্রয় মূল্য (৳) *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockQty,
                            onValueChange = { stockQty = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("বর্তমান স্টক") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = minStock,
                            onValueChange = { minStock = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("সতর্ক স্টক লিমিট") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("বারকোড / কোড (ঐচ্ছিক)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameBn.isNotBlank()) {
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
                enabled = nameBn.isNotBlank() && (salePrice.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun StockAdjustmentDialog(
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
        title = { Text("স্টক সমন্বয়: ${product.nameBn}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "বর্তমান স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                    fontWeight = FontWeight.Bold
                )

                // Toggle Add vs Deduct
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true; selectedReason = "স্টক বৃদ্ধি/যোগ" },
                        label = { Text("+ যোগ করুন") }
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false; selectedReason = "নষ্ট/ড্যামেজ" },
                        label = { Text("- কমান") }
                    )
                }

                OutlinedTextField(
                    value = qtyChangeText,
                    onValueChange = { qtyChangeText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("সমন্বয়ের পরিমাণ (${product.unitName})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("কারণ নির্বাচন:", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reasons) { r ->
                        FilterChip(
                            selected = selectedReason == r,
                            onClick = { selectedReason = r },
                            label = { Text(r, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("মন্তব্য / নোট (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                enabled = (qtyChangeText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
