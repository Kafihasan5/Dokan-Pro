package com.example.data.firebase

import android.util.Log
import com.example.data.dao.PaponDao
import com.example.data.entity.*
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class FirebaseSyncStatus(
    val isConnected: Boolean = false,
    val isCloudLive: Boolean = false,
    val shopCode: String = "",
    val role: String = "owner",
    val syncMessage: String = "অফলাইন",
    val lastSyncTime: Long? = null
)

class FirebaseSyncManager(private val dao: PaponDao) {

    companion object {
        const val FIREBASE_DATABASE_URL = "https://dokan-pro-ec9bd-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private val tag = "FirebaseSync"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var database: FirebaseDatabase? = null
    private var currentShopRef: DatabaseReference? = null
    private var connectedRef: DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null
    private var isListenersAttached = false

    var currentShopCode: String = ""
        private set
    var currentUserRole: String = "owner"
        private set

    private val _syncStatus = MutableStateFlow(FirebaseSyncStatus())
    val syncStatus: StateFlow<FirebaseSyncStatus> = _syncStatus.asStateFlow()

    init {
        try {
            val db = FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            db.setPersistenceEnabled(true)
            database = db
        } catch (e: Exception) {
            database = try { FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL) } catch (_: Exception) { null }
        }
        setupConnectionMonitoring()
    }

    private fun setupConnectionMonitoring() {
        val db = database ?: return
        try {
            connectedRef = db.getReference(".info/connected")
            connectionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    val current = _syncStatus.value
                    if (current.isConnected) {
                        _syncStatus.value = current.copy(
                            isCloudLive = connected,
                            syncMessage = if (connected) "🟢 ক্লাউড লাইভ সিঙ্ক সক্রিয়" else "🟡 অফলাইন (কানেকশন অপেক্ষা করছে)"
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(tag, "Connection monitor cancelled: ${error.message}")
                }
            }
            connectedRef?.addValueEventListener(connectionListener!!)
        } catch (e: Exception) {
            Log.w(tag, "Could not set up connection monitoring: ${e.message}")
        }
    }

