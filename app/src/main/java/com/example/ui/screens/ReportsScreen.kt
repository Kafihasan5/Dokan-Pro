package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.util.Formatters
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
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
    val totalDue by viewModel.totalDue.collectAsState()

    var selectedPeriod by remember { mutableStateOf("আজ") } // আজ, এই সপ্তাহ, এই মাস, সব সময়
    val periods = listOf("আজ", "এই সপ্তাহ", "এই মাস", "সব সময়")

    // Filter range calculation
    val now = System.currentTimeMillis()
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

    // Stock Valuation (All current active products)
    val totalStockCostPoisha = products.sumOf { (it.stockQty * it.purchasePricePoisha).toLong() }
    val totalStockSaleValuePoisha = products.sumOf { (it.stockQty * it.salePricePoisha).toLong() }
    val expectedFutureProfitPoisha = (totalStockSaleValuePoisha - totalStockCostPoisha).coerceAtLeast(0)

    // Top Products ranking by Qty
    val productSalesMap = mutableMapOf<String, Pair<Double, Long>>() // name -> (qty, revenue)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "দোকানের হিসাব ও রিপোর্ট",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Time Period Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods) { p ->
                    FilterChip(
                        selected = selectedPeriod == p,
                        onClick = { selectedPeriod = p },
                        label = { Text(p, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Section 1: Profit & Loss Statement (P&L)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("লাভ ও ক্ষতি বিবরণী ($selectedPeriod)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ReportDetailRow("মোট বিক্রয়", Formatters.formatMoney(periodSalesTotalPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = null)
                    ReportDetailRow("বিক্রিত পণ্যের ক্রয়মূল্য", "-${Formatters.formatMoney(periodCostPoisha, config.useBengaliNumerals, config.currencySymbol)}", isPositive = false)
                    ReportDetailRow("মোট গ্রস লাভ", Formatters.formatMoney(periodGrossProfitPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = true)
                    ReportDetailRow("দোকানের অন্যান্য খরচ", "-${Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol)}", isPositive = false)

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("প্রকৃত নিট লাভ:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = Formatters.formatMoney(periodNetProfitPoisha, config.useBengaliNumerals, config.currencySymbol),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (periodNetProfitPoisha >= 0) StatusSuccess else StatusDanger
                        )
                    }
                }
            }
        }

        // Section 2: Stock Valuation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("স্টক ভ্যালুয়েশন (দোকানের মোট ইনভেস্টমেন্ট)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ReportDetailRow("মোট সক্রিয় পণ্য সংখ্যা", "${if (config.useBengaliNumerals) Formatters.toBengaliDigits(products.size.toString()) else products.size} টি আইটেম", isPositive = null)
                    ReportDetailRow("স্টকের মোট ক্রয়মূল্য (ইনভেস্টমেন্ট)", Formatters.formatMoney(totalStockCostPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = null)
                    ReportDetailRow("স্টকের আনুমানিক বিক্রয়মূল্য", Formatters.formatMoney(totalStockSaleValuePoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = null)

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সম্ভাব্য মোট স্টক লাভ:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = Formatters.formatMoney(expectedFutureProfitPoisha, config.useBengaliNumerals, config.currencySymbol),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = StatusSuccess
                        )
                    }
                }
            }
        }

        // Section 3: Top Selling Products
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("সর্বাধিক বিক্রিত পণ্য ($selectedPeriod)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (topProducts.isEmpty()) {
                        Text("এই সময়ে কোনো বিক্রির রেকর্ড নেই", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        for ((idx, entry) in topProducts.withIndex()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${idx + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column {
                                        Text(entry.key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${Formatters.formatQty(entry.value.first, "", config.useBengaliNumerals)} বিক্রয়",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    Formatters.formatMoney(entry.value.second, config.useBengaliNumerals, config.currencySymbol),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Daily Cash Register Closing simulation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("দৈনিক ক্যাশ ড্রয়ার মিলকরণ (ক্যাশ ক্লোজিং)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = StatusSuccess)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val cashSalesPoisha = periodSales.filter { it.paymentMethod == "cash" }.sumOf { it.paidAmountPoisha }
                    val mfsSalesPoisha = periodSales.filter { it.paymentMethod == "mfs" }.sumOf { it.paidAmountPoisha }
                    val dueSalesPoisha = periodSales.sumOf { it.dueAmountPoisha }

                    ReportDetailRow("নগদ বিক্রয় জমা (Cash in Drawer)", Formatters.formatMoney(cashSalesPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = true)
                    ReportDetailRow("বিকাশ/নগদ ডিজিটাল জমা (MFS)", Formatters.formatMoney(mfsSalesPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = true)
                    ReportDetailRow("বাকিতে বিক্রি (খাতা)", Formatters.formatMoney(dueSalesPoisha, config.useBengaliNumerals, config.currencySymbol), isPositive = null)
                    ReportDetailRow("নগদ খরচ কর্তন", "-${Formatters.formatMoney(periodExpenseTotalPoisha, config.useBengaliNumerals, config.currencySymbol)}", isPositive = false)

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    val netDrawerCashPoisha = (cashSalesPoisha - periodExpenseTotalPoisha).coerceAtLeast(0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ক্যাশ বাক্সে থাকার কথা:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = Formatters.formatMoney(netDrawerCashPoisha, config.useBengaliNumerals, config.currencySymbol),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportDetailRow(label: String, value: String, isPositive: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (isPositive) {
                true -> StatusSuccess
                false -> StatusDanger
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
