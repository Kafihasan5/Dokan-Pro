package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.entity.Customer
import com.example.data.entity.CustomerLedger
import com.example.data.entity.Sale
import com.example.data.entity.SaleItem
import com.example.ui.ShopConfig
import java.io.File
import java.io.FileOutputStream

object InvoiceImageHelper {

    private const val BITMAP_WIDTH = 1080

    // Design Tokens Colors
    private val Brand900 = Color.rgb(0x04, 0x23, 0x1A)
    private val Brand700 = Color.rgb(0x07, 0x6B, 0x4C)
    private val Brand500 = Color.rgb(0x0E, 0x9F, 0x6E)
    private val Brand100 = Color.rgb(0xD7, 0xF5, 0xE9)
    private val Gold500 = Color.rgb(0xE0, 0xA1, 0x06)
    private val StatusDangerColor = Color.rgb(0xDC, 0x26, 0x26)
    private val StatusWarningColor = Color.rgb(0xB4, 0x53, 0x09)
    private val StatusSuccessColor = Color.rgb(0x15, 0x80, 0x3D)
    private val SurfaceAltColor = Color.rgb(0xED, 0xF1, 0xF2)
    private val BorderColor = Color.rgb(0xE1, 0xE7, 0xE9)
    private val InkColor = Color.rgb(0x0C, 0x15, 0x12)
    private val Ink2Color = Color.rgb(0x5B, 0x6A, 0x64)
    private val Ink3Color = Color.rgb(0x8A, 0x99, 0x93)

    // Load bundled typography
    private fun getDisplayFontBold(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.anek_bangla_bold) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    private fun getBodyFontRegular(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.hind_siliguri_regular) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun getBodyFontMedium(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.hind_siliguri_medium) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun getBodyFontBold(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.hind_siliguri_bold) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    /**
     * Extracts readable salesperson or cashier name from sale tag or shop configuration.
     */
    fun getSalespersonName(sale: Sale, config: ShopConfig): String {
        val note = sale.note?.trim() ?: ""
        if (note.startsWith("staff:", ignoreCase = true)) {
            val afterStaff = note.substringAfter("staff:").trim()
            val parenStart = afterStaff.indexOf('(')
            val parenEnd = afterStaff.indexOf(')')
            if (parenStart != -1 && parenEnd > parenStart) {
                val insideName = afterStaff.substring(parenStart + 1, parenEnd).trim()
                if (insideName.isNotBlank()) return insideName
            }
            val firstPart = if (parenStart != -1) afterStaff.substring(0, parenStart).trim() else afterStaff
            if (firstPart.isNotBlank() && !firstPart.contains("@")) return firstPart
            if (firstPart.contains("@")) return firstPart.substringBefore("@")
            return "কর্মচারী"
        } else if (note.startsWith("owner:", ignoreCase = true)) {
            val afterOwner = note.substringAfter("owner:").trim()
            return if (afterOwner.isNotBlank()) afterOwner else "দোকান মালিক"
        } else if (note.contains("staff", ignoreCase = true)) {
            return "কর্মচারী"
        }

        return if (config.userRole == "staff") {
            config.staffName.ifBlank { "কর্মচারী" }
        } else {
            "দোকান মালিক"
        }
    }

