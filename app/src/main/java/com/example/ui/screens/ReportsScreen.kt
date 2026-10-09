package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import com.example.data.entity.StaffSalesSummary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChipRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatCard
import com.example.ui.components.StatTone
import com.example.ui.theme.*
import com.example.util.Formatters
import java.util.Calendar

@Composable
fun ReportsScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    modifier: Modifier = Modifier
) {
    val sales by viewModel.sales.collectAsState()
    val saleItems by viewModel.allSaleItems.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val products by viewModel.products.collectAsState()
    var selectedPeriod by remember { mutableStateOf("আজ") }
    var selectedReport by remember { mutableStateOf<String?>(null) }
    var customDate by remember { mutableLongStateOf(0L) }
    var customRangeStart by remember { mutableLongStateOf(0L) }
    var customRangeEnd by remember { mutableLongStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val periods = listOf("আজ", "গতকাল", "এই সপ্তাহ", "এই মাস", "সব সময়", "নির্দিষ্ট দিন", "তারিখসীমা")
    val nowCalendar = Calendar.getInstance()
    val todayStart = (nowCalendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val rangeStart = remember(selectedPeriod, customDate, customRangeStart, todayStart) {
        when (selectedPeriod) {
            "আজ" -> todayStart
            "গতকাল" -> yesterdayStart
            "এই সপ্তাহ" -> (Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }).timeInMillis
            "এই মাস" -> (Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }).timeInMillis
            "নির্দিষ্ট দিন" -> customDate.takeIf { it > 0L } ?: todayStart
            "তারিখসীমা" -> customRangeStart.takeIf { it > 0L } ?: todayStart
            else -> 0L
        }
    }
    val rangeEnd = when (selectedPeriod) {
        "আজ" -> todayStart + 86_400_000L
        "গতকাল" -> todayStart
        "নির্দিষ্ট দিন" -> rangeStart + 86_400_000L
        "তারিখসীমা" -> (customRangeEnd.takeIf { it > 0L } ?: todayStart) + 86_400_000L
        else -> todayStart + 86_400_000L
    }
    val periodSales = sales.filter { it.saleDate >= rangeStart && it.saleDate < rangeEnd && !it.isReturned }
    val periodSaleIds = periodSales.map { it.id }.toSet()
    val periodSoldItems = saleItems.filter { it.saleId in periodSaleIds }
    val periodSalesTotalPoisha = periodSales.sumOf { it.totalPoisha }
    val periodCostPoisha = periodSoldItems.sumOf { (it.qty * it.purchasePriceAtSalePoisha).toLong() }
    val periodGrossProfitPoisha = (periodSalesTotalPoisha - periodCostPoisha).coerceAtLeast(0L)
    val periodExpenses = expenses.filter { it.expenseDate >= rangeStart && it.expenseDate < rangeEnd }
    val periodExpenseTotalPoisha = periodExpenses.sumOf { it.amountPoisha }
    val periodNetProfitPoisha = periodGrossProfitPoisha - periodExpenseTotalPoisha
    val isStaff = config.userRole == "staff"
    val staffSalesSummaries = remember(periodSales, config.staffMembersJson) {
        try { viewModel.calculateStaffSalesSummaries(periodSales) } catch (_: Throwable) { emptyList() }
    }
    val totalStockCostPoisha = products.sumOf { (it.stockQty * it.purchasePricePoisha).toLong() }
    val totalStockSaleValuePoisha = products.sumOf { (it.stockQty * it.salePricePoisha).toLong() }
    val expectedFutureProfitPoisha = (totalStockSaleValuePoisha - totalStockCostPoisha).coerceAtLeast(0L)
    val cashSalesPoisha = periodSales.filter { it.paymentMethod == "cash" }.sumOf { it.paidAmountPoisha }
    val mfsSalesPoisha = periodSales.filter { it.paymentMethod == "mfs" }.sumOf { it.paidAmountPoisha }
    val dueSalesPoisha = periodSales.sumOf { it.dueAmountPoisha }
    val netDrawerCashPoisha = (cashSalesPoisha - periodExpenseTotalPoisha).coerceAtLeast(0L)
    val productSalesMap = mutableMapOf<String, Pair<Double, Long>>()
    periodSoldItems.forEach { item ->
        val current = productSalesMap[item.productName] ?: (0.0 to 0L)
        productSalesMap[item.productName] = (current.first + item.qty) to (current.second + item.lineTotalPoisha)
    }
    val topProducts = productSalesMap.entries.sortedByDescending { it.value.first }.take(10)

    val reportCards = buildList {
        add(ReportCardInfo("বিক্রয় ও লাভ", "বিক্রয়, পণ্যের খরচ ও নিট লাভ", Icons.Default.TrendingUp))
        add(ReportCardInfo("খরচের বিস্তারিত", "খরচের ধরন ও মোট পরিমাণ", Icons.Default.Calculate))
        add(ReportCardInfo("দৈনিক হিসাব", "প্রতিদিনের বিক্রয়, লাভ ও খরচ", Icons.Default.CalendarMonth))
        add(ReportCardInfo("ক্যাশ ও বাকি", "নগদ, ডিজিটাল ও বকেয়া বিক্রয়", Icons.Default.AccountBalanceWallet))
        add(ReportCardInfo("স্টক ও সম্ভাব্য লাভ", "বর্তমান পণ্যের বিনিয়োগ ও মূল্য", Icons.Default.Inventory2))
        add(ReportCardInfo("সর্বাধিক বিক্রিত পণ্য", "পরিমাণ ও বিক্রয়মূল্য অনুযায়ী", Icons.Default.ShoppingCart))
        if (!isStaff) add(ReportCardInfo("কর্মচারীভিত্তিক বিক্রয়", "কর্মী অনুযায়ী বিক্রির তুলনা", Icons.Default.Person))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedReport != null) {
                        IconButton(onClick = { selectedReport = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "রিপোর্ট তালিকায় ফিরুন", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selectedReport ?: "রিপোর্ট ও বিশ্লেষণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Text(if (selectedReport == null) "যে হিসাব দরকার, কার্ডে ট্যাপ করুন" else "$selectedPeriod · বিস্তারিত হিসাব", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                    }
                }
            }
            FilterChipRow(options = periods, selected = selectedPeriod, onSelect = { period ->
                if (period == "নির্দিষ্ট দিন") {
                    val c = Calendar.getInstance().apply { timeInMillis = if (customDate > 0L) customDate else todayStart }
                    android.app.DatePickerDialog(context, { _, year, month, day ->
                        customDate = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        selectedPeriod = "নির্দিষ্ট দিন"
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                } else if (period == "তারিখসীমা") {
                    val initial = Calendar.getInstance().apply { timeInMillis = if (customRangeStart > 0L) customRangeStart else todayStart }
                    android.app.DatePickerDialog(context, { _, startYear, startMonth, startDay ->
                        val start = Calendar.getInstance().apply { set(startYear, startMonth, startDay, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        customRangeStart = start
                        val endInitial = Calendar.getInstance().apply { timeInMillis = if (customRangeEnd >= start) customRangeEnd else start }
                        android.app.DatePickerDialog(context, { _, endYear, endMonth, endDay ->
                            val end = Calendar.getInstance().apply { set(endYear, endMonth, endDay, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            customRangeEnd = end.coerceAtLeast(start)
                            selectedPeriod = "তারিখসীমা"
                        }, endInitial.get(Calendar.YEAR), endInitial.get(Calendar.MONTH), endInitial.get(Calendar.DAY_OF_MONTH)).show()
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                } else selectedPeriod = period
            })
        }
        if (selectedReport == null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "$selectedPeriod · সারাংশ")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(Modifier.weight(1f)) { StatCard("মোট বিক্রয়", Formatters.formatMoney(periodSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol), tone = StatTone.Positive) }
                        Box(Modifier.weight(1f)) { StatCard("নিট লাভ", Formatters.formatMoney(periodNetProfitPoisha, config.useBengaliNumerals, config.currencySymbol), tone = if (periodNetProfitPoisha >= 0) StatTone.Positive else StatTone.Negative) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(Modifier.weight(1f)) { StatCard("মোট খরচ", Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol), tone = StatTone.Negative) }
                        Box(Modifier.weight(1f)) { StatCard("বিক্রির সংখ্যা", if (config.useBengaliNumerals) Formatters.toBengaliDigits(periodSales.size.toString()) else periodSales.size.toString(), tone = StatTone.Neutral) }
                    }
                }
            }
            itemsIndexed(reportCards.chunked(2)) { _, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { card ->
                        Box(Modifier.weight(1f)) { ReportCategoryCard(card = card, onClick = { selectedReport = card.title }) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            item {
                when (selectedReport) {
                    "বিক্রয় ও লাভ" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "$selectedPeriod · লাভ-ক্ষতির হিসাব")
                        ProfitLossWaterfallCard(periodSalesTotalPoisha, periodCostPoisha, periodGrossProfitPoisha, periodExpenseTotalPoisha, periodNetProfitPoisha, config)
                    }
                    "খরচের বিস্তারিত" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "খরচের ধরন অনুযায়ী")
                        ExpenseBreakdownCard(periodExpenses, config)
                        SectionHeader(title = "দিনভিত্তিক খরচ ও লাভ")
                        DailyBreakdownCard(periodSales, periodSoldItems, periodExpenses, config)
                    }
                    "দৈনিক হিসাব" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "প্রতিদিনের হিসাব")
                        DailyBreakdownCard(periodSales, periodSoldItems, periodExpenses, config)
                    }
                    "ক্যাশ ও বাকি" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "পেমেন্টের বিস্তারিত")
                        CashDrawerReconciliationCard(cashSalesPoisha, mfsSalesPoisha, periodExpenseTotalPoisha, dueSalesPoisha, netDrawerCashPoisha, config)
                    }
                    "স্টক ও সম্ভাব্য লাভ" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "বর্তমান স্টক ভ্যালুয়েশন")
                        StockValuationCard(totalStockCostPoisha, totalStockSaleValuePoisha, expectedFutureProfitPoisha, products.size, config)
                    }
                    "সর্বাধিক বিক্রিত পণ্য" -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "$selectedPeriod · জনপ্রিয় পণ্য")
                        TopProductsCard(topProducts, config)
                    }
                    "কর্মচারীভিত্তিক বিক্রয়" -> if (!isStaff) Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader(title = "$selectedPeriod · কর্মচারী বিক্রয়")
                        EmployeeSalesCard(staffSalesSummaries, periodSalesTotalPoisha, config)
                    }
                }
            }
        }
    }
}

