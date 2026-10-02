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

data class FirebaseSyncStatus(
    val isConnected: Boolean = false,
    val isCloudLive: Boolean = false,
    val shopCode: String = "",
    val role: String = "owner",
    val syncMessage: String = "অফলাইন",
    val lastSyncTime: Long? = null
)

class FirebaseSyncManager(private val dao: PaponDao, val cloudAuth: CloudAuthManager = CloudAuthManager()) {

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
    private var currentAttachedShopCode: String = ""
    private var productsListener: ChildEventListener? = null
    private var salesListener: ChildEventListener? = null
    private var productsQueryRef: DatabaseReference? = null
    private var salesQueryRef: DatabaseReference? = null
    /** Keys present in the cloud per node, recorded during the initial pull, so pushes send only what's missing. */
    private val cloudKeys = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

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

    fun getDb(): FirebaseDatabase? {
        if (database != null) return database
        database = try {
            FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
        } catch (e: Throwable) {
            Log.e(tag, "FirebaseDatabase instance error: ${e.message}")
            null
        }
        return database
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
     * Sanitizes any key to ensure it NEVER contains Firebase invalid path characters (., $, #, [, ], /).
     */
    fun sanitizeFirebaseKey(key: String): String {
        return key.trim().replace(Regex("[.#$\\[\\]/\\s]"), "")
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
            .replace(" ", "")
    }

    /**
     * Maps an owner's email to their shopCode in Firebase (/email_to_shop/{encodedEmail} = shopCode).
     */
    suspend fun linkEmailToShop(email: String, shopCode: String) = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanShop = sanitizeFirebaseKey(shopCode.trim().uppercase())
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || cleanShop.isBlank()) return@withContext
        try {
            val db = getDb() ?: return@withContext
            val encodedKey = encodeEmailKey(cleanEmail)
            db.getReference("email_to_shop").child(encodedKey).setValue(cleanShop).await()
            db.getReference("shops").child(cleanShop).child("info").child("ownerEmail").setValue(cleanEmail).await()
            Log.d(tag, "Successfully linked email $cleanEmail to shop $cleanShop in Firebase")
        } catch (e: Throwable) {
            Log.w(tag, "Error linking email to shop: ${e.message}")
        }
    }

    /**
     * Normalises what the user typed as a shop code. E-mail addresses are passed through unchanged:
     * the server resolves them during login (the e-mail index is not readable by the app any more).
     */
    fun resolveShopCode(query: String): String? {
        val clean = com.example.util.Formatters.replaceBengaliDigits(query).trim()
        if (clean.isBlank()) return null
        if (clean.contains("@")) return clean.lowercase()
        return sanitizeFirebaseKey(clean.uppercase()).takeIf { it.isNotBlank() }
    }

    /**
     * Stores the owner's e-mail on the shop and in the e-mail → shop index.
     * PINs are no longer written here: they live hashed on the server (see CloudAuthManager).
     */
    suspend fun saveOwnerEmail(shopCode: String, ownerEmail: String) {
        val email = ownerEmail.trim().lowercase()
        if (email.contains("@")) linkEmailToShop(email, shopCode)
    }

