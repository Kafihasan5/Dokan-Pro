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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Person
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
    val periods = listOf("আজ", "এই সপ্তাহ", "এই মাস", "সব সময়")

    // Filter range calculation
    val calendar = Calendar.getInstance()

    val filterStartTime = remember(selectedPeriod) {
        when (selectedPeriod) {
            "আজ" -> {
                calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            "এই সপ্তাহ" -> {
                calendar.apply {
                    set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }.timeInMillis
            }
            "এই মাস" -> {
                calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                }.timeInMillis
            }
            else -> 0L
        }
    }

    val periodSales = sales.filter { it.saleDate >= filterStartTime && !it.isReturned }
    val periodSaleIds = periodSales.map { it.id }.toSet()
    val periodSoldItems = saleItems.filter { it.saleId in periodSaleIds }

    val periodSalesTotalPoisha = periodSales.sumOf { it.totalPoisha }
    val periodCostPoisha = periodSoldItems.sumOf { (it.qty * it.purchasePriceAtSalePoisha).toLong() }
    val periodGrossProfitPoisha = (periodSalesTotalPoisha - periodCostPoisha).coerceAtLeast(0)

    val periodExpenses = expenses.filter { it.expenseDate >= filterStartTime }
    val periodExpenseTotalPoisha = periodExpenses.sumOf { it.amountPoisha }
    val periodNetProfitPoisha = periodGrossProfitPoisha - periodExpenseTotalPoisha

    val isStaff = config.userRole == "staff"
    val staffSalesSummaries = remember(periodSales, config.staffMembersJson) {
        try {
            viewModel.calculateStaffSalesSummaries(periodSales)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // Stock Valuation (All current active products)
    val totalStockCostPoisha = products.sumOf { (it.stockQty * it.purchasePricePoisha).toLong() }
    val totalStockSaleValuePoisha = products.sumOf { (it.stockQty * it.salePricePoisha).toLong() }
    val expectedFutureProfitPoisha = (totalStockSaleValuePoisha - totalStockCostPoisha).coerceAtLeast(0)

    // Daily Cash Register Closing simulation
    val cashSalesPoisha = periodSales.filter { it.paymentMethod == "cash" }.sumOf { it.paidAmountPoisha }
    val mfsSalesPoisha = periodSales.filter { it.paymentMethod == "mfs" }.sumOf { it.paidAmountPoisha }
    val dueSalesPoisha = periodSales.sumOf { it.dueAmountPoisha }
    val netDrawerCashPoisha = (cashSalesPoisha - periodExpenseTotalPoisha).coerceAtLeast(0)

    // Top Products ranking by Qty
    val productSalesMap = mutableMapOf<String, Pair<Double, Long>>()
    for (item in periodSoldItems) {
        val cur = productSalesMap.getOrDefault(item.productName, Pair(0.0, 0L))
        productSalesMap[item.productName] = Pair(cur.first + item.qty, cur.second + item.lineTotalPoisha)
    }
    val topProducts = productSalesMap.entries.sortedByDescending { it.value.first }.take(5)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.md,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Screen Header & Period Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "দোকানের হিসাব ও রিপোর্ট",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // 1) Period selector at the top using FilterChipRow
                FilterChipRow(
                    options = periods,
                    selected = selectedPeriod,
                    onSelect = { selectedPeriod = it }
                )
            }
        }

        // 2) PROFIT & LOSS WATERFALL
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "লাভ ও ক্ষতি বিবরণী ($selectedPeriod)")

                ProfitLossWaterfallCard(
                    salesTotal = periodSalesTotalPoisha,
                    costTotal = periodCostPoisha,
                    grossProfit = periodGrossProfitPoisha,
                    expensesTotal = periodExpenseTotalPoisha,
                    netProfit = periodNetProfitPoisha,
                    config = config
                )
            }
        }

        // 3) CASH DRAWER RECONCILIATION CARD
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "দৈনিক ক্যাশ ড্রয়ার মিলকরণ (ক্যাশ ক্লোজিং)")

                CashDrawerReconciliationCard(
                    cashSales = cashSalesPoisha,
                    mfsSales = mfsSalesPoisha,
                    expenses = periodExpenseTotalPoisha,
                    dueSales = dueSalesPoisha,
                    netDrawerCash = netDrawerCashPoisha,
                    config = config
                )
            }
        }

        // 4) STOCK VALUATION
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "স্টক ভ্যালুয়েশন (দোকানের মোট ইনভেস্টমেন্ট)")

                StockValuationCard(
                    totalCost = totalStockCostPoisha,
                    totalSaleValue = totalStockSaleValuePoisha,
                    expectedProfit = expectedFutureProfitPoisha,
                    productCount = products.size,
                    config = config
                )
            }
        }

        // 5) TOP PRODUCTS RANKED LIST
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "সর্বাধিক বিক্রিত পণ্য ($selectedPeriod)")

                TopProductsCard(
                    topProducts = topProducts,
                    config = config
                )
            }
        }

        // 6) EMPLOYEE SALES BREAKDOWN (কর্মচারীভিত্তিক বিক্রয় ও পারফরম্যান্স)
        if (!isStaff) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "কর্মচারীভিত্তিক বিক্রয় হিসাব ($selectedPeriod)")

                    EmployeeSalesCard(
                        summaries = staffSalesSummaries,
                        totalPeriodSales = periodSalesTotalPoisha,
                        config = config
                    )
                }
            }
        }
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
