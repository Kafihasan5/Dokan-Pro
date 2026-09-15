package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.entity.Customer
import com.example.data.entity.CustomerLedger
import com.example.data.entity.Sale
import com.example.data.entity.SaleItem
import com.example.ui.ShopConfig
import java.io.File
import java.io.FileOutputStream

object InvoiceImageHelper {

    private const val BITMAP_WIDTH = 800

    private fun c(hex: Long): Int = hex.toInt()

    /**
     * Generate Sales Invoice Bitmap (Cash or Due sale)
     */
    fun generateSaleInvoiceBitmap(
        context: Context,
        config: ShopConfig,
        sale: Sale,
        items: List<SaleItem>,
        customerPreviousDue: Long = 0L
    ): Bitmap {
        val isDue = sale.dueAmountPoisha > 0
        val hasCustomer = !sale.customerName.isNullOrBlank()

        // Calculate dynamic height
        val headerHeight = 250
        val metaHeight = if (hasCustomer) 130 else 80
        val tableHeaderHeight = 55
        val itemsHeight = (items.size.coerceAtLeast(1) * 65)
        val calcBoxHeight = if (isDue) {
            if (customerPreviousDue > 0) 380 else 300
        } else 240
        val footerHeight = 120
        val totalHeight = headerHeight + metaHeight + tableHeaderHeight + itemsHeight + calcBoxHeight + footerHeight

        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // Top Emerald Accent Bar
        paint.color = c(0xFF059669) // Emerald 600
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), 16f, paint)

        // Outer Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = c(0xFFE2E8F0) // Slate 200
        canvas.drawRoundRect(RectF(12f, 12f, (BITMAP_WIDTH - 12).toFloat(), (totalHeight - 12).toFloat()), 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        var y = 55f

        // Shop Name
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 38f
        paint.color = c(0xFF0F172A) // Slate 900
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        // Tagline
        if (config.tagline.isNotBlank()) {
            y += 30f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 20f
            paint.color = c(0xFF059669) // Emerald
            canvas.drawText(config.tagline, BITMAP_WIDTH / 2f, y, paint)
        }

        // Address & Phone
        y += 28f
        paint.textSize = 19f
        paint.color = c(0xFF64748B) // Slate 500
        val addressLine = if (config.shopAddress.isNotBlank()) "${config.shopAddress} | " else ""
        canvas.drawText("${addressLine}মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // Memo Type Badge
        y += 38f
        val badgeText = if (isDue) "বাকি বিক্রয় মেমো (Due Invoice)" else "ক্যাশ মেমো / বিক্রয় ইনভয়েস"
        val badgeColor = if (isDue) c(0xFFDC2626) else c(0xFF059669)
        val badgeBg = if (isDue) c(0xFFFEF2F2) else c(0xFFECFDF5)

        paint.textSize = 19f
        val badgeWidth = paint.measureText(badgeText) + 40f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 22f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 12f)
        paint.color = badgeBg
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = badgeColor
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = badgeColor
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y, paint)

        // Horizontal Divider
        y += 28f
        drawDivider(canvas, y)

        // Meta Info (Invoice No, Date, Customer)
        y += 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 19f
        paint.color = c(0xFF334155) // Slate 700

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ইনভয়েস: ${sale.invoiceNo}", 40f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("তারিখ: ${Formatters.formatDateTime(sale.saleDate, config.useBengaliNumerals)}", (BITMAP_WIDTH - 40).toFloat(), y, paint)

        if (hasCustomer) {
            y += 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("ক্রেতা: ${sale.customerName}", 40f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            val paymentTitle = when (sale.paymentMethod) {
                "due" -> "বাকি"
                "mfs" -> "বিকাশ/নগদ"
                "card" -> "কার্ড"
                else -> "নগদ"
            }
            canvas.drawText("পেমেন্ট: $paymentTitle", (BITMAP_WIDTH - 40).toFloat(), y, paint)
        }

        // Table Header
        y += 30f
        val tableHeaderRect = RectF(35f, y, (BITMAP_WIDTH - 35).toFloat(), y + 42f)
        paint.color = c(0xFFF1F5F9) // Slate 100
        canvas.drawRoundRect(tableHeaderRect, 10f, 10f, paint)

        y += 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 19f
        paint.color = c(0xFF1E293B)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("#", 50f, y, paint)
        canvas.drawText("পণ্যের বিবরণ", 95f, y, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("দর", 490f, y, paint)
        canvas.drawText("পরিমাণ", 610f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("মোট", (BITMAP_WIDTH - 55).toFloat(), y, paint)

        // Item Rows
        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for ((idx, item) in items.withIndex()) {
            y += 36f

            // Index
            paint.textAlign = Paint.Align.LEFT
            paint.color = c(0xFF64748B)
            paint.textSize = 17f
            val idxStr = if (config.useBengaliNumerals) Formatters.toBengaliDigits((idx + 1).toString()) else "${idx + 1}"
            canvas.drawText(idxStr, 50f, y, paint)

            // Product Name (truncated if too long)
            paint.color = c(0xFF0F172A)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 20f
            val displayName = if (item.productName.length > 25) item.productName.take(23) + "..." else item.productName
            canvas.drawText(displayName, 95f, y, paint)

            // Rate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 18f
            paint.color = c(0xFF475569)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(Formatters.formatMoney(item.unitPricePoisha, config.useBengaliNumerals, config.currencySymbol), 490f, y, paint)

            // Quantity
            canvas.drawText(Formatters.formatQty(item.qty, item.unitName, config.useBengaliNumerals), 610f, y, paint)

            // Line Total
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = c(0xFF0F172A)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Formatters.formatMoney(item.lineTotalPoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 55).toFloat(), y, paint)

            // Subline separator
            y += 18f
            paint.color = c(0xFFF1F5F9)
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(40f, y, (BITMAP_WIDTH - 40).toFloat(), y, paint)
            paint.style = Paint.Style.FILL
        }

        // Summary Calculations Box
        y += 24f
        val calcBoxStart = y
        val calcBoxRect = RectF(35f, calcBoxStart, (BITMAP_WIDTH - 35).toFloat(), calcBoxStart + calcBoxHeight - 20f)
        paint.color = c(0xFFF8FAFC)
        canvas.drawRoundRect(calcBoxRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = c(0xFFE2E8F0)
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(calcBoxRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // Subtotal
        y += 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF475569)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("উপ-মোট (Subtotal):", 60f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.subtotalPoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 60).toFloat(), y, paint)

        // Discount if any
        if (sale.discountPoisha > 0) {
            y += 28f
            paint.textAlign = Paint.Align.LEFT
            paint.color = c(0xFFDC2626)
            canvas.drawText("ছাড় / ডিসকাউন্ট (-):", 60f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-${Formatters.formatMoney(sale.discountPoisha, config.useBengaliNumerals, config.currencySymbol)}", (BITMAP_WIDTH - 60).toFloat(), y, paint)
        }

        // Grand Total Row (Emerald Banner)
        y += 32f
        val grandTotalRect = RectF(50f, y - 24f, (BITMAP_WIDTH - 50).toFloat(), y + 36f)
        paint.color = c(0xFF059669)
        canvas.drawRoundRect(grandTotalRect, 12f, 12f, paint)

        y += 16f
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 25f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সর্বমোট প্রদেয় বিল:", 70f, y, paint)

        paint.textSize = 30f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 70).toFloat(), y, paint)

        // Paid
        y += 50f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.color = c(0xFF047857) // Dark Emerald
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("নগদ পরিশোধ:", 60f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(sale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 60).toFloat(), y, paint)

        // Due
        if (isDue) {
            y += 36f
            val dueRect = RectF(50f, y - 24f, (BITMAP_WIDTH - 50).toFloat(), y + 26f)
            paint.color = c(0xFFFEF2F2)
            canvas.drawRoundRect(dueRect, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = c(0xFFFCA5A5)
            canvas.drawRoundRect(dueRect, 10f, 10f, paint)
            paint.style = Paint.Style.FILL

            y += 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 22f
            paint.color = c(0xFFDC2626)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("আজকের বাকি:", 70f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 70).toFloat(), y, paint)

            if (customerPreviousDue > 0) {
                y += 32f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 19f
                paint.color = c(0xFF64748B)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("পূর্বের বকেয়া বাকি: ${Formatters.formatMoney(customerPreviousDue, config.useBengaliNumerals, config.currencySymbol)}", 60f, y, paint)

                y += 26f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 21f
                paint.color = c(0xFFB91C1C)
                val totalRemaining = customerPreviousDue + sale.dueAmountPoisha
                canvas.drawText("বর্তমানে সর্বমোট বাকি: ${Formatters.formatMoney(totalRemaining, config.useBengaliNumerals, config.currencySymbol)}", 60f, y, paint)
            }
        }

        // Footer
        y = (totalHeight - 75).toFloat()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF475569)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("--- ধন্যবাদ! আবার আসবেন ---", BITMAP_WIDTH / 2f, y, paint)

        y += 30f
        paint.textSize = 15f
        paint.color = c(0xFF94A3B8)
        canvas.drawText("পাপন শপ ডিজিটাল ইনভয়েস সিস্টেম দ্বারা প্রস্তুতকৃত", BITMAP_WIDTH / 2f, y, paint)

        // Bottom Accent
        paint.color = c(0xFF059669)
        canvas.drawRect(0f, (totalHeight - 12).toFloat(), BITMAP_WIDTH.toFloat(), totalHeight.toFloat(), paint)

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
        val recentLedger = ledgerItems.take(8)
        val headerHeight = 220
        val custInfoHeight = 110
        val dueCardHeight = 140
        val tableHeaderHeight = 50
        val ledgerRowsHeight = (recentLedger.size.coerceAtLeast(1) * 55)
        val footerHeight = 130
        val totalHeight = headerHeight + custInfoHeight + dueCardHeight + tableHeaderHeight + ledgerRowsHeight + footerHeight

        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // Top Red Accent Bar for Due Statement
        paint.color = c(0xFFDC2626)
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), 16f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = c(0xFFE2E8F0)
        canvas.drawRoundRect(RectF(12f, 12f, (BITMAP_WIDTH - 12).toFloat(), (totalHeight - 12).toFloat()), 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        var y = 55f

        // Shop Name
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 38f
        paint.color = c(0xFF0F172A)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        // Address & Phone
        y += 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF64748B)
        canvas.drawText("${config.shopAddress} | মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // Badge
        y += 40f
        val badgeText = "বাকি খাতার হিসাব বিবরণী (Statement)"
        val badgeWidth = paint.measureText(badgeText) + 40f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 24f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 12f)
        paint.color = c(0xFFFEF2F2)
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = c(0xFFFCA5A5)
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.color = c(0xFFDC2626)
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y, paint)

        y += 28f
        drawDivider(canvas, y)

        // Customer Info Card
        y += 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        paint.color = c(0xFF0F172A)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সম্মানিত ক্রেতা: ${customer.name}", 40f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 19f
        paint.color = c(0xFF475569)
        canvas.drawText("মোবাইল: ${customer.phone}", (BITMAP_WIDTH - 40).toFloat(), y, paint)

        y += 30f
        paint.textAlign = Paint.Align.LEFT
        paint.color = c(0xFF64748B)
        canvas.drawText("বিবরণী তারিখ: ${Formatters.formatBengaliDate(System.currentTimeMillis())}", 40f, y, paint)

        // Big Total Due Card
        y += 36f
        val dueCardRect = RectF(35f, y, (BITMAP_WIDTH - 35).toFloat(), y + 110f)
        paint.color = c(0xFFFFF1F2)
        canvas.drawRoundRect(dueCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = c(0xFFFDA4AF)
        canvas.drawRoundRect(dueCardRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        y += 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        paint.color = c(0xFF9F1239)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("বর্তমানে মোট বকেয়া বাকি", BITMAP_WIDTH / 2f, y, paint)

        y += 48f
        paint.textSize = 44f
        paint.color = c(0xFFE11D48)
        canvas.drawText(Formatters.formatMoney(currentDue, config.useBengaliNumerals, config.currencySymbol), BITMAP_WIDTH / 2f, y, paint)

        // Recent Transactions Header
        y += 55f
        val tableHeaderRect = RectF(35f, y, (BITMAP_WIDTH - 35).toFloat(), y + 38f)
        paint.color = c(0xFFF1F5F9)
        canvas.drawRoundRect(tableHeaderRect, 8f, 8f, paint)

        y += 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.color = c(0xFF1E293B)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("তারিখ", 50f, y, paint)
        canvas.drawText("বিবরণ", 220f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("বাকি বৃদ্ধি (+)", 520f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("জমা/পরিশোধ (-)", (BITMAP_WIDTH - 50).toFloat(), y, paint)

        // Transaction Rows
        y += 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 17f

        if (recentLedger.isEmpty()) {
            y += 30f
            paint.textAlign = Paint.Align.CENTER
            paint.color = c(0xFF94A3B8)
            canvas.drawText("কোনো পূর্ববর্তী লেনদেনের রেকর্ড নেই", BITMAP_WIDTH / 2f, y, paint)
        } else {
            for (entry in recentLedger) {
                y += 32f
                paint.textAlign = Paint.Align.LEFT
                paint.color = c(0xFF475569)
                canvas.drawText(Formatters.formatDateTime(entry.entryDate, config.useBengaliNumerals).take(10), 50f, y, paint)

                val desc = when (entry.refType) {
                    "sale" -> "পণ্য ক্রয় (বাকি)"
                    "payment" -> "বাকি পরিশোধ/জমা"
                    else -> entry.note ?: "সমন্বয়"
                }
                canvas.drawText(desc, 220f, y, paint)

                // Debit (Due increased)
                paint.textAlign = Paint.Align.CENTER
                if (entry.debitPoisha > 0) {
                    paint.color = c(0xFFDC2626)
                    canvas.drawText("+${Formatters.formatMoney(entry.debitPoisha, config.useBengaliNumerals, config.currencySymbol)}", 520f, y, paint)
                } else {
                    paint.color = c(0xFF94A3B8)
                    canvas.drawText("-", 520f, y, paint)
                }

                // Credit (Payment received)
                paint.textAlign = Paint.Align.RIGHT
                if (entry.creditPoisha > 0) {
                    paint.color = c(0xFF059669)
                    canvas.drawText("-${Formatters.formatMoney(entry.creditPoisha, config.useBengaliNumerals, config.currencySymbol)}", (BITMAP_WIDTH - 50).toFloat(), y, paint)
                } else {
                    paint.color = c(0xFF94A3B8)
                    canvas.drawText("-", (BITMAP_WIDTH - 50).toFloat(), y, paint)
                }

                // Divider line
                y += 14f
                paint.color = c(0xFFF8FAFC)
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(40f, y, (BITMAP_WIDTH - 40).toFloat(), y, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // Footer Note
        y = (totalHeight - 80).toFloat()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 19f
        paint.color = c(0xFF475569)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("অনুগ্রহ করে সুবিধাজনক সময়ে বকেয়া টাকা পরিশোধের অনুরোধ রইল।", BITMAP_WIDTH / 2f, y, paint)

        y += 28f
        paint.textSize = 15f
        paint.color = c(0xFF94A3B8)
        canvas.drawText("পাপন শপ খতিয়ান সিস্টেম দ্বারা স্বয়ংক্রিয়ভাবে তৈরি", BITMAP_WIDTH / 2f, y, paint)

        // Bottom Accent
        paint.color = c(0xFFDC2626)
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
        val totalHeight = 720
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            isFilterBitmap = true
        }

        // Top Green Accent Bar
        paint.color = c(0xFF059669)
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), 16f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = c(0xFFE2E8F0)
        canvas.drawRoundRect(RectF(12f, 12f, (BITMAP_WIDTH - 12).toFloat(), (totalHeight - 12).toFloat()), 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        var y = 55f

        // Shop Name
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 38f
        paint.color = c(0xFF0F172A)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.shopName, BITMAP_WIDTH / 2f, y, paint)

        // Address & Phone
        y += 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF64748B)
        canvas.drawText("${config.shopAddress} | মোবাইল: ${config.shopPhone}", BITMAP_WIDTH / 2f, y, paint)

        // Badge
        y += 40f
        val badgeText = "টাকা জমা ও বাকি আদায় ভাউচার"
        val badgeWidth = paint.measureText(badgeText) + 40f
        val badgeRect = RectF((BITMAP_WIDTH - badgeWidth) / 2f, y - 24f, (BITMAP_WIDTH + badgeWidth) / 2f, y + 12f)
        paint.color = c(0xFFECFDF5)
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = c(0xFF6EE7B7)
        canvas.drawRoundRect(badgeRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        paint.color = c(0xFF059669)
        canvas.drawText(badgeText, BITMAP_WIDTH / 2f, y, paint)

        y += 28f
        drawDivider(canvas, y)

        // Meta info
        y += 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 21f
        paint.color = c(0xFF0F172A)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("সম্মানিত ক্রেতা: ${customer.name}", 40f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 19f
        paint.color = c(0xFF475569)
        canvas.drawText("মোবাইল: ${customer.phone}", (BITMAP_WIDTH - 40).toFloat(), y, paint)

        y += 30f
        paint.textAlign = Paint.Align.LEFT
        paint.color = c(0xFF64748B)
        canvas.drawText("তারিখ: ${Formatters.formatDateTime(System.currentTimeMillis(), config.useBengaliNumerals)}", 40f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("মাধ্যম: $paymentMethod", (BITMAP_WIDTH - 40).toFloat(), y, paint)

        // Big Payment Highlight Card
        y += 40f
        val cardRect = RectF(35f, y, (BITMAP_WIDTH - 35).toFloat(), y + 130f)
        paint.color = c(0xFFECFDF5)
        canvas.drawRoundRect(cardRect, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = c(0xFF34D399)
        canvas.drawRoundRect(cardRect, 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        y += 45f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        paint.color = c(0xFF065F46)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("জমা নেওয়া টাকার পরিমাণ", BITMAP_WIDTH / 2f, y, paint)

        y += 50f
        paint.textSize = 48f
        paint.color = c(0xFF059669)
        canvas.drawText(Formatters.formatMoney(amountPoisha, config.useBengaliNumerals, config.currencySymbol), BITMAP_WIDTH / 2f, y, paint)

        // Due Details Breakdown Box
        y += 65f
        val breakdownRect = RectF(45f, y, (BITMAP_WIDTH - 45).toFloat(), y + 110f)
        paint.color = c(0xFFF8FAFC)
        canvas.drawRoundRect(breakdownRect, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = c(0xFFE2E8F0)
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(breakdownRect, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        y += 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF475569)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("পূর্বের মোট বকেয়া ছিল:", 70f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(previousDuePoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 70).toFloat(), y, paint)

        y += 38f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        paint.color = c(0xFFDC2626)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("বর্তমানে অবশিষ্ট বকেয়া বাকি:", 70f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(Formatters.formatMoney(remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol), (BITMAP_WIDTH - 70).toFloat(), y, paint)

        // Footer
        y = (totalHeight - 75).toFloat()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 20f
        paint.color = c(0xFF047857)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("আপনার বাকি পরিশোধের জন্য আন্তরিক ধন্যবাদ!", BITMAP_WIDTH / 2f, y, paint)

        y += 28f
        paint.textSize = 15f
        paint.color = c(0xFF94A3B8)
        canvas.drawText("পাপন শপ ডিজিটাল ভাউচার সিস্টেম", BITMAP_WIDTH / 2f, y, paint)

        paint.color = c(0xFF059669)
        canvas.drawRect(0f, (totalHeight - 12).toFloat(), BITMAP_WIDTH.toFloat(), totalHeight.toFloat(), paint)

        return bitmap
    }

    private fun drawDivider(canvas: Canvas, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c(0xFFE2E8F0)
            strokeWidth = 1.5f
        }
        canvas.drawLine(35f, y, (BITMAP_WIDTH - 35).toFloat(), y, paint)
    }

    // ==========================================
    // CAPTION GENERATORS (Sundor Bangla Text)
    // ==========================================

    fun buildSaleInvoiceCaption(config: ShopConfig, sale: Sale, customerPreviousDue: Long = 0L): String {
        val sb = StringBuilder()
        val isDue = sale.dueAmountPoisha > 0
        val isCustomer = !sale.customerName.isNullOrBlank()

        if (isDue) {
            sb.appendLine("🧾 *${config.shopName} - বাকি বিক্রয় মেমো*")
        } else {
            sb.appendLine("🧾 *${config.shopName} - ক্যাশ মেমো / ইনভয়েস*")
        }

        if (isCustomer) {
            sb.appendLine("আসসালামু আলাইকুম, সম্মানিত ক্রেতা *${sale.customerName}*,")
        } else {
            sb.appendLine("আসসালামু আলাইকুম,")
        }
        sb.appendLine("আপনার কেনাকাটার ডিজিটাল রসিদ ছবি আকারে সংযুক্ত করা হলো।")
        sb.appendLine("-----------------------------")
        sb.appendLine("📄 ইনভয়েস নং: *${sale.invoiceNo}*")
        sb.appendLine("📅 তারিখ: ${Formatters.formatDateTime(sale.saleDate, config.useBengaliNumerals)}")
        sb.appendLine("💰 মোট বিল: *${Formatters.formatMoney(sale.totalPoisha, config.useBengaliNumerals, config.currencySymbol)}*")
        sb.appendLine("💵 নগদ পরিশোধ: ${Formatters.formatMoney(sale.paidAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}")

        if (isDue) {
            sb.appendLine("⚠️ আজকের বাকি: *${Formatters.formatMoney(sale.dueAmountPoisha, config.useBengaliNumerals, config.currencySymbol)}*")
            if (customerPreviousDue > 0) {
                sb.appendLine("📌 পূর্বের বকেয়া: ${Formatters.formatMoney(customerPreviousDue, config.useBengaliNumerals, config.currencySymbol)}")
                val totalRemaining = customerPreviousDue + sale.dueAmountPoisha
                sb.appendLine("🔴 *বর্তমানে মোট বকেয়া বাকি: ${Formatters.formatMoney(totalRemaining, config.useBengaliNumerals, config.currencySymbol)}*")
            }
            sb.appendLine("-----------------------------")
            sb.appendLine("সুবিধাজনক সময়ে বকেয়া পরিশোধের অনুরোধ রইল।")
        } else {
            sb.appendLine("-----------------------------")
            sb.appendLine("আমাদের সাথে কেনাকাটা করার জন্য ধন্যবাদ!")
        }

        sb.appendLine("- *${config.shopName}*")
        if (config.shopPhone.isNotBlank()) {
            sb.appendLine("📞 ${config.shopPhone}")
        }
        return sb.toString()
    }

    fun buildDueStatementCaption(config: ShopConfig, customer: Customer, currentDue: Long): String {
        val sb = StringBuilder()
        sb.appendLine("🧾 *${config.shopName} - বাকি খাতার হিসাব বিবরণী*")
        sb.appendLine("আসসালামু আলাইকুম, সম্মানিত ক্রেতা *${customer.name}*,")
        sb.appendLine("${config.shopName}-এ আপনার হালনাগাদ বাকি খাতার সম্পূর্ণ হিসাব বিবরণী ছবি সংযুক্ত করা হলো।")
        sb.appendLine("-----------------------------")
        sb.appendLine("📅 তারিখ: ${Formatters.formatBengaliDate(System.currentTimeMillis())}")
        sb.appendLine("🔴 *বর্তমানে আপনার মোট বকেয়া বাকি: ${Formatters.formatMoney(currentDue, config.useBengaliNumerals, config.currencySymbol)}*")
        sb.appendLine("-----------------------------")
        sb.appendLine("অনুগ্রহ করে হিসাবটি মিলিয়ে দেখবেন এবং সুবিধাজনক সময়ে পরিশোধের অনুরোধ রইল।")
        sb.appendLine("- *${config.shopName}*")
        if (config.shopPhone.isNotBlank()) {
            sb.appendLine("📞 ${config.shopPhone}")
        }
        return sb.toString()
    }

    fun buildPaymentReceiptCaption(
        config: ShopConfig,
        customer: Customer,
        amountPoisha: Long,
        previousDuePoisha: Long,
        remainingDuePoisha: Long
    ): String {
        val sb = StringBuilder()
        sb.appendLine("🧾 *${config.shopName} - টাকা জমা ও বাকি আদায় রসিদ*")
        sb.appendLine("আসসালামু আলাইকুম, সম্মানিত ক্রেতা *${customer.name}*,")
        sb.appendLine("আপনার বাকি পরিশোধের টাকা জমার ডিজিটাল রসিদ সংযুক্ত করা হলো।")
        sb.appendLine("-----------------------------")
        sb.appendLine("📅 তারিখ: ${Formatters.formatDateTime(System.currentTimeMillis(), config.useBengaliNumerals)}")
        sb.appendLine("✅ *জমা নেওয়া টাকা: ${Formatters.formatMoney(amountPoisha, config.useBengaliNumerals, config.currencySymbol)}*")
        sb.appendLine("📌 পূর্বের মোট বাকি ছিল: ${Formatters.formatMoney(previousDuePoisha, config.useBengaliNumerals, config.currencySymbol)}")
        sb.appendLine("🟢 *বর্তমানে অবশিষ্ট বকেয়া বাকি: ${Formatters.formatMoney(remainingDuePoisha, config.useBengaliNumerals, config.currencySymbol)}*")
        sb.appendLine("-----------------------------")
        sb.appendLine("টাকা পরিশোধের জন্য আপনাকে আন্তরিক ধন্যবাদ!")
        sb.appendLine("- *${config.shopName}*")
        if (config.shopPhone.isNotBlank()) {
            sb.appendLine("📞 ${config.shopPhone}")
        }
        return sb.toString()
    }

    // ==========================================
    // STORAGE & SHARING HELPERS
    // ==========================================

    /**
     * Save Bitmap to device public Pictures/PaponShop directory (visible in Gallery)
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String): Uri? {
        val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PaponShop")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Toast.makeText(context, "ইনভয়েস ছবিটি গ্যালারিতে সেভ হয়েছে!", Toast.LENGTH_LONG).show()
                    return uri
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PaponShop")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                Toast.makeText(context, "ইনভয়েস ছবিটি গ্যালারিতে সেভ হয়েছে!", Toast.LENGTH_LONG).show()
                return Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "ছবি সেভ করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        return null
    }

    /**
     * Save Bitmap to internal cache and return FileProvider URI for immediate sharing
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileNamePrefix: String): Uri {
        val cacheDir = File(context.cacheDir, "invoices")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /**
     * Share Image to WhatsApp with Caption
     */
    fun shareToWhatsApp(context: Context, imageUri: Uri, phoneNumber: String? = null, caption: String = "") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (caption.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }

        // Target WhatsApp contact if phone is provided
        val cleanNumber = phoneNumber?.filter { it.isDigit() }?.let {
            if (it.startsWith("880")) it else if (it.startsWith("0")) "88$it" else "880$it"
        }
        if (!cleanNumber.isNullOrBlank()) {
            intent.putExtra("jid", "$cleanNumber@s.whatsapp.net")
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // WhatsApp not found, fallback to system chooser
            shareToGeneral(context, imageUri, caption)
        }
    }

    /**
     * Share Image to any app (System Chooser)
     */
    fun shareToGeneral(context: Context, imageUri: Uri, caption: String = "") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (caption.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ইনভয়েস ছবি শেয়ার করুন"))
    }
}