    /**
     * Saves staff profiles (without PINs) under /shops/{shopCode}/staff and registers staff_to_shop indexes.
     * Each record is written separately because the rules grant write access per staff member.
     */
    suspend fun saveStaffMembers(
        shopCode: String,
        staffList: List<com.example.data.entity.StaffMember>
    ) = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeFirebaseKey(shopCode.uppercase())
        if (cleanCode.isBlank()) return@withContext
        try {
            val db = getDb() ?: return@withContext
            val staffRef = db.getReference("shops").child(cleanCode).child("staff")
            staffList.forEach { staff ->
                staffRef.child(staffKeyFor(staff)).updateChildren(
                    mapOf(
                        "id" to staff.id,
                        "name" to staff.name.trim(),
                        "email" to staff.email.trim().lowercase(),
                        "phone" to staff.phone.trim(),
                        "role" to staff.role,
                        "isActive" to staff.isActive,
                        "createdAt" to staff.createdAt,
                        "hasPin" to staff.pin.isNotBlank()
                    )
                ).await()
                if (staff.email.isNotBlank() && staff.email.contains("@")) {
                    try {
                        db.getReference("staff_to_shop").child(encodeEmailKey(staff.email)).setValue(cleanCode).await()
                    } catch (_: Exception) {}
                }
            }
            Log.d(tag, "Saved ${staffList.size} staff profiles in Firebase for $cleanCode")
        } catch (e: Exception) {
            Log.w(tag, "Error saving staff members: ${e.message}")
        }
    }

    /** Firebase key of a staff record; the server uses the same key as the staff ID in login tokens. */
    fun staffKeyFor(staff: com.example.data.entity.StaffMember): String =
        if (staff.email.isNotBlank() && staff.email.contains("@")) encodeEmailKey(staff.email) else sanitizeFirebaseKey(staff.id)

    /**
     * Fetches staff profiles for a shop from Firebase. PINs are never returned (they are hashed on the
     * server), so callers must keep the locally known PIN.
     */
    suspend fun fetchStaffMembers(shopCode: String): List<com.example.data.entity.StaffMember> = withContext(Dispatchers.IO) {
        val cleanCode = sanitizeFirebaseKey(shopCode.uppercase())
        if (cleanCode.isBlank()) return@withContext emptyList()
        try {
            val db = getDb() ?: return@withContext emptyList()
            val snap = withTimeout(10000L) {
                db.getReference("shops").child(cleanCode).child("staff").get().await()
            }
            if (!snap.exists()) return@withContext emptyList()
            val list = mutableListOf<com.example.data.entity.StaffMember>()
            for (child in snap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: java.util.UUID.randomUUID().toString()
                val name = child.child("name").getValue(String::class.java) ?: ""
                val email = child.child("email").getValue(String::class.java) ?: ""
                val phone = child.child("phone").getValue(String::class.java) ?: ""
                val role = child.child("role").getValue(String::class.java) ?: "staff"
                val isActive = child.child("isActive").getValue(Boolean::class.java) ?: true
                val createdAt = child.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                if (name.isNotBlank() || email.isNotBlank()) {
                    list.add(com.example.data.entity.StaffMember(id, name, email, "", phone, role, isActive, createdAt))
                }
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Error fetching staff members: ${e.message}")
            emptyList()
        }
    }

    /**
     * Dedicated, safe cloud restore function.
     * Completely downloads products, customers, suppliers, sales, and ledgers into local Room DB
     * in batch transactions BEFORE transitioning screens. Returns success or failure safely without throwing.
     */
    suspend fun restoreShopData(
        shopCode: String,
        role: String = "owner"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedCode = sanitizeFirebaseKey(shopCode.uppercase())
        if (trimmedCode.isBlank()) {
            return@withContext Result.failure(Exception("দোকান কোড সঠিক নয়"))
        }

        val db = getDb() ?: return@withContext Result.failure(Exception("Firebase ডাটাবেস প্রস্তুত নয়"))

        currentShopCode = trimmedCode
        currentUserRole = role

        val shopRef = db.getReference("shops").child(trimmedCode)
        currentShopRef = shopRef

        try {
            withTimeout(35000L) {
                pullInitialDataFromCloud(shopRef)
            }
            _syncStatus.value = FirebaseSyncStatus(
                isConnected = true,
                isCloudLive = true,
                shopCode = trimmedCode,
                role = role,
                syncMessage = "🟢 ক্লাউড থেকে সফলভাবে রিস্টোর হয়েছে",
                lastSyncTime = System.currentTimeMillis()
            )
            Result.success("দোকানের সমস্ত তথ্য সফলভাবে রিস্টোর হয়েছে!")
        } catch (e: TimeoutCancellationException) {
            Log.e(tag, "Timeout restoring shop data", e)
            Result.failure(Exception("ডাটা রিস্টোর করার সময় শেষ হয়েছে (টাইমআউট)। ইন্টারনেট কানেকশন চেক করে আবার চেষ্টা করুন।"))
        } catch (e: Throwable) {
            Log.e(tag, "Error restoring shop data", e)
            Result.failure(Exception(e.message ?: "ডাটা রিস্টোর করতে সমস্যা হয়েছে। ইন্টারনেট চেক করুন।"))
        }
    }

    /**
     * Fetches shop metadata (shopName, shopPhone, shopAddress, ownerEmail, tagline) from Firebase.
     */
    suspend fun fetchShopInfo(shopCode: String): Map<String, String>? = withContext(Dispatchers.IO) {
        val clean = sanitizeFirebaseKey(shopCode.uppercase())
        if (clean.isBlank()) return@withContext null
        try {
            val db = getDb() ?: return@withContext null
            val infoSnap = withTimeout(10000L) {
                db.getReference("shops").child(clean).child("info").get().await()
            }
            if (!infoSnap.exists()) return@withContext null
            val map = mutableMapOf<String, String>()
            infoSnap.child("shopName").getValue(String::class.java)?.let { if (it.isNotBlank()) map["shopName"] = it }
            infoSnap.child("shopPhone").getValue(String::class.java)?.let { if (it.isNotBlank()) map["shopPhone"] = it }
            infoSnap.child("shopAddress").getValue(String::class.java)?.let { if (it.isNotBlank()) map["shopAddress"] = it }
            infoSnap.child("ownerEmail").getValue(String::class.java)?.let { if (it.isNotBlank()) map["ownerEmail"] = it }
            infoSnap.child("tagline").getValue(String::class.java)?.let { if (it.isNotBlank()) map["tagline"] = it }
            map
        } catch (e: Throwable) {
            Log.w(tag, "Error fetching shop info: ${e.message}")
            null
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
        val trimmedCode = sanitizeFirebaseKey(shopCode.uppercase())
        if (trimmedCode.isBlank()) {
            onComplete?.invoke(false, "দোকান কোড সঠিক নয়")
            return
        }

        val db = getDb() ?: run {
            onComplete?.invoke(false, "Firebase ডাটাবেস প্রস্তুত নয়")
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

        scope.launch {
            // The database only answers requests from a server-verified session for this shop.
            val session = cloudAuth.currentSession()
            if (session == null || session.shopCode != trimmedCode || session.pinChangeRequired) {
                _syncStatus.value = FirebaseSyncStatus(
                    isConnected = false,
                    isCloudLive = false,
                    shopCode = trimmedCode,
                    role = role,
                    syncMessage = "🔒 ক্লাউড লগইন প্রয়োজন"
                )
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, "ক্লাউড সিঙ্কের জন্য আগে পিন দিয়ে লগইন করুন")
                }
                return@launch
            }
            currentUserRole = session.role
            // Only now may pushSale()/pushProduct() write: before this the rules would reject them.
            currentShopRef = shopRef
            try {
                shopRef.child("products").keepSynced(true)
                if (session.role == "owner") shopRef.child("sales").keepSynced(true)
            } catch (_: Exception) {}

            try {
                withTimeout(20000L) {
                    if (session.role == "owner") {
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

                    // Attach realtime listeners safely
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
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true, "দোকানে সংযুক্ত হয়েছে (অফলাইন মোড চালু)")
                    }
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
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false, "সংযোগে সমস্যা: ${e.message ?: "ইন্টারনেট চেক করুন"}")
                    }
                }
            }
        }
    }

    /**
     * Push local Room data to Firebase (zero data loss for existing users).
     * Products, customers and suppliers are always pushed (they change in place). Sales, expenses,
     * purchases and ledger rows are append-only, so only the ones missing in the cloud are sent.
     */
    suspend fun pushAllLocalDataToCloud(targetRef: DatabaseReference? = null) = withContext(Dispatchers.IO) {
        val shopRef = targetRef ?: currentShopRef ?: return@withContext
        val known = { node: String -> cloudKeys[node] ?: emptySet() }
        try {
            // 1. Products
            val productMap = dao.getAllActiveProducts().first().associate { it.id.toString() to CloudMappers.product(it) }
            if (productMap.isNotEmpty()) shopRef.child("products").updateChildren(productMap)

            // 2. Customers, with their current balance so the web app shows the same due
            val ledgers = dao.getAllCustomerLedgersSync()
            val dueByCustomer = ledgers.groupBy { it.customerId }.mapValues { (_, l) -> l.sumOf { it.debitPoisha - it.creditPoisha } }
            val customerMap = dao.getAllCustomers().first().associate {
                it.id.toString() to CloudMappers.customer(it, dueByCustomer[it.id] ?: 0L)
            }
            if (customerMap.isNotEmpty()) shopRef.child("customers").updateChildren(customerMap)

            // 3. Suppliers
            val supplierMap = dao.getAllSuppliers().first().associate { it.id.toString() to CloudMappers.supplier(it) }
            if (supplierMap.isNotEmpty()) shopRef.child("suppliers").updateChildren(supplierMap)

            // 4. Sales (all of them, not just the last 1000) that the cloud doesn't have yet
            val itemsBySale = dao.getAllSaleItemsSync().groupBy { it.saleId }
            val saleMap = dao.getAllSalesSync()
                .filter { it.id.toString() !in known("sales") }
                .associate { it.id.toString() to saleRecord(it, itemsBySale[it.id].orEmpty()) }
            saleMap.entries.chunked(500).forEach { chunk -> shopRef.child("sales").updateChildren(chunk.associate { it.key to it.value }) }

            // 5. Expenses, purchases and the customer due ledger (previously never backed up)
            val expenseMap = dao.getAllExpensesSync()
                .filter { it.id.toString() !in known("expenses") }
                .associate { it.id.toString() to CloudMappers.expense(it) }
            if (expenseMap.isNotEmpty()) shopRef.child("expenses").updateChildren(expenseMap)

            val itemsByPurchase = dao.getAllPurchaseItemsSync().groupBy { it.purchaseId }
            val purchaseMap = dao.getAllPurchasesSync()
                .filter { it.id.toString() !in known("purchases") }
                .associate { it.id.toString() to CloudMappers.purchase(it, itemsByPurchase[it.id].orEmpty()) }
            if (purchaseMap.isNotEmpty()) shopRef.child("purchases").updateChildren(purchaseMap)

            val ledgerMap = ledgers
                .filter { it.id.toString() !in known("customer_ledger") }
                .associate { it.id.toString() to CloudMappers.ledger(it) }
            ledgerMap.entries.chunked(500).forEach { chunk -> shopRef.child("customer_ledger").updateChildren(chunk.associate { it.key to it.value }) }

            // 6. Metadata
            shopRef.child("info").updateChildren(mapOf(
                "shopCode" to currentShopCode,
                "lastBackupAt" to ServerValue.TIMESTAMP
            ))

            _syncStatus.value = _syncStatus.value.copy(lastSyncTime = System.currentTimeMillis())
            Log.d(tag, "Local data pushed to Firebase for shop $currentShopCode")
        } catch (e: Exception) {
            Log.e(tag, "Error pushing all local data to Firebase", e)
        }
    }

    private fun saleRecord(sale: Sale, items: List<SaleItem>): Map<String, Any?> = mapOf(
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
        "createdAt" to sale.createdAt,
        "paymentMethod" to sale.paymentMethod,
        "note" to (sale.note ?: ""),
        "isReturned" to sale.isReturned,
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

    /**
     * Pull data from cloud on employee first connection.
     */
    private suspend fun pullInitialDataFromCloud(shopRef: DatabaseReference) = withContext(Dispatchers.IO) {
        // Each node is read separately: the rules grant access per node (staff can't read the whole shop).

        // Safe snapshot helper extensions
        fun DataSnapshot.readLong(childKey: String, default: Long = 0L): Long =
            this.child(childKey).value?.toString()?.toLongOrNull() ?: default

        fun DataSnapshot.readDouble(childKey: String, default: Double = 0.0): Double =
            this.child(childKey).value?.toString()?.toDoubleOrNull() ?: default

        fun DataSnapshot.readString(childKey: String, default: String = ""): String =
            this.child(childKey).value?.toString()?.trim() ?: default

        fun DataSnapshot.readBoolean(childKey: String, default: Boolean = true): Boolean {
            val v = this.child(childKey).value ?: return default
            return when (v) {
                is Boolean -> v
                is Number -> v.toLong() == 1L
                is String -> v.equals("true", ignoreCase = true) || v == "1"
                else -> default
            }
        }

        // Pull Products
        val productsSnap = shopRef.child("products").get().await()
        cloudKeys["products"] = productsSnap.children.mapNotNull { it.key }.toSet()
        val productList = mutableListOf<Product>()
        for (pChild in productsSnap.children) {
            try {
                val id = pChild.readLong("id", 0L).takeIf { it > 0 }
                    ?: pChild.key?.toLongOrNull() ?: continue
                val nameBn = pChild.readString("nameBn")
                val nameEn = pChild.readString("nameEn")
                val barcode = pChild.readString("barcode")
                val salePrice = pChild.readLong("salePricePoisha", 0L).takeIf { it > 0 }
                    ?: pChild.readLong("sellingPricePoisha", 0L)
                val purchasePrice = pChild.readLong("purchasePricePoisha", 0L)
                val wholesalePrice = pChild.readLong("wholesalePricePoisha", 0L)
                val stockQty = pChild.readDouble("stockQty", 0.0)
                val minStock = pChild.readDouble("minStock", 5.0)
                val unitName = pChild.readString("unitName", "কেজি")
                val categoryId = pChild.readLong("categoryId", 1L)
                val isActive = CloudMappers.readActive(pChild, true)
                val updatedAt = pChild.readLong("updatedAt", System.currentTimeMillis())

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
            val customersSnap = shopRef.child("customers").get().await()
        cloudKeys["customers"] = customersSnap.children.mapNotNull { it.key }.toSet()
            val customerList = mutableListOf<Customer>()
            for (cChild in customersSnap.children) {
                try {
                    val cId = cChild.readLong("id", 0L).takeIf { it > 0 }
                        ?: cChild.key?.toLongOrNull() ?: continue
                    val c = Customer(
                        id = cId,
                        name = cChild.readString("name"),
                        phone = cChild.readString("phone"),
                        address = cChild.readString("address").takeIf { it.isNotBlank() },
                        creditLimitPoisha = cChild.readLong("creditLimitPoisha", 500000L),
                        isActive = cChild.readBoolean("isActive", true),
                        createdAt = cChild.readLong("createdAt", System.currentTimeMillis())
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
            val suppliersSnap = shopRef.child("suppliers").get().await()
        cloudKeys["suppliers"] = suppliersSnap.children.mapNotNull { it.key }.toSet()
            val supplierList = mutableListOf<Supplier>()
            for (sChild in suppliersSnap.children) {
                try {
                    val sId = sChild.readLong("id", 0L).takeIf { it > 0 }
                        ?: sChild.key?.toLongOrNull() ?: continue
                    val s = Supplier(
                        id = sId,
                        name = sChild.readString("name"),
                        phone = sChild.readString("phone"),
                        company = sChild.readString("company").takeIf { it.isNotBlank() },
                        address = sChild.readString("address").takeIf { it.isNotBlank() },
                        isActive = sChild.readBoolean("isActive", true),
                        createdAt = sChild.readLong("createdAt", System.currentTimeMillis())
                    )
                    supplierList.add(s)
                } catch (e: Exception) {
                    Log.w(tag, "Error parsing supplier from cloud", e)
                }
            }
            if (supplierList.isNotEmpty()) {
                dao.insertSuppliers(supplierList)
            }

            // Pull Sales & Sale Items in BATCH (Atomic & Ultra-Fast)
            val salesSnap = shopRef.child("sales").get().await()
        cloudKeys["sales"] = salesSnap.children.mapNotNull { it.key }.toSet()
            val salesToInsert = mutableListOf<Sale>()
            val saleItemsToInsert = mutableListOf<SaleItem>()
            val ledgersToInsert = mutableListOf<CustomerLedger>()

            for (sChild in salesSnap.children) {
                try {
                    val saleId = sChild.readLong("id", 0L).takeIf { it > 0 }
                        ?: sChild.key?.toLongOrNull() ?: continue
                    if (dao.getSaleById(saleId) != null) continue
                    val invoiceNo = sChild.readString("invoiceNo", "INV-$saleId")
                    val subtotalPoisha = sChild.readLong("subtotalPoisha", 0L)
                    val discountPoisha = sChild.readLong("discountPoisha", 0L)
                    val vatPoisha = sChild.readLong("vatPoisha", 0L)
                    val totalPoisha = sChild.readLong("totalPoisha", 0L).takeIf { it > 0 }
                        ?: (subtotalPoisha - discountPoisha + vatPoisha)
                    val paidAmountPoisha = sChild.readLong("paidAmountPoisha", 0L).takeIf { it > 0 }
                        ?: totalPoisha
                    val dueAmountPoisha = sChild.readLong("dueAmountPoisha", 0L)
                    val customerId = sChild.readLong("customerId", 0L).takeIf { it > 0 }
                    val customerName = sChild.readString("customerName").takeIf { it.isNotBlank() }
                    val saleDate = sChild.readLong("saleDate", System.currentTimeMillis())
                    val paymentMethod = sChild.readString("paymentMethod", "cash")
                    val note = sChild.readString("note").takeIf { it.isNotBlank() }

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
                        paymentMethod = paymentMethod,
                        note = note
                    )
                    salesToInsert.add(sale)

                    val itemsSnap = sChild.child("items")
                    for (itemChild in itemsSnap.children) {
                        val itemId = itemChild.readLong("id", 0L)
                        val productId = itemChild.readLong("productId", 0L)
                        val productName = itemChild.readString("productName")
                        val unitName = itemChild.readString("unitName", "পিস")
                        val qty = itemChild.readDouble("qty", 1.0)
                        val unitPrice = itemChild.readLong("unitPricePoisha", 0L)
                        val purchasePrice = itemChild.readLong("purchasePriceAtSalePoisha", 0L)
                        val discount = itemChild.readLong("discountPoisha", 0L)
                        val lineTotal = itemChild.readLong("lineTotalPoisha", 0L)
                        saleItemsToInsert.add(
                            SaleItem(
                                id = 0L,
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

                    if (dueAmountPoisha > 0 && customerId != null) {
                        ledgersToInsert.add(
                            CustomerLedger(
                                customerId = customerId,
                                refType = "sale",
                                refId = saleId,
                                debitPoisha = dueAmountPoisha,
                                creditPoisha = 0L,
                                note = "বাকিতে বিক্রয় (রিস্টোর): $invoiceNo",
                                entryDate = saleDate
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Error parsing sale in initial pull", e)
                }
            }

            if (salesToInsert.isNotEmpty()) {
                dao.insertSales(salesToInsert)
            }
            if (saleItemsToInsert.isNotEmpty()) {
                dao.insertSaleItems(saleItemsToInsert)
            }
            // Due ledger: prefer the real ledger backup (includes payments); older backups only
            // have sales, so fall back to rebuilding the ledger from sale dues.
            val ledgerSnap = shopRef.child("customer_ledger").get().await()
            cloudKeys["customer_ledger"] = ledgerSnap.children.mapNotNull { it.key }.toSet()
            val cloudLedgers = ledgerSnap.children.mapNotNull { CloudMappers.readLedger(it) }
            if (cloudLedgers.isNotEmpty()) {
                dao.insertCustomerLedgers(cloudLedgers)
            } else if (ledgersToInsert.isNotEmpty()) {
                dao.insertCustomerLedgers(ledgersToInsert)
            }

            val expenseSnap = shopRef.child("expenses").get().await()
            cloudKeys["expenses"] = expenseSnap.children.mapNotNull { it.key }.toSet()
            val expenses = expenseSnap.children.mapNotNull { CloudMappers.readExpense(it) }
            if (expenses.isNotEmpty()) dao.insertExpenses(expenses)

            val purchaseSnap = shopRef.child("purchases").get().await()
            cloudKeys["purchases"] = purchaseSnap.children.mapNotNull { it.key }.toSet()
            val purchases = purchaseSnap.children.mapNotNull { CloudMappers.readPurchase(it) }
            val newPurchases = purchases.filter { dao.getPurchaseById(it.first.id) == null }
            if (newPurchases.isNotEmpty()) {
                dao.insertPurchases(newPurchases.map { it.first })
                dao.insertPurchaseItems(newPurchases.flatMap { it.second })
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
     * Detach all active realtime child event listeners.
     */
    fun detachRealtimeListeners() {
        try {
            productsListener?.let { productsQueryRef?.removeEventListener(it) }
            salesListener?.let { salesQueryRef?.removeEventListener(it) }
        } catch (_: Exception) {}
        productsListener = null
        salesListener = null
        productsQueryRef = null
        salesQueryRef = null
        isListenersAttached = false
        currentAttachedShopCode = ""
    }

    /**
     * Attach realtime listeners for instantaneous live syncing across all devices.
     */
    private fun attachRealtimeListeners(shopRef: DatabaseReference) {
        val targetCode = currentShopCode
        if (isListenersAttached && currentAttachedShopCode == targetCode) return
        detachRealtimeListeners()
        isListenersAttached = true
        currentAttachedShopCode = targetCode

        // 1. Live Product Sync (Stock adjustment, price changes)
        val prodRef = shopRef.child("products")
        productsQueryRef = prodRef
        val pListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleProductUpdate(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                handleProductUpdate(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.child("id").getValue(Long::class.java) ?: snapshot.key?.toLongOrNull()
                id?.let {
                    scope.launch {
                        try { dao.softDeleteProduct(it) } catch (_: Exception) {}
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Products sync cancelled: ${error.message}")
            }
        }
        productsListener = pListener
        prodRef.addChildEventListener(pListener)

        // 2. Live Sales Sync (Employee sells -> Owner immediately receives the sale)
        val sRef = shopRef.child("sales")
        salesQueryRef = sRef
        val sListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleSaleAdded(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(tag, "Sales sync cancelled: ${error.message}")
            }
        }
        salesListener = sListener
        sRef.addChildEventListener(sListener)
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
                val isActive = CloudMappers.readActive(snapshot, local?.isActive ?: true)

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
                val note = snapshot.child("note").getValue(String::class.java)?.takeIf { it.isNotBlank() }

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
                    paymentMethod = paymentMethod,
                    note = note
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
                            id = 0L,
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

                    // No local stock change here: the seller already adjusted the cloud stock with a
                    // transaction, and the products listener delivers that value. Deducting again here
                    // counted every remote sale twice.
                }
                if (items.isNotEmpty()) {
                    dao.insertSaleItems(items)
                }

                // A due sale from another phone (e.g. staff) must also raise the customer's due here.
                if (dueAmountPoisha > 0 && customerId != null && dao.countCustomerLedgerByRef("sale", saleId) == 0) {
                    dao.insertCustomerLedger(
                        CustomerLedger(
                            id = com.example.util.IdGen.next(),
                            customerId = customerId,
                            refType = "sale",
                            refId = saleId,
                            debitPoisha = dueAmountPoisha,
                            creditPoisha = 0L,
                            note = "বাকিতে বিক্রয়: $invoiceNo",
                            entryDate = saleDate
                        )
                    )
                }

                _syncStatus.value = _syncStatus.value.copy(lastSyncTime = System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(tag, "Error handling realtime sale added", e)
            }
        }
    }

    /**
     * Applies a stock change atomically in the cloud. Several phones selling at once used to
     * overwrite each other's absolute stock values; a transaction adds the deltas instead.
     */
    fun adjustCloudStock(productId: Long, delta: Double) {
        val ref = currentShopRef ?: return
        if (productId <= 0L || delta == 0.0) return
        val productRef = ref.child("products").child(productId.toString())
        productRef.child("stockQty").runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val now = (current.value as? Number)?.toDouble() ?: current.value?.toString()?.toDoubleOrNull()
                    ?: return Transaction.abort() // product not in the cloud yet; the next full push sends it
                current.value = now + delta
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) Log.w(tag, "Stock transaction failed for $productId: ${error.message}")
                else if (committed) productRef.child("updatedAt").setValue(System.currentTimeMillis())
            }
        })
    }

    /** Broadcast a new sale to Firebase instantly. */
    fun pushSale(sale: Sale, items: List<SaleItem>) {
        val ref = currentShopRef ?: return
        ref.child("sales").child(sale.id.toString()).setValue(saleRecord(sale, items))
        items.forEach { adjustCloudStock(it.productId, -it.qty) }
    }

    /** Re-sends a sale after a return/edit (owner only by the rules) and the stock that came back. */
    fun pushSaleUpdate(sale: Sale, items: List<SaleItem>, restocked: Map<Long, Double>) {
        val ref = currentShopRef ?: return
        if (currentUserRole == "owner") ref.child("sales").child(sale.id.toString()).setValue(saleRecord(sale, items))
        restocked.forEach { (productId, qty) -> adjustCloudStock(productId, qty) }
    }

    fun deleteSaleFromCloud(saleId: Long) {
        if (currentUserRole != "owner") return
        currentShopRef?.child("sales")?.child(saleId.toString())?.removeValue()
    }

    /** Push product addition / price update to cloud. */
    fun pushProduct(product: Product) {
        val ref = currentShopRef ?: return
        ref.child("products").child(product.id.toString()).setValue(CloudMappers.product(product))
    }

    fun deleteProduct(productId: Long) {
        val ref = currentShopRef ?: return
        ref.child("products").child(productId.toString()).child("isActive").setValue(false)
    }

    /** Manual stock adjustment (damage, count correction…): sent as a delta. */
    fun syncProductStock(productId: Long, delta: Double) = adjustCloudStock(productId, delta)

    fun pushCustomer(customer: Customer, duePoisha: Long?) {
        currentShopRef?.child("customers")?.child(customer.id.toString())?.updateChildren(CloudMappers.customer(customer, duePoisha))
    }

    fun pushSupplier(supplier: Supplier) {
        currentShopRef?.child("suppliers")?.child(supplier.id.toString())?.updateChildren(CloudMappers.supplier(supplier))
    }

    fun pushExpense(expense: Expense) {
        currentShopRef?.child("expenses")?.child(expense.id.toString())?.setValue(CloudMappers.expense(expense))
    }

    fun pushPurchase(purchase: Purchase, items: List<PurchaseItem>) {
        val ref = currentShopRef ?: return
        ref.child("purchases").child(purchase.id.toString()).setValue(CloudMappers.purchase(purchase, items))
        items.forEach { adjustCloudStock(it.productId, it.qty) }
    }

    /** Owner only (rules): staff phones' due sales reach the ledger through the owner's phone. */
    fun pushCustomerLedger(entry: CustomerLedger, customer: Customer?, duePoisha: Long?) {
        val ref = currentShopRef ?: return
        if (currentUserRole != "owner") return
        ref.child("customer_ledger").child(entry.id.toString()).setValue(CloudMappers.ledger(entry))
        if (customer != null) pushCustomer(customer, duePoisha)
    }

    /**
     * Disconnect shop.
     */
    fun disconnect(signOut: Boolean = false) {
        detachRealtimeListeners()
        if (signOut) cloudAuth.signOut()
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

@JvmName("awaitVoid")
private suspend fun com.google.android.gms.tasks.Task<Void>.await(): Unit = suspendCancellableCoroutine { cont ->
    if (isComplete) {
        val ex = exception
        if (ex != null) cont.resumeWith(Result.failure(ex))
        else if (isCanceled) cont.cancel()
        else cont.resumeWith(Result.success(Unit))
        return@suspendCancellableCoroutine
    }
    addOnCompleteListener { task ->
        if (cont.isActive) {
            val ex = task.exception
            if (ex != null) {
                cont.resumeWith(Result.failure(ex))
            } else if (task.isCanceled) {
                cont.cancel()
            } else {
                cont.resumeWith(Result.success(Unit))
            }
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    if (isComplete) {
        val ex = exception
        if (ex != null) throw ex
        if (isCanceled) throw kotlinx.coroutines.CancellationException("Task was cancelled.")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (cont.isActive) {
                val ex = task.exception
                if (ex != null) {
                    cont.resumeWith(Result.failure(ex))
                } else if (task.isCanceled) {
                    cont.cancel()
                } else {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        cont.resumeWith(Result.success(task.result as T))
                    } catch (t: Throwable) {
                        cont.resumeWith(Result.failure(t))
                    }
                }
            }
        }
    }
}