    /**
     * Generate Sales Invoice Bitmap (Cash, Due, or Returned sale)
     * High-res 1080px width, 3x density sharp rendering.
     */
    fun generateSaleInvoiceBitmap(
        context: Context,
        config: ShopConfig,
        sale: Sale,
        items: List<SaleItem>,
        customerPreviousDue: Long = 0L,
        cashTenderedPoisha: Long = 0L,
        changeReturnPoisha: Long = 0L
    ): Bitmap {
        val isDue = sale.dueAmountPoisha > 0
        val hasCustomer = !sale.customerName.isNullOrBlank()
        val hasChange = sale.paymentMethod == "cash" && cashTenderedPoisha > sale.paidAmountPoisha
        val salesmanName = getSalespersonName(sale, config)

        val displayBold = getDisplayFontBold(context)
        val bodyRegular = getBodyFontRegular(context)
        val bodyMedium = getBodyFontMedium(context)
        val bodyBold = getBodyFontBold(context)

        // Height calculation for 1080px layout (increased footer & safety buffer to prevent bottom clipping)
        val headerBandHeight = if (config.tagline.isNotBlank()) 250 else 220
        val badgeHeight = 60
        val metaHeight = if (hasCustomer) 195 else 165
        val tableHeaderHeight = 56
        val itemsHeight = items.size.coerceAtLeast(1) * 76
        val calcBoxHeight = when {
            sale.isReturned -> 230
            isDue && customerPreviousDue > 0 -> 400
            isDue -> 330
            hasChange -> 320
            sale.discountPoisha > 0 || sale.vatPoisha > 0 -> 280
            else -> 230
        }
        val footerHeight = 220
        val totalHeight = headerBandHeight + badgeHeight + metaHeight + tableHeaderHeight + itemsHeight + calcBoxHeight + footerHeight + 100

        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // 1) HEADER BAND: Solid Brand700 fill
        paint.color = Brand700
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), headerBandHeight.toFloat(), paint)

        // Shop Name in Anek Bangla Bold 52px (White)
        paint.color = Color.WHITE
        paint.typeface = displayBold
        paint.textSize = 52f
        paint.textAlign = Paint.Align.CENTER
        var y = 80f
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        // Tagline (30px white at 80%)
        paint.typeface = bodyMedium
        paint.textSize = 30f
        paint.color = Color.argb(204, 255, 255, 255)
        if (config.tagline.isNotBlank()) {
            y += 44f
            canvas.drawText(config.tagline, BITMAP_WIDTH / 2f, y, paint)
        }

        // Address & Mobile (30px white at 80%)
        y += 44f
        val addressLine = if (config.shopAddress.isNotBlank()) "${config.shopAddress}  •  " else ""
        canvas.drawText("${addressLine}মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // 2) WATERMARK: Diagonal watermark
        canvas.save()
        canvas.rotate(-30f, BITMAP_WIDTH / 2f, totalHeight / 2f)
        if (sale.isReturned) {
            paint.typeface = displayBold
            paint.textSize = 220f
            paint.color = Color.argb(20, 220, 38, 38)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("ফেরত", BITMAP_WIDTH / 2f, totalHeight / 2f, paint)
        } else if (isDue) {
            paint.typeface = displayBold
            paint.textSize = 220f
            paint.color = Color.argb(16, 180, 83, 9)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("বাকি", BITMAP_WIDTH / 2f, totalHeight / 2f, paint)
        }
        canvas.restore()

        // 3) STATUS BADGE PILL
        y = headerBandHeight + 36f
        val badgeText = when {
            sale.isReturned -> "❌ ফেরতকৃত মেমো (RETURNED INVOICE)"
            isDue -> "বাকি বিক্রয় মেমো (CREDIT INVOICE)"
            else -> "ক্যাশ মেমো / বিক্রয় ইনভয়েস (CASH INVOICE)"
        }
        val badgeBg = when {
            sale.isReturned -> Color.rgb(0xFE, 0xE2, 0xE2)
            isDue -> Color.rgb(0xFE, 0xF3, 0xC7)
            else -> Brand100
        }
        val badgeTextColor = when {
            sale.isReturned -> StatusDangerColor
            isDue -> StatusWarningColor
            else -> Brand700
        }

        paint.textSize = 24f
        paint.typeface = bodyBold
        val badgeWidth = paint.measureText(badgeText) + 50f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 24f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 16f)

        paint.color = badgeBg
        canvas.drawRoundRect(badgeRect, 20f, 20f, paint)

        paint.color = badgeTextColor
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y + 6f, paint)

        // 2px Hairline Rule
        y += 34f
        paint.color = BorderColor
        paint.strokeWidth = 2f
        canvas.drawLine(50f, y, (BITMAP_WIDTH - 50).toFloat(), y, paint)

        // 4) METADATA CARD (Customer & Invoice info)
        y += 20f
        val metaRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + metaHeight.toFloat())
        paint.color = SurfaceAltColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(metaRect, 16f, 16f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = BorderColor
        canvas.drawRoundRect(metaRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        val metaY = y + 34f
        // Left: Customer Details
        paint.typeface = bodyBold
        paint.textSize = 18f
        paint.color = Ink2Color
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ক্রেতার বিবরণ / BILLED TO", 76f, metaY, paint)

        paint.textSize = 26f
        paint.typeface = bodyBold
        paint.color = InkColor
        val custName = sale.customerName?.ifBlank { "সাধারণ খরিদ্দার" } ?: "সাধারণ খরিদ্দার"
        canvas.drawText(custName, 76f, metaY + 36f, paint)

        paint.typeface = bodyRegular
        paint.textSize = 20f
        paint.color = Ink2Color
        val phoneText = if (hasCustomer) "নিয়মিত গ্রাহক" else "নগদ কাউন্টার খরিদ্দার"
        canvas.drawText(phoneText, 76f, metaY + 68f, paint)

        paint.typeface = bodyMedium
        paint.textSize = 20f
        paint.color = Ink2Color
        canvas.drawText("বিক্রয়কর্মী: $salesmanName", 76f, metaY + 100f, paint)

        // Middle Divider
        paint.color = BorderColor
        paint.strokeWidth = 1.5f
        canvas.drawLine(540f, y + 16f, 540f, y + metaHeight - 16f, paint)

        // Right: Invoice Details
        paint.typeface = bodyBold
        paint.textSize = 18f
        paint.color = Ink2Color
        canvas.drawText("ইনভয়েস বিবরণ / INVOICE INFO", 570f, metaY, paint)

        paint.textSize = 25f
        paint.typeface = bodyBold
        paint.color = InkColor
        canvas.drawText("ইনভয়েস নং: #${sale.invoiceNo}", 570f, metaY + 36f, paint)

        paint.typeface = bodyRegular
        paint.textSize = 20f
        paint.color = Ink2Color
        canvas.drawText("তারিখ: ${Formatters.formatDateTime(sale.saleDate, config.useBengaliNumerals)}", 570f, metaY + 66f, paint)

        val paymentTitle = when (sale.paymentMethod) {
            "due" -> "বাকি (Credit)"
            "bkash" -> "বিকাশ (bKash)"
            "nagad" -> "নগদ (Nagad)"
            "bank" -> "ব্যাংক (Bank)"
            else -> "নগদ (Cash)"
        }
        paint.typeface = bodyBold
        paint.textSize = 19f
        paint.color = if (sale.paymentMethod == "due") StatusDangerColor else Brand700
        canvas.drawText("পেমেন্ট মাধ্যম: $paymentTitle", 570f, metaY + 96f, paint)

        paint.typeface = bodyBold
        paint.textSize = 20f
        paint.color = Brand700
        canvas.drawText("ক্যাশিয়ার/বিক্রেতা: $salesmanName", 570f, metaY + 126f, paint)

        // 5) ITEMS TABLE HEADER BAND
        y += metaHeight + 26f
        val tableHeaderRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + tableHeaderHeight.toFloat())
        paint.color = SurfaceAltColor
        canvas.drawRoundRect(tableHeaderRect, 10f, 10f, paint)

        paint.typeface = bodyBold
        paint.textSize = 22f
        paint.color = Ink2Color
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("বিবরণ (ITEM)", 76f, y + 36f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("দর (PRICE)", 660f, y + 36f, paint)
        canvas.drawText("পরিমাণ (QTY)", 840f, y + 36f, paint)
        canvas.drawText("মোট (TOTAL)", 1010f, y + 36f, paint)

        // Items Rows (Alternating 3% grey)
        y += tableHeaderHeight + 6f
        items.forEachIndexed { index, item ->
            val rowRect = RectF(50f, y - 4f, (BITMAP_WIDTH - 50).toFloat(), y + 68f)
            if (index % 2 == 1) {
                paint.color = Color.rgb(0xF9, 0xFA, 0xFB)
                canvas.drawRect(rowRect, paint)
            }

            paint.typeface = bodyBold
            paint.textSize = 24f
            paint.color = InkColor
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(item.productName, 76f, y + 32f, paint)

            paint.typeface = bodyRegular
            paint.textSize = 21f
            paint.color = Ink2Color
            paint.textAlign = Paint.Align.RIGHT
            val unitPriceFormatted = Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol)
            canvas.drawText(unitPriceFormatted, 660f, y + 32f, paint)

            val qtyFormatted = Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals)
            canvas.drawText(qtyFormatted, 840f, y + 32f, paint)

            paint.typeface = bodyBold
            paint.textSize = 24f
            paint.color = InkColor
            val lineTotalFormatted = Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol)
            canvas.drawText(lineTotalFormatted, 1010f, y + 32f, paint)

            // Underline
            paint.color = BorderColor
            paint.strokeWidth = 1f
            canvas.drawLine(60f, y + 66f, 1020f, y + 66f, paint)

            y += 74f
        }

        // 6) TOTALS BLOCK: Right-aligned in light surfaceAlt box
        y += 18f
        val totalsBoxWidth = 560f
        val totalsBoxRect = RectF((BITMAP_WIDTH - 50 - totalsBoxWidth), y, (BITMAP_WIDTH - 50).toFloat(), y + calcBoxHeight.toFloat())
        paint.color = SurfaceAltColor
        canvas.drawRoundRect(totalsBoxRect, 16f, 16f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = BorderColor
        canvas.drawRoundRect(totalsBoxRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        var ty = y + 42f
        val leftCol = BITMAP_WIDTH - 50 - totalsBoxWidth + 30f
        val rightCol = (BITMAP_WIDTH - 80).toFloat()

        // Subtotal
        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink2Color
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("উপমোট (Subtotal):", leftCol, ty, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.subtotalPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol, ty, paint)

        // Discount
        if (sale.discountPoisha > 0) {
            ty += 38f
            paint.color = StatusSuccessColor
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("ছা়ড় (Discount):", leftCol, ty, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-${Formatters.formatMoney(sale.discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", rightCol, ty, paint)
        }

        // VAT
        if (sale.vatPoisha > 0) {
            ty += 38f
            paint.color = Ink2Color
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("ভ্যাট (VAT):", leftCol, ty, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("+${Formatters.formatMoney(sale.vatPoisha, config.useBengaliNumerals, config.currencySymbol)}", rightCol, ty, paint)
        }

        // Grand Total: সর্বমোট বিল 46px bold
        ty += 50f
        paint.color = BorderColor
        paint.strokeWidth = 2f
        canvas.drawLine(leftCol, ty - 24f, rightCol, ty - 24f, paint)

        paint.typeface = displayBold
        paint.textSize = 34f
        paint.color = InkColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সর্বমোট বিল:", leftCol, ty + 10f, paint)

        paint.textSize = 46f
        paint.color = Brand700
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol, ty + 12f, paint)

        // Paid
        ty += 60f
        paint.typeface = bodyBold
        paint.textSize = 26f
        paint.color = StatusSuccessColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("পরিশোধিত (Paid):", leftCol, ty, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol, ty, paint)

        if (sale.paymentMethod == "cash" && cashTenderedPoisha > sale.paidAmountPoisha) {
            ty += 34f
            paint.typeface = bodyRegular
            paint.textSize = 22f
            paint.color = Ink2Color
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("নগদ গ্রহণ (Tendered):", leftCol, ty, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Formatters.formatMoney(cashTenderedPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol, ty, paint)

            if (changeReturnPoisha > 0) {
                ty += 32f
                paint.typeface = bodyBold
                paint.textSize = 23f
                paint.color = StatusSuccessColor
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("ফেরত দেওয়া হয়েছে (Change):", leftCol, ty, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(Formatters.formatMoney(changeReturnPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol, ty, paint)
            }
        }

        // Due Amount Section: Warning-toned box
        if (isDue) {
            ty += 30f
            val dueBoxHeight = if (customerPreviousDue > 0) 130f else 64f
            val dueBoxRect = RectF(leftCol - 10f, ty, rightCol + 10f, ty + dueBoxHeight)
            paint.color = Color.rgb(0xFE, 0xF3, 0xC7)
            canvas.drawRoundRect(dueBoxRect, 12f, 12f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.rgb(0xFC, 0xD3, 0x4D)
            canvas.drawRoundRect(dueBoxRect, 12f, 12f, paint)
            paint.style = Paint.Style.FILL

            ty += 40f
            paint.typeface = bodyBold
            paint.textSize = 26f
            paint.color = StatusDangerColor
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("আজকের বকেয়া বাকি:", leftCol + 6f, ty, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), rightCol - 6f, ty, paint)

            if (customerPreviousDue > 0) {
                ty += 34f
                paint.typeface = bodyRegular
                paint.textSize = 21f
                paint.color = Ink2Color
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("পূর্বের বকেয়া:", leftCol + 6f, ty, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(Formatters.formatMoney(customerPreviousDue, config.useBengaliNumerals, config.currencySymbol), rightCol - 6f, ty, paint)

                ty += 34f
                paint.typeface = bodyBold
                paint.textSize = 24f
                paint.color = StatusDangerColor
                paint.textAlign = Paint.Align.LEFT
                val totalRemaining = customerPreviousDue + sale.dueAmountPoisha
                canvas.drawText("সর্বমোট বকেয়া বাকি:", leftCol + 6f, ty, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(Formatters.formatMoney(totalRemaining, config.useBengaliNumerals, config.currencySymbol), rightCol - 6f, ty, paint)
            }
        }

        // 7) FOOTER (Calculated with safe margin so bottom text and ribbon never get cut off)
        y = (totalHeight - 190).toFloat()
        paint.color = BorderColor
        paint.strokeWidth = 2f
        canvas.drawLine(50f, y, (BITMAP_WIDTH - 50).toFloat(), y, paint)

        y += 40f
        paint.typeface = bodyBold
        paint.textSize = 26f
        paint.color = InkColor
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("✨ ধন্যবাদ! আপনার কেনাকাটা শুভ হোক ✨", BITMAP_WIDTH / 2f, y, paint)

        y += 36f
        paint.typeface = bodyRegular
        paint.textSize = 22f
        paint.color = Ink2Color
        canvas.drawText("যেকোনো প্রয়োজনে কল করুন: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        y += 34f
        paint.textSize = 24f
        paint.color = Ink3Color
        canvas.drawText("Dokan Pro ডিজিটাল ইনভয়েস সিস্টেম", BITMAP_WIDTH / 2f, y, paint)

        // Bottom Accent Ribbon (14px from bottom edge, 60px+ away from last text)
        paint.color = Brand700
        canvas.drawRect(0f, (totalHeight - 14).toFloat(), BITMAP_WIDTH.toFloat(), totalHeight.toFloat(), paint)

        return bitmap
    }

    /**
     * Generate Due Statement / Khata Slip Bitmap (বাকি খাতা হিসাব বিবরণী)
     */
    fun generateDueStatementBitmap(
        context: Context,
        config: ShopConfig,
        customer: Customer,
        ledgerItems: List<CustomerLedger>,
        currentDue: Long
    ): Bitmap {
        val displayBold = getDisplayFontBold(context)
        val bodyRegular = getBodyFontRegular(context)
        val bodyBold = getBodyFontBold(context)

        val recentLedger = ledgerItems.take(8)
        val headerHeight = 240
        val custInfoHeight = 130
        val dueCardHeight = 160
        val tableHeaderHeight = 56
        val ledgerRowsHeight = (recentLedger.size.coerceAtLeast(1) * 68)
        val footerHeight = 140
        val totalHeight = headerHeight + custInfoHeight + dueCardHeight + tableHeaderHeight + ledgerRowsHeight + footerHeight

        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // Top Red Accent Band
        paint.color = StatusDangerColor
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), 180f, paint)

        var y = 75f
        paint.typeface = displayBold
        paint.textSize = 50f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        y += 40f
        paint.typeface = bodyRegular
        paint.textSize = 26f
        paint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("${config.shopAddress} | মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // Badge
        y = 215f
        val badgeText = "বাকি খাতার হিসাব বিবরণী (STATEMENT)"
        paint.typeface = bodyBold
        paint.textSize = 24f
        val badgeWidth = paint.measureText(badgeText) + 50f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 24f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 16f)
        paint.color = Color.rgb(0xFE, 0xE2, 0xE2)
        canvas.drawRoundRect(badgeRect, 18f, 18f, paint)

        paint.color = StatusDangerColor
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y + 6f, paint)

        // Customer Info Card
        y += 45f
        val infoRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + 100f)
        paint.color = SurfaceAltColor
        canvas.drawRoundRect(infoRect, 14f, 14f, paint)

        paint.typeface = bodyBold
        paint.textSize = 26f
        paint.color = InkColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সম্মানিত ক্রেতা: ${customer.name}", 76f, y + 42f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink2Color
        canvas.drawText("মোবাইল: ${customer.phone}", 1004f, y + 42f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 21f
        canvas.drawText("বিবরণী তারিখ: ${Formatters.formatBengaliDate(System.currentTimeMillis())}", 76f, y + 78f, paint)

        // Big Total Due Card
        y += 120f
        val dueCardRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + 140f)
        paint.color = Color.rgb(0xFF, 0xF1, 0xF2)
        canvas.drawRoundRect(dueCardRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(0xFD, 0xA4, 0xAF)
        canvas.drawRoundRect(dueCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = bodyBold
        paint.textSize = 28f
        paint.color = Color.rgb(0x9F, 0x12, 0x39)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("বর্তমানে মোট বকেয়া বাকি", BITMAP_WIDTH / 2f, y + 50f, paint)

        paint.typeface = displayBold
        paint.textSize = 56f
        paint.color = StatusDangerColor
        canvas.drawText(Formatters.formatMoney(currentDue, config.useBengaliNumerals, config.currencySymbol), BITMAP_WIDTH / 2f, y + 114f, paint)

        // Footer
        y = (totalHeight - 75).toFloat()
        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink3Color
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Dokan Pro ডিজিটাল ইনভয়েস সিস্টেম", BITMAP_WIDTH / 2f, y, paint)

        paint.color = StatusDangerColor
        canvas.drawRect(0f, (totalHeight - 12).toFloat(), BITMAP_WIDTH.toFloat(), totalHeight.toFloat(), paint)

        return bitmap
    }

    /**
     * Generate Payment Collection Receipt Bitmap (বাকি আদায় / টাকা জমার রসিদ)
     */
    fun generatePaymentReceiptBitmap(
        context: Context,
        config: ShopConfig,
        customer: Customer,
        amountPoisha: Long,
        previousDuePoisha: Long,
        remainingDuePoisha: Long,
        paymentMethod: String = "নগদ",
        note: String? = null
    ): Bitmap {
        val displayBold = getDisplayFontBold(context)
        val bodyRegular = getBodyFontRegular(context)
        val bodyBold = getBodyFontBold(context)

        val totalHeight = 880
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // Top Brand700 Header
        paint.color = Brand700
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), 180f, paint)

        var y = 75f
        paint.typeface = displayBold
        paint.textSize = 50f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        y += 40f
        paint.typeface = bodyRegular
        paint.textSize = 26f
        paint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("${config.shopAddress} | মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // Badge
        y = 215f
        val badgeText = "টাকা জমা ও বাকি আদায় ভাউচার"
        paint.typeface = bodyBold
        paint.textSize = 24f
        val badgeWidth = paint.measureText(badgeText) + 50f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 24f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 16f)
        paint.color = Brand100
        canvas.drawRoundRect(badgeRect, 18f, 18f, paint)

        paint.color = Brand700
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y + 6f, paint)

        // Customer & Meta Info Card
        y += 45f
        val infoRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + 110f)
        paint.color = SurfaceAltColor
        canvas.drawRoundRect(infoRect, 14f, 14f, paint)

        paint.typeface = bodyBold
        paint.textSize = 26f
        paint.color = InkColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সম্মানিত ক্রেতা: ${customer.name}", 76f, y + 44f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink2Color
        canvas.drawText("মোবাইল: ${customer.phone}", 1004f, y + 44f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 21f
        canvas.drawText("তারিখ: ${Formatters.formatDateTime(System.currentTimeMillis(), config.useBengaliNumerals)}", 76f, y + 84f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("পেমেন্ট মাধ্যম: $paymentMethod", 1004f, y + 84f, paint)

        // Big Payment Highlight Card
        y += 135f
        val payCardRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + 150f)
        paint.color = Brand100
        canvas.drawRoundRect(payCardRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Brand500
        canvas.drawRoundRect(payCardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = bodyBold
        paint.textSize = 28f
        paint.color = Brand700
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("জমা নেওয়া টাকার পরিমাণ", BITMAP_WIDTH / 2f, y + 50f, paint)

        paint.typeface = displayBold
        paint.textSize = 58f
        paint.color = Brand700
        canvas.drawText(Formatters.formatMoney(amountPoisha, config.useBengaliNumerals, config.currencySymbol), BITMAP_WIDTH / 2f, y + 120f, paint)

        // Breakdown Box
        y += 175f
        val breakdownRect = RectF(50f, y, (BITMAP_WIDTH - 50).toFloat(), y + 120f)
        paint.color = SurfaceAltColor
        canvas.drawRoundRect(breakdownRect, 14f, 14f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = BorderColor
        canvas.drawRoundRect(breakdownRect, 14f, 14f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink2Color
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("পূর্বের মোট বকেয়া ছিল:", 80f, y + 46f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(previousDuePoisha, config.useBengaliNumerals, config.currencySymbol), 1000f, y + 46f, paint)

        paint.typeface = bodyBold
        paint.textSize = 26f
        paint.color = StatusDangerColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("বর্তমানে অবশিষ্ট বকেয়া বাকি:", 80f, y + 92f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol), 1000f, y + 92f, paint)

        // Footer
        y = (totalHeight - 75).toFloat()
        paint.typeface = bodyRegular
        paint.textSize = 24f
        paint.color = Ink3Color
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Dokan Pro ডিজিটাল ইনভয়েস সিস্টেম", BITMAP_WIDTH / 2f, y, paint)

        paint.color = Brand700
        canvas.drawRect(0f, (totalHeight - 12).toFloat(), BITMAP_WIDTH.toFloat(), totalHeight.toFloat(), paint)

        return bitmap
    }

    // Storage & Sharing Helpers
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, baseName: String): Uri {
        val cachePath = File(context.cacheDir, "images")
        if (!cachePath.exists()) cachePath.mkdirs()
        val file = File(cachePath, "${baseName}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (_: Exception) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        val fileName = "${title}_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DokanPro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Toast.makeText(context, "ছবিটি ফোনে সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    true
                } else false
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "DokanPro")
                if (!appDir.exists()) appDir.mkdirs()
                val imageFile = File(appDir, fileName)
                FileOutputStream(imageFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                MediaScannerConnection.scanFile(context, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                Toast.makeText(context, "ছবিটি ফোনে সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "ছবি সেভ করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareToWhatsApp(context: Context, imageUri: Uri, phoneNumber: String?, captionText: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                clipData = ClipData.newRawUri("invoice_image", imageUri)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val cleanPhone = phoneNumber?.replace(Regex("[^0-9+]"), "")?.let {
                if (it.startsWith("01")) "88$it" else it
            }

            if (!cleanPhone.isNullOrBlank()) {
                intent.setPackage("com.whatsapp")
                intent.putExtra("jid", "$cleanPhone@s.whatsapp.net")
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                    // WhatsApp direct failed, fall back to general intent with whatsapp package
                }
            }

            intent.setPackage("com.whatsapp")
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    intent.setPackage("com.whatsapp.w4b") // WhatsApp Business
                    context.startActivity(intent)
                } catch (_: Exception) {
                    intent.setPackage(null)
                    val chooser = Intent.createChooser(intent, "ইনভয়েস ছবি শেয়ার করুন").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "শেয়ার করতে সমস্যা হয়েছে: ${e.message ?: "অন্য মাধ্যমে চেষ্টা করুন"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareToGeneral(context: Context, imageUri: Uri, captionText: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                clipData = ClipData.newRawUri("invoice_image", imageUri)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "ইনভয়েস ছবি শেয়ার করুন").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "শেয়ার করতে সমস্যা হয়েছে: ${e.message ?: "অন্য মাধ্যমে চেষ্টা করুন"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildSaleInvoiceCaption(config: ShopConfig, sale: Sale, customerPreviousDue: Long = 0L): String {
        val shopName = config.shopName
        val inv = sale.invoiceNo
        val total = Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol)
        val paid = Formatters.formatMoney(sale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol)
        val isDue = sale.dueAmountPoisha > 0
        val salesman = getSalespersonName(sale, config)

        val sb = StringBuilder()
        sb.appendLine("🧾 *$shopName*")
        if (config.tagline.isNotBlank()) sb.appendLine(config.tagline)
        sb.appendLine("চালান নং: #$inv")
        sb.appendLine("তারিখ: ${Formatters.formatDateTime(sale.saleDate, config.useBengaliNumerals)}")
        sb.appendLine("বিক্রয়কর্মী: $salesman")
        sb.appendLine("-------------------------")
        sb.appendLine("মোট বিল: $total")
        sb.appendLine("পরিশোধিত: $paid")

        if (isDue) {
            val due = Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)
            sb.appendLine("আজকের বাকি: $due")
            if (customerPreviousDue > 0) {
                val totalDue = Formatters.formatMoney(customerPreviousDue + sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)
                sb.appendLine("সর্বমোট বকেয়া: $totalDue")
            }
        }
        sb.appendLine("-------------------------")
        sb.appendLine("ধন্যবাদ, আবার আসবেন!")
        if (config.shopPhone.isNotBlank()) sb.appendLine("যোগাযোগ: ${config.shopPhone}")
        return sb.toString()
    }

    fun buildDueStatementCaption(config: ShopConfig, customer: Customer, balance: Long): String {
        val shopName = config.shopName
        val dueStr = Formatters.formatMoney(balance, config.useBengaliNumerals, config.currencySymbol)
        val sb = StringBuilder()
        sb.appendLine("📋 *$shopName*")
        sb.appendLine("বাকি খাতার হিসাব বিবরণী")
        sb.appendLine("গ্রাহক: ${customer.name}")
        if (customer.phone.isNotBlank()) sb.appendLine("মোবাইল: ${customer.phone}")
        sb.appendLine("-------------------------")
        sb.appendLine("বর্তমান মোট বাকি: $dueStr")
        sb.appendLine("অনুগ্রহ করে দ্রুত পরিশোধের ব্যবস্থা করবেন। ধন্যবাদ।")
        return sb.toString()
    }

    fun buildPaymentReceiptCaption(
        config: ShopConfig,
        customer: Customer,
        amountPoisha: Long,
        previousDuePoisha: Long,
        remainingDuePoisha: Long
    ): String {
        val shopName = config.shopName
        val paidStr = Formatters.formatMoney(amountPoisha, config.useBengaliNumerals, config.currencySymbol)
        val prevStr = Formatters.formatMoney(previousDuePoisha, config.useBengaliNumerals, config.currencySymbol)
        val remStr = Formatters.formatMoney(remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol)
        val sb = StringBuilder()
        sb.appendLine("🧾 *$shopName*")
        sb.appendLine("বাকি আদায় মানি রিসিট")
        sb.appendLine("গ্রাহক: ${customer.name}")
        sb.appendLine("-------------------------")
        sb.appendLine("পূর্বের বাকি: $prevStr")
        sb.appendLine("আজ জমা: $paidStr")
        sb.appendLine("বর্তমান বাকি: $remStr")
        sb.appendLine("-------------------------")
        sb.appendLine("ধন্যবাদ, আবার আসবেন!")
        return sb.toString()
    }
}
