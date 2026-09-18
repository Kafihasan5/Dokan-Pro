package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameBn: String,
    val nameEn: String = "",
    val categoryId: Long = 1,
    val unitName: String = "কেজি",
    val barcode: String = "",
    val purchasePricePoisha: Long = 0,
    val salePricePoisha: Long = 0,
    val wholesalePricePoisha: Long = 0,
    val stockQty: Double = 0.0,
    val minStock: Double = 5.0,
    val expiryDate: String? = null,
    val supplierId: Long? = null,
    val isActive: Boolean = true,
    val localImagePath: String? = null, // LOCAL ONLY: Phone storage path, never synced to cloud/Supabase
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameBn: String,
    val nameEn: String,
    val iconName: String = "shopping_basket",
    val sortOrder: Int = 0
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val customerId: Long? = null,
    val customerName: String? = null,
    val saleDate: Long = System.currentTimeMillis(),
    val subtotalPoisha: Long = 0,
    val discountPoisha: Long = 0,
    val vatPoisha: Long = 0,
    val totalPoisha: Long = 0,
    val paidAmountPoisha: Long = 0,
    val dueAmountPoisha: Long = 0,
    val paymentMethod: String = "cash", // "cash", "mfs", "card", "due"
    val userId: Long = 1,
    val note: String? = null,
    val isReturned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val unitName: String = "পিস",
    val qty: Double,
    val unitPricePoisha: Long,
    val purchasePriceAtSalePoisha: Long,
    val discountPoisha: Long = 0,
    val lineTotalPoisha: Long
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String? = null,
    val creditLimitPoisha: Long = 500000, // 5000 Tk default
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customer_ledger")
data class CustomerLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val refType: String, // "sale", "payment", "adjustment"
    val refId: Long? = null,
    val debitPoisha: Long = 0, // due increased
    val creditPoisha: Long = 0, // payment received
    val note: String? = null,
    val entryDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val company: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val supplierId: Long,
    val supplierName: String,
    val purchaseDate: Long = System.currentTimeMillis(),
    val totalPoisha: Long,
    val paidAmountPoisha: Long,
    val dueAmountPoisha: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchase_items")
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitPricePoisha: Long,
    val lineTotalPoisha: Long
)

@Entity(tableName = "supplier_ledger")
data class SupplierLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val refType: String, // "purchase", "payment"
    val refId: Long? = null,
    val debitPoisha: Long = 0, // paid to supplier
    val creditPoisha: Long = 0, // bill from supplier
    val note: String? = null,
    val entryDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameBn: String,
    val nameEn: String,
    val icon: String = "receipt"
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val categoryName: String,
    val amountPoisha: Long,
    val note: String? = null,
    val expenseDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_adjustments")
data class StockAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val qtyChange: Double,
    val reason: String, // "নষ্ট", "চুরি", "গণনা ভুল", "অন্যান্য"
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_logs")
data class BackupLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "manual", "auto", "restore"
    val status: String, // "success", "failed"
    val fileName: String,
    val sizeBytes: Long,
    val recordCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "deleted_records", primaryKeys = ["tableName", "recordId"])
data class DeletedRecord(
    val tableName: String,
    val recordId: Long,
    val deletedAt: Long = System.currentTimeMillis()
)

data class StaffMember(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val pin: String,
    val phone: String = "",
    val role: String = "staff",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
