package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun toBengaliDigits(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (c in input) {
            if (c in '0'..'9') {
                sb.append(bnDigits[c - '0'])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun fromBengaliDigits(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (c in input) {
            val idx = bnDigits.indexOf(c)
            if (idx != -1) {
                sb.append(idx)
            } else if (c in '0'..'9' || c == '.') {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun formatMoney(poisha: Long, useBn: Boolean = true, symbol: String = "৳"): String {
        val isNegative = poisha < 0
        val absPoisha = Math.abs(poisha)
        val taka = absPoisha / 100
        val remainingPoisha = absPoisha % 100

        val formattedStr = if (remainingPoisha == 0L) {
            String.format(Locale.US, "%,d", taka)
        } else {
            String.format(Locale.US, "%,d.%02d", taka, remainingPoisha)
        }

        val prefix = if (isNegative) "-" else ""
        return if (useBn) {
            "$prefix$symbol" + toBengaliDigits(formattedStr)
        } else {
            "$prefix$symbol$formattedStr"
        }
    }

    fun formatQty(qty: Double, unit: String, useBn: Boolean = true): String {
        val str = if (qty % 1.0 == 0.0) {
            qty.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')
        }
        val displayNum = if (useBn) toBengaliDigits(str) else str
        return "$displayNum $unit"
    }

    fun formatBengaliDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale.US)
        val dateStr = sdf.format(Date(timestamp))
        val bnMonths = mapOf(
            "January" to "জানুয়ারি", "February" to "ফেব্রুয়ারি", "March" to "মার্চ",
            "April" to "এপ্রিল", "May" to "মে", "June" to "জুন",
            "July" to "জুলাই", "August" to "আগস্ট", "September" to "সেপ্টেম্বর",
            "October" to "অক্টোবর", "November" to "নভেম্বর", "December" to "ডিসেম্বর"
        )
        var result = dateStr
        for ((en, bn) in bnMonths) {
            result = result.replace(en, bn)
        }
        return toBengaliDigits(result)
    }

    fun formatBengaliTime(timestamp: Long): String {
        return formatDateTime(timestamp, useBn = true)
    }

    fun formatTime(timestamp: Long, useBn: Boolean = true): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        val formatted = sdf.format(Date(timestamp))
        return if (useBn) toBengaliDigits(formatted) else formatted
    }

    fun formatDateTime(timestamp: Long, useBn: Boolean = true): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
        val formatted = sdf.format(Date(timestamp))
        return if (useBn) toBengaliDigits(formatted) else formatted
    }

    fun generateInvoiceNumber(saleId: Long): String {
        return "INV-${1000 + saleId}"
    }
}
