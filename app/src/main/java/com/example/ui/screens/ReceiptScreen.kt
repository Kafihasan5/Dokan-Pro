package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.PaponViewModel
import com.example.ui.ShopConfig
import com.example.ui.components.ProductReturnDialog
import com.example.ui.theme.*
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
    val lastCashTendered by viewModel.lastCashTenderedPoisha.collectAsState()
    val lastChangeReturn by viewModel.lastChangeReturnPoisha.collectAsState()

    if (sale == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো রসিদ পাওয়া যায়নি", style = MaterialTheme.typography.bodyLarge)
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
    var cachedBitmap by remember(currentSale.id, saleItems.size, currentSale.isReturned, lastCashTendered, lastChangeReturn) { mutableStateOf<Bitmap?>(null) }

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
            customerPreviousDue = previousDue,
            cashTenderedPoisha = lastCashTendered,
            changeReturnPoisha = lastChangeReturn
        )
        cachedBitmap = newBmp
        return newBmp
    }

    fun handleSendWhatsApp() {
        try {
            val bmp = getOrGenerateBitmap()
            val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${currentSale.invoiceNo}")
            val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, currentSale, previousDue)
            InvoiceImageHelper.shareToWhatsApp(context, uri, customerPhone, caption)
        } catch (e: Exception) {
            Toast.makeText(context, "হোয়াটসঅ্যাপে শেয়ার করতে সমস্যা হয়েছে: ${e.message ?: "অন্য মাধ্যমে শেয়ার করুন"}", Toast.LENGTH_LONG).show()
        }
    }

    fun handleSaveToPhone() {
        try {
            val bmp = getOrGenerateBitmap()
            InvoiceImageHelper.saveBitmapToGallery(context, bmp, "invoice_${currentSale.invoiceNo}")
        } catch (e: Exception) {
            Toast.makeText(context, "ছবি সেভ করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleShareGeneral() {
        try {
            val bmp = getOrGenerateBitmap()
            val uri = InvoiceImageHelper.saveBitmapToCache(context, bmp, "invoice_${currentSale.invoiceNo}")
            val caption = InvoiceImageHelper.buildSaleInvoiceCaption(config, currentSale, previousDue)
            InvoiceImageHelper.shareToGeneral(context, uri, caption)
        } catch (e: Exception) {
            Toast.makeText(context, "শেয়ার করতে সমস্যা হয়েছে: ${e.message ?: "অন্য মাধ্যমে শেয়ার করুন"}", Toast.LENGTH_LONG).show()
        }
    }

    // Success checkmark animation (scale and fade in over 400ms)
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationStarted = true
    }

    val checkScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.3f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "check_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "check_alpha"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.md,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // 1) SUCCESS MOMENT AT THE TOP: Animated checkmark + text on success container
        item {
            Surface(
                shape = RoundedCornerShape(Radius.pill),
                color = if (currentSale.isReturned) MaterialTheme.dokanColors.dangerContainer
                else MaterialTheme.dokanColors.successContainer,
                border = BorderStroke(
                    1.dp,
                    if (currentSale.isReturned) MaterialTheme.dokanColors.danger.copy(alpha = 0.3f)
                    else MaterialTheme.dokanColors.success.copy(alpha = 0.3f)
                ),
                modifier = Modifier.padding(top = Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .scale(checkScale)
                            .alpha(checkAlpha)
                    ) {
                        Icon(
                            imageVector = if (currentSale.isReturned) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (currentSale.isReturned) MaterialTheme.dokanColors.danger else MaterialTheme.dokanColors.success,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = if (currentSale.isReturned) "এই বিক্রয়টি ফেরত (Returned) হিসেবে চিহ্নিত!"
                        else "বিক্রয় সফলভাবে সংরক্ষিত হয়েছে!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSale.isReturned) MaterialTheme.dokanColors.danger else MaterialTheme.dokanColors.success
                    )
                }
            }
        }

        // 2) RECEIPT PREVIEW: Paper-styled card with torn-edge effect at bottom
        item {
            PaperReceiptCard(
                sale = currentSale,
                items = saleItems,
                config = config,
                cashTenderedPoisha = lastCashTendered,
                changeReturnPoisha = lastChangeReturn,
                onReturnClick = { showReturnDialog = true }
            )
        }

        // 3) ACTIONS: 2x2 Grid of equal tiles
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Row 1: WhatsApp (Most prominent filled primary) & Save to Phone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    ActionTile(
                        icon = Icons.Default.Share,
                        label = "হোয়াটসঅ্যাপে ছবি পাঠান",
                        isPrimary = true,
                        onClick = { handleSendWhatsApp() },
                        modifier = Modifier.weight(1f)
                    )

                    ActionTile(
                        icon = Icons.Default.FileDownload,
                        label = "ছবি সেভ",
                        isPrimary = false,
                        onClick = { handleSaveToPhone() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Preview & Share General
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    ActionTile(
                        icon = Icons.Default.Visibility,
                        label = "প্রিভিউ",
                        isPrimary = false,
                        onClick = {
                            getOrGenerateBitmap()
                            showImagePreviewDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ActionTile(
                        icon = Icons.Default.Share,
                        label = "অন্যান্য মাধ্যমে শেয়ার",
                        isPrimary = false,
                        onClick = { handleShareGeneral() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4) BOTTOM ROW OF TEXT BUTTONS: হোমে যান and নতুন বিক্রয়
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onGoHome) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("হোমে যান", style = MaterialTheme.typography.labelLarge)
                }

                TextButton(onClick = onNewSale) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("নতুন বিক্রয়", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Fullscreen Image Preview Dialog
    if (showImagePreviewDialog && cachedBitmap != null) {
        Dialog(
            onDismissRequest = { showImagePreviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = cachedBitmap!!.asImageBitmap(),
                        contentDescription = "রসিদ প্রিভিউ",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.lg)
                    )

                    IconButton(
                        onClick = { showImagePreviewDialog = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.lg)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
                    }
                }
            }
        }
    }

    // Product Return Dialog
    if (showReturnDialog) {
        ProductReturnDialog(
            sale = currentSale,
            items = saleItems,
            config = config,
            onDismiss = { showReturnDialog = false },
            onConfirmReturn = { returnedMap ->
                viewModel.returnSaleItems(currentSale.id, returnedMap) {
                    showReturnDialog = false
                }
            }
        )
    }
}

// ==============================================================================
// 2. PAPER-STYLED RECEIPT CARD WITH TORN-EDGE CANVAS
// ==============================================================================
@Composable
private fun PaperReceiptCard(
    sale: com.example.data.entity.Sale,
    items: List<com.example.data.entity.SaleItem>,
    config: ShopConfig,
    cashTenderedPoisha: Long = 0L,
    changeReturnPoisha: Long = 0L,
    onReturnClick: () -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.dokanColors.border
    val parentBgColor = MaterialTheme.colorScheme.background

    Surface(
        shape = RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(1, RoundedCornerShape(Radius.sm))
    ) {
        Column {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Shop Header in memo
                Text(
                    text = config.shopName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (config.shopAddress.isNotBlank()) {
                    Text(
                        text = config.shopAddress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Meta Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "চালান নং: #${sale.invoiceNo}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = Formatters.formatDateTime(sale.saleDate, config.useBengaliNumerals),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val salesman = InvoiceImageHelper.getSalespersonName(sale, config)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!sale.customerName.isNullOrBlank()) {
                        Text(
                            text = "ক্রেতা: ${sale.customerName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "ক্রেতা: সাধারণ খরিদ্দার",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "বিক্রেতা: $salesman",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Items list summary
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)} × ${Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(15.sp)
                        )
                    }
                }

                HorizontalDivider(color = borderColor, thickness = 1.dp)

                // Totals
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "মোট বিল",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(20.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("পরিশোধিত", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = Formatters.formatMoney(sale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol),
                        style = amountTextStyle(15.sp),
                        color = MaterialTheme.dokanColors.success
                    )
                }

                if (sale.paymentMethod == "cash" && cashTenderedPoisha > sale.paidAmountPoisha) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("নগদ গ্রহণ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Formatters.formatMoney(cashTenderedPoisha, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(15.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (changeReturnPoisha > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ফেরত দেওয়া হয়েছে",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.success
                            )
                            Text(
                                text = Formatters.formatMoney(changeReturnPoisha, config.useBengaliNumerals, config.currencySymbol),
                                style = amountTextStyle(16.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.dokanColors.success
                            )
                        }
                    }
                }

                if (sale.dueAmountPoisha > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "বাকি",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.dokanColors.danger
                        )
                        Text(
                            text = Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol),
                            style = amountTextStyle(16.sp),
                            color = MaterialTheme.dokanColors.danger
                        )
                    }
                }

                if (!sale.isReturned) {
                    TextButton(
                        onClick = onReturnClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.dokanColors.danger)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("পণ্য ফেরত এন্ট্রি", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.dokanColors.danger)
                    }
                }
            }

            // Torn-edge effect drawn with Canvas as a row of small semicircles
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            ) {
                val circleRadius = 6.dp.toPx()
                val diameter = circleRadius * 2
                val count = (size.width / diameter).toInt() + 2
                for (i in 0..count) {
                    drawCircle(
                        color = parentBgColor,
                        radius = circleRadius,
                        center = Offset(x = i * diameter - circleRadius, y = size.height)
                    )
                }
            }
        }
    }
}

// ==============================================================================
// 3. ACTION TILE COMPONENT
// ==============================================================================
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.surfaceAlt
    val contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.dokanColors.border

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
