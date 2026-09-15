package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Product
import com.example.data.entity.Sale
import com.example.data.entity.SaleItem
import com.example.ui.AppScreen
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.AnimatedAmount
import com.example.ui.components.DokanConfirmDialog
import com.example.ui.components.DokanSkeletonList
import com.example.ui.components.DokanTextField
import com.example.ui.components.EmptyState
import com.example.ui.components.ProductReturnDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatTone
import com.example.ui.components.TopHeader
import com.example.ui.theme.Brand500
import com.example.ui.theme.Brand700
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.amountTextStyle
import com.example.ui.theme.dokanColors
import com.example.ui.theme.softShadow
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val sales by viewModel.sales.collectAsState()
    val saleItems by viewModel.allSaleItems.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val totalDue by viewModel.totalDue.collectAsState()
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val products by viewModel.products.collectAsState()
    val totalStockSaleValue by viewModel.totalStockSaleValuePoisha.collectAsState()
    val totalStockPurchaseValue by viewModel.totalStockPurchaseValuePoisha.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val context = LocalContext.current

    // Date calculations for Today & Yesterday
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = calendar.timeInMillis
    val endOfToday = startOfToday + 86400000L
    val startOfYesterday = startOfToday - 86400000L

    var dashboardFilterMode by remember { mutableStateOf("today") }
    var customSelectedDate by remember { mutableStateOf<Long?>(null) }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customSelectedDate = cal.timeInMillis
                dashboardFilterMode = "custom"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val todaySalesCount = remember(sales) { sales.count { it.saleDate in startOfToday..endOfToday && !it.isReturned } }
    val yesterdaySalesCount = remember(sales) { sales.count { it.saleDate in startOfYesterday until startOfToday && !it.isReturned } }

    val (periodSales, periodExpenses, periodLabel) = remember(sales, expenses, dashboardFilterMode, customSelectedDate) {
        when (dashboardFilterMode) {
            "today" -> {
                val s = sales.filter { it.saleDate in startOfToday..endOfToday && !it.isReturned }
                val e = expenses.filter { it.expenseDate in startOfToday..endOfToday }
                Triple(s, e, "আজকের")
            }
            "yesterday" -> {
                val s = sales.filter { it.saleDate in startOfYesterday until startOfToday && !it.isReturned }
                val e = expenses.filter { it.expenseDate in startOfYesterday until startOfToday }
                Triple(s, e, "গতকালের")
            }
            "custom" -> {
                val start = customSelectedDate ?: startOfToday
                val end = start + 86400000L
                val s = sales.filter { it.saleDate in start until end && !it.isReturned }
                val e = expenses.filter { it.expenseDate in start until end }
                val dateStr = SimpleDateFormat("dd MMM", Locale("bn", "BD")).format(Date(start))
                Triple(s, e, dateStr)
            }
            else -> {
                val s = sales.filter { !it.isReturned }
                val e = expenses
                Triple(s, e, "সর্বমোট")
            }
        }
    }

    val periodSalesTotalPoisha = periodSales.sumOf { it.totalPoisha }
    val periodSaleIds = periodSales.map { it.id }.toSet()
    val periodSoldItems = saleItems.filter { it.saleId in periodSaleIds }
    val periodCostPoisha = periodSoldItems.sumOf { (it.qty * it.purchasePriceAtSalePoisha).toLong() }
    val periodGrossProfitPoisha = (periodSalesTotalPoisha - periodCostPoisha).coerceAtLeast(0)
    val periodExpenseTotalPoisha = periodExpenses.sumOf { it.amountPoisha }
    val periodNetProfitPoisha = periodGrossProfitPoisha - periodExpenseTotalPoisha

    var showExpenseDialog by remember { mutableStateOf(false) }
    var salesSearchQuery by remember { mutableStateOf("") }
    var currentSalesPage by remember { mutableIntStateOf(1) }
    val salesPageSize = 10

    val sortedSales = remember(sales, dashboardFilterMode, customSelectedDate, salesSearchQuery) {
        val base = when (dashboardFilterMode) {
            "today" -> sales.filter { it.saleDate in startOfToday..endOfToday }
            "yesterday" -> sales.filter { it.saleDate in startOfYesterday until startOfToday }
            "custom" -> {
                val start = customSelectedDate ?: startOfToday
                sales.filter { it.saleDate in start until (start + 86400000L) }
            }
            else -> sales
        }
        val query = salesSearchQuery.trim().lowercase()
        val filtered = if (query.isBlank()) {
            base
        } else {
            base.filter {
                it.invoiceNo.lowercase().contains(query) ||
                (it.customerName?.lowercase()?.contains(query) == true)
            }
        }
        filtered.sortedByDescending { it.saleDate }
    }

    val totalSalesPages = ((sortedSales.size + salesPageSize - 1) / salesPageSize).coerceAtLeast(1)
    val displayedPage = currentSalesPage.coerceIn(1, totalSalesPages)
    val paginatedSales = sortedSales.drop((displayedPage - 1) * salesPageSize).take(salesPageSize)

    var isManualRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    PullToRefreshBox(
        isRefreshing = isManualRefreshing,
        onRefresh = {
            isManualRefreshing = true
            coroutineScope.launch {
                viewModel.syncToSupabase(silent = true)
                delay(600)
                isManualRefreshing = false
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            TopHeader(
                config = config,
                onOpenMoreMenu = onNavigate,
                isSyncing = isSyncing,
                onSyncNow = { viewModel.syncToSupabase(silent = false) },
                onToggleTheme = { viewModel.toggleThemeMode() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Spacer(modifier = Modifier.height(Spacing.xs))

                // 1) HERO SUMMARY
                DashboardHeroSummary(
                    periodLabel = periodLabel,
                    salesTotalPoisha = periodSalesTotalPoisha,
                    grossProfitPoisha = periodGrossProfitPoisha,
                    netProfitPoisha = periodNetProfitPoisha,
                    salesCount = periodSales.size,
                    todaySalesCount = todaySalesCount,
                    yesterdaySalesCount = yesterdaySalesCount,
                    totalSalesCount = sales.size,
                    dashboardFilterMode = dashboardFilterMode,
                    customSelectedDate = customSelectedDate,
                    config = config,
                    onSelectFilter = { mode ->
                        dashboardFilterMode = mode
                        currentSalesPage = 1
                    },
                    onOpenDatePicker = { datePickerDialog.show() }
                )

                // 2) SECONDARY STATS (2-Column Grid)
                DashboardSecondaryStats(
                    periodExpenseTotalPoisha = periodExpenseTotalPoisha,
                    periodExpensesCount = periodExpenses.size,
                    totalDue = totalDue,
                    totalStockSaleValue = totalStockSaleValue,
                    totalStockPurchaseValue = totalStockPurchaseValue,
                    productsCount = products.size,
                    config = config,
                    onNavigateToDue = { onNavigate(AppScreen.DUE_KHATA) },
                    onNavigateToProducts = { onNavigate(AppScreen.PRODUCTS) }
                )

                // 3) QUICK ACTIONS (4 Square Tiles)
                DashboardQuickActions(
                    onNavigate = onNavigate,
                    onAddExpense = { showExpenseDialog = true }
                )

                // 4) WEEKLY CHART
                DashboardWeeklyChart(sales = sales, config = config, onNavigateToReports = { onNavigate(AppScreen.REPORTS) })

                // 5) LOW STOCK SECTION
                DashboardLowStockSection(
                    lowStockProducts = lowStockProducts,
                    config = config,
                    onNavigateToProducts = { onNavigate(AppScreen.PRODUCTS) },
                    onNavigateToPurchases = { onNavigate(AppScreen.PURCHASES) }
                )

                // 6) SALES HISTORY SECTION
                DashboardSalesHistorySection(
                    sales = paginatedSales,
                    saleItems = saleItems,
                    totalSalesCount = sortedSales.size,
                    displayedPage = displayedPage,
                    totalSalesPages = totalSalesPages,
                    searchQuery = salesSearchQuery,
                    onSearchQueryChange = {
                        salesSearchQuery = it
                        currentSalesPage = 1
                    },
                    onPageSelected = { currentSalesPage = it },
                    config = config,
                    isSyncing = isSyncing,
                    onViewReceipt = { sale -> viewModel.viewSaleReceipt(sale) },
                    onDeleteSale = { id -> viewModel.deleteSale(id) },
                    onReturnSale = { id, returnedMap -> viewModel.returnSaleItems(id, returnedMap) }
                )

                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }

    if (showExpenseDialog) {
        AddExpenseDialog(
            categories = viewModel.expenseCategories.collectAsState().value,
            onDismiss = { showExpenseDialog = false },
            onConfirm = { catId, catName, amount, note ->
                viewModel.addExpense(catId, catName, amount, note) {
                    showExpenseDialog = false
                }
            }
        )
    }
}

// ==============================================================================
// 1. HERO SUMMARY CARD
// ==============================================================================
@Composable
private fun DashboardHeroSummary(
    periodLabel: String,
    salesTotalPoisha: Long,
    grossProfitPoisha: Long,
    netProfitPoisha: Long,
    salesCount: Int,
    todaySalesCount: Int,
    yesterdaySalesCount: Int,
    totalSalesCount: Int,
    dashboardFilterMode: String,
    customSelectedDate: Long?,
    config: ShopConfig,
    onSelectFilter: (String) -> Unit,
    onOpenDatePicker: () -> Unit
) {
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    val heroGradient = Brush.linearGradient(
        colors = listOf(Brand700, Brand500),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(2, RoundedCornerShape(Radius.lg)),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient)
                .padding(Spacing.lg)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Period Label + Filter Dropdown Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$periodLabel হিসাব",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    // Filter Chip Button
                    Box {
                        Surface(
                            onClick = { isFilterMenuExpanded = true },
                            shape = RoundedCornerShape(Radius.pill),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (dashboardFilterMode) {
                                        "today" -> "আজ (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(todaySalesCount.toString()) else todaySalesCount})"
                                        "yesterday" -> "গতকাল (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(yesterdaySalesCount.toString()) else yesterdaySalesCount})"
                                        "custom" -> if (customSelectedDate != null) {
                                            SimpleDateFormat("dd MMM", Locale("bn", "BD")).format(Date(customSelectedDate))
                                        } else "তারিখ"
                                        else -> "সব (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(totalSalesCount.toString()) else totalSalesCount})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "ফিল্টার ড্রপডাউন",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isFilterMenuExpanded,
                            onDismissRequest = { isFilterMenuExpanded = false },
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("আজকের হিসাব", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.Today, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = if (dashboardFilterMode == "today") {
                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    onSelectFilter("today")
                                    isFilterMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("গতকালের হিসাব", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = if (dashboardFilterMode == "yesterday") {
                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    onSelectFilter("yesterday")
                                    isFilterMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("নির্দিষ্ট তারিখ", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = if (dashboardFilterMode == "custom") {
                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    isFilterMenuExpanded = false
                                    onOpenDatePicker()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("সর্বমোট (সব সময়)", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.AllInclusive, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = if (dashboardFilterMode == "all") {
                                    { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    onSelectFilter("all")
                                    isFilterMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Sales Amount Figure at 34sp
                AnimatedAmount(
                    value = salesTotalPoisha,
                    style = amountTextStyle(34.sp),
                    color = Color.White,
                    useBengaliNumerals = config.useBengaliNumerals,
                    currencySymbol = config.currencySymbol
                )

                Text(
                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(salesCount.toString()) else salesCount} টি বিক্রয় সম্পন্ন",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.80f)
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Dedicated "পণ্যের লাভ" (Profit from Products) Card
                val isGrossProfit = grossProfitPoisha >= 0
                val netPrefix = if (netProfitPoisha < 0) "-" else ""
                val netFormatted = Formatters.formatMoney(kotlin.math.abs(netProfitPoisha), config.useBengaliNumerals, config.currencySymbol)

                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = 10.dp)
                    ) {
                        // Main Line: পণ্যের লাভ: ৳ ২,৫০০
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGrossProfit) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "পণ্যের লাভ:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = Formatters.formatMoney(grossProfitPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Sub Line: (দোকান খরচ বাদে চূড়ান্ত উদ্বৃত্ত: ৳ ১,৮০০)
                        val netColor = if (netProfitPoisha >= 0) Color.White.copy(alpha = 0.88f) else Color(0xFFFFCDD2)
                        Text(
                            text = "(দোকান খরচ বাদে চূড়ান্ত উদ্বৃত্ত: $netPrefix$netFormatted)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = netColor,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 2. SECONDARY STATS (2-COLUMN GRID)
// ==============================================================================
@Composable
private fun DashboardSecondaryStats(
    periodExpenseTotalPoisha: Long,
    periodExpensesCount: Int,
    totalDue: Long,
    totalStockSaleValue: Long,
    totalStockPurchaseValue: Long,
    productsCount: Int,
    config: ShopConfig,
    onNavigateToDue: () -> Unit,
    onNavigateToProducts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Card 1: খরচ
            StatCard(
                label = "খরচ",
                value = Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                tone = StatTone.Neutral,
                caption = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodExpensesCount.toString()) else periodExpensesCount} টি এন্ট্রি",
                icon = Icons.Default.ReceiptLong,
                modifier = Modifier.weight(1f)
            )

            // Card 2: মোট বাকি
            StatCard(
                label = "মোট বাকি",
                value = Formatters.formatMoney(totalDue, config.useBengaliNumerals, config.currencySymbol),
                tone = StatTone.Negative,
                caption = "খাতায় পাওনা",
                icon = Icons.Default.AccountBalanceWallet,
                onClick = onNavigateToDue,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Card 3: দোকানের মোট স্টক মূল্য
            StatCard(
                label = "দোকানের মোট স্টক মূল্য",
                value = Formatters.formatMoney(totalStockSaleValue, config.useBengaliNumerals, config.currencySymbol),
                tone = StatTone.Gold,
                caption = "ক্রয়: ${Formatters.formatMoney(totalStockPurchaseValue, config.useBengaliNumerals, config.currencySymbol)}",
                icon = Icons.Default.ShoppingCart,
                onClick = onNavigateToProducts,
                modifier = Modifier.weight(1f)
            )

            // Card 4: আইটেম সংখ্যা
            StatCard(
                label = "আইটেম সংখ্যা",
                value = if (config.useBengaliNumerals) Formatters.toBengaliDigits(productsCount.toString()) else productsCount.toString(),
                tone = StatTone.Neutral,
                caption = "স্টকে মোট আইটেম",
                icon = Icons.Default.AddBox,
                onClick = onNavigateToProducts,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==============================================================================
// 3. QUICK ACTIONS ROW (4 SQUARE TILES: 96x96dp)
// ==============================================================================
@Composable
private fun DashboardQuickActions(
    onNavigate: (AppScreen) -> Unit,
    onAddExpense: () -> Unit
) {
    Column {
        SectionHeader(title = "কুইক অ্যাকশন")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            QuickActionTile(
                title = "নতুন বিক্রয়",
                icon = Icons.Default.ShoppingCart,
                color = MaterialTheme.colorScheme.primary,
                onClick = { onNavigate(AppScreen.POS) }
            )
            QuickActionTile(
                title = "বাকি খাতা",
                icon = Icons.Default.AccountBalanceWallet,
                color = MaterialTheme.dokanColors.danger,
                onClick = { onNavigate(AppScreen.DUE_KHATA) }
            )
            QuickActionTile(
                title = "পণ্য যোগ",
                icon = Icons.Default.AddBox,
                color = MaterialTheme.dokanColors.info,
                onClick = { onNavigate(AppScreen.PRODUCTS) }
            )
            QuickActionTile(
                title = "খরচ যোগ",
                icon = Icons.Default.NoteAdd,
                color = MaterialTheme.dokanColors.warning,
                onClick = onAddExpense
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(96.dp)
            .softShadow(1, RoundedCornerShape(Radius.md))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.md)),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.dokanColors.surfaceAlt
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

// ==============================================================================
// 4. WEEKLY SALES CHART WITH CANVAS ANIMATION
// ==============================================================================
@Composable
private fun DashboardWeeklyChart(
    sales: List<Sale>,
    config: ShopConfig,
    onNavigateToReports: () -> Unit
) {
    val days = listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র")
    val dailyTotals = remember(sales) {
        val totals = LongArray(7) { 0L }
        val now = System.currentTimeMillis()
        for (i in 0..6) {
            val start = now - (6 - i) * 86400000L
            val end = start + 86400000L
            totals[i] = sales.filter { it.saleDate in start..end && !it.isReturned }.sumOf { it.totalPoisha }
        }
        totals
    }

    val maxTotal = dailyTotals.maxOrNull()?.coerceAtLeast(100000L) ?: 100000L
    val tallestIndex = dailyTotals.indexOfFirst { it == dailyTotals.maxOrNull() }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.md)),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            SectionHeader(
                title = "গত ৭ দিনের বিক্রয় চিত্র",
                actionLabel = "সাপ্তাহিক রিপোর্ট",
                onAction = onNavigateToReports
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            val primaryColor = MaterialTheme.colorScheme.primary
            val dimColor = primaryColor.copy(alpha = 0.22f)
            val outlineColor = MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height - 24.dp.toPx()
                    val barWidth = 24.dp.toPx()
                    val totalSlots = 7
                    val spacing = (width - (barWidth * totalSlots)) / (totalSlots + 1)

                    // Faint dashed baseline
                    drawLine(
                        color = outlineColor.copy(alpha = 0.6f),
                        start = Offset(0f, height),
                        end = Offset(width, height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    for (i in 0..6) {
                        val total = dailyTotals[i]
                        val fraction = ((total.toFloat() / maxTotal.toFloat()) * animProgress.value).coerceIn(0.06f, 1f)
                        val barHeight = height * fraction
                        val x = spacing + i * (barWidth + spacing)
                        val y = height - barHeight

                        val barColor = if (i == 6) primaryColor else dimColor

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                // Row for Day Labels & Tallest Bar Value Label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 0..6) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = days[i],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Highest bar value badge if non-zero
                if (tallestIndex >= 0 && dailyTotals[tallestIndex] > 0) {
                    val k = dailyTotals[tallestIndex] / 100000L
                    val badgeText = if (config.useBengaliNumerals) Formatters.toBengaliDigits("${k}k") else "${k}k"
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 5. LOW STOCK SECTION
// ==============================================================================
@Composable
private fun DashboardLowStockSection(
    lowStockProducts: List<Product>,
    config: ShopConfig,
    onNavigateToProducts: () -> Unit,
    onNavigateToPurchases: () -> Unit
) {
    Column {
        SectionHeader(
            title = "স্টক সতর্কবার্তা (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(lowStockProducts.size.toString()) else lowStockProducts.size})",
            actionLabel = if (lowStockProducts.isNotEmpty()) "সব দেখুন" else null,
            onAction = onNavigateToProducts
        )

        if (lowStockProducts.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "সব পণ্যের স্টক পর্যাপ্ত রয়েছে!",
                message = "কোনো পণ্যের স্টক সতর্কসীমার নিচে নেই।"
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                lowStockProducts.take(4).forEach { prod ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softShadow(1, RoundedCornerShape(Radius.sm)),
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ProductThumbnail(
                                    imagePath = prod.localImagePath,
                                    size = 40.dp,
                                    shape = RoundedCornerShape(Radius.xs)
                                )
                                Spacer(modifier = Modifier.width(Spacing.md))
                                Column {
                                    Text(
                                        text = prod.nameBn,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "বিক্রয়: ${Formatters.formatMoney(prod.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${prod.unitName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(MaterialTheme.dokanColors.warningContainer.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "স্টক: ${Formatters.formatQty(prod.stockQty, prod.unitName, config.useBengaliNumerals)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.dokanColors.warning
                                    )
                                }

                                Spacer(modifier = Modifier.width(Spacing.xs))

                                IconButton(
                                    onClick = onNavigateToPurchases,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "মাল ক্রয়",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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

// ==============================================================================
// 6. SALES HISTORY SECTION WITH RESTYLED ROWS
// ==============================================================================
@Composable
private fun DashboardSalesHistorySection(
    sales: List<Sale>,
    saleItems: List<SaleItem>,
    totalSalesCount: Int,
    displayedPage: Int,
    totalSalesPages: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPageSelected: (Int) -> Unit,
    config: ShopConfig,
    isSyncing: Boolean = false,
    onViewReceipt: (Sale) -> Unit,
    onDeleteSale: (Long) -> Unit,
    onReturnSale: (Long, Map<Long, Double>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "বিক্রয় হিস্ট্রি ও ইনভয়েস")

        // Search Field
        DokanTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = "ইনভয়েস বা কাস্টমার খুঁজুন",
            placeholder = "ইনভয়েস নং বা কাস্টমার খুঁজুন...",
            leadingIcon = Icons.Default.Search,
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "পরিষ্কার", modifier = Modifier.size(18.dp))
                    }
                }
            } else null
        )

        if (sales.isEmpty()) {
            if (isSyncing && searchQuery.isBlank()) {
                DokanSkeletonList(itemCount = 4)
            } else {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = if (searchQuery.isNotBlank()) "খোঁজার সাথে কোনো বিক্রয় মেলেনি" else "এখনো কোনো বিক্রয় রেকর্ড নেই",
                    message = "নতুন বিক্রয় সম্পন্ন হলে এখানে তালিকা দেখা যাবে।"
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                sales.forEach { sale ->
                    val itemsForSale = remember(sale.id, saleItems) {
                        saleItems.filter { it.saleId == sale.id }
                    }
                    SaleRowCard(
                        sale = sale,
                        items = itemsForSale,
                        config = config,
                        onViewReceipt = { onViewReceipt(sale) },
                        onDeleteSale = { onDeleteSale(sale.id) },
                        onReturnSale = { map -> onReturnSale(sale.id, map) }
                    )
                }

                // Pagination
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "পৃষ্ঠা ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(displayedPage.toString()) else displayedPage} এর ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(totalSalesPages.toString()) else totalSalesPages} (মোট ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(totalSalesCount.toString()) else totalSalesCount} টি ইনভয়েস)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    PaginationBar(
                        currentPage = displayedPage,
                        totalPages = totalSalesPages,
                        useBengali = config.useBengaliNumerals,
                        onPageSelected = onPageSelected
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 7. RESTYLED SALE ROW CARD
// ==============================================================================
@Composable
private fun SaleRowCard(
    sale: Sale,
    items: List<SaleItem>,
    config: ShopConfig,
    onViewReceipt: () -> Unit,
    onDeleteSale: () -> Unit,
    onReturnSale: (Map<Long, Double>) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.sm)),
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Line 1: Invoice No + Time on top in labelSmall, Payment Tone Chip on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sale.invoiceNo} • ${Formatters.formatDateTime(sale.saleDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (sale.isReturned) {
                    Surface(
                        color = MaterialTheme.dokanColors.dangerContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(Radius.pill)
                    ) {
                        Text(
                            text = "❌ ফেরতকৃত",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.dokanColors.danger,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    val (chipColor, textColor, label) = when (sale.paymentMethod) {
                        "due" -> Triple(MaterialTheme.dokanColors.dangerContainer.copy(alpha = 0.2f), MaterialTheme.dokanColors.danger, "বাকি")
                        "bkash" -> Triple(Color(0xFFE2136E).copy(alpha = 0.15f), Color(0xFFE2136E), "বিকাশ")
                        "nagad" -> Triple(Color(0xFFF7941D).copy(alpha = 0.15f), Color(0xFFF7941D), "নগদ")
                        "bank" -> Triple(MaterialTheme.dokanColors.infoContainer.copy(alpha = 0.2f), MaterialTheme.dokanColors.info, "ব্যাংক")
                        else -> Triple(MaterialTheme.dokanColors.successContainer.copy(alpha = 0.2f), MaterialTheme.dokanColors.success, "নগদ")
                    }
                    Surface(color = chipColor, shape = RoundedCornerShape(Radius.pill)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Line 2: Customer Name in titleMedium, Total in amountTextStyle(18.sp) on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sale.customerName ?: "সাধারণ খরিদ্দার",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                    style = amountTextStyle(18.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (sale.dueAmountPoisha > 0) {
                Text(
                    text = "বাকি: ${Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.dokanColors.danger
                )
            }

            // Line 3: Items breakdown with expand/collapse
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Surface(
                    color = MaterialTheme.dokanColors.surfaceAlt,
                    shape = RoundedCornerShape(Radius.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        val displayItems = if (expanded) items else items.take(2)
                        displayItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${item.productName} (${Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (items.size > 2) {
                            Text(
                                text = if (expanded) "সংক্ষেপ করুন ▲" else "+ আরও ${items.size - 2} টি পণ্য দেখুন ▼",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { expanded = !expanded }
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Action Buttons Row (WhatsApp, Return, Receipt, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val bmp = InvoiceImageHelper.generateSaleInvoiceBitmap(context, config, sale, items)
                        val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${sale.invoiceNo}")
                        val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, sale)
                        InvoiceImageHelper.shareToWhatsApp(context, uri, sale.customerName, caption)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "WhatsApp ইনভয়েস ছবি",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.xs))

                if (!sale.isReturned) {
                    OutlinedButton(
                        onClick = { showReturnDialog = true },
                        shape = RoundedCornerShape(Radius.xs),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.dokanColors.warning),
                        border = BorderStroke(1.dp, MaterialTheme.dokanColors.warning.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.AssignmentReturn, contentDescription = "পণ্য ফেরত", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ফেরত", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(Spacing.xs))
                }

                OutlinedButton(
                    onClick = onViewReceipt,
                    shape = RoundedCornerShape(Radius.xs),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = "রসিদ", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রসিদ", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(Spacing.xs))

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "মুছুন",
                        tint = MaterialTheme.dokanColors.danger.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DokanConfirmDialog(
            title = "বিক্রয় রেকর্ড মুছুন",
            message = "ইনভয়েস নং ${sale.invoiceNo}-এর রেকর্ডটি মুছে ফেলতে চান?",
            confirmLabel = "মুছুন",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteSale()
            },
            onDismiss = { showDeleteConfirm = false },
            isDestructive = true
        )
    }

    if (showReturnDialog) {
        ProductReturnDialog(
            sale = sale,
            items = items,
            config = config,
            onDismiss = { showReturnDialog = false },
            onConfirmReturn = { returnedMap ->
                showReturnDialog = false
                onReturnSale(returnedMap)
            }
        )
    }
}

// ==============================================================================
// 8. ADD EXPENSE DIALOG
// ==============================================================================
@Composable
fun AddExpenseDialog(
    categories: List<com.example.data.entity.ExpenseCategory>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Long, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.lg),
        title = {
            Text(
                text = "নতুন খরচ এন্ট্রি",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                DokanTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "টাকার পরিমাণ (৳)",
                    singleLine = true
                )

                Text("ক্যাটাগরি নির্বাচন করুন:", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.nameBn) },
                            shape = RoundedCornerShape(Radius.pill)
                        )
                    }
                }

                DokanTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = "বিবরণ / নোট (ঐচ্ছিক)",
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amtDouble = amountText.toDoubleOrNull() ?: 0.0
                    val poisha = (amtDouble * 100).toLong()
                    if (poisha > 0 && selectedCategory != null) {
                        onConfirm(selectedCategory!!.id, selectedCategory!!.nameBn, poisha, noteText.ifBlank { null })
                    }
                },
                shape = RoundedCornerShape(Radius.sm),
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("সংরক্ষণ", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ==============================================================================
// 9. PAGINATION BAR
// ==============================================================================
@Composable
fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    useBengali: Boolean,
    onPageSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = { if (currentPage > 1) onPageSelected(currentPage - 1) },
            enabled = currentPage > 1,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "আগের পৃষ্ঠা", modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        val startPage = (currentPage - 2).coerceAtLeast(1)
        val endPage = (startPage + 4).coerceAtMost(totalPages)

        for (p in startPage..endPage) {
            val isSelected = p == currentPage
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.dokanColors.surfaceAlt
                    )
                    .clickable { onPageSelected(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (useBengali) Formatters.toBengaliDigits(p.toString()) else p.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        FilledTonalIconButton(
            onClick = { if (currentPage < totalPages) onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "পরের পৃষ্ঠা", modifier = Modifier.size(20.dp))
        }
    }
}
