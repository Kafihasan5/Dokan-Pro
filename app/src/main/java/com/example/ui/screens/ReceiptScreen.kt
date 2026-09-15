package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.components.ProductReturnDialog
import com.example.util.Formatters
import com.example.util.InvoiceImageHelper

@Composable
fun ReceiptScreen(
    viewModel: PaponViewModel,
    config: ShopConfig,
    onNewSale: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sale by viewModel.lastCompletedSale.collectAsState()
    val saleItems by viewModel.lastCompletedSaleItems.collectAsState()
    val customers by viewModel.customers.collectAsState()

    if (sale == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো রসিদ পাওয়া যায়নি")
        }
        return
    }

    val currentSale = sale!!
    val currentCustomer = remember(customers, currentSale.customerId) {
        customers.firstOrNull { it.id == currentSale.customerId }
    }
    val customerPhone = currentCustomer?.phone

    val customerBalance by viewModel.getCustomerBalanceFlow(currentSale.customerId ?: -1L).collectAsState(initial = 0L)
    val previousDue = remember(customerBalance, currentSale.dueAmountPoisha) {
        if (currentSale.customerId != null) {
            (customerBalance - currentSale.dueAmountPoisha).coerceAtLeast(0L)
        } else 0L
    }

    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var cachedBitmap by remember(currentSale.id, saleItems.size, currentSale.isReturned) { mutableStateOf<Bitmap?>(null) }

    fun getOrGenerateBitmap(): Bitmap {
        val existing = cachedBitmap
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        val newBmp = InvoiceImageHelper.generateSaleInvoiceBitmap(
            context = context,
            config = config,
            sale = currentSale,
            items = saleItems,
            customerPreviousDue = previousDue
        )
        cachedBitmap = newBmp
        return newBmp
    }

    fun handleSendWhatsApp() {
        val bmp = getOrGenerateBitmap()
        val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${currentSale.invoiceNo}")
        val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, currentSale, previousDue)
        InvoiceImageHelper.shareToWhatsApp(context, uri, customerPhone, caption)
    }

    fun handleSaveToPhone() {
        val bmp = getOrGenerateBitmap()
        InvoiceImageHelper.saveBitmapToGallery(context, bmp, "invoice_${currentSale.invoiceNo}")
    }

    fun handleShareGeneral() {
        val bmp = getOrGenerateBitmap()
        val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${currentSale.invoiceNo}")
        val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, currentSale, previousDue)
        InvoiceImageHelper.shareToGeneral(context, uri, caption)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            // Status Badge
            if (currentSale.isReturned) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(StatusDanger.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("এই বিক্রয়টি ফেরত (Returned) হিসেবে চিহ্নিত!", color = StatusDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(StatusSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("বিক্রয় সফলভাবে সংরক্ষিত হয়েছে!", color = StatusSuccess, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // IMAGE ACTIONS CARD (হোয়াটসঅ্যাপ ও ফোনে সেভ)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ডিজিটাল ইনভয়েস ছবি (JPEG/PNG)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary WhatsApp Share Button
                    Button(
                        onClick = { handleSendWhatsApp() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (customerPhone != null) "হোয়াটসঅ্যাপে ছবি পাঠান (${currentSale.customerName ?: ""})" else "হোয়াটসঅ্যাপে ছবি পাঠান",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary: Save to Phone & Preview & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { handleSaveToPhone() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ছবি সেভ", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                getOrGenerateBitmap()
                                showImagePreviewDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্রিভিউ", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = { handleShareGeneral() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "অন্যান্য মাধ্যমে শেয়ার",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // DIGITAL INVOICE CARD (Modern Luxury POS Theme)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top Accent Strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(
                                when {
                                    currentSale.isReturned -> StatusDanger
                                    currentSale.dueAmountPoisha > 0 -> Color(0xFFF59E0B)
                                    else -> Color(0xFF0F766E)
                                }
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Shop Initial Emblem
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        currentSale.isReturned -> StatusDanger.copy(alpha = 0.15f)
                                        currentSale.dueAmountPoisha > 0 -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        else -> Color(0xFF0F766E).copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = config.shopName.trim().take(1).ifEmpty { "D" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    currentSale.isReturned -> StatusDanger
                                    currentSale.dueAmountPoisha > 0 -> Color(0xFFD97706)
                                    else -> Color(0xFF0F766E)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Shop Name
                        Text(
                            text = config.shopName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (config.tagline.isNotBlank()) {
                            Text(
                                text = config.tagline,
                                fontSize = 12.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }

                        // Address & Phone
                        val addr = if (config.shopAddress.isNotBlank()) "${config.shopAddress}  •  " else ""
                        Text(
                            text = "${addr}মোবাইল: ${config.shopPhone}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Badge Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                currentSale.isReturned -> StatusDanger.copy(alpha = 0.12f)
                                currentSale.dueAmountPoisha > 0 -> Color(0xFFFFFBEB)
                                else -> Color(0xFFECFDF5)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    currentSale.isReturned -> StatusDanger.copy(alpha = 0.6f)
                                    currentSale.dueAmountPoisha > 0 -> Color(0xFFFCD34D)
                                    else -> Color(0xFF6EE7B7)
                                }
                            )
                        ) {
                            Text(
                                text = when {
                                    currentSale.isReturned -> "❌ ফেরতকৃত মেমো (RETURNED)"
                                    currentSale.dueAmountPoisha > 0 -> "বাকি বিক্রয় মেমো (CREDIT INVOICE)"
                                    else -> "ক্যাশ মেমো / বিক্রয় ইনভয়েস"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    currentSale.isReturned -> StatusDanger
                                    currentSale.dueAmountPoisha > 0 -> Color(0xFFB45309)
                                    else -> Color(0xFF047857)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metadata Card (Billed To & Invoice Info)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left Column: Customer
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ক্রেতার তথ্য",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentSale.customerName ?: "সাধারণ খরিদ্দার",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (customerPhone != null) {
                                        Text(
                                            text = customerPhone,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Right Column: Invoice Info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "ইনভয়েস বিবরণ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "#${currentSale.invoiceNo}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = Formatters.formatDateTime(currentSale.saleDate, config.useBengaliNumerals),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val pMethodName = when (currentSale.paymentMethod) {
                                        "due" -> "বাকি"
                                        "bkash" -> "বিকাশ"
                                        "nagad" -> "নগদ"
                                        "bank" -> "ব্যাংক"
                                        else -> "নগদ"
                                    }
                                    Text(
                                        text = "পেমেন্ট: $pMethodName",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (currentSale.paymentMethod == "due") StatusDanger else Color(0xFF047857)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Table Header Row
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "পণ্য ও দর",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(2f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "পরিমাণ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "মোট",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Item Rows
                        for (item in saleItems) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(2f)) {
                                    Text(
                                        text = item.productName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "@ ${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${item.unitName}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals).trim(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Calculations Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Subtotal
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("উপ-মোট (Subtotal):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Formatters.formatMoney(currentSale.subtotalPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (currentSale.discountPoisha > 0) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ছাড় / ডিসকাউন্ট (-):", fontSize = 12.sp, color = StatusDanger)
                                        Text("-${Formatters.formatMoney(currentSale.discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                                    }
                                }

                                if (currentSale.vatPoisha > 0) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ভ্যাট / ট্যাক্স (+):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("+${Formatters.formatMoney(currentSale.vatPoisha, config.useBengaliNumerals, config.currencySymbol)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Grand Total Box (High-Contrast Hero Row)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F172A)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "সর্বমোট বিল:",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = Formatters.formatMoney(currentSale.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Paid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("পরিশোধিত (Paid):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF047857))
                                    Text(Formatters.formatMoney(currentSale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                }

                                // Due
                                if (currentSale.dueAmountPoisha > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = StatusDanger.copy(alpha = 0.1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusDanger.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("আজকের বকেয়া বাকি:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                                                Text(Formatters.formatMoney(currentSale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                                            }
                                            if (previousDue > 0) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("পূর্বের বকেয়া:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(Formatters.formatMoney(previousDue, config.useBengaliNumerals, config.currencySymbol), fontSize = 11.sp)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("সর্বমোট বকেয়া বাকি:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                                    Text(Formatters.formatMoney(previousDue + currentSale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "✨ ধন্যবাদ! আপনার কেনাকাটা শুভ হোক ✨",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Dokan Pro ডিজিটাল ইনভয়েস সিস্টেম",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Return Sale Action Button (If not already returned)
        if (!currentSale.isReturned) {
            item {
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showReturnDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFD97706))
                ) {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("পণ্য ফেরত / রিটার্ন নিন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Navigation Buttons
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onGoHome,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("হোমে যান")
                }

                Button(
                    onClick = onNewSale,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("নতুন বিক্রয়")
                }
            }
        }
    }

    // IMAGE PREVIEW DIALOG
    if (showImagePreviewDialog && cachedBitmap != null) {
        Dialog(
            onDismissRequest = { showImagePreviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ইনভয়েস ছবি প্রিভিউ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { showImagePreviewDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable Image
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Image(
                            bitmap = cachedBitmap!!.asImageBitmap(),
                            contentDescription = "ইনভয়েস ছবি",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showImagePreviewDialog = false
                                handleSendWhatsApp()
                            },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("হোয়াটসঅ্যাপ", color = Color.White)
                        }

                        Button(
                            onClick = {
                                handleSaveToPhone()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সেভ করুন")
                        }
                    }
                }
            }
        }
    }

    // PRODUCT RETURN DIALOG
    if (showReturnDialog) {
        ProductReturnDialog(
            sale = currentSale,
            items = saleItems,
            config = config,
            onDismiss = { showReturnDialog = false },
            onConfirmReturn = { returnedMap ->
                showReturnDialog = false
                viewModel.returnSaleItems(currentSale.id, returnedMap) {
                    cachedBitmap = null
                }
            }
        )
    }
}
