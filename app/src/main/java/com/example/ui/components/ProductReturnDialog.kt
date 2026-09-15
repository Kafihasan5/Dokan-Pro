package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.Sale
import com.example.data.entity.SaleItem
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.util.Formatters

@Composable
fun ProductReturnDialog(
    sale: Sale,
    items: List<SaleItem>,
    config: ShopConfig,
    onDismiss: () -> Unit,
    onConfirmReturn: (returnedMap: Map<Long, Double>) -> Unit
) {
    var isFullInvoiceReturn by remember { mutableStateOf(false) }

    // Map of saleItemId to return qty
    val returnQtyMap = remember {
        mutableStateMapOf<Long, Double>().apply {
            items.forEach { put(it.id, it.qty) }
        }
    }

    // Set of selected saleItemIds
    val selectedItemIds = remember {
        mutableStateListOf<Long>().apply {
            items.forEach { add(it.id) }
        }
    }

    // Calculate total refund
    val totalRefundPoisha = remember(selectedItemIds.toList(), returnQtyMap.toMap()) {
        selectedItemIds.sumOf { itemId ->
            val item = items.find { it.id == itemId }
            val qty = returnQtyMap[itemId] ?: 0.0
            if (item != null) (qty * item.unitPricePoisha).toLong() else 0L
        }
    }

    val hasCustomerDue = sale.dueAmountPoisha > 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AssignmentReturn,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "পণ্য ফেরত / রিটার্ন",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ইনভয়েস: ${sale.invoiceNo}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বাতিল")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Full Invoice Return Switch Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isFullInvoiceReturn = !isFullInvoiceReturn
                                if (isFullInvoiceReturn) {
                                    selectedItemIds.clear()
                                    items.forEach {
                                        selectedItemIds.add(it.id)
                                        returnQtyMap[it.id] = it.qty
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "সম্পূর্ণ ইনভয়েস ফেরত নিন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = isFullInvoiceReturn,
                            onCheckedChange = { checked ->
                                isFullInvoiceReturn = checked
                                if (checked) {
                                    selectedItemIds.clear()
                                    items.forEach {
                                        selectedItemIds.add(it.id)
                                        returnQtyMap[it.id] = it.qty
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isFullInvoiceReturn) "ইনভয়েসের সকল পণ্য ফেরত হবে:" else "যে পণ্যটি ফেরত নেবেন সেটি নির্বাচন করুন:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Product items list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = selectedItemIds.contains(item.id)
                        val returnQty = returnQtyMap[item.id] ?: item.qty
                        val itemRefundPoisha = (returnQty * item.unitPricePoisha).toLong()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = if (isSelected)
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            else null
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            enabled = !isFullInvoiceReturn,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (!selectedItemIds.contains(item.id)) selectedItemIds.add(item.id)
                                                } else {
                                                    selectedItemIds.remove(item.id)
                                                }
                                            }
                                        )
                                        Column {
                                            Text(
                                                text = item.productName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "ক্রয়: ${Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)} • দর: ${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "ফেরত মূল্য:",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = Formatters.formatMoney(itemRefundPoisha, config.useBengaliNumerals, config.currencySymbol),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }

                                // Quantity Stepper (if item is selected and not in full invoice return mode)
                                if (isSelected && !isFullInvoiceReturn) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ফেরতের পরিমাণ:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    val step = if (item.qty % 1.0 != 0.0) 0.5 else 1.0
                                                    val current = returnQtyMap[item.id] ?: item.qty
                                                    val next = (current - step).coerceAtLeast(step)
                                                    returnQtyMap[item.id] = next
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "কমান", modifier = Modifier.size(14.dp))
                                            }

                                            Text(
                                                text = Formatters.formatQty(returnQty, item.unitName, config.useBengaliNumerals),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            )

                                            FilledTonalIconButton(
                                                onClick = {
                                                    val step = if (item.qty % 1.0 != 0.0) 0.5 else 1.0
                                                    val current = returnQtyMap[item.id] ?: item.qty
                                                    val next = (current + step).coerceAtMost(item.qty)
                                                    returnQtyMap[item.id] = next
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "বাড়ান", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Financial and Restock Impact Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBEB)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "সর্বমোট ফেরত মূল্য:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = Formatters.formatMoney(totalRefundPoisha, config.useBengaliNumerals, config.currencySymbol),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "নির্বাচিত পণ্যগুলো পুনরায় দোকানে বিক্রয়ের মূল স্টকে যুক্ত হবে।",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F)
                            )
                        }

                        if (hasCustomerDue) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = StatusDanger,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ক্রেতার বকেয়া বাকি থেকে এই ফেরতের পরিমাণ স্বয়ংক্রিয়ভাবে সমন্বয় হবে।",
                                    fontSize = 11.sp,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("বাতিল")
                    }

                    Button(
                        onClick = {
                            val mapToSubmit = mutableMapOf<Long, Double>()
                            selectedItemIds.forEach { id ->
                                val q = returnQtyMap[id] ?: 0.0
                                if (q > 0.0) mapToSubmit[id] = q
                            }
                            onConfirmReturn(mapToSubmit)
                        },
                        enabled = selectedItemIds.isNotEmpty() && totalRefundPoisha > 0L,
                        modifier = Modifier.weight(1.4f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFullInvoiceReturn) "সম্পূর্ণ ফেরত করুন" else "ফেরত নিশ্চিত করুন",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