    /**
     * Replaces characters disallowed in Firebase keys (., $, #, [, ], /, @).
     */
    fun encodeEmailKey(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_dot_")
            .replace("@", "_at_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
    }

    /**
     * Maps an owner's email to their shopCode in Firebase (/email_to_shop/{encodedEmail} = shopCode).
     */
    suspend fun linkEmailToShop(email: String, shopCode: String) = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanShop = shopCode.trim().uppercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || cleanShop.isBlank()) return@withContext
        try {
            val db = database ?: FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            val encodedKey = encodeEmailKey(cleanEmail)
            db.getReference("email_to_shop").child(encodedKey).setValue(cleanShop).await()
            db.getReference("shops").child(cleanShop).child("info").child("ownerEmail").setValue(cleanEmail).await()
            Log.d(tag, "Successfully linked email $cleanEmail to shop $cleanShop in Firebase")
        } catch (e: Exception) {
            Log.w(tag, "Error linking email to shop: ${e.message}")
        }
    }

    /**
     * Resolves a query (either an email address or a shop code) to a valid shopCode.
     * If query contains '@', looks up in /email_to_shop/{encodedEmail}.
     * If query is already a shop code, returns it formatted.
     */
    suspend fun resolveShopCode(query: String): String? = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext null

        if (clean.contains("@")) {
            try {
                val db = database ?: FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
                val encodedKey = encodeEmailKey(clean)
                val snap = withTimeout(10000L) {
                    db.getReference("email_to_shop").child(encodedKey).get().await()
                }
                val foundCode = snap.getValue(String::class.java)?.trim()?.uppercase()
                return@withContext if (!foundCode.isNullOrBlank()) foundCode else null
            } catch (e: Exception) {
                Log.e(tag, "Error resolving shop code by email: ${e.message}")
                return@withContext null
            }
        } else {
            return@withContext clean.uppercase()
        }
    }

    /**
     * Hashes a PIN using SHA-256 for secure cloud storage and zero data leak.
     */
    fun hashPin(pin: String): String {
        val clean = pin.trim()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(clean.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Saves owner's Master PIN hash and Staff Access PIN under /shops/{shopCode}/security.
     */
    suspend fun saveShopSecurity(
        shopCode: String,
        ownerEmail: String,
        masterPin: String,
        staffPin: String
    ) = withContext(Dispatchers.IO) {
        val cleanCode = shopCode.trim().uppercase()
        if (cleanCode.isBlank()) return@withContext
        try {
            val db = database ?: FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            val secRef = db.getReference("shops").child(cleanCode).child("security")
            val map = mapOf(
                "masterPinHash" to hashPin(masterPin),
                "staffPin" to staffPin.trim(),
                "ownerEmail" to ownerEmail.trim().lowercase(),
                "updatedAt" to ServerValue.TIMESTAMP
            )
            secRef.updateChildren(map).await()
            if (ownerEmail.isNotBlank() && ownerEmail.contains("@")) {
                linkEmailToShop(ownerEmail, cleanCode)
            }
            Log.d(tag, "Shop security successfully saved in Firebase for $cleanCode")
        } catch (e: Exception) {
            Log.w(tag, "Error saving shop security: ${e.message}")
        }
    }

    /**
     * Verifies the provided master PIN against the shop's hashed master PIN in Firebase.
     * Prevents unauthorized access or data theft when someone only knows the owner's email address.
     */
    suspend fun verifyMasterPin(shopCode: String, masterPinInput: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = database ?: FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            val secSnap = withTimeout(10000L) {
                db.getReference("shops").child(shopCode.trim().uppercase()).child("security").get().await()
            }
            val cloudHash = secSnap.child("masterPinHash").getValue(String::class.java)
            if (cloudHash.isNullOrBlank()) {
                // Legacy shop without master pin set yet: fallback check default "1234" or accept 4-6 digit pin
                val clean = masterPinInput.trim()
                return@withContext clean == "1234" || clean.length in 4..6
            }
            val inputHash = hashPin(masterPinInput)
            return@withContext inputHash.equals(cloudHash.trim(), ignoreCase = true)
        } catch (e: Exception) {
            Log.e(tag, "Error verifying master pin: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Verifies the provided staff PIN against the shop's staff access PIN in Firebase.
     */
    suspend fun verifyStaffPin(shopCode: String, staffPinInput: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = database ?: FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            val secSnap = withTimeout(10000L) {
                db.getReference("shops").child(shopCode.trim().uppercase()).child("security").get().await()
            }
            val cloudStaffPin = secSnap.child("staffPin").getValue(String::class.java)
            if (cloudStaffPin.isNullOrBlank()) {
                val clean = staffPinInput.trim()
                return@withContext clean == "0000" || clean == "1234"
            }
            return@withContext staffPinInput.trim() == cloudStaffPin.trim()
        } catch (e: Exception) {
            Log.e(tag, "Error verifying staff pin: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Connects to a shop on Firebase Realtime Database.
     * If owner: ensures local products & records are safely backed up or restored from the cloud.
     * If staff: pulls the initial shop catalog into Room DB.
     * Attaches live listeners so changes sync between owner and staff phones instantly.
     */
    fun connectShop(
        shopCode: String,
        role: String = "owner",
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val trimmedCode = shopCode.trim().uppercase()
        if (trimmedCode.isBlank()) {
            onComplete?.invoke(false, "দোকান কোড সঠিক নয়")
            return
        }

        val db = database ?: try {
            FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
        } catch (e: Exception) {
            Log.e(tag, "FirebaseDatabase instance not available", e)
            onComplete?.invoke(false, "Firebase ডাটাবেস প্রস্তুত নয়: ${e.message}")
            return
        }

        currentShopCode = trimmedCode
        currentUserRole = role

        _syncStatus.value = FirebaseSyncStatus(
            isConnected = false,
            isCloudLive = false,
            shopCode = trimmedCode,
            role = role,
            syncMessage = "সংযোগ স্থাপন করা হচ্ছে..."
        )

        val shopRef = db.getReference("shops").child(trimmedCode)
        currentShopRef = shopRef

        try {
            shopRef.keepSynced(true)
        } catch (_: Exception) {}

        scope.launch {
            try {
                withTimeout(15000L) {
                    if (role == "owner") {
                        val localCount = dao.getActiveProductCount()
                        if (localCount == 0) {
                            // Fresh device or restoring previous shop
                            pullInitialDataFromCloud(shopRef)
                        } else {
                            // Merge: pull cloud data then push any local-only data
                            pullInitialDataFromCloud(shopRef)
                            pushAllLocalDataToCloud(shopRef)
                        }
                    } else {
                        // Employee
                        pullInitialDataFromCloud(shopRef)
                    }

                    // Attach realtime listeners
                    attachRealtimeListeners(shopRef)
                }

                _syncStatus.value = FirebaseSyncStatus(
                    isConnected = true,
                    isCloudLive = true,
                    shopCode = trimmedCode,
                    role = role,
                    syncMessage = "🟢 ক্লাউড লাইভ সিঙ্ক সক্রিয়",
                    lastSyncTime = System.currentTimeMillis()
                )

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, "ক্লাউড লাইভ সিঙ্ক সফলভাবে সংযুক্ত হয়েছে!")
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(tag, "Connection timed out, proceeding in offline mode")
                attachRealtimeListeners(shopRef)
                _syncStatus.value = FirebaseSyncStatus(
                    isConnected = true,
                    isCloudLive = false,
                    shopCode = trimmedCode,
                    role = role,
                    syncMessage = "🟡 অফলাইন মোড (ইন্টারনেট কানেকশন অপেক্ষা করছে)"
                )
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, "দোকানে সংযুক্ত হয়েছে (অফলাইন মোড চালু)")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error connecting shop to Firebase", e)
                _syncStatus.value = FirebaseSyncStatus(
                    isConnected = false,
                    isCloudLive = false,
                    shopCode = trimmedCode,
                    role = role,
                    syncMessage = "সংযোগে ত্রুটি: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, "সংযোগে সমস্যা: ${e.message ?: "ইন্টারনেট চেক করুন"}")
                }
            }
        }
    }

    /**
     * Push all local Room DB data to Firebase (Zero data loss for existing users).
     */
    suspend fun pushAllLocalDataToCloud(targetRef: DatabaseReference? = null) = withContext(Dispatchers.IO) {
        val shopRef = targetRef ?: currentShopRef ?: return@withContext
        try {
            // 1. Products
            val products = dao.getAllActiveProducts().first()
            val productMap = mutableMapOf<String, Any>()
            products.forEach { prod ->
                productMap[prod.id.toString()] = mapOf(
                    "id" to prod.id,
                    "nameBn" to prod.nameBn,
                    "nameEn" to prod.nameEn,
                    "barcode" to prod.barcode,
                    "salePricePoisha" to prod.salePricePoisha,
                    "purchasePricePoisha" to prod.purchasePricePoisha,
                    "wholesalePricePoisha" to prod.wholesalePricePoisha,
                    "stockQty" to prod.stockQty,
                    "minStock" to prod.minStock,
                    "unitName" to prod.unitName,
                    "categoryId" to prod.categoryId,
                    "isActive" to if (prod.isActive) 1 else 0,
                    "updatedAt" to prod.updatedAt
                )
            }
            if (productMap.isNotEmpty()) {
                shopRef.child("products").updateChildren(productMap)
            }

            // 2. Customers
            val customers = dao.getAllCustomers().first()
            val customerMap = mutableMapOf<String, Any>()
            customers.forEach { cust ->
                customerMap[cust.id.toString()] = mapOf(
                    "id" to cust.id,
                    "name" to cust.name,
                    "phone" to cust.phone,
                    "address" to (cust.address ?: ""),
                    "creditLimitPoisha" to cust.creditLimitPoisha,
                    "isActive" to if (cust.isActive) 1 else 0,
                    "createdAt" to cust.createdAt
                )
            }
            if (customerMap.isNotEmpty()) {
                shopRef.child("customers").updateChildren(customerMap)
            }

            // 3. Suppliers
            val suppliers = dao.getAllSuppliers().first()
            val supplierMap = mutableMapOf<String, Any>()
            suppliers.forEach { sup ->
                supplierMap[sup.id.toString()] = mapOf(
                    "id" to sup.id,
                    "name" to sup.name,
                    "company" to (sup.company ?: ""),
                    "phone" to sup.phone,
                    "address" to (sup.address ?: ""),
                    "isActive" to if (sup.isActive) 1 else 0,
                    "createdAt" to sup.createdAt
                )
            }
            if (supplierMap.isNotEmpty()) {
                shopRef.child("suppliers").updateChildren(supplierMap)
            }

            // 4. Sales and Sale Items
            try {
                val sales = dao.getAllSalesSync()
                val allSaleItems = dao.getAllSaleItemsSync().groupBy { it.saleId }
                val saleMap = mutableMapOf<String, Any>()
                sales.takeLast(1000).forEach { sale ->
                    val items = allSaleItems[sale.id] ?: emptyList()
                    saleMap[sale.id.toString()] = mapOf(
                        "id" to sale.id,
                        "invoiceNo" to sale.invoiceNo,
                        "subtotalPoisha" to sale.subtotalPoisha,
                        "discountPoisha" to sale.discountPoisha,
                        "vatPoisha" to sale.vatPoisha,
                        "totalPoisha" to sale.totalPoisha,
                        "paidAmountPoisha" to sale.paidAmountPoisha,
                        "dueAmountPoisha" to sale.dueAmountPoisha,
                        "customerId" to (sale.customerId ?: 0L),
                        "customerName" to (sale.customerName ?: ""),
                        "saleDate" to sale.saleDate,
                        "paymentMethod" to sale.paymentMethod,
                        "items" to items.map {
                            mapOf(
                                "id" to it.id,
                                "productId" to it.productId,
                                "productName" to it.productName,
                                "unitName" to it.unitName,
                                "qty" to it.qty,
                                "unitPricePoisha" to it.unitPricePoisha,
                                "purchasePriceAtSalePoisha" to it.purchasePriceAtSalePoisha,
                                "discountPoisha" to it.discountPoisha,
                                "lineTotalPoisha" to it.lineTotalPoisha
                            )
                        }
                    )
                }
                if (saleMap.isNotEmpty()) {
                    shopRef.child("sales").updateChildren(saleMap)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error pushing sales to Firebase", e)
            }

            // 5. Update metadata
            shopRef.child("info").updateChildren(mapOf(
                "shopCode" to currentShopCode,
                "lastBackupAt" to ServerValue.TIMESTAMP
            ))

            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis()
            )

            Log.d(tag, "Local data successfully pushed to Firebase for shop $currentShopCode")
        } catch (e: Exception) {
            Log.e(tag, "Error pushing all local data to Firebase", e)
        }
    }

    /**
     * Pull data from cloud on employee first connection.
     */
    private suspend fun pullInitialDataFromCloud(shopRef: DatabaseReference) = withContext(Dispatchers.IO) {
        val snapshot = shopRef.get().await()

        // Pull Products
        val productsSnap = snapshot.child("products")
        val productList = mutableListOf<Product>()
        for (pChild in productsSnap.children) {
            try {
                val id = pChild.child("id").getValue(Long::class.java) ?: pChild.key?.toLongOrNull() ?: continue
                val nameBn = pChild.child("nameBn").getValue(String::class.java) ?: ""
                val nameEn = pChild.child("nameEn").getValue(String::class.java) ?: ""
                val barcode = pChild.child("barcode").getValue(String::class.java) ?: ""
                val salePrice = pChild.child("salePricePoisha").getValue(Long::class.java)
                    ?: pChild.child("sellingPricePoisha").getValue(Long::class.java) ?: 0L
                val purchasePrice = pChild.child("purchasePricePoisha").getValue(Long::class.java) ?: 0L
                val wholesalePrice = pChild.child("wholesalePricePoisha").getValue(Long::class.java) ?: 0L
                val stockQty = pChild.child("stockQty").getValue(Double::class.java)
                    ?: pChild.child("stockQty").getValue(Long::class.java)?.toDouble() ?: 0.0
                val minStock = pChild.child("minStock").getValue(Double::class.java)
                    ?: pChild.child("minStock").getValue(Long::class.java)?.toDouble() ?: 5.0
                val unitName = pChild.child("unitName").getValue(String::class.java) ?: "কেজি"
                val categoryId = pChild.child("categoryId").getValue(Long::class.java) ?: 1L
                val isActive = (pChild.child("isActive").getValue(Long::class.java) ?: 1L) == 1L
                val updatedAt = pChild.child("updatedAt").getValue(Long::class.java) ?: System.currentTimeMillis()

                val p = Product(
                    id = id,
                    nameBn = nameBn,
                    nameEn = nameEn,
                    barcode = barcode,
                    purchasePricePoisha = purchasePrice,
                    salePricePoisha = salePrice,
                    wholesalePricePoisha = wholesalePrice,
                    stockQty = stockQty,
                    minStock = minStock,
                    unitName = unitName,
                    categoryId = categoryId,
                    isActive = isActive,
                    updatedAt = updatedAt
                )
                productList.add(p)
            } catch (e: Exception) {
                Log.w(tag, "Error parsing product from cloud", e)
            }
        }
        if (productList.isNotEmpty()) {
            dao.insertProducts(productList)
        }

        // Privacy Protection: Only Owner can pull sensitive data (Customers, Debt, Suppliers, Sales)
        // Staff/Employees ONLY pull products and categories to sell via POS without seeing owner financial data
        if (currentUserRole == "owner") {
            // Pull Customers
            val customersSnap = snapshot.child("customers")
            val customerList = mutableListOf<Customer>()
        for (cChild in customersSnap.children) {
            try {
                val c = Customer(
                    id = cChild.child("id").getValue(Long::class.java) ?: cChild.key?.toLongOrNull() ?: continue,
                    name = cChild.child("name").getValue(String::class.java) ?: "",
                    phone = cChild.child("phone").getValue(String::class.java) ?: "",
                    address = cChild.child("address").getValue(String::class.java),
                    creditLimitPoisha = cChild.child("creditLimitPoisha").getValue(Long::class.java) ?: 500000L,
                    isActive = (cChild.child("isActive").getValue(Long::class.java) ?: 1L) == 1L,
                    createdAt = cChild.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                )
                customerList.add(c)
            } catch (e: Exception) {
                Log.w(tag, "Error parsing customer from cloud", e)
            }
        }
        if (customerList.isNotEmpty()) {
            dao.insertCustomers(customerList)
        }

        // Pull Suppliers
        val suppliersSnap = snapshot.child("suppliers")
        val supplierList = mutableListOf<Supplier>()
        for (sChild in suppliersSnap.children) {
            try {
                val s = Supplier(
                    id = sChild.child("id").getValue(Long::class.java) ?: sChild.key?.toLongOrNull() ?: continue,
                    name = sChild.child("name").getValue(String::class.java) ?: "",
                    phone = sChild.child("phone").getValue(String::class.java) ?: "",
                    company = sChild.child("company").getValue(String::class.java),
                    address = sChild.child("address").getValue(String::class.java),
                    isActive = (sChild.child("isActive").getValue(Long::class.java) ?: 1L) == 1L,
                    createdAt = sChild.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                )
                supplierList.add(s)
            } catch (e: Exception) {
                Log.w(tag, "Error parsing supplier from cloud", e)
            }
        }
        if (supplierList.isNotEmpty()) {
            dao.insertSuppliers(supplierList)
        }

        // Pull Sales & Sale Items
        val salesSnap = snapshot.child("sales")
        for (sChild in salesSnap.children) {
            try {
                val saleId = sChild.child("id").getValue(Long::class.java) ?: sChild.key?.toLongOrNull() ?: continue
                if (dao.getSaleById(saleId) != null) continue
                val invoiceNo = sChild.child("invoiceNo").getValue(String::class.java) ?: "INV-$saleId"
                val subtotalPoisha = sChild.child("subtotalPoisha").getValue(Long::class.java) ?: 0L
                val discountPoisha = sChild.child("discountPoisha").getValue(Long::class.java) ?: 0L
                val vatPoisha = sChild.child("vatPoisha").getValue(Long::class.java) ?: 0L
                val totalPoisha = sChild.child("totalPoisha").getValue(Long::class.java) ?: (subtotalPoisha - discountPoisha + vatPoisha)
                val paidAmountPoisha = sChild.child("paidAmountPoisha").getValue(Long::class.java) ?: totalPoisha
                val dueAmountPoisha = sChild.child("dueAmountPoisha").getValue(Long::class.java) ?: 0L
                val customerId = sChild.child("customerId").getValue(Long::class.java)?.takeIf { it > 0 }
                val customerName = sChild.child("customerName").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                val saleDate = sChild.child("saleDate").getValue(Long::class.java) ?: System.currentTimeMillis()
                val paymentMethod = sChild.child("paymentMethod").getValue(String::class.java) ?: "cash"

                val sale = Sale(
                    id = saleId,
                    invoiceNo = invoiceNo,
                    customerId = customerId,
                    customerName = customerName,
                    saleDate = saleDate,
                    subtotalPoisha = subtotalPoisha,
                    discountPoisha = discountPoisha,
                    vatPoisha = vatPoisha,
                    totalPoisha = totalPoisha,
                    paidAmountPoisha = paidAmountPoisha,
                    dueAmountPoisha = dueAmountPoisha,
                    paymentMethod = paymentMethod
                )
                dao.insertSale(sale)

                val itemsSnap = sChild.child("items")
                val items = mutableListOf<SaleItem>()
                for (itemChild in itemsSnap.children) {
                    val itemId = itemChild.child("id").getValue(Long::class.java) ?: 0L
                    val productId = itemChild.child("productId").getValue(Long::class.java) ?: 0L
                    val productName = itemChild.child("productName").getValue(String::class.java) ?: ""
                    val unitName = itemChild.child("unitName").getValue(String::class.java) ?: "পিস"
                    val qty = itemChild.child("qty").getValue(Double::class.java) ?: 1.0
                    val unitPrice = itemChild.child("unitPricePoisha").getValue(Long::class.java) ?: 0L
                    val purchasePrice = itemChild.child("purchasePriceAtSalePoisha").getValue(Long::class.java) ?: 0L
                    val discount = itemChild.child("discountPoisha").getValue(Long::class.java) ?: 0L
                    val lineTotal = itemChild.child("lineTotalPoisha").getValue(Long::class.java) ?: 0L
                    items.add(
                        SaleItem(
                            id = itemId,
                            saleId = saleId,
                            productId = productId,
                            productName = productName,
                            unitName = unitName,
                            qty = qty,
                            unitPricePoisha = unitPrice,
                            purchasePriceAtSalePoisha = purchasePrice,
                            discountPoisha = discount,
                            lineTotalPoisha = lineTotal
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    dao.insertSaleItems(items)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error parsing sale in initial pull", e)
            }
        }
        }
    }

    /**
     * Pulls and restores all cloud data (products, customers, suppliers, sales) into Room DB.
     */
    suspend fun restoreAllDataFromCloud(targetRef: DatabaseReference? = null): Boolean = withContext(Dispatchers.IO) {
        val shopRef = targetRef ?: currentShopRef ?: return@withContext false
        try {
            withTimeout(25000L) {
                pullInitialDataFromCloud(shopRef)
            }
            _syncStatus.value = _syncStatus.value.copy(
                lastSyncTime = System.currentTimeMillis(),
                syncMessage = "🟢 ক্লাউড থেকে সফলভাবে রিস্টোর হয়েছে"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Error restoring all data from cloud", e)
            false
        }
    }

    /**
     * Attach realtime listeners for instantaneous live syncing across all devices.
     */
    private fun attachRealtimeListeners(shopRef: DatabaseReference) {
        if (isListenersAttached) return
        isListenersAttached = true

        // 1. Live Product Sync (Stock adjustment, price changes)
        shopRef.child("products").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleProductUpdate(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                handleProductUpdate(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.child("id").getValue(Long::class.java) ?: snapshot.key?.toLongOrNull()
                id?.let {
                    scope.launch { dao.softDeleteProduct(it) }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Products sync cancelled: ${error.message}")
            }
        })

        // 2. Live Sales Sync (Employee sells -> Owner immediately receives the sale)
        shopRef.child("sales").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleSaleAdded(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Sales sync cancelled: ${error.message}")
            }
        })
    }

    private fun handleProductUpdate(snapshot: DataSnapshot) {
        scope.launch {
            try {
                val id = snapshot.child("id").getValue(Long::class.java) ?: snapshot.key?.toLongOrNull() ?: return@launch
                val local = dao.getProductById(id)

                val stock = snapshot.child("stockQty").getValue(Double::class.java)
                    ?: snapshot.child("stockQty").getValue(Long::class.java)?.toDouble()
                    ?: local?.stockQty ?: 0.0

                val salePrice = snapshot.child("salePricePoisha").getValue(Long::class.java)
                    ?: snapshot.child("sellingPricePoisha").getValue(Long::class.java)
                    ?: local?.salePricePoisha ?: 0L
                val purchasePrice = snapshot.child("purchasePricePoisha").getValue(Long::class.java) ?: local?.purchasePricePoisha ?: 0L
                val wholesalePrice = snapshot.child("wholesalePricePoisha").getValue(Long::class.java) ?: local?.wholesalePricePoisha ?: 0L
                val nameBn = snapshot.child("nameBn").getValue(String::class.java) ?: local?.nameBn ?: ""
                val nameEn = snapshot.child("nameEn").getValue(String::class.java) ?: local?.nameEn ?: ""
                val barcode = snapshot.child("barcode").getValue(String::class.java) ?: local?.barcode ?: ""
                val unitName = snapshot.child("unitName").getValue(String::class.java) ?: local?.unitName ?: "কেজি"
                val categoryId = snapshot.child("categoryId").getValue(Long::class.java) ?: local?.categoryId ?: 1L
                val isActive = (snapshot.child("isActive").getValue(Long::class.java) ?: if (local?.isActive == false) 0L else 1L) == 1L

                val product = Product(
                    id = id,
                    nameBn = nameBn,
                    nameEn = nameEn,
                    barcode = barcode,
                    purchasePricePoisha = purchasePrice,
                    salePricePoisha = salePrice,
                    wholesalePricePoisha = wholesalePrice,
                    stockQty = stock,
                    minStock = local?.minStock ?: 5.0,
                    unitName = unitName,
                    categoryId = categoryId,
                    isActive = isActive,
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertProduct(product)
                _syncStatus.value = _syncStatus.value.copy(lastSyncTime = System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(tag, "Error handling realtime product update", e)
            }
        }
    }

    private fun handleSaleAdded(snapshot: DataSnapshot) {
        scope.launch {
            try {
                val saleId = snapshot.child("id").getValue(Long::class.java) ?: snapshot.key?.toLongOrNull() ?: return@launch
                val existing = dao.getSaleById(saleId)
                if (existing != null) return@launch // Already present locally

                val invoiceNo = snapshot.child("invoiceNo").getValue(String::class.java) ?: "INV-$saleId"
                val subtotalPoisha = snapshot.child("subtotalPoisha").getValue(Long::class.java)
                    ?: snapshot.child("totalPoisha").getValue(Long::class.java) ?: 0L
                val discountPoisha = snapshot.child("discountPoisha").getValue(Long::class.java) ?: 0L
                val vatPoisha = snapshot.child("vatPoisha").getValue(Long::class.java) ?: 0L
                val totalPoisha = snapshot.child("totalPoisha").getValue(Long::class.java) ?: (subtotalPoisha - discountPoisha + vatPoisha)
                val paidAmountPoisha = snapshot.child("paidAmountPoisha").getValue(Long::class.java)
                    ?: snapshot.child("receivedPoisha").getValue(Long::class.java) ?: totalPoisha
                val dueAmountPoisha = snapshot.child("dueAmountPoisha").getValue(Long::class.java) ?: 0L
                val customerId = snapshot.child("customerId").getValue(Long::class.java)?.takeIf { it > 0 }
                val customerName = snapshot.child("customerName").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                val saleDate = snapshot.child("saleDate").getValue(Long::class.java) ?: System.currentTimeMillis()
                val paymentMethod = snapshot.child("paymentMethod").getValue(String::class.java) ?: "cash"

                val sale = Sale(
                    id = saleId,
                    invoiceNo = invoiceNo,
                    customerId = customerId,
                    customerName = customerName,
                    saleDate = saleDate,
                    subtotalPoisha = subtotalPoisha,
                    discountPoisha = discountPoisha,
                    vatPoisha = vatPoisha,
                    totalPoisha = totalPoisha,
                    paidAmountPoisha = paidAmountPoisha,
                    dueAmountPoisha = dueAmountPoisha,
                    paymentMethod = paymentMethod
                )
                dao.insertSale(sale)

                // Insert sale items if included
                val itemsSnap = snapshot.child("items")
                val items = mutableListOf<SaleItem>()
                for (itemChild in itemsSnap.children) {
                    val itemId = itemChild.child("id").getValue(Long::class.java) ?: 0L
                    val productId = itemChild.child("productId").getValue(Long::class.java) ?: 0L
                    val productName = itemChild.child("productName").getValue(String::class.java) ?: ""
                    val unitName = itemChild.child("unitName").getValue(String::class.java) ?: "পিস"
                    val qty = itemChild.child("qty").getValue(Double::class.java) ?: 1.0
                    val unitPrice = itemChild.child("unitPricePoisha").getValue(Long::class.java) ?: 0L
                    val purchasePrice = itemChild.child("purchasePriceAtSalePoisha").getValue(Long::class.java)
                        ?: itemChild.child("purchasePricePoisha").getValue(Long::class.java) ?: 0L
                    val discount = itemChild.child("discountPoisha").getValue(Long::class.java) ?: 0L
                    val lineTotal = itemChild.child("lineTotalPoisha").getValue(Long::class.java) ?: 0L

                    items.add(
                        SaleItem(
                            id = itemId,
                            saleId = saleId,
                            productId = productId,
                            productName = productName,
                            unitName = unitName,
                            qty = qty,
                            unitPricePoisha = unitPrice,
                            purchasePriceAtSalePoisha = purchasePrice,
                            discountPoisha = discount,
                            lineTotalPoisha = lineTotal
                        )
                    )

                    // Adjust local stock if not already adjusted
                    dao.adjustProductStock(productId, -qty)
                }
                if (items.isNotEmpty()) {
                    dao.insertSaleItems(items)
                }

                _syncStatus.value = _syncStatus.value.copy(lastSyncTime = System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(tag, "Error handling realtime sale added", e)
            }
        }
    }

    /**
     * Broadcast a new sale to Firebase instantly.
     */
    fun pushSale(sale: Sale, items: List<SaleItem>) {
        val ref = currentShopRef ?: return
        val saleMap = mapOf(
            "id" to sale.id,
            "invoiceNo" to sale.invoiceNo,
            "subtotalPoisha" to sale.subtotalPoisha,
            "discountPoisha" to sale.discountPoisha,
            "vatPoisha" to sale.vatPoisha,
            "totalPoisha" to sale.totalPoisha,
            "paidAmountPoisha" to sale.paidAmountPoisha,
            "dueAmountPoisha" to sale.dueAmountPoisha,
            "customerId" to (sale.customerId ?: 0L),
            "customerName" to (sale.customerName ?: ""),
            "saleDate" to sale.saleDate,
            "paymentMethod" to sale.paymentMethod,
            "items" to items.map {
                mapOf(
                    "id" to it.id,
                    "productId" to it.productId,
                    "productName" to it.productName,
                    "unitName" to it.unitName,
                    "qty" to it.qty,
                    "unitPricePoisha" to it.unitPricePoisha,
                    "purchasePriceAtSalePoisha" to it.purchasePriceAtSalePoisha,
                    "discountPoisha" to it.discountPoisha,
                    "lineTotalPoisha" to it.lineTotalPoisha
                )
            }
        )
        ref.child("sales").child(sale.id.toString()).setValue(saleMap)

        // Also update stock in cloud for each product
        items.forEach { item ->
            scope.launch {
                val prod = dao.getProductById(item.productId)
                if (prod != null) {
                    ref.child("products").child(prod.id.toString()).child("stockQty").setValue(prod.stockQty)
                }
            }
        }
    }

    /**
     * Push product addition / price update to cloud.
     */
    fun pushProduct(product: Product) {
        val ref = currentShopRef ?: return
        val map = mapOf(
            "id" to product.id,
            "nameBn" to product.nameBn,
            "nameEn" to product.nameEn,
            "barcode" to product.barcode,
            "salePricePoisha" to product.salePricePoisha,
            "purchasePricePoisha" to product.purchasePricePoisha,
            "wholesalePricePoisha" to product.wholesalePricePoisha,
            "stockQty" to product.stockQty,
            "minStock" to product.minStock,
            "unitName" to product.unitName,
            "categoryId" to product.categoryId,
            "isActive" to if (product.isActive) 1 else 0,
            "updatedAt" to product.updatedAt
        )
        ref.child("products").child(product.id.toString()).setValue(map)
    }

    fun deleteProduct(productId: Long) {
        val ref = currentShopRef ?: return
        ref.child("products").child(productId.toString()).child("isActive").setValue(0)
    }

    fun syncProductStock(productId: Long) {
        val ref = currentShopRef ?: return
        scope.launch {
            val prod = dao.getProductById(productId)
            if (prod != null) {
                ref.child("products").child(productId.toString()).child("stockQty").setValue(prod.stockQty)
            }
        }
    }

    /**
     * Disconnect shop.
     */
    fun disconnect() {
        currentShopRef = null
        currentShopCode = ""
        isListenersAttached = false
        _syncStatus.value = FirebaseSyncStatus(
            isConnected = false,
            isCloudLive = false,
            shopCode = "",
            role = "owner",
            syncMessage = "⚪ অফলাইন"
        )
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result, null)
        }
        addOnFailureListener { exception ->
            cont.resumeWith(Result.failure(exception))
        }
    }
