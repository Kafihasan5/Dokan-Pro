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
    var cachedBitmap by remember(currentSale.id, saleItems.size) { mutableStateOf<Bitmap?>(null) }

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
            // Success Badge
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

        // 58mm Thermal Paper Card (Visual On-Screen Slip)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = config.shopName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.shopAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "মোবাইল: ${config.shopPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (config.tagline.isNotBlank()) {
                        Text(
                            text = config.tagline,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ইনভয়েস: ${currentSale.invoiceNo}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(Formatters.formatDateTime(currentSale.saleDate, config.useBengaliNumerals), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (!currentSale.customerName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ক্রেতা: ${currentSale.customerName}", fontSize = 12.sp)
                            val paymentTitle = when (currentSale.paymentMethod) {
                                "due" -> "বাকি"
                                "mfs" -> "বিকাশ/নগদ"
                                "card" -> "কার্ড"
                                else -> "নগদ"
                            }
                            Text("পেমেন্ট: $paymentTitle", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Items Table
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("পণ্য ও দর", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                        Text("পরিমাণ", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("মোট", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    for (item in saleItems) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text(item.productName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)}/${item.unitName}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                Formatters.formatQty(item.qty, "", config.useBengaliNumerals).trim(),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Calculations
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("উপ-মোট:", fontSize = 13.sp)
                        Text(Formatters.formatMoney(currentSale.subtotalPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 13.sp)
                    }

                    if (currentSale.discountPoisha > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ছাড়:", fontSize = 13.sp, color = StatusDanger)
                            Text("-${Formatters.formatMoney(currentSale.discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", fontSize = 13.sp, color = StatusDanger)
                        }
                    }

                    if (currentSale.vatPoisha > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ভ্যাট:", fontSize = 13.sp)
                            Text(Formatters.formatMoney(currentSale.vatPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সর্বমোট প্রদেয়:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Formatters.formatMoney(currentSale.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("পরিশোধিত:", fontSize = 13.sp)
                        Text(Formatters.formatMoney(currentSale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 13.sp)
                    }

                    if (currentSale.dueAmountPoisha > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("আজকের বাকি:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                            Text(Formatters.formatMoney(currentSale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                        }
                        if (previousDue > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("পূর্বের বকেয়া বাকি:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formatters.formatMoney(previousDue, config.useBengaliNumerals, config.currencySymbol), fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("মোট বাকি:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                                Text(Formatters.formatMoney(previousDue + currentSale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusDanger)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("--- ধন্যবাদ! আবার আসবেন ---", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("দোকান প্রো ডিজিটাল ইনভয়েস সিস্টেম", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        // Navigation Buttons
        item {
            Spacer(modifier = Modifier.height(20.dp))

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
}
