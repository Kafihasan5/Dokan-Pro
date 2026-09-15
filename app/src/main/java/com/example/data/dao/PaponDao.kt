package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaponDao {

    // --- PRODUCTS ---
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY nameBn ASC")
    fun getAllActiveProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stockQty <= minStock ORDER BY stockQty ASC")
    fun getLowStockProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET stockQty = stockQty + :qtyChange, updatedAt = :timestamp WHERE id = :productId")
    suspend fun adjustProductStock(productId: Long, qtyChange: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isActive = 0, updatedAt = :timestamp WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun getActiveProductCount(): Int

    // --- CATEGORIES ---
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Long)

    @Query("UPDATE products SET categoryId = :newCatId WHERE categoryId = :oldCatId")
    suspend fun reassignProductsCategory(oldCatId: Long, newCatId: Long)

    @Query("UPDATE products SET unitName = :newUnit WHERE unitName = :oldUnit")
    suspend fun updateProductUnitName(oldUnit: String, newUnit: String)

    // --- SALES & SALE ITEMS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<Sale>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE saleDate >= :startTime AND saleDate <= :endTime ORDER BY saleDate DESC")
    fun getSalesBetween(startTime: Long, endTime: Long): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Long): Sale?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItem>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItemsFlow(saleId: Long): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items ORDER BY id DESC")
    fun getAllSaleItems(): Flow<List<SaleItem>>

    @Query("UPDATE sales SET isReturned = 1 WHERE id = :saleId")
    suspend fun markSaleAsReturned(saleId: Long)

    // --- CUSTOMERS & LEDGER ---
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerLedger(ledger: CustomerLedger): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerLedgers(ledgers: List<CustomerLedger>)

    @Query("SELECT * FROM customer_ledger WHERE customerId = :customerId ORDER BY entryDate DESC")
    fun getCustomerLedger(customerId: Long): Flow<List<CustomerLedger>>

    @Query("SELECT * FROM customer_ledger ORDER BY entryDate DESC")
    fun getAllCustomerLedgers(): Flow<List<CustomerLedger>>

    @Query("SELECT COALESCE(SUM(debitPoisha - creditPoisha), 0) FROM customer_ledger WHERE customerId = :customerId")
    fun getCustomerBalanceFlow(customerId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(debitPoisha - creditPoisha), 0) FROM customer_ledger")
    fun getTotalDueFlow(): Flow<Long>

    // --- SUPPLIERS & PURCHASES ---
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppliers(suppliers: List<Supplier>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<Purchase>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItem>)

    @Query("SELECT * FROM purchases ORDER BY purchaseDate DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplierLedger(ledger: SupplierLedger): Long

    @Query("SELECT * FROM supplier_ledger WHERE supplierId = :supplierId ORDER BY entryDate DESC")
    fun getSupplierLedger(supplierId: Long): Flow<List<SupplierLedger>>

    // --- EXPENSES ---
    @Query("SELECT * FROM expense_categories ORDER BY id ASC")
    fun getAllExpenseCategories(): Flow<List<ExpenseCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseCategories(categories: List<ExpenseCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE expenseDate >= :startTime AND expenseDate <= :endTime ORDER BY expenseDate DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<Expense>>

    // --- STOCK ADJUSTMENT ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockAdjustment(adjustment: StockAdjustment): Long

    @Query("SELECT * FROM stock_adjustments ORDER BY createdAt DESC")
    fun getAllStockAdjustments(): Flow<List<StockAdjustment>>

    // --- BACKUP LOGS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupLog(log: BackupLog): Long

    @Query("SELECT * FROM backup_logs ORDER BY createdAt DESC")
    fun getAllBackupLogs(): Flow<List<BackupLog>>

    // Bulk queries for export & backup
    @Query("SELECT * FROM products")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSync(): List<Category>

    @Query("SELECT * FROM sales")
    suspend fun getAllSalesSync(): List<Sale>

    @Query("SELECT * FROM sale_items")
    suspend fun getAllSaleItemsSync(): List<SaleItem>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersSync(): List<Customer>

    @Query("SELECT * FROM customer_ledger")
    suspend fun getAllCustomerLedgersSync(): List<CustomerLedger>

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliersSync(): List<Supplier>

    @Query("SELECT * FROM purchases")
    suspend fun getAllPurchasesSync(): List<Purchase>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<Expense>

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM sale_items")
    suspend fun clearSaleItems()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM customer_ledger")
    suspend fun clearCustomerLedger()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Query("DELETE FROM purchases")
    suspend fun clearPurchases()

    @Query("DELETE FROM purchase_items")
    suspend fun clearPurchaseItems()

    @Query("DELETE FROM suppliers")
    suspend fun clearSuppliers()

    @Query("DELETE FROM stock_adjustments")
    suspend fun clearStockAdjustments()

    @Query("DELETE FROM sales WHERE saleDate < :beforeTimestamp")
    suspend fun clearSalesBefore(beforeTimestamp: Long)

    @Query("DELETE FROM sale_items WHERE saleId NOT IN (SELECT id FROM sales)")
    suspend fun cleanOrphanSaleItems()

    @Query("DELETE FROM expenses WHERE expenseDate < :beforeTimestamp")
    suspend fun clearExpensesBefore(beforeTimestamp: Long)

    @Query("SELECT * FROM categories")
    suspend fun getCategoriesSync(): List<Category>

    @Query("SELECT * FROM expense_categories")
    suspend fun getExpenseCategoriesSync(): List<ExpenseCategory>

    // Individual item deletions
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    @Query("DELETE FROM sales WHERE id = :id")
    suspend fun deleteSaleById(id: Long)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySaleId(saleId: Long)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("DELETE FROM customer_ledger WHERE customerId = :customerId")
    suspend fun deleteCustomerLedgerByCustomerId(customerId: Long)

    @Query("DELETE FROM customer_ledger WHERE id = :id")
    suspend fun deleteCustomerLedgerById(id: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplierById(id: Long)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchaseById(id: Long)

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deletePurchaseItemsByPurchaseId(purchaseId: Long)

    // Deleted records tracker for offline & cloud sync synchronization
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletedItem(item: DeletedRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletedItems(items: List<DeletedRecord>)

    @Query("SELECT recordId FROM deleted_records WHERE tableName = :tableName")
    suspend fun getDeletedRecordIds(tableName: String): List<Long>

    @Query("SELECT * FROM deleted_records")
    suspend fun getAllDeletedRecords(): List<DeletedRecord>

    @Query("DELETE FROM deleted_records WHERE tableName = :tableName AND recordId = :recordId")
    suspend fun removeDeletedRecord(tableName: String, recordId: Long)

    @Query("DELETE FROM deleted_records")
    suspend fun clearDeletedRecords()
}