private data class ReportCardInfo(val title: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun ReportCategoryCard(card: ReportCardInfo, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border), modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).softShadow(1, RoundedCornerShape(Radius.lg))) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(Radius.md)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(card.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(card.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExpenseBreakdownCard(expenses: List<com.example.data.entity.Expense>, config: ShopConfig) {
    val grouped = expenses.groupBy { it.categoryName }.mapValues { (_, list) -> list.sumOf { it.amountPoisha } }.toList().sortedByDescending { it.second }
    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().softShadow(1, RoundedCornerShape(Radius.lg))) {
        if (grouped.isEmpty()) EmptyState(icon = Icons.Default.Calculate, title = "কোনো খরচের তথ্য নেই", message = "এই সময়ে খরচ যোগ করা হয়নি।")
        else Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            grouped.forEachIndexed { index, (name, amount) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.dokanColors.border)
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Formatters.formatMoney(amount, config.useBengaliNumerals, config.currencySymbol), style = amountTextStyle(16.sp), fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = MaterialTheme.dokanColors.border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("সর্বমোট খরচ", fontWeight = FontWeight.Bold)
                Text(Formatters.formatMoney(grouped.sumOf { it.second }, config.useBengaliNumerals, config.currencySymbol), fontWeight = FontWeight.Bold, color = MaterialTheme.dokanColors.danger)
            }
        }
    }
}

