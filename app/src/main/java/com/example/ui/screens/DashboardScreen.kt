package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExpenseCategory
import com.example.data.entity.Product
import com.example.data.entity.Sale
import com.example.data.entity.SaleItem
import com.example.ui.AppScreen
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.ProductReturnDialog
import com.example.ui.components.TopHeader
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    // Sales Pagination & Search State
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
            // 1. TOP STORE HEADER
            item {
                TopHeader(
                    config = config,
                    onOpenMoreMenu = onNavigate,
                    isSyncing = isSyncing,
                    onSyncNow = { viewModel.syncToSupabase(silent = false) },
                    onToggleTheme = { viewModel.toggleThemeMode() }
                )
            }

            // 2. EXECUTIVE FINANCIAL HERO CARD WITH INTEGRATED TIME FILTER
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0D9488), // Deep Teal
                                        Color(0xFF065F46)  // Emerald Forest
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            // Top Segmented Time Filter Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filterOptions = listOf(
                                    "today" to "আজ (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(todaySalesCount.toString()) else todaySalesCount})",
                                    "yesterday" to "গতকাল (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(yesterdaySalesCount.toString()) else yesterdaySalesCount})",
                                    "custom" to (if (dashboardFilterMode == "custom" && customSelectedDate != null)
                                        SimpleDateFormat("dd MMM", Locale("bn", "BD")).format(Date(customSelectedDate!!))
                                    else "তারিখ 📅"),
                                    "all" to "সব (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(sales.size.toString()) else sales.size})"
                                )

                                filterOptions.forEach { (mode, label) ->
                                    val isSelected = dashboardFilterMode == mode
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.16f),
                                        modifier = Modifier
                                            .weight(if (mode == "custom") 1.15f else 1f)
                                            .clickable {
                                                if (mode == "custom") {
                                                    datePickerDialog.show()
                                                } else {
                                                    dashboardFilterMode = mode
                                                    currentSalesPage = 1
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF065F46) else Color.White.copy(alpha = 0.9f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Hero Metric: Period Total Sales
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$periodLabel মোট বিক্রয়",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodSales.size.toString()) else periodSales.size} টি মেমো",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = Formatters.formatMoney(periodSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )

                            Text(
                                text = "দোকানের সর্বমোট বিক্রয়: ${Formatters.formatMoney(allSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Dual Glass Sub-Metrics Grid (Net Profit & Total Expenses)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Net Profit Glass Tile
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "$periodLabel নিট লাভ",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = if (periodNetProfitPoisha >= 0) Color(0xFF34D399) else Color(0xFFFCA5A5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = Formatters.formatMoney(periodNetProfitPoisha, config.useBengaliNumerals, config.currencySymbol),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (periodNetProfitPoisha >= 0) Color.White else Color(0xFFFCA5A5)
                                        )
                                        Text(
                                            text = if (periodNetProfitPoisha >= 0) "✅ নিট মুনাফা" else "⚠️ ক্ষতি",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                // Total Expenses Glass Tile
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "$periodLabel মোট খরচ",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = Color(0xFFFDE047),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodExpenses.size.toString()) else periodExpenses.size} টি খরচ রেকর্ড",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. STORE ASSETS & OUTSTANDING DUE MATRIX
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: মোট বাকি খাতা
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.DUE_KHATA) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, StatusDanger.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(StatusDanger.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = StatusDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StatusDanger.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "বাকি খাতা →",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusDanger,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "মোট বাকি পাওনা",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = Formatters.formatMoney(totalDue, config.useBengaliNumerals, config.currencySymbol),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusDanger
                            )
                            Text(
                                text = "খাতায় কাস্টমার পাওনা",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Card 2: ইনভেন্টরি স্টক ও সম্পদ
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.PRODUCTS) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF3B82F6).copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(products.size.toString()) else products.size} টি পণ্য",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "মোট স্টক বিক্রয়মূল্য",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = Formatters.formatMoney(totalStockSaleValue, config.useBengaliNumerals, config.currencySymbol),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ক্রয়মূল্য: ${Formatters.formatMoney(totalStockPurchaseValue, config.useBengaliNumerals, config.currencySymbol)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. MODERN QUICK ACTION HUB
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "কুইক অ্যাকশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModernQuickActionCard(
                        title = "নতুন বিক্রয়",
                        subtitle = "POS বিল",
                        icon = Icons.Default.ShoppingCart,
                        accentColor = Color(0xFF0E9F6E),
                        onClick = { onNavigate(AppScreen.POS) },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "পণ্য যোগ",
                        subtitle = "স্টক এন্ট্রি",
                        icon = Icons.Default.AddBox,
                        accentColor = Color(0xFF0284C7),
                        onClick = { onNavigate(AppScreen.PRODUCTS) },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "বাকি খাতা",
                        subtitle = "লেনদেন",
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Color(0xFF8B5CF6),
                        onClick = { onNavigate(AppScreen.DUE_KHATA) },
                        modifier = Modifier.weight(1f)
                    )
                    ModernQuickActionCard(
                        title = "খরচ যোগ",
                        subtitle = "দৈনিক ব্যয়",
                        icon = Icons.Default.ReceiptLong,
                        accentColor = Color(0xFFF59E0B),
                        onClick = { showExpenseDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. 7-DAY SALES TREND BAR CHART
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "গত ৭ দিনের বিক্রয় গ্রাফ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.clickable { onNavigate(AppScreen.REPORTS) }
                            ) {
                                Text(
                                    text = "রিপোর্ট দেখুন →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        WeeklySalesChart(sales = sales, config = config)
                    }
                }
            }

            // 6. LOW STOCK ALERT SECTION
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (lowStockProducts.isNotEmpty()) Icons.Default.WarningAmber else Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = if (lowStockProducts.isNotEmpty()) StatusWarning else StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "স্টক স্থিতি (${if (config.useBengaliNumerals) Formatters.toBengaliDigits(lowStockProducts.size.toString()) else lowStockProducts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (lowStockProducts.isNotEmpty()) {
                        Text(
                            text = "সব পণ্য →",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
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
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "সব পণ্যের স্টক পর্যাপ্ত রয়েছে!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "দোকানে বর্তমানে কোনো পণ্য ঘাটতি নেই।",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(lowStockProducts.take(3), key = { it.id }) { prod ->
                    LowStockItemCard(product = prod, config = config, onRestock = { onNavigate(AppScreen.PURCHASES) })
                }
            }

            // 7. SALES INVOICES STREAM & SEARCH
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "সাম্প্রতিক বিক্রয় ও মেমো",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(sortedSales.size.toString()) else sortedSales.size} টি ইনভয়েস পাওয়া গেছে",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Rounded Modern Search Bar
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
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
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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
                        onDeleteSale = { viewModel.deleteSale(sale.id) },
                        onReturnSale = { returnedMap -> viewModel.returnSaleItems(sale.id, returnedMap) }
                    )
                }

                // Clean Pagination Controls
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "পৃষ্ঠা ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(displayedPage.toString()) else displayedPage} এর ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(totalSalesPages.toString()) else totalSalesPages} (মোট ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(sortedSales.size.toString()) else sortedSales.size} টি মেমো)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
fun ModernQuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WeeklySalesChart(sales: List<Sale>, config: ShopConfig) {
    val days = listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র")
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
            .height(115.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..6) {
            val total = dailyTotals[i]
            val heightFraction = (total.toFloat() / maxTotal.toFloat()).coerceIn(0.12f, 1f)
            val isToday = (i == 6)

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
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (isToday) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    )
                                )
                            }
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = days[i],
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.25f)),
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "বিক্রয় মূল্য: ${Formatters.formatMoney(product.salePricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${product.unitName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusDanger.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "স্টক: ${Formatters.formatQty(product.stockQty, product.unitName, config.useBengaliNumerals)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusDanger,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = onRestock,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "মাল ক্রয়",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    categories: List<ExpenseCategory>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Long, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("নতুন খরচ এন্ট্রি", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("টাকার পরিমাণ (৳)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("ক্যাটাগরি নির্বাচন করুন:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                    shape = RoundedCornerShape(12.dp),
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
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun DashboardSaleItemCard(
    sale: Sale,
    items: List<SaleItem>,
    config: ShopConfig,
    onViewReceipt: () -> Unit,
    onDeleteSale: () -> Unit = {},
    onReturnSale: (Map<Long, Double>) -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Invoice No + Date + Payment Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
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

                if (sale.isReturned) {
                    Surface(
                        color = StatusDanger.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "❌ ফেরতকৃত",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusDanger,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Customer Name with Avatar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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

            // Row 4: Total, Paid, Due & Action Buttons
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
                            fontSize = 16.sp,
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

                    // Return Sale Button (if not already returned)
                    if (!sale.isReturned) {
                        OutlinedButton(
                            onClick = { showReturnDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                            border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AssignmentReturn,
                                contentDescription = "পণ্য ফেরত",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ফেরত", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                    }

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

                    Spacer(modifier = Modifier.width(4.dp))

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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("মুছুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল")
                }
            },
            shape = RoundedCornerShape(16.dp)
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
