package com.example.data.firebase

import com.example.data.entity.Customer
import com.example.data.entity.CustomerLedger
import com.example.data.entity.Expense
import com.example.data.entity.Product
import com.example.data.entity.Purchase
import com.example.data.entity.PurchaseItem
import com.example.data.entity.Supplier
import com.google.firebase.database.DataSnapshot

/**
 * Firebase record shapes shared by the Android app and the web app.
 * Where the two apps historically used different field names, both are written so either app
 * can read records created by the other.
 */
internal object CloudMappers {

    fun product(p: Product): Map<String, Any?> = mapOf(
        "id" to p.id,
        "nameBn" to p.nameBn,
        "nameEn" to p.nameEn,
        "barcode" to p.barcode,
        "salePricePoisha" to p.salePricePoisha,
        "purchasePricePoisha" to p.purchasePricePoisha,
        "wholesalePricePoisha" to p.wholesalePricePoisha,
        "stockQty" to p.stockQty,
        "minStock" to p.minStock,
        "unitName" to p.unitName,
        "categoryId" to p.categoryId,
        // Boolean (not 1/0): the web app treats any non-false value as active.
        "isActive" to p.isActive,
        "updatedAt" to p.updatedAt
    )

    fun customer(c: Customer, duePoisha: Long?): Map<String, Any?> = buildMap {
        put("id", c.id)
        put("name", c.name)
        put("phone", c.phone)
        put("address", c.address ?: "")
        put("creditLimitPoisha", c.creditLimitPoisha)
        put("isActive", c.isActive)
        put("createdAt", c.createdAt)
        if (duePoisha != null) put("totalDuePoisha", duePoisha.coerceAtLeast(0L))
    }

    fun supplier(s: Supplier): Map<String, Any?> = mapOf(
        "id" to s.id,
        "name" to s.name,
        "company" to (s.company ?: ""),
        "phone" to s.phone,
        "address" to (s.address ?: ""),
        "isActive" to s.isActive,
        "createdAt" to s.createdAt
    )

    fun expense(e: Expense): Map<String, Any?> = mapOf(
        "id" to e.id,
        // web fields
        "title" to (e.note?.takeIf { it.isNotBlank() } ?: e.categoryName).take(200),
        "category" to e.categoryName,
        // android fields
        "categoryId" to e.categoryId,
        "categoryName" to e.categoryName,
        "amountPoisha" to e.amountPoisha,
        "note" to (e.note ?: ""),
        "expenseDate" to e.expenseDate,
        "createdAt" to e.createdAt
    )

    fun purchase(p: Purchase, items: List<PurchaseItem>): Map<String, Any?> = mapOf(
        "id" to p.id,
        "invoiceNo" to p.invoiceNo,
        "supplierId" to p.supplierId.toString(),
        "supplierName" to p.supplierName,
        "purchaseDate" to p.purchaseDate,
        "totalPoisha" to p.totalPoisha,
        "totalAmountPoisha" to p.totalPoisha,
        "paidAmountPoisha" to p.paidAmountPoisha,
        "dueAmountPoisha" to p.dueAmountPoisha,
        "notes" to (p.note ?: ""),
        "createdAt" to p.createdAt,
        "items" to items.map {
            mapOf(
                "productId" to it.productId.toString(),
                "productName" to it.productName,
                "qty" to it.qty,
                "quantity" to it.qty,
                "unitPricePoisha" to it.unitPricePoisha,
                "purchasePricePoisha" to it.unitPricePoisha,
                "lineTotalPoisha" to it.lineTotalPoisha
            )
        }
    )

    fun ledger(l: CustomerLedger): Map<String, Any?> = mapOf(
        "id" to l.id,
        "customerId" to l.customerId,
        "refType" to l.refType,
        "refId" to l.refId,
        "debitPoisha" to l.debitPoisha,
        "creditPoisha" to l.creditPoisha,
        "note" to (l.note ?: ""),
        "entryDate" to l.entryDate,
        "createdAt" to l.createdAt
    )