@Composable
private fun DailyBreakdownCard(
    sales: List<com.example.data.entity.Sale>,
    items: List<com.example.data.entity.SaleItem>,
    expenses: List<com.example.data.entity.Expense>,
    config: ShopConfig
) {
    val dayFormatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("bn", "BD"))
    val salesByDay = sales.groupBy { Calendar.getInstance().apply { timeInMillis = it.saleDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val expenseByDay = expenses.groupBy { Calendar.getInstance().apply { timeInMillis = it.expenseDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val days = (salesByDay.keys + expenseByDay.keys).distinct().sortedDescending().take(60)
    Surface(shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().softShadow(1, RoundedCornerShape(Radius.lg))) {
        if (days.isEmpty()) EmptyState(icon = Icons.Default.CalendarMonth, title = "কোনো হিসাব পাওয়া যায়নি", message = "সময়সীমা বদলে আবার দেখুন।")
        else Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            days.forEachIndexed { index, day ->
                val daySales = salesByDay[day].orEmpty()
                val ids = daySales.map { it.id }.toSet()
                val cost = items.filter { it.saleId in ids }.sumOf { (it.qty * it.purchasePriceAtSalePoisha).toLong() }
                val revenue = daySales.sumOf { it.totalPoisha }
                val expense = expenseByDay[day].orEmpty().sumOf { it.amountPoisha }
                val net = revenue - cost - expense
                if (index > 0) HorizontalDivider(color = MaterialTheme.dokanColors.border)
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dayFormatter.format(java.util.Date(day)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ReportValueRow("বিক্রয়", revenue, config)
                    ReportValueRow("পণ্যের ক্রয়মূল্য", cost, config)
                    ReportValueRow("খরচ", expense, config)
                    ReportValueRow("নিট লাভ", net, config, emphasize = true)
                }
            }
        }
    }
}

@Composable
private fun ReportValueRow(label: String, amount: Long, config: ShopConfig, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (emphasize) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Formatters.formatMoney(amount, config.useBengaliNumerals, config.currencySymbol),
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

// ==============================================================================
// 2. PROFIT & LOSS WATERFALL CARD
// ==============================================================================
@Composable
private fun ProfitLossWaterfallCard(
    salesTotal: Long,
    costTotal: Long,
    grossProfit: Long,
    expensesTotal: Long,
    netProfit: Long,
    config: ShopConfig
) {
    var animateStart by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateStart = true
    }

    val maxVal = maxOf(salesTotal, costTotal, grossProfit, expensesTotal, kotlin.math.abs(netProfit), 1L).toFloat()

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Waterfall item 1: মোট বিক্রয় (Income -> Success)
            WaterfallRow(
                label = "মোট বিক্রয়",
                amountText = Formatters.formatMoney(salesTotal, config.useBengaliNumerals, config.currencySymbol),
                fraction = salesTotal / maxVal,
                barColor = MaterialTheme.dokanColors.success,
                shouldAnimate = animateStart
            )

            // Waterfall item 2: বিক্রিত পণ্যের ক্রয়মূল্য (Deduction -> Danger)
            WaterfallRow(
                label = "বিক্রিত পণ্যের ক্রয়মূল্য",
                amountText = "-${Formatters.formatMoney(costTotal, config.useBengaliNumerals, config.currencySymbol)}",
                fraction = costTotal / maxVal,
                barColor = MaterialTheme.dokanColors.danger,
                shouldAnimate = animateStart
            )

            // Waterfall item 3: মোট গ্রস লাভ (Income -> Success)
            WaterfallRow(
                label = "মোট গ্রস লাভ",
                amountText = Formatters.formatMoney(grossProfit, config.useBengaliNumerals, config.currencySymbol),
                fraction = grossProfit / maxVal,
                barColor = MaterialTheme.dokanColors.success,
                shouldAnimate = animateStart
            )

            // Waterfall item 4: দোকানের অন্যান্য খরচ (Deduction -> Danger)
            WaterfallRow(
                label = "দোকানের অন্যান্য খরচ",
                amountText = "-${Formatters.formatMoney(expensesTotal, config.useBengaliNumerals, config.currencySymbol)}",
                fraction = expensesTotal / maxVal,
                barColor = MaterialTheme.dokanColors.danger,
                shouldAnimate = animateStart
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.dokanColors.border
            )

            // Final Row: প্রকৃত নিট লাভ on a primaryContainer band
            val isNetPositive = netProfit >= 0
            val bandContainerColor = if (isNetPositive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.dokanColors.dangerContainer
            }
            val netTextColor = if (isNetPositive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.dokanColors.danger
            }

            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = bandContainerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "প্রকৃত নিট লাভ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = (if (netProfit < 0) "-" else "") + Formatters.formatMoney(
                            kotlin.math.abs(netProfit),
                            config.useBengaliNumerals,
                            config.currencySymbol
                        ),
                        style = amountTextStyle(30.sp),
                        color = netTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterfallRow(
    label: String,
    amountText: String,
    fraction: Float,
    barColor: Color,
    shouldAnimate: Boolean
) {
    val animatedFraction by animateFloatAsState(
        targetValue = if (shouldAnimate) fraction.coerceIn(0.04f, 1f) else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "waterfall_bar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = amountText,
                style = amountTextStyle(15.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.dokanColors.surfaceAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(barColor)
            )
        }
    }
}

// ==============================================================================
// 3. CASH DRAWER RECONCILIATION CARD
// ==============================================================================
@Composable
private fun CashDrawerReconciliationCard(
    cashSales: Long,
    mfsSales: Long,
    expenses: Long,
    dueSales: Long,
    netDrawerCash: Long,
    config: ShopConfig
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.dokanColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Header with small calculator icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "দৈনিক ক্যাশ ড্রয়ার মিলকরণ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.dokanColors.border
            )

            // Signed rows: +, +, −, −
            SignedRow(
                sign = "+",
                signColor = MaterialTheme.dokanColors.success,
                label = "নগদ বিক্রয় জমা",
                amount = Formatters.formatMoney(cashSales, config.useBengaliNumerals, config.currencySymbol)
            )

            SignedRow(
                sign = "+",
                signColor = MaterialTheme.dokanColors.success,
                label = "বিকাশ/নগদ ডিজিটাল জমা",
                amount = Formatters.formatMoney(mfsSales, config.useBengaliNumerals, config.currencySymbol)
            )

            SignedRow(
                sign = "−",
                signColor = MaterialTheme.dokanColors.danger,
                label = "নগদ খরচ কর্তন",
                amount = Formatters.formatMoney(expenses, config.useBengaliNumerals, config.currencySymbol)
            )

            SignedRow(
                sign = "−",
                signColor = MaterialTheme.dokanColors.warning,
                label = "বাকিতে বিক্রি",
                amount = Formatters.formatMoney(dueSales, config.useBengaliNumerals, config.currencySymbol)
            )

            // Double Rule
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.dokanColors.border)
                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.dokanColors.border)
            }

            // Final: ক্যাশ বাক্সে থাকার কথা
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ক্যাশ বাক্সে থাকার কথা",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = Formatters.formatMoney(netDrawerCash, config.useBengaliNumerals, config.currencySymbol),
                    style = amountTextStyle(28.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SignedRow(
    sign: String,
    signColor: Color,
    label: String,
    amount: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sign,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = signColor
            ),
            modifier = Modifier.width(22.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = amount,
            style = amountTextStyle(15.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==============================================================================
// 4. STOCK VALUATION CARD
// ==============================================================================
@Composable
private fun StockValuationCard(
    totalCost: Long,
    totalSaleValue: Long,
    expectedProfit: Long,
    productCount: Int,
    config: ShopConfig
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Two StatCards side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        label = "স্টকের মোট ক্রয়মূল্য (ইনভেস্ট)",
                        value = Formatters.formatMoney(totalCost, config.useBengaliNumerals, config.currencySymbol),
                        tone = StatTone.Neutral
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        label = "স্টকের আনুমানিক বিক্রয়মূল্য",
                        value = Formatters.formatMoney(totalSaleValue, config.useBengaliNumerals, config.currencySymbol),
                        tone = StatTone.Gold
                    )
                }
            }

            // Slim stacked bar showing cost against potential profit
            val total = (totalCost + expectedProfit).coerceAtLeast(1L).toFloat()
            val costFrac = (totalCost / total).coerceIn(0.05f, 0.95f)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.dokanColors.surfaceAlt)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Cost portion
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(costFrac)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        )
                        // Profit portion
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(MaterialTheme.dokanColors.success)
                        )
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "ইনভেস্টমেন্ট",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.dokanColors.success)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "সম্ভাব্য মোট লাভ: +${Formatters.formatMoney(expectedProfit, config.useBengaliNumerals, config.currencySymbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.dokanColors.success
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 5. TOP PRODUCTS RANKED LIST CARD
// ==============================================================================
@Composable
private fun TopProductsCard(
    topProducts: List<Map.Entry<String, Pair<Double, Long>>>,
    config: ShopConfig
) {
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (topProducts.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.TrendingUp,
                    title = "এই সময়ে কোনো বিক্রির রেকর্ড নেই",
                    message = "পণ্য বিক্রি হলে এখানে সর্বাধিক বিক্রিত তালিকা দেখতে পাবেন।"
                )
            } else {
                val maxQty = topProducts.firstOrNull()?.value?.first?.coerceAtLeast(1.0) ?: 1.0

                topProducts.forEachIndexed { idx, entry ->
                    val share = (entry.value.first / maxQty).toFloat().coerceIn(0.05f, 1f)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 28dp numbered badge
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (idx == 0) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.dokanColors.surfaceAlt
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (idx == 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(Spacing.md))

                            // Name and quantity sold
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.key,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${Formatters.formatQty(entry.value.first, "", config.useBengaliNumerals)} বিক্রয়",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Revenue
                            Text(
                                text = Formatters.formatMoney(entry.value.second, config.useBengaliNumerals, config.currencySymbol),
                                style = amountTextStyle(15.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Thin bar showing share of the top seller
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(MaterialTheme.dokanColors.surfaceAlt)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(share)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        if (idx < topProducts.size - 1) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// 6. EMPLOYEE SALES BREAKDOWN CARD (কর্মচারীভিত্তিক বিক্রয় হিসাব)
// ==============================================================================
@Composable
private fun EmployeeSalesCard(
    summaries: List<StaffSalesSummary>,
    totalPeriodSales: Long,
    config: ShopConfig
) {
    if (summaries.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .softShadow(1, RoundedCornerShape(Radius.lg))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "কোনো কর্মচারীর বিক্রয় তথ্য নেই",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "কর্মচারীরা তাদের আইডি দিয়ে অ্যাপে লগইন করে বিক্রি শুরু করলে স্বয়ংক্রিয়ভাবে এখানে তাদের বিক্রয় ও মেমোর হিসাব যুক্ত হবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.lg))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            summaries.forEachIndexed { index, summary ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }

                val fraction = if (totalPeriodSales > 0) {
                    (summary.totalSalesPoisha.toFloat() / totalPeriodSales.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val percent = (fraction * 100).toInt()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar Circle
                        val avatarColor = if (summary.isOwner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(avatarColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = summary.staffName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = avatarColor
                            )
                        }

                        Spacer(modifier = Modifier.width(Spacing.md))

                        // Staff Name + Role
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = summary.staffName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (summary.isOwner) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(Radius.xs),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "মালিক",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            val subText = if (summary.staffEmail.isNotBlank()) summary.staffEmail else "অর্ডার: ${if (config.useBengaliNumerals) Formatters.toBengaliDigits(summary.totalOrdersCount.toString()) else summary.totalOrdersCount} টি"
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Total Sales Value + Invoices Count Badge
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatters.formatMoney(summary.totalSalesPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (summary.totalSalesPoisha > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(summary.totalOrdersCount.toString()) else summary.totalOrdersCount} টি মেমো",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Progress bar for percentage share of total store sales
                    if (totalPeriodSales > 0 && summary.totalSalesPoisha > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = fraction,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(Radius.pill)),
                                color = if (summary.isOwner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(percent.toString()) else percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Sub row: Cash & Due breakdown
                    if (summary.totalSalesPoisha > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "নগদ: ${Formatters.formatMoney(summary.totalCashPoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF15803D)
                            )
                            if (summary.totalDuePoisha > 0) {
                                Text(
                                    text = "বাকি: ${Formatters.formatMoney(summary.totalDuePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
