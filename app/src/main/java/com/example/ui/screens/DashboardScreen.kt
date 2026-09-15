package com.example.ui.screens

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
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.Product
import com.example.data.entity.Sale
import com.example.ui.AppScreen
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.TopHeader
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper
import java.util.Calendar

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
    val customers by viewModel.customers.collectAsState()
    val products by viewModel.products.collectAsState()
    val totalStockSaleValue by viewModel.totalStockSaleValuePoisha.collectAsState()
    val totalStockPurchaseValue by viewModel.totalStockPurchaseValuePoisha.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val context = LocalContext.current

    // Compute today's start and end timestamps
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = calendar.timeInMillis
    val endOfToday = startOfToday + 86400000L
    val startOfYesterday = startOfToday - 86400000L

    // Dashboard Time Filter: "today", "yesterday", "custom", "all"
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

    // Filtered period sales & expenses based on selected filter
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
    val allSalesTotalPoisha = sales.filter { !it.isReturned }.sumOf { it.totalPoisha }

    val periodSaleIds = periodSales.map { it.id }.toSet()
    val periodSoldItems = saleItems.filter { it.saleId in periodSaleIds }
    val periodCostPoisha = periodSoldItems.sumOf { (it.qty * it.purchasePriceAtSalePoisha).toLong() }
    val periodGrossProfitPoisha = (periodSalesTotalPoisha - periodCostPoisha).coerceAtLeast(0)

    val periodExpenseTotalPoisha = periodExpenses.sumOf { it.amountPoisha }
    val periodNetProfitPoisha = periodGrossProfitPoisha - periodExpenseTotalPoisha

    // Quick add expense dialog state
    var showExpenseDialog by remember { mutableStateOf(false) }

    // Sales Pagination State
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                TopHeader(
                    config = config,
                    onOpenMoreMenu = onNavigate,
                    isSyncing = isSyncing,
                    onSyncNow = { viewModel.syncToSupabase(silent = false) },
                    onToggleTheme = { viewModel.toggleThemeMode() }
                )
            }

            // --- DASHBOARD TIME FILTER BAR (আজ / কাল / নির্দিষ্ট তারিখ / সব) ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "সময়কাল ফিল্টার:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (dashboardFilterMode == "custom" && customSelectedDate != null) {
                                val fullDateStr = SimpleDateFormat("dd MMMM, yyyy", Locale("bn", "BD")).format(Date(customSelectedDate!!))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = fullDateStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = dashboardFilterMode == "today",
                                onClick = {
                                    dashboardFilterMode = "today"
                                    currentSalesPage = 1
                                },
                                label = { Text("আজ (${todaySalesCount})", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = dashboardFilterMode == "yesterday",
                                onClick = {
                                    dashboardFilterMode = "yesterday"
                                    currentSalesPage = 1
                                },
                                label = { Text("গতকাল (${yesterdaySalesCount})", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = dashboardFilterMode == "custom",
                                onClick = {
                                    datePickerDialog.show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "তারিখ বাছুন",
                                        modifier = Modifier.size(13.dp)
                                    )
                                },
                                label = {
                                    val chipLabel = if (dashboardFilterMode == "custom" && customSelectedDate != null) {
                                        SimpleDateFormat("dd MMM", Locale("bn", "BD")).format(Date(customSelectedDate!!))
                                    } else {
                                        "তারিখ"
                                    }
                                    Text(chipLabel, fontSize = 11.sp)
                                },
                                modifier = Modifier.weight(1.15f)
                            )

                            FilterChip(
                                selected = dashboardFilterMode == "all",
                                onClick = {
                                    dashboardFilterMode = "all"
                                    currentSalesPage = 1
                                },
                                label = { Text("সব (${sales.size})", fontSize = 11.sp) },
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }

        // Summary Cards Grid (2x2)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = "$periodLabel বিক্রয়",
                        amount = Formatters.formatMoney(periodSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        subtitle = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodSales.size.toString()) else periodSales.size} টি • সর্বমোট: ${Formatters.formatMoney(allSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                        icon = Icons.Default.PointOfSale,
                        iconColor = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    )

                    SummaryCard(
                        title = "$periodLabel নিট লাভ",
                        amount = Formatters.formatMoney(periodNetProfitPoisha, config.useBengaliNumerals, config.currencySymbol),
                        subtitle = if (periodNetProfitPoisha >= 0) "মোট লাভ" else "ক্ষতি",
                        icon = Icons.Default.TrendingUp,
                        iconColor = if (periodNetProfitPoisha >= 0) StatusSuccess else StatusDanger,
                        bgColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        title = "$periodLabel খরচ",
                        amount = Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        subtitle = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodExpenses.size.toString()) else periodExpenses.size} টি এন্ট্রি",
                        icon = Icons.Default.ReceiptLong,
                        iconColor = StatusWarning,
                        bgColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    )

                    SummaryCard(
                        title = "মোট বাকি",
                        amount = Formatters.formatMoney(totalDue, config.useBengaliNumerals, config.currencySymbol),
                        subtitle = "খাতায় পাওনা",
                        icon = Icons.Default.MenuBook,
                        iconColor = StatusDanger,
                        bgColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Total Store Stock Value Card (দোকানের মোট পণ্যের মূল্য / ইনভেস্টমেন্ট)
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onNavigate(AppScreen.PRODUCTS) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "দোকানের মোট স্টক মূল্য",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(products.size.toString()) else products.size} টি পণ্য স্টকে বিদ্যমান",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "বিক্রয়মূল্য অনুসারে:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatMoney(totalStockSaleValue, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ক্রয়মূল্য (ইনভেস্ট):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatMoney(totalStockPurchaseValue, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val potentialProfit = (totalStockSaleValue - totalStockPurchaseValue).coerceAtLeast(0)
                        if (potentialProfit > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "স্টক থেকে সম্ভাব্য মোট লাভ: +${Formatters.formatMoney(potentialProfit, config.useBengaliNumerals, config.currencySymbol)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusSuccess
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "পণ্য তালিকায় যান",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }


        // Quick Actions Row
        item {
            Text(
                text = "কুইক অ্যাকশন",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardActionBtn(
                    title = "নতুন বিক্রয়",
                    icon = Icons.Default.ShoppingCart,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate(AppScreen.POS) }
                )
                DashboardActionBtn(
                    title = "পণ্য যোগ",
                    icon = Icons.Default.AddBox,
                    color = Color(0xFF0284C7),
                    onClick = { onNavigate(AppScreen.PRODUCTS) }
                )
                DashboardActionBtn(
                    title = "বাকি খাতা",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = StatusSuccess,
                    onClick = { onNavigate(AppScreen.DUE_KHATA) }
                )
                DashboardActionBtn(
                    title = "খরচ যোগ",
                    icon = Icons.Default.NoteAdd,
                    color = StatusWarning,
                    onClick = { showExpenseDialog = true }
                )
            }
        }

        // 7-Day Sales Mini-Bar Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "গত ৭ দিনের বিক্রয় চিত্র",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সাপ্তাহিক রিপোর্ট",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onNavigate(AppScreen.REPORTS) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    WeeklySalesChart(sales = sales, config = config)
                }
            }
        }

        // Low Stock Alert Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "স্টক সতর্কবার্তা (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(lowStockProducts.size.toString()) else lowStockProducts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (lowStockProducts.isNotEmpty()) {
                    Text(
                        text = "সব দেখুন",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate(AppScreen.PRODUCTS) }
                    )
                }
            }
        }

        if (lowStockProducts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "সব পণ্যের স্টক পর্যাপ্ত রয়েছে!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            items(lowStockProducts.take(4)) { prod ->
                LowStockItemCard(product = prod, config = config, onRestock = { onNavigate(AppScreen.PURCHASES) })
            }
        }

        // --- SALES HISTORY & INVOICES WITH PAGINATION (10 per page, 1, 2, 3 page system) ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "বিক্রয় হিস্ট্রি ও ইনভয়েস",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "আজকের ও পূর্ববর্তী সকল বিক্রয়ের তালিকা",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = dashboardFilterMode == "today",
                        onClick = {
                            dashboardFilterMode = "today"
                            currentSalesPage = 1
                        },
                        label = { Text("আজ (${todaySalesCount})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dashboardFilterMode == "yesterday",
                        onClick = {
                            dashboardFilterMode = "yesterday"
                            currentSalesPage = 1
                        },
                        label = { Text("গতকাল (${yesterdaySalesCount})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dashboardFilterMode == "custom",
                        onClick = {
                            datePickerDialog.show()
                        },
                        label = {
                            val chipLabel = if (dashboardFilterMode == "custom" && customSelectedDate != null) {
                                SimpleDateFormat("dd MMM", Locale("bn", "BD")).format(Date(customSelectedDate!!))
                            } else {
                                "তারিখ"
                            }
                            Text(chipLabel, fontSize = 11.sp)
                        }
                    )
                    FilterChip(
                        selected = dashboardFilterMode == "all",
                        onClick = {
                            dashboardFilterMode = "all"
                            currentSalesPage = 1
                        },
                        label = { Text("সব (${sales.size})", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Search Bar for Sales
        item {
            OutlinedTextField(
                value = salesSearchQuery,
                onValueChange = {
                    salesSearchQuery = it
                    currentSalesPage = 1
                },
                placeholder = { Text("ইনভয়েস নং বা কাস্টমার খুঁজুন...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "খুঁজুন",
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (salesSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { salesSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "পরিষ্কার", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Empty state or Sales list
        if (paginatedSales.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (salesSearchQuery.isNotBlank()) "খোঁজার সাথে কোনো বিক্রয় মেলেনি" else "এখনো কোনো বিক্রয় রেকর্ড নেই",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(paginatedSales, key = { it.id }) { sale ->
                val itemsForSale = remember(sale.id, saleItems) {
                    saleItems.filter { it.saleId == sale.id }
                }
                DashboardSaleItemCard(
                    sale = sale,
                    items = itemsForSale,
                    config = config,
                    onViewReceipt = { viewModel.viewSaleReceipt(sale) },
                    onDeleteSale = { viewModel.deleteSale(sale.id) }
                )
            }

            // Pagination Controls (1, 2, 3...)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "পৃষ্ঠা ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(displayedPage.toString()) else displayedPage} এর ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(totalSalesPages.toString()) else totalSalesPages} (মোট ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(sortedSales.size.toString()) else sortedSales.size} টি ইনভয়েস)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PaginationBar(
                        currentPage = displayedPage,
                        totalPages = totalSalesPages,
                        useBengali = config.useBengaliNumerals,
                        onPageSelected = { currentSalesPage = it }
                    )
                }
            }
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

@Composable
fun SummaryCard(
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DashboardActionBtn(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun WeeklySalesChart(sales: List<Sale>, config: ShopConfig) {
    // Generate 7 days bins
    val days = listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র")
    val cal = Calendar.getInstance()
    val dailyTotals = LongArray(7) { 0L }

    val now = System.currentTimeMillis()
    for (i in 0..6) {
        val start = now - (6 - i) * 86400000L
        val end = start + 86400000L
        dailyTotals[i] = sales.filter { it.saleDate in start..end && !it.isReturned }.sumOf { it.totalPoisha }
    }

    val maxTotal = dailyTotals.maxOrNull()?.coerceAtLeast(100000L) ?: 100000L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..6) {
            val total = dailyTotals[i]
            val heightFraction = (total.toFloat() / maxTotal.toFloat()).coerceIn(0.12f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (total > 0) {
                        val k = total / 100000L
                        if (config.useBengaliNumerals) Formatters.toBengaliDigits("${k}k") else "${k}k"
                    } else "",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (i == 6) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = days[i],
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LowStockItemCard(product: Product, config: ShopConfig, onRestock: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.nameBn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "বিক্রয় মূল্য: ${Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${product.unitName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusDanger.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusDanger
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onRestock,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "মাল ক্রয়",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        title = { Text("নতুন খরচ এন্ট্রি") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("টাকার পরিমাণ (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("ক্যাটাগরি নির্বাচন করুন:", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory?.id == cat.id,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.nameBn) }
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("বিবরণ / নোট (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun DashboardSaleItemCard(
    sale: Sale,
    items: List<com.example.data.entity.SaleItem>,
    config: ShopConfig,
    onViewReceipt: () -> Unit,
    onDeleteSale: () -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Invoice No + Date + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = sale.invoiceNo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatDateTime(sale.saleDate),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when (sale.paymentMethod) {
                        "due" -> StatusDanger.copy(alpha = 0.12f)
                        "bkash" -> Color(0xFFE2136E).copy(alpha = 0.12f)
                        "nagad" -> Color(0xFFF7941D).copy(alpha = 0.12f)
                        else -> StatusSuccess.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (sale.paymentMethod) {
                            "due" -> "বাকি"
                            "bkash" -> "বিকাশ"
                            "nagad" -> "নগদ"
                            "bank" -> "ব্যাংক"
                            else -> "নগদ"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (sale.paymentMethod) {
                            "due" -> StatusDanger
                            "bkash" -> Color(0xFFE2136E)
                            "nagad" -> Color(0xFFF7941D)
                            else -> StatusSuccess
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Customer Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = sale.customerName ?: "সাধারণ খরিদ্দার",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Products Purchased Summary
            if (items.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
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
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (items.size > 2) {
                            Text(
                                text = if (expanded) "সংক্ষেপ করুন ▲" else "+ আরও ${items.size - 2} টি পণ্য দেখুন ▼",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { expanded = !expanded }
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Row 4: Total, Paid, Due & Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "মোট: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (sale.dueAmountPoisha > 0) {
                        Text(
                            text = "বাকি: ${Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusDanger
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick WhatsApp Invoice Image button
                    IconButton(
                        onClick = {
                            val bmp = InvoiceImageHelper.generateSaleInvoiceBitmap(context, config, sale, items)
                            val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${sale.invoiceNo}")
                            val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, sale)
                            InvoiceImageHelper.shareToWhatsApp(context, uri, sale.customerName, caption)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "WhatsApp ইনভয়েস ছবি",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedButton(
                        onClick = onViewReceipt,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "রসিদ",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("রসিদ দেখুন", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "মুছুন",
                            tint = StatusDanger.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("বিক্রয় রেকর্ড মুছুন") },
            text = { Text("ইনভয়েস নং ${sale.invoiceNo}-এর রেকর্ডটি মুছে ফেলতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSale()
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev button
        FilledTonalIconButton(
            onClick = { if (currentPage > 1) onPageSelected(currentPage - 1) },
            enabled = currentPage > 1,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "আগের পৃষ্ঠা",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Page buttons (display up to 5 surrounding pages)
        val startPage = (currentPage - 2).coerceAtLeast(1)
        val endPage = (startPage + 4).coerceAtMost(totalPages)

        for (p in startPage..endPage) {
            val isSelected = p == currentPage
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onPageSelected(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (useBengali) Formatters.toBengaliDigits(p.toString()) else p.toString(),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Next button
        FilledTonalIconButton(
            onClick = { if (currentPage < totalPages) onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "পরের পৃষ্ঠা",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