    // ---------- reading (accepts both apps' field names) ----------

    private fun DataSnapshot.long(vararg keys: String, default: Long = 0L): Long {
        for (k in keys) child(k).value?.toString()?.toDoubleOrNull()?.let { return it.toLong() }
        return default
    }

    private fun DataSnapshot.double(vararg keys: String, default: Double = 0.0): Double {
        for (k in keys) child(k).value?.toString()?.toDoubleOrNull()?.let { return it }
        return default
    }

    private fun DataSnapshot.text(vararg keys: String): String {
        for (k in keys) child(k).value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    /** Firebase keys created by the web app are numbers too (Date.now()), so the key is a fallback id. */
    private fun DataSnapshot.recordId(): Long? =
        long("id").takeIf { it > 0 } ?: key?.toLongOrNull() ?: key?.substringBefore('_')?.toLongOrNull()

    fun readExpense(s: DataSnapshot): Expense? {
        val id = s.recordId() ?: return null
        val category = s.text("categoryName", "category").ifEmpty { "অন্যান্য" }
        return Expense(
            id = id,
            categoryId = s.long("categoryId"),
            categoryName = category,
            amountPoisha = s.long("amountPoisha"),
            note = s.text("note", "title").takeIf { it.isNotEmpty() },
            expenseDate = s.long("expenseDate", default = System.currentTimeMillis()),
            createdAt = s.long("createdAt", "expenseDate", default = System.currentTimeMillis())
        )
    }

    fun readPurchase(s: DataSnapshot): Pair<Purchase, List<PurchaseItem>>? {
        val id = s.recordId() ?: return null
        val purchase = Purchase(
            id = id,
            invoiceNo = s.text("invoiceNo", "invoiceNumber").ifEmpty { "PUR-$id" },
            supplierId = s.text("supplierId").toLongOrNull() ?: 0L,
            supplierName = s.text("supplierName").ifEmpty { "সাধারণ মহাজন" },
            purchaseDate = s.long("purchaseDate", "createdAt", default = System.currentTimeMillis()),
            totalPoisha = s.long("totalPoisha", "totalAmountPoisha"),
            paidAmountPoisha = s.long("paidAmountPoisha", "paidPoisha"),
            dueAmountPoisha = s.long("dueAmountPoisha", "duePoisha"),
            note = s.text("notes", "note").takeIf { it.isNotEmpty() },
            createdAt = s.long("createdAt", default = System.currentTimeMillis())
        )
        val items = s.child("items").children.map {
            PurchaseItem(
                id = 0L,
                purchaseId = id,
                productId = it.text("productId").toLongOrNull() ?: 0L,
                productName = it.text("productName", "name"),
                qty = it.double("qty", "quantity", default = 1.0),
                unitPricePoisha = it.long("unitPricePoisha", "purchasePricePoisha"),
                lineTotalPoisha = it.long("lineTotalPoisha")
            )
        }
        return purchase to items
    }

    fun readLedger(s: DataSnapshot): CustomerLedger? {
        val id = s.recordId() ?: return null
        val customerId = s.long("customerId").takeIf { it > 0 } ?: return null
        return CustomerLedger(
            id = id,
            customerId = customerId,
            refType = s.text("refType").ifEmpty { "adjustment" },
            refId = s.long("refId").takeIf { it > 0 },
            debitPoisha = s.long("debitPoisha"),
            creditPoisha = s.long("creditPoisha"),
            note = s.text("note").takeIf { it.isNotEmpty() },
            entryDate = s.long("entryDate", default = System.currentTimeMillis()),
            createdAt = s.long("createdAt", default = System.currentTimeMillis())
        )
    }

    /** Accepts true/false (web, new app) and 1/0 (older app versions). */
    fun readActive(s: DataSnapshot, fallback: Boolean): Boolean = when (val v = s.child("isActive").value) {
        is Boolean -> v
        is Number -> v.toLong() != 0L
        is String -> v.equals("true", true) || v == "1"
        else -> fallback
    }
}
