package com.example.data.repository

import com.example.data.dao.PaponDao
import com.example.data.entity.*
import com.example.data.supabase.SupabaseSyncManager
import com.example.util.Formatters
import com.example.util.SampleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class PaponRepository(private val dao: PaponDao) {

    val supabaseSync = SupabaseSyncManager(dao)
    private val scope = CoroutineScope(Dispatchers.IO)

    // --- PRODUCTS ---
    val allActiveProducts: Flow<List<Product>> = dao.getAllActiveProducts()
    val lowStockProducts: Flow<List<Product>> = dao.getLowStockProducts()
    val allCategories: Flow<List<Category>> = dao.getAllCategories()

    suspend fun saveCategory(category: Category): Long = withContext(Dispatchers.IO) {
        if (category.id == 0L) {
            dao.insertCategory(category)
        } else {
            dao.updateCategory(category)
            category.id
        }
    }

    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        val all = dao.getCategoriesSync().filter { it.id != categoryId && it.id != 1L }
        val fallbackId = all.firstOrNull()?.id ?: 2L
        dao.reassignProductsCategory(categoryId, fallbackId)
        dao.deleteCategoryById(categoryId)
    }

    suspend fun updateProductUnit(oldUnit: String, newUnit: String) = withContext(Dispatchers.IO) {
        dao.updateProductUnitName(oldUnit, newUnit)
    }

    suspend fun getProductById(id: Long): Product? = dao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = dao.getProductByBarcode(barcode)
    suspend fun saveProduct(product: Product): Long {
        val id = dao.insertProduct(product)
        val saved = product.copy(id = id)
        dao.removeDeletedRecord("products", id)
        scope.launch { supabaseSync.syncProduct(saved) }
        return id
    }
    suspend fun updateProduct(product: Product) {
        dao.updateProduct(product)
        dao.removeDeletedRecord("products", product.id)
        scope.launch { supabaseSync.syncProduct(product) }
    }
    suspend fun deleteProduct(productId: Long) = withContext(Dispatchers.IO) {
        dao.deleteProductById(productId)
        dao.recordDeletedItem(DeletedRecord("products", productId))
        scope.launch {
            supabaseSync.deleteProductFromSupabase(productId)
        }
    }


    suspend fun adjustStock(productId: Long, productName: String, qtyChange: Double, reason: String, note: String?) {
        dao.adjustProductStock(productId, qtyChange)
        dao.insertStockAdjustment(
            StockAdjustment(
                productId = productId,
                productName = productName,
                qtyChange = qtyChange,
                reason = reason,
                note = note
            )
        )
        scope.launch {
            dao.getProductById(productId)?.let {
                supabaseSync.syncProduct(it)
            }
        }
    }

    // --- SALES ---
    val allSales: Flow<List<Sale>> = dao.getAllSales()
    val allSaleItems: Flow<List<SaleItem>> = dao.getAllSaleItems()

    suspend fun getSaleById(saleId: Long): Sale? = dao.getSaleById(saleId)
    suspend fun getSaleItems(saleId: Long): List<SaleItem> = dao.getSaleItems(saleId)
    fun getSaleItemsFlow(saleId: Long): Flow<List<SaleItem>> = dao.getSaleItemsFlow(saleId)

    suspend fun completeSale(
        sale: Sale,
        items: List<SaleItem>,
        updateStock: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val saleId = dao.insertSale(sale)
        val preparedItems = items.map { it.copy(saleId = saleId) }
        dao.insertSaleItems(preparedItems)

        val affectedProducts = mutableListOf<Product>()
        if (updateStock) {
            for (item in preparedItems) {
                if (item.productId > 0) {
                    dao.adjustProductStock(item.productId, -item.qty)
                    dao.getProductById(item.productId)?.let { affectedProducts.add(it) }
                }
            }
        }

        // If sale has customer and due amount > 0, record in customer ledger
        if (sale.customerId != null && sale.dueAmountPoisha > 0) {
            val ledger = CustomerLedger(
                customerId = sale.customerId,
                refType = "sale",
                refId = saleId,
                debitPoisha = sale.dueAmountPoisha,
                creditPoisha = 0,
                note = "বিক্রয় ইনভয়েস: ${sale.invoiceNo}"
            )
            val ledgerId = dao.insertCustomerLedger(ledger)
            scope.launch {
                supabaseSync.syncCustomerLedger(ledger.copy(id = ledgerId))
            }
        }

        scope.launch {
            supabaseSync.syncSale(sale.copy(id = saleId), preparedItems, affectedProducts)
        }

        saleId
    }


    /**
     * Process return of sale items (full or partial return)
     * @param saleId The ID of the sale
     * @param returnedItems Map of saleItemId to returned quantity (Double)
     */
    suspend fun returnSaleItems(
        saleId: Long,
        returnedItems: Map<Long, Double>
    ): Boolean = withContext(Dispatchers.IO) {
        val sale = dao.getSaleById(saleId) ?: return@withContext false
        if (sale.isReturned) return@withContext false
        val allItems = dao.getSaleItems(saleId)
        if (allItems.isEmpty() || returnedItems.isEmpty()) return@withContext false

        val restockedProducts = mutableListOf<Product>()
        var totalRefundPoisha = 0L
        var allItemsFullyReturned = true
        val remainingItemsAfterReturn = mutableListOf<SaleItem>()

        for (item in allItems) {
            val returnQty = (returnedItems[item.id] ?: 0.0).coerceAtMost(item.qty)
            if (returnQty > 0.0) {
                // 1. Restock product
                if (item.productId > 0) {
                    dao.adjustProductStock(item.productId, returnQty)
                    dao.insertStockAdjustment(
                        StockAdjustment(
                            productId = item.productId,
                            productName = item.productName,
                            qtyChange = returnQty,
                            reason = "পণ্য ফেরত",
                            note = "ইনভয়েস: ${sale.invoiceNo} (ফেরত: ${returnQty} ${item.unitName})"
                        )
                    )
                    dao.getProductById(item.productId)?.let { restockedProducts.add(it) }
                }

                val refundForThisItem = (returnQty * item.unitPricePoisha).toLong()
                totalRefundPoisha += refundForThisItem

                val remainingQty = item.qty - returnQty
                if (remainingQty > 0.001) {
                    allItemsFullyReturned = false
                    val updatedItem = item.copy(
                        qty = remainingQty,
                        lineTotalPoisha = (remainingQty * item.unitPricePoisha).toLong()
                    )
                    dao.updateSaleItem(updatedItem)
                    remainingItemsAfterReturn.add(updatedItem)
                } else {
                    // Fully returned this item
                    dao.deleteSaleItemById(item.id)
                }
            } else {
                allItemsFullyReturned = false
                remainingItemsAfterReturn.add(item)
            }
        }

        // Check if full invoice was returned
        val isFullInvoiceReturn = allItemsFullyReturned || remainingItemsAfterReturn.isEmpty()

        if (isFullInvoiceReturn) {
            // Mark sale as returned
            val updatedSale = sale.copy(
                isReturned = true,
                note = (sale.note?.let { "$it | " } ?: "") + "সম্পূর্ণ ফেরতকৃত"
            )
            dao.updateSale(updatedSale)

            // If customer had due, adjust customer ledger
            if (sale.customerId != null && sale.dueAmountPoisha > 0) {
                val ledger = CustomerLedger(
                    customerId = sale.customerId,
                    refType = "sale_return",
                    refId = saleId,
                    debitPoisha = 0,
                    creditPoisha = sale.dueAmountPoisha,
                    note = "বিক্রয় ফেরত সমন্বয়: ${sale.invoiceNo}"
                )
                val ledgerId = dao.insertCustomerLedger(ledger)
                scope.launch {
                    supabaseSync.syncCustomerLedger(ledger.copy(id = ledgerId))
                }
            }

            scope.launch {
                supabaseSync.syncSaleReturn(updatedSale, restockedProducts)
            }
        } else {
            // Partial Return: Update sale totals
            val newSubtotal = remainingItemsAfterReturn.sumOf { it.lineTotalPoisha }
            val newTotal = (newSubtotal - sale.discountPoisha + sale.vatPoisha).coerceAtLeast(0L)

            val newDue: Long
            val newPaid: Long
            if (sale.dueAmountPoisha > 0) {
                val dueReduction = totalRefundPoisha.coerceAtMost(sale.dueAmountPoisha)
                newDue = (sale.dueAmountPoisha - dueReduction).coerceAtLeast(0L)
                newPaid = sale.paidAmountPoisha

                // Adjust customer ledger for due reduction
                if (sale.customerId != null && dueReduction > 0) {
                    val ledger = CustomerLedger(
                        customerId = sale.customerId,
                        refType = "sale_return",
                        refId = saleId,
                        debitPoisha = 0,
                        creditPoisha = dueReduction,
                        note = "আংশিক পণ্য ফেরত সমন্বয়: ${sale.invoiceNo}"
                    )
                    val ledgerId = dao.insertCustomerLedger(ledger)
                    scope.launch {
                        supabaseSync.syncCustomerLedger(ledger.copy(id = ledgerId))
                    }
                }
            } else {
                newDue = 0L
                newPaid = newTotal
            }

            val updatedSale = sale.copy(
                subtotalPoisha = newSubtotal,
                totalPoisha = newTotal,
                paidAmountPoisha = newPaid,
                dueAmountPoisha = newDue,
                note = (sale.note?.let { "$it | " } ?: "") + "আংশিক ফেরত"
            )
            dao.updateSale(updatedSale)

            scope.launch {
                supabaseSync.syncSale(updatedSale, remainingItemsAfterReturn, restockedProducts)
            }
        }

        true
    }

    suspend fun returnFullSale(saleId: Long): Boolean = withContext(Dispatchers.IO) {
        val items = dao.getSaleItems(saleId)
        val returnMap = items.associate { it.id to it.qty }
        returnSaleItems(saleId, returnMap)
    }

    suspend fun returnSale(saleId: Long) = returnFullSale(saleId)

    suspend fun deleteSale(saleId: Long) = withContext(Dispatchers.IO) {
        dao.deleteSaleById(saleId)
        dao.deleteSaleItemsBySaleId(saleId)
        dao.recordDeletedItem(DeletedRecord("sales", saleId))
        scope.launch {
            supabaseSync.deleteSaleFromSupabase(saleId)
        }
    }

    // --- CUSTOMERS & LEDGER ---
    val allCustomers: Flow<List<Customer>> = dao.getAllCustomers()
    val totalDueFlow: Flow<Long> = dao.getTotalDueFlow()

    suspend fun saveCustomer(customer: Customer): Long {
        val id = dao.insertCustomer(customer)
        val saved = customer.copy(id = id)
        dao.removeDeletedRecord("customers", id)
        scope.launch { supabaseSync.syncCustomer(saved) }
        return id
    }
    suspend fun getCustomerById(id: Long): Customer? = dao.getCustomerById(id)
    fun getCustomerLedger(customerId: Long): Flow<List<CustomerLedger>> = dao.getCustomerLedger(customerId)
    fun getCustomerBalance(customerId: Long): Flow<Long> = dao.getCustomerBalanceFlow(customerId)

    suspend fun deleteCustomer(customerId: Long) = withContext(Dispatchers.IO) {
        dao.deleteCustomerById(customerId)
        dao.deleteCustomerLedgerByCustomerId(customerId)
        dao.recordDeletedItem(DeletedRecord("customers", customerId))
        scope.launch {
            supabaseSync.deleteCustomerFromSupabase(customerId)
        }
    }

    suspend fun collectDuePayment(customerId: Long, amountPoisha: Long, note: String?): Long {
        val ledger = CustomerLedger(
            customerId = customerId,
            refType = "payment",
            refId = null,
            debitPoisha = 0,
            creditPoisha = amountPoisha,
            note = note ?: "বাকি আদায়"
        )
        val id = dao.insertCustomerLedger(ledger)
        scope.launch { supabaseSync.syncCustomerLedger(ledger.copy(id = id)) }
        return id
    }

    // --- SUPPLIERS & PURCHASES ---
    val allSuppliers: Flow<List<Supplier>> = dao.getAllSuppliers()
    val allPurchases: Flow<List<Purchase>> = dao.getAllPurchases()

    suspend fun saveSupplier(supplier: Supplier): Long {
        val id = dao.insertSupplier(supplier)
        val saved = supplier.copy(id = id)
        dao.removeDeletedRecord("suppliers", id)
        scope.launch { supabaseSync.syncSupplier(saved) }
        return id
    }

    suspend fun deleteSupplier(supplierId: Long) = withContext(Dispatchers.IO) {
        dao.deleteSupplierById(supplierId)
        dao.recordDeletedItem(DeletedRecord("suppliers", supplierId))
        scope.launch {
            supabaseSync.deleteSupplierFromSupabase(supplierId)
        }
    }

    suspend fun deletePurchase(purchaseId: Long) = withContext(Dispatchers.IO) {
        dao.deletePurchaseById(purchaseId)
        dao.deletePurchaseItemsByPurchaseId(purchaseId)
        dao.recordDeletedItem(DeletedRecord("purchases", purchaseId))
        scope.launch {
            supabaseSync.deletePurchaseFromSupabase(purchaseId)
        }
    }

    suspend fun recordPurchase(
        purchase: Purchase,
        items: List<PurchaseItem>
    ): Long = withContext(Dispatchers.IO) {
        val purchaseId = dao.insertPurchase(purchase)
        dao.removeDeletedRecord("purchases", purchaseId)
        val preparedItems = items.map { it.copy(purchaseId = purchaseId) }
        dao.insertPurchaseItems(preparedItems)

        val affectedProducts = mutableListOf<Product>()
        // Increase product stock and update purchase price
        for (item in preparedItems) {
            if (item.productId > 0) {
                dao.adjustProductStock(item.productId, item.qty)
                val existing = dao.getProductById(item.productId)
                if (existing != null) {
                    val updated = existing.copy(
                        purchasePricePoisha = item.unitPricePoisha,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.updateProduct(updated)
                    affectedProducts.add(updated)
                }
            }
        }

        // Add to supplier ledger if due
        if (purchase.dueAmountPoisha > 0) {
            dao.insertSupplierLedger(
                SupplierLedger(
                    supplierId = purchase.supplierId,
                    refType = "purchase",
                    refId = purchaseId,
                    debitPoisha = 0,
                    creditPoisha = purchase.dueAmountPoisha,
                    note = "ক্রয় ইনভয়েস: ${purchase.invoiceNo}"
                )
            )
        }

        scope.launch {
            supabaseSync.syncPurchase(purchase.copy(id = purchaseId), preparedItems, affectedProducts)
        }

        purchaseId
    }

    // --- EXPENSES ---
    val allExpenseCategories: Flow<List<ExpenseCategory>> = dao.getAllExpenseCategories()
    val allExpenses: Flow<List<Expense>> = dao.getAllExpenses()

    suspend fun addExpense(expense: Expense): Long {
        val id = dao.insertExpense(expense)
        val saved = expense.copy(id = id)
        dao.removeDeletedRecord("expenses", id)
        scope.launch { supabaseSync.syncExpense(saved) }
        return id
    }

    suspend fun deleteExpense(expenseId: Long) = withContext(Dispatchers.IO) {
        dao.deleteExpenseById(expenseId)
        dao.recordDeletedItem(DeletedRecord("expenses", expenseId))
        scope.launch {
            supabaseSync.deleteExpenseFromSupabase(expenseId)
        }
    }

    // --- BACKUP & RESTORE ---
    val backupLogs: Flow<List<BackupLog>> = dao.getAllBackupLogs()

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Dokan Pro")
        root.put("version", "1.0")
        root.put("timestamp", System.currentTimeMillis())

        // Products
        val products = dao.getAllProductsSync()
        val prodArr = JSONArray()
        for (p in products) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("nameBn", p.nameBn)
            obj.put("nameEn", p.nameEn)
            obj.put("categoryId", p.categoryId)
            obj.put("unitName", p.unitName)
            obj.put("barcode", p.barcode)
            obj.put("purchasePricePoisha", p.purchasePricePoisha)
            obj.put("salePricePoisha", p.salePricePoisha)
            obj.put("stockQty", p.stockQty)
            obj.put("minStock", p.minStock)
            prodArr.put(obj)
        }
        root.put("products", prodArr)

        // Customers
        val customers = dao.getAllCustomersSync()
        val custArr = JSONArray()
        for (c in customers) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("phone", c.phone)
            obj.put("address", c.address)
            obj.put("creditLimitPoisha", c.creditLimitPoisha)
            custArr.put(obj)
        }
        root.put("customers", custArr)

        // Sales & Sale Items
        val sales = dao.getAllSalesSync()
        val salesArr = JSONArray()
        for (s in sales) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("invoiceNo", s.invoiceNo)
            obj.put("customerId", s.customerId ?: JSONObject.NULL)
            obj.put("customerName", s.customerName ?: JSONObject.NULL)
            obj.put("saleDate", s.saleDate)
            obj.put("subtotalPoisha", s.subtotalPoisha)
            obj.put("discountPoisha", s.discountPoisha)
            obj.put("vatPoisha", s.vatPoisha)
            obj.put("totalPoisha", s.totalPoisha)
            obj.put("paidAmountPoisha", s.paidAmountPoisha)
            obj.put("dueAmountPoisha", s.dueAmountPoisha)
            obj.put("paymentMethod", s.paymentMethod)
            salesArr.put(obj)
        }
        root.put("sales", salesArr)

        val saleItems = dao.getAllSaleItemsSync()
        val saleItemsArr = JSONArray()
        for (si in saleItems) {
            val obj = JSONObject()
            obj.put("id", si.id)
            obj.put("saleId", si.saleId)
            obj.put("productId", si.productId)
            obj.put("productName", si.productName)
            obj.put("unitName", si.unitName)
            obj.put("qty", si.qty)
            obj.put("unitPricePoisha", si.unitPricePoisha)
            obj.put("lineTotalPoisha", si.lineTotalPoisha)
            saleItemsArr.put(obj)
        }
        root.put("saleItems", saleItemsArr)

        // Expenses
        val expenses = dao.getAllExpensesSync()
        val expArr = JSONArray()
        for (e in expenses) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("categoryId", e.categoryId)
            obj.put("categoryName", e.categoryName)
            obj.put("amountPoisha", e.amountPoisha)
            obj.put("note", e.note ?: JSONObject.NULL)
            obj.put("expenseDate", e.expenseDate)
            expArr.put(obj)
        }
        root.put("expenses", expArr)

        // Suppliers
        val suppliers = dao.getAllSuppliersSync()
        val supArr = JSONArray()
        for (sp in suppliers) {
            val obj = JSONObject()
            obj.put("id", sp.id)
            obj.put("name", sp.name)
            obj.put("phone", sp.phone)
            obj.put("company", sp.company ?: JSONObject.NULL)
            obj.put("address", sp.address ?: JSONObject.NULL)
            supArr.put(obj)
        }
        root.put("suppliers", supArr)

        // Purchases
        val purchases = dao.getAllPurchasesSync()
        val purArr = JSONArray()
        for (pur in purchases) {
            val obj = JSONObject()
            obj.put("id", pur.id)
            obj.put("invoiceNo", pur.invoiceNo)
            obj.put("supplierId", pur.supplierId)
            obj.put("supplierName", pur.supplierName)
            obj.put("purchaseDate", pur.purchaseDate)
            obj.put("totalPoisha", pur.totalPoisha)
            obj.put("paidAmountPoisha", pur.paidAmountPoisha)
            obj.put("dueAmountPoisha", pur.dueAmountPoisha)
            obj.put("note", pur.note ?: JSONObject.NULL)
            purArr.put(obj)
        }
        root.put("purchases", purArr)

        // Purchase Items
        val purchaseItems = dao.getAllPurchaseItemsSync()
        val purItemsArr = JSONArray()
        purchaseItems.forEach { pi ->
            val obj = JSONObject()
            obj.put("id", pi.id)
            obj.put("purchaseId", pi.purchaseId)
            obj.put("productId", pi.productId)
            obj.put("productName", pi.productName)
            obj.put("qty", pi.qty)
            obj.put("unitPricePoisha", pi.unitPricePoisha)
            obj.put("lineTotalPoisha", pi.lineTotalPoisha)
            purItemsArr.put(obj)
        }
        root.put("purchaseItems", purItemsArr)

        val jsonStr = root.toString(2)

        dao.insertBackupLog(
            BackupLog(
                type = "manual",
                status = "success",
                fileName = "dokan_pro_backup_${System.currentTimeMillis()}.json",
                sizeBytes = jsonStr.toByteArray().size.toLong(),
                recordCount = products.size + customers.size + sales.size + expenses.size + purchases.size + suppliers.size
            )
        )

        jsonStr
    }

    suspend fun restoreBackupFromJson(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)

            // Restore Products
            if (root.has("products")) {
                val arr = root.getJSONArray("products")
                val list = mutableListOf<Product>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Product(
                            id = obj.optLong("id", 0L),
                            nameBn = obj.optString("nameBn", ""),
                            nameEn = obj.optString("nameEn", ""),
                            categoryId = obj.optLong("categoryId", 1L),
                            unitName = obj.optString("unitName", "পিস"),
                            barcode = obj.optString("barcode", ""),
                            purchasePricePoisha = obj.optLong("purchasePricePoisha", 0L),
                            salePricePoisha = obj.optLong("salePricePoisha", 0L),
                            stockQty = obj.optDouble("stockQty", 0.0),
                            minStock = obj.optDouble("minStock", 5.0)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertProducts(list)
            }

            // Restore Customers
            if (root.has("customers")) {
                val arr = root.getJSONArray("customers")
                val list = mutableListOf<Customer>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Customer(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", ""),
                            address = if (obj.isNull("address")) null else obj.optString("address"),
                            creditLimitPoisha = obj.optLong("creditLimitPoisha", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertCustomers(list)
            }

            // Restore Sales
            if (root.has("sales")) {
                val arr = root.getJSONArray("sales")
                val list = mutableListOf<Sale>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Sale(
                            id = obj.optLong("id", 0L),
                            invoiceNo = obj.optString("invoiceNo", ""),
                            customerId = if (obj.isNull("customerId")) null else obj.optLong("customerId"),
                            customerName = if (obj.isNull("customerName")) null else obj.optString("customerName"),
                            saleDate = obj.optLong("saleDate", System.currentTimeMillis()),
                            subtotalPoisha = obj.optLong("subtotalPoisha", 0L),
                            discountPoisha = obj.optLong("discountPoisha", 0L),
                            vatPoisha = obj.optLong("vatPoisha", 0L),
                            totalPoisha = obj.optLong("totalPoisha", 0L),
                            paidAmountPoisha = obj.optLong("paidAmountPoisha", 0L),
                            dueAmountPoisha = obj.optLong("dueAmountPoisha", 0L),
                            paymentMethod = obj.optString("paymentMethod", "cash")
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertSales(list)
            }

            // Restore Sale Items
            if (root.has("saleItems")) {
                val arr = root.getJSONArray("saleItems")
                val list = mutableListOf<SaleItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SaleItem(
                            id = obj.optLong("id", 0L),
                            saleId = obj.optLong("saleId", 0L),
                            productId = obj.optLong("productId", 0L),
                            productName = obj.optString("productName", ""),
                            unitName = obj.optString("unitName", "পিস"),
                            qty = obj.optDouble("qty", 1.0),
                            unitPricePoisha = obj.optLong("unitPricePoisha", 0L),
                            purchasePriceAtSalePoisha = obj.optLong("purchasePriceAtSalePoisha", 0L),
                            discountPoisha = obj.optLong("discountPoisha", 0L),
                            lineTotalPoisha = obj.optLong("lineTotalPoisha", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertSaleItems(list)
            }

            // Restore Expenses
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                val list = mutableListOf<Expense>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Expense(
                            id = obj.optLong("id", 0L),
                            categoryId = obj.optLong("categoryId", 1L),
                            categoryName = obj.optString("categoryName", ""),
                            amountPoisha = obj.optLong("amountPoisha", 0L),
                            note = if (obj.isNull("note")) null else obj.optString("note"),
                            expenseDate = obj.optLong("expenseDate", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertExpenses(list)
            }

            // Restore Suppliers
            if (root.has("suppliers")) {
                val arr = root.getJSONArray("suppliers")
                val list = mutableListOf<Supplier>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Supplier(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", ""),
                            company = if (obj.isNull("company")) null else obj.optString("company"),
                            address = if (obj.isNull("address")) null else obj.optString("address")
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertSuppliers(list)
            }

            // Restore Purchases
            if (root.has("purchases")) {
                val arr = root.getJSONArray("purchases")
                val list = mutableListOf<Purchase>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Purchase(
                            id = obj.optLong("id", 0L),
                            invoiceNo = obj.optString("invoiceNo", ""),
                            supplierId = obj.optLong("supplierId", 1L),
                            supplierName = obj.optString("supplierName", ""),
                            purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                            totalPoisha = obj.optLong("totalPoisha", 0L),
                            paidAmountPoisha = obj.optLong("paidAmountPoisha", 0L),
                            dueAmountPoisha = obj.optLong("dueAmountPoisha", 0L),
                            note = if (obj.isNull("note")) null else obj.optString("note")
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertPurchases(list)
            }

            // Restore Purchase Items
            if (root.has("purchaseItems")) {
                val arr = root.getJSONArray("purchaseItems")
                val list = mutableListOf<PurchaseItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PurchaseItem(
                            id = obj.optLong("id", 0L),
                            purchaseId = obj.optLong("purchaseId", 0L),
                            productId = obj.optLong("productId", 0L),
                            productName = obj.optString("productName", ""),
                            qty = obj.optDouble("qty", 1.0),
                            unitPricePoisha = obj.optLong("unitPricePoisha", obj.optLong("unitCostPoisha", 0L)),
                            lineTotalPoisha = obj.optLong("lineTotalPoisha", 0L)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.insertPurchaseItems(list)
            }

            dao.insertBackupLog(
                BackupLog(
                    type = "restore",
                    status = "success",
                    fileName = "restored_backup.json",
                    sizeBytes = jsonStr.toByteArray().size.toLong(),
                    recordCount = 0
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        // Only initialize standard categories if empty; never seed dummy products/customers/suppliers
        if (dao.getCategoriesSync().isEmpty()) {
            dao.insertCategories(SampleData.getDefaultCategories())
        }
        if (dao.getExpenseCategoriesSync().isEmpty()) {
            dao.insertExpenseCategories(SampleData.getDefaultExpenseCategories())
        }
    }

    suspend fun clearAllDummyData() = withContext(Dispatchers.IO) {
        dao.clearProducts()
        dao.clearSales()
        dao.clearSaleItems()
        dao.clearCustomers()
        dao.clearCustomerLedger()
        dao.clearExpenses()
        dao.clearPurchases()
        dao.clearPurchaseItems()
        dao.clearSuppliers()
        dao.clearStockAdjustments()
        dao.clearDeletedRecords()

        // Ensure default grocery categories remain ready for real product additions
        if (dao.getCategoriesSync().isEmpty()) {
            dao.insertCategories(SampleData.getDefaultCategories())
        }
        if (dao.getExpenseCategoriesSync().isEmpty()) {
            dao.insertExpenseCategories(SampleData.getDefaultExpenseCategories())
        }

        // Also purge all dummy data from Supabase cloud database immediately
        scope.launch {
            supabaseSync.clearAllDataInSupabase()
        }
    }

    suspend fun wipeAllDataCloudAndLocal(): Boolean = withContext(Dispatchers.IO) {
        dao.clearProducts()
        dao.clearSales()
        dao.clearSaleItems()
        dao.clearCustomers()
        dao.clearCustomerLedger()
        dao.clearExpenses()
        dao.clearPurchases()
        dao.clearPurchaseItems()
        dao.clearSuppliers()
        dao.clearStockAdjustments()
        dao.clearDeletedRecords()

        // Ensure default grocery categories remain ready for real product additions
        if (dao.getCategoriesSync().isEmpty()) {
            dao.insertCategories(SampleData.getDefaultCategories())
        }
        if (dao.getExpenseCategoriesSync().isEmpty()) {
            dao.insertExpenseCategories(SampleData.getDefaultExpenseCategories())
        }

        val cloudSuccess = supabaseSync.clearAllDataInSupabase()
        supabaseSync.invalidateTableCache()
        cloudSuccess
    }

    suspend fun clearSelectiveData(
        clearSales: Boolean,
        clearProducts: Boolean,
        clearCustomers: Boolean,
        clearExpenses: Boolean,
        clearPurchases: Boolean,
        beforeTimestamp: Long? = null
    ) = withContext(Dispatchers.IO) {
        if (clearSales) {
            if (beforeTimestamp != null) {
                dao.clearSalesBefore(beforeTimestamp)
                dao.cleanOrphanSaleItems()
                scope.launch {
                    supabaseSync.deleteFromSupabaseFilter("sales", "sale_date=lt.$beforeTimestamp")
                }
            } else {
                dao.clearSales()
                dao.clearSaleItems()
                scope.launch {
                    supabaseSync.clearTableInSupabase("sale_items")
                    supabaseSync.clearTableInSupabase("sales")
                }
            }
        }
        if (clearProducts) {
            dao.clearProducts()
            dao.clearStockAdjustments()
            scope.launch {
                supabaseSync.clearTableInSupabase("products")
                supabaseSync.clearTableInSupabase("stock_adjustments")
            }
        }
        if (clearCustomers) {
            dao.clearCustomers()
            dao.clearCustomerLedger()
            scope.launch {
                supabaseSync.clearTableInSupabase("customer_ledger")
                supabaseSync.clearTableInSupabase("customers")
            }
        }
        if (clearExpenses) {
            if (beforeTimestamp != null) {
                dao.clearExpensesBefore(beforeTimestamp)
                scope.launch {
                    supabaseSync.deleteFromSupabaseFilter("expenses", "expense_date=lt.$beforeTimestamp")
                }
            } else {
                dao.clearExpenses()
                scope.launch {
                    supabaseSync.clearTableInSupabase("expenses")
                }
            }
        }
        if (clearPurchases) {
            dao.clearPurchases()
            dao.clearPurchaseItems()
            scope.launch {
                supabaseSync.clearTableInSupabase("purchase_items")
                supabaseSync.clearTableInSupabase("purchases")
            }
        }
    }

    suspend fun publishAppVersion(versionCode: Int, versionName: String, updateNotes: String, apkUrl: String): Boolean = withContext(Dispatchers.IO) {
        supabaseSync.publishAppVersion(versionCode, versionName, updateNotes, apkUrl)
    }

    suspend fun syncWithSupabase(): com.example.data.supabase.SyncResult = withContext(Dispatchers.IO) {
        supabaseSync.syncTwoWay()
    }

    suspend fun pullFromSupabase(force: Boolean = false): com.example.data.supabase.SyncResult = withContext(Dispatchers.IO) {
        supabaseSync.pullFromSupabase(force)
    }

    fun updateCustomerCloudCredentials(url: String, key: String) {
        supabaseSync.setCustomerCredentials(url, key)
    }

    val isCustomerCloudConfigured: Boolean
        get() = supabaseSync.isCustomerConfigured

    suspend fun testCustomerCloudConnection(url: String, key: String): Pair<Boolean, String> {
        return supabaseSync.testConnection(url, key)
    }

    suspend fun backupToCustomerCloud(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!supabaseSync.isCustomerConfigured) {
            return@withContext Pair(false, "কাস্টমার ক্লাউড সিঙ্ক কনফিগার করা নেই। অনুগ্রহ করে সেটিংস থেকে Supabase URL ও Key সেট করুন।")
        }
        val success = supabaseSync.syncAllLocalToSupabase()
        val recordCount = dao.getAllProductsSync().size + dao.getAllSalesSync().size + dao.getAllCustomersSync().size
        dao.insertBackupLog(
            BackupLog(
                type = "cloud_push",
                status = if (success) "success" else "failed",
                fileName = "Supabase Cloud Backup",
                sizeBytes = 0L,
                recordCount = recordCount
            )
        )
        if (success) {
            Pair(true, "ক্লাউডে সম্পূর্ণ ডেটা সফলভাবে ব্যাকআপ সংরক্ষণ করা হয়েছে!")
        } else {
            Pair(false, "ক্লাউডে ব্যাকআপ ব্যর্থ হয়েছে। অনুগ্রহ করে ইন্টারনেট ও Supabase সেটিংস পরীক্ষা করুন।")
        }
    }

    suspend fun restoreFromCustomerCloud(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!supabaseSync.isCustomerConfigured) {
            return@withContext Pair(false, "কাস্টমার ক্লাউড সিঙ্ক কনফিগার করা নেই। অনুগ্রহ করে সেটিংস থেকে Supabase URL ও Key সেট করুন।")
        }
        val result = supabaseSync.pullFromSupabase(force = true)
        val recordCount = result.pulledProductsCount + result.pulledSalesCount
        dao.insertBackupLog(
            BackupLog(
                type = "cloud_pull",
                status = if (result.success) "success" else "failed",
                fileName = "Supabase Cloud Restore",
                sizeBytes = 0L,
                recordCount = recordCount
            )
        )
        if (result.success) {
            Pair(true, "ক্লাউড থেকে সফলভাবে ডেটা রিস্টোর সম্পন্ন হয়েছে! (${result.pulledProductsCount}টি পণ্য, ${result.pulledSalesCount}টি বিক্রি)")
        } else {
            Pair(false, result.message.ifBlank { "ক্লাউড থেকে রিস্টোর ব্যর্থ হয়েছে।" })
        }
    }

    suspend fun resetAllData() = withContext(Dispatchers.IO) {
        clearAllDummyData()
        seedInitialDataIfEmpty()
        syncWithSupabase()
    }
}


