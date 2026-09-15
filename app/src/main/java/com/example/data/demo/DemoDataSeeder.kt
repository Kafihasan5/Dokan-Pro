package com.example.data.demo

import com.example.data.dao.PaponDao
import com.example.data.entity.*
import com.example.util.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DemoDataSeeder {

    /**
     * Seeds 7 days of rich, realistic grocery shop dummy data.
     * Used for the 1-hour free demo mode so prospective buyers can experience
     * all features (Dashboard charts, POS, Due Khata, Reports, Expenses).
     *
     * IMPORTANT: When the customer activates with their purchase email,
     * this data is wiped clean!
     */
    suspend fun seed7DaysDemoData(dao: PaponDao) = withContext(Dispatchers.IO) {
        // 1. Clear any existing data first to guarantee clean slate
        dao.clearProducts()
        dao.clearSales()
        dao.clearSaleItems()
        dao.clearCustomers()
        dao.clearCustomerLedger()
        dao.clearExpenses()
        dao.clearPurchases()
        dao.clearPurchaseItems()
        dao.clearSuppliers()
        dao.clearSupplierLedger()
        dao.clearStockAdjustments()
        dao.clearDeletedRecords()

        // 2. Categories
        val defaultCategories = SampleData.getDefaultCategories()
        dao.insertCategories(defaultCategories)

        val defaultExpenseCategories = SampleData.getDefaultExpenseCategories()
        dao.insertExpenseCategories(defaultExpenseCategories)

        // 3. Products
        val sampleProducts = SampleData.getSampleGroceryProducts()
        dao.insertProducts(sampleProducts)
        val insertedProducts = dao.getAllProductsSync().associateBy { it.barcode }

        // Helper to get product by barcode
        fun getProd(barcode: String): Product = insertedProducts[barcode] ?: insertedProducts.values.first()

        // 4. Customers
        val sampleCustomers = SampleData.getSampleCustomers()
        dao.insertCustomers(sampleCustomers)
        val insertedCustomers = dao.getAllCustomersSync()
        val custRafiq = insertedCustomers.getOrNull(0)
        val custKarim = insertedCustomers.getOrNull(1)
        val custShahin = insertedCustomers.getOrNull(2)

        // 5. Suppliers
        val sampleSuppliers = SampleData.getSampleSuppliers()
        dao.insertSuppliers(sampleSuppliers)
        val insertedSuppliers = dao.getAllSuppliersSync()
        val supplierHaq = insertedSuppliers.getOrNull(0)

        // Date calculations
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)

        var invoiceCounter = 1001

        fun makeInvoice(dateMs: Long): String {
            val dateStr = dateFormat.format(Date(dateMs))
            invoiceCounter++
            return "INV-$dateStr-$invoiceCounter"
        }

        // Helper to record a complete sale with items and due tracking
        suspend fun recordSale(
            saleDate: Long,
            customer: Customer?,
            items: List<Pair<Product, Double>>,
            paidAmountTk: Long,
            paymentMethod: String = "cash",
            note: String? = null
        ) {
            val invoiceNo = makeInvoice(saleDate)
            var subtotalPoisha = 0L

            items.forEach { (prod, qty) ->
                subtotalPoisha += (prod.salePricePoisha * qty).toLong()
            }

            val totalPoisha = subtotalPoisha
            val paidPoisha = (paidAmountTk * 100).coerceAtMost(totalPoisha)
            val duePoisha = (totalPoisha - paidPoisha).coerceAtLeast(0L)

            val sale = Sale(
                invoiceNo = invoiceNo,
                customerId = customer?.id,
                customerName = customer?.name,
                saleDate = saleDate,
                subtotalPoisha = subtotalPoisha,
                discountPoisha = 0L,
                vatPoisha = 0L,
                totalPoisha = totalPoisha,
                paidAmountPoisha = paidPoisha,
                dueAmountPoisha = duePoisha,
                paymentMethod = if (duePoisha > 0 && paidPoisha == 0L) "due" else paymentMethod,
                userId = 1L,
                note = note
            )

            val saleId = dao.insertSale(sale)

            val saleItems = items.map { (prod, qty) ->
                SaleItem(
                    saleId = saleId,
                    productId = prod.id,
                    productName = prod.nameBn,
                    unitName = prod.unitName,
                    qty = qty,
                    unitPricePoisha = prod.salePricePoisha,
                    purchasePriceAtSalePoisha = prod.purchasePricePoisha,
                    discountPoisha = 0L,
                    lineTotalPoisha = (prod.salePricePoisha * qty).toLong()
                )
            }
            dao.insertSaleItems(saleItems)

            // If customer has due, record into customer ledger
            if (customer != null && duePoisha > 0) {
                dao.insertCustomerLedger(
                    CustomerLedger(
                        customerId = customer.id,
                        refType = "sale",
                        refId = saleId,
                        debitPoisha = duePoisha,
                        creditPoisha = 0L,
                        note = "ইনভয়েস #$invoiceNo থেকে বাকি",
                        entryDate = saleDate,
                        createdAt = saleDate
                    )
                )
            }
        }

        // Helper to record customer due repayment
        suspend fun recordDuePayment(
            customer: Customer,
            amountTk: Long,
            dateMs: Long,
            note: String
        ) {
            dao.insertCustomerLedger(
                CustomerLedger(
                    customerId = customer.id,
                    refType = "payment",
                    refId = null,
                    debitPoisha = 0L,
                    creditPoisha = amountTk * 100,
                    note = note,
                    entryDate = dateMs,
                    createdAt = dateMs
                )
            )
        }

        // ==========================================
        // 6. 7 DAYS OF SALES & DUE TRANSACTIONS
        // ==========================================

        // DAY -6 (6 days ago)
        val day6 = now - (6 * dayMs)
        recordSale(
            saleDate = day6 + 3600000L * 2,
            customer = null,
            items = listOf(getProd("8901001") to 5.0, getProd("8901003") to 1.0), // Miniket 5kg + Deshi Dal 1kg
            paidAmountTk = 510,
            paymentMethod = "cash"
        )
        if (custRafiq != null) {
            recordSale(
                saleDate = day6 + 3600000L * 4,
                customer = custRafiq,
                items = listOf(getProd("8901006") to 1.0, getProd("8901007") to 2.0), // Teer Oil 5L + Sugar 2kg
                paidAmountTk = 600,
                paymentMethod = "cash",
                note = "বাকি ৫০০ টাকা পরে দেবে"
            )
        }
        recordSale(
            saleDate = day6 + 3600000L * 6,
            customer = null,
            items = listOf(getProd("8901016") to 2.0, getProd("8901014") to 1.0), // Eggs 2 hali + Tea 400g
            paidAmountTk = 338,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = day6 + 3600000L * 8,
            customer = null,
            items = listOf(getProd("8901009") to 3.0, getProd("8901010") to 5.0, getProd("8901011") to 1.0), // Onion, Potato, Garlic
            paidAmountTk = 715,
            paymentMethod = "mfs"
        )

        // DAY -5 (5 days ago)
        val day5 = now - (5 * dayMs)
        recordSale(
            saleDate = day5 + 3600000L * 1,
            customer = null,
            items = listOf(getProd("8901017") to 2.0, getProd("8901007") to 1.0), // Atta 2kg + Sugar 1kg
            paidAmountTk = 387,
            paymentMethod = "cash"
        )
        if (custKarim != null) {
            recordSale(
                saleDate = day5 + 3600000L * 3,
                customer = custKarim,
                items = listOf(getProd("8901001") to 10.0, getProd("8901005") to 1.0), // Miniket 10kg + Oil 1L
                paidAmountTk = 500,
                paymentMethod = "cash",
                note = "বাকি ৪২৫ টাকা"
            )
        }
        recordSale(
            saleDate = day5 + 3600000L * 5,
            customer = null,
            items = listOf(getProd("8901008") to 2.0, getProd("8901012") to 1.0, getProd("8901013") to 1.0), // Salt, Turmeric, Chilli
            paidAmountTk = 286,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = day5 + 3600000L * 7,
            customer = null,
            items = listOf(getProd("8901019") to 4.0, getProd("8901020") to 3.0), // Biscuits + Soap
            paidAmountTk = 365,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = day5 + 3600000L * 9,
            customer = null,
            items = listOf(getProd("8901002") to 5.0), // Nazirshail Rice 5kg
            paidAmountTk = 425,
            paymentMethod = "cash"
        )

        // DAY -4 (4 days ago)
        val day4 = now - (4 * dayMs)
        recordSale(
            saleDate = day4 + 3600000L * 2,
            customer = null,
            items = listOf(getProd("8901016") to 3.0, getProd("8901015") to 1.0), // Eggs + Milk Powder
            paidAmountTk = 637,
            paymentMethod = "cash"
        )
        if (custShahin != null) {
            recordSale(
                saleDate = day4 + 3600000L * 4,
                customer = custShahin,
                items = listOf(getProd("8901005") to 1.0, getProd("8901003") to 2.0, getProd("8901001") to 5.0),
                paidAmountTk = 820,
                paymentMethod = "mfs"
            )
        }
        recordSale(
            saleDate = day4 + 3600000L * 6,
            customer = null,
            items = listOf(getProd("8901009") to 2.0, getProd("8901010") to 3.0), // Onion + Potato
            paidAmountTk = 320,
            paymentMethod = "cash"
        )
        if (custRafiq != null) {
            recordDuePayment(
                customer = custRafiq,
                amountTk = 300,
                dateMs = day4 + 3600000L * 7,
                note = "বাকি আদায় (ক্যাশ পরিশোধ)"
            )
        }

        // DAY -3 (3 days ago)
        val day3 = now - (3 * dayMs)
        recordSale(
            saleDate = day3 + 3600000L * 2,
            customer = null,
            items = listOf(getProd("8901018") to 2.0, getProd("8901007") to 2.0), // Maida + Sugar
            paidAmountTk = 566,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = day3 + 3600000L * 4,
            customer = null,
            items = listOf(getProd("8901001") to 10.0, getProd("8901003") to 2.0), // Miniket 10kg + Dal 2kg
            paidAmountTk = 1020,
            paymentMethod = "cash"
        )
        if (custKarim != null) {
            recordSale(
                saleDate = day3 + 3600000L * 6,
                customer = custKarim,
                items = listOf(getProd("8901006") to 1.0), // Teer Oil 5L
                paidAmountTk = 400,
                paymentMethod = "cash",
                note = "বাকি ৪৪৫ টাকা"
            )
        }
        recordSale(
            saleDate = day3 + 3600000L * 8,
            customer = null,
            items = listOf(getProd("8901014") to 1.0, getProd("8901019") to 2.0), // Tea + Biscuits
            paidAmountTk = 330,
            paymentMethod = "cash"
        )

        // DAY -2 (2 days ago)
        val day2 = now - (2 * dayMs)
        recordSale(
            saleDate = day2 + 3600000L * 1,
            customer = null,
            items = listOf(getProd("8901011") to 2.0, getProd("8901009") to 4.0, getProd("8901010") to 4.0),
            paidAmountTk = 960,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = day2 + 3600000L * 3,
            customer = null,
            items = listOf(getProd("8901015") to 1.0, getProd("8901007") to 1.0), // Milk + Sugar
            paidAmountTk = 610,
            paymentMethod = "cash"
        )
        if (custRafiq != null) {
            recordSale(
                saleDate = day2 + 3600000L * 5,
                customer = custRafiq,
                items = listOf(getProd("8901001") to 15.0, getProd("8901005") to 2.0), // Rice 15kg + 2 Oil
                paidAmountTk = 1000,
                paymentMethod = "cash",
                note = "বাকি ৪৭৫ টাকা"
            )
        }
        recordSale(
            saleDate = day2 + 3600000L * 7,
            customer = null,
            items = listOf(getProd("8901004") to 2.0, getProd("8901012") to 1.0, getProd("8901013") to 1.0),
            paidAmountTk = 522,
            paymentMethod = "cash"
        )

        // DAY -1 (Yesterday)
        val day1 = now - (1 * dayMs)
        recordSale(
            saleDate = day1 + 3600000L * 2,
            customer = null,
            items = listOf(getProd("8901002") to 10.0, getProd("8901003") to 3.0), // Nazirshail 10kg + Dal 3kg
            paidAmountTk = 1255,
            paymentMethod = "cash"
        )
        if (custShahin != null) {
            recordSale(
                saleDate = day1 + 3600000L * 4,
                customer = custShahin,
                items = listOf(getProd("8901006") to 1.0, getProd("8901007") to 3.0), // Oil 5L + Sugar 3kg
                paidAmountTk = 700,
                paymentMethod = "cash",
                note = "বাকি ৫৫০ টাকা"
            )
        }
        recordSale(
            saleDate = day1 + 3600000L * 5,
            customer = null,
            items = listOf(getProd("8901016") to 5.0, getProd("8901017") to 1.0), // Eggs + Atta
            paidAmountTk = 396,
            paymentMethod = "cash"
        )
        if (custKarim != null) {
            recordDuePayment(
                customer = custKarim,
                amountTk = 500,
                dateMs = day1 + 3600000L * 6,
                note = "বাকি টাকা পরিশোধ (বিকাশ)"
            )
        }
        recordSale(
            saleDate = day1 + 3600000L * 8,
            customer = null,
            items = listOf(getProd("8901009") to 5.0, getProd("8901010") to 10.0), // Onion + Potato
            paidAmountTk = 925,
            paymentMethod = "cash"
        )

        // DAY 0 (TODAY)
        recordSale(
            saleDate = now - 3600000L * 5,
            customer = null,
            items = listOf(getProd("8901001") to 5.0, getProd("8901005") to 1.0), // Miniket 5kg + Oil 1L
            paidAmountTk = 550,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = now - 3600000L * 4,
            customer = null,
            items = listOf(getProd("8901016") to 2.0, getProd("8901007") to 1.0, getProd("8901014") to 1.0),
            paidAmountTk = 473,
            paymentMethod = "cash"
        )
        if (custRafiq != null) {
            recordSale(
                saleDate = now - 3600000L * 2,
                customer = custRafiq,
                items = listOf(getProd("8901003") to 2.0, getProd("8901009") to 2.0, getProd("8901011") to 0.5),
                paidAmountTk = 300,
                paymentMethod = "cash",
                note = "বাকি ২৪৫ টাকা"
            )
        }
        recordSale(
            saleDate = now - 3600000L * 1,
            customer = null,
            items = listOf(getProd("8901018") to 1.0, getProd("8901015") to 1.0), // Maida + Milk
            paidAmountTk = 623,
            paymentMethod = "cash"
        )
        recordSale(
            saleDate = now - 1800000L, // 30 minutes ago
            customer = null,
            items = listOf(getProd("8901019") to 3.0, getProd("8901020") to 2.0), // Biscuits + Soap
            paidAmountTk = 260,
            paymentMethod = "cash"
        )

        // ==========================================
        // 7. EXPENSES ACROSS 7 DAYS
        // ==========================================
        val expenses = listOf(
            Expense(
                categoryId = 2L,
                categoryName = "বিদ্যুৎ বিল",
                amountPoisha = 185000L, // 1850 Tk
                note = "দোকানের গত মাসের বিদ্যুৎ বিল",
                expenseDate = day5,
                createdAt = day5
            ),
            Expense(
                categoryId = 4L,
                categoryName = "পরিবহন খরচ",
                amountPoisha = 45000L, // 450 Tk
                note = "আড়ত থেকে মালামাল ভ্যান ভাড়া",
                expenseDate = day3,
                createdAt = day3
            ),
            Expense(
                categoryId = 5L,
                categoryName = "অন্যান্য খরচ",
                amountPoisha = 18000L, // 180 Tk
                note = "দোকান আপ্যায়ন ও চা-নাস্তা",
                expenseDate = day2,
                createdAt = day2
            ),
            Expense(
                categoryId = 5L,
                categoryName = "অন্যান্য খরচ",
                amountPoisha = 12000L, // 120 Tk
                note = "দোকান পরিচ্ছন্নতা খরচ",
                expenseDate = now - 7200000L,
                createdAt = now - 7200000L
            )
        )
        dao.insertExpenses(expenses)

        // ==========================================
        // 8. SUPPLIER PURCHASE (STOCK IN)
        // ==========================================
        if (supplierHaq != null) {
            val purchaseDate = day6
            val pInvoice = "PUR-" + dateFormat.format(Date(purchaseDate)) + "-001"
            val pTotalPoisha = 2500000L // 25,000 Tk
            val pPaidPoisha = 2000000L  // 20,000 Tk
            val pDuePoisha = 500000L    // 5,000 Tk

            val purchaseId = dao.insertPurchase(
                Purchase(
                    invoiceNo = pInvoice,
                    supplierId = supplierHaq.id,
                    supplierName = supplierHaq.name,
                    purchaseDate = purchaseDate,
                    totalPoisha = pTotalPoisha,
                    paidAmountPoisha = pPaidPoisha,
                    dueAmountPoisha = pDuePoisha,
                    note = "নতুন চাল ও ডালের স্টক লোড"
                )
            )

            dao.insertSupplierLedger(
                SupplierLedger(
                    supplierId = supplierHaq.id,
                    refType = "purchase",
                    refId = purchaseId,
                    debitPoisha = pPaidPoisha,
                    creditPoisha = pTotalPoisha,
                    note = "চাল ও ডাল ক্রয় (#$pInvoice)",
                    entryDate = purchaseDate
                )
            )
        }
    }
}
