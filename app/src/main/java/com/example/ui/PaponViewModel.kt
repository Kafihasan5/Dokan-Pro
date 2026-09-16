package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.db.PaponDatabase
import com.example.data.entity.*
import com.example.data.repository.PaponRepository
import com.example.util.AppUpdater
import com.example.util.Formatters
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Calendar
import org.json.JSONArray

data class CartItem(
    val productId: Long, // 0 for custom quick item
    val productName: String,
    val unitPricePoisha: Long,
    val purchasePricePoisha: Long = 0,
    val unitName: String = "পিস",
    val qty: Double = 1.0,
    val discountPoisha: Long = 0
) {
    val lineTotalPoisha: Long
        get() = ((qty * unitPricePoisha).toLong() - discountPoisha).coerceAtLeast(0)
}

data class ShopConfig(
    val shopName: String = "Dokan Pro",
    val shopAddress: String = "বাজার রোড, ঢাকা",
    val shopPhone: String = "০১৭১১-০০০০০০",
    val tagline: String = "আপনার বিশ্বস্ত মুদি দোকান",
    val currencySymbol: String = "৳",
    val useBengaliNumerals: Boolean = true,
    val themeMode: String = "system", // "light", "dark", "system"
    val vatEnabled: Boolean = false,
    val vatPercentage: Double = 0.0,
    val pinEnabled: Boolean = false,
    val pinCode: String = "1234",
    val userRole: String = "owner", // "owner" or "staff"
    val allowNegativeStock: Boolean = true,
    val isOnboardingCompleted: Boolean = false,
    val noticeMessage: String = ""
)

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String = BuildConfig.VERSION_NAME,
    val latestVersionCode: Int = BuildConfig.VERSION_CODE,
    val updateNotes: String = "",
    val apkDownloadUrl: String = "",
    val isForceUpdate: Boolean = false
)

enum class AppScreen {
    DASHBOARD,
    PRODUCTS,
    POS,
    DUE_KHATA,
    REPORTS,
    PURCHASES,
    EXPENSES,
    BACKUP,
    SETTINGS,
    RECEIPT
}

class PaponViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PaponRepository
    private val prefs = application.getSharedPreferences("papon_settings", android.content.Context.MODE_PRIVATE)

    val networkMonitor = com.example.util.NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _shopConfig = MutableStateFlow(ShopConfig())
    val shopConfig: StateFlow<ShopConfig> = _shopConfig.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow(AppUpdateInfo())
    val appUpdateInfo: StateFlow<AppUpdateInfo> = _appUpdateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _showUpdateDialogEvent = MutableStateFlow(false)
    val showUpdateDialogEvent: StateFlow<Boolean> = _showUpdateDialogEvent.asStateFlow()

    fun openUpdateDialog() {
        if (_appUpdateInfo.value.isUpdateAvailable) {
            _showUpdateDialogEvent.value = true
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialogEvent.value = false
    }

    private val defaultUnits = listOf("কেজি", "গ্রাম", "লিটার", "মিলি", "পিস", "প্যাকেট", "হালি", "ডজন", "বস্তা", "বক্স", "কার্টুন", "মিটার", "বোতল")
    private val _units = MutableStateFlow<List<String>>(defaultUnits)
    val units: StateFlow<List<String>> = _units.asStateFlow()

    val licenseManager = com.example.data.license.AppLicenseManager(application)
    private val _isAppActivated = MutableStateFlow(licenseManager.isActivated())
    val isAppActivated: StateFlow<Boolean> = _isAppActivated.asStateFlow()

    private val _isActivating = MutableStateFlow(false)
    val isActivating: StateFlow<Boolean> = _isActivating.asStateFlow()

    private val _activationError = MutableStateFlow<String?>(null)
    val activationError: StateFlow<String?> = _activationError.asStateFlow()

    private val _licenseInfo = MutableStateFlow(licenseManager.getLicenseInfo())
    val licenseInfo: StateFlow<com.example.data.license.LicenseInfo?> = _licenseInfo.asStateFlow()

    // 1-Hour Free Demo Mode State
    private val _isDemoMode = MutableStateFlow(licenseManager.isDemoMode())
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _remainingDemoMillis = MutableStateFlow(licenseManager.getRemainingDemoMillis())
    val remainingDemoMillis: StateFlow<Long> = _remainingDemoMillis.asStateFlow()

    private val _isDemoUsed = MutableStateFlow(licenseManager.wasDemoUsed())
    val isDemoUsed: StateFlow<Boolean> = _isDemoUsed.asStateFlow()

    private val _isDemoExpired = MutableStateFlow(licenseManager.isDemoExpired())
    val isDemoExpired: StateFlow<Boolean> = _isDemoExpired.asStateFlow()

    private var demoTimerJob: Job? = null

    private fun startDemoTimerTicker() {
        demoTimerJob?.cancel()
        demoTimerJob = viewModelScope.launch {
            while (licenseManager.isDemoMode()) {
                val remaining = licenseManager.getRemainingDemoMillis()
                _remainingDemoMillis.value = remaining
                if (remaining <= 0) {
                    _isDemoMode.value = false
                    _isDemoExpired.value = true
                    if (!licenseManager.isRealLicenseActive()) {
                        _isAppActivated.value = false
                        showToast("⏱️ ১ ঘণ্টার ফ্রি ডেমো মেয়াদ সমাপ্ত হয়েছে। নিয়মিত ব্যবহারের জন্য লাইসেন্স সংগ্রহ করুন (৳৪৯০)।")
                    }
                    break
                }
                delay(1000L)
            }
        }
    }

    fun startOneHourDemo() {
        viewModelScope.launch {
            _isActivating.value = true
            when (val res = licenseManager.startOneHourDemo()) {
                is com.example.data.license.DemoStartResult.Success -> {
                    // Seed 7 days of realistic grocery shop dummy data
                    repository.seedDemoData()
                    _isDemoMode.value = true
                    _isDemoUsed.value = true
                    _isDemoExpired.value = false
                    _remainingDemoMillis.value = res.remainingMillis
                    _isAppActivated.value = true
                    // Auto-complete onboarding so demo enters dashboard directly with all 20 products
                    if (!_shopConfig.value.isOnboardingCompleted) {
                        val current = _shopConfig.value
                        val updated = current.copy(
                            shopName = if (current.shopName.isBlank() || current.shopName == "দোকান প্রো") "Dokan Pro" else current.shopName,
                            isOnboardingCompleted = true
                        )
                        updateShopConfig(updated)
                    }
                    startDemoTimerTicker()
                    _isActivating.value = false
                    showToast(res.message.ifBlank { "১ ঘণ্টার ফ্রি ডেমো মোড চালু হয়েছে! ২০টি পণ্য ও ৭ দিনের ডামি ডাটা লোড করা হয়েছে।" })
                }
                is com.example.data.license.DemoStartResult.Expired -> {
                    _isDemoMode.value = false
                    _isDemoUsed.value = true
                    _isDemoExpired.value = true
                    _isAppActivated.value = false
                    _isActivating.value = false
                    showToast(res.message)
                }
                is com.example.data.license.DemoStartResult.Error -> {
                    _isActivating.value = false
                    showToast(res.message)
                }
            }
        }
    }

    // Customer Personal Supabase Cloud Credentials & Sync State
    private val _customerSupabaseUrl = MutableStateFlow(prefs.getString("customer_supabase_url", "") ?: "")
    val customerSupabaseUrl: StateFlow<String> = _customerSupabaseUrl.asStateFlow()

    private val _customerSupabaseKey = MutableStateFlow(prefs.getString("customer_supabase_key", "") ?: "")
    val customerSupabaseKey: StateFlow<String> = _customerSupabaseKey.asStateFlow()

    private val _isCustomerCloudConfigured = MutableStateFlow(
        (prefs.getString("customer_supabase_url", "") ?: "").isNotBlank() &&
        (prefs.getString("customer_supabase_key", "") ?: "").isNotBlank()
    )
    val isCustomerCloudConfigured: StateFlow<Boolean> = _isCustomerCloudConfigured.asStateFlow()

    fun getDeviceId(): String = licenseManager.getDeviceId()

    fun clearActivationError() {
        _activationError.value = null
    }

    fun activateApp(email: String) {
        viewModelScope.launch {
            _isActivating.value = true
            _activationError.value = null
            when (val res = licenseManager.activateWithEmail(email)) {
                is com.example.data.license.ActivationResult.Success -> {
                    // CRITICAL REQUIREMENT: Real customer activated the app!
                    // If demo was used or currently active, wipe all dummy data clean so they get a 100% fresh shop.
                    val wasDemo = licenseManager.wasDemoUsed() || _isDemoMode.value
                    if (wasDemo) {
                        repository.clearAllDummyData()
                        licenseManager.clearDemoState()
                    }
                    demoTimerJob?.cancel()
                    _isDemoMode.value = false
                    _isAppActivated.value = true
                    _licenseInfo.value = res.info
                    _isActivating.value = false
                    showToast(res.message.ifBlank { "অভিনন্দন! Dokan-Pro সফলভাবে সক্রিয় হয়েছে" })
                }
                is com.example.data.license.ActivationResult.Error -> {
                    _activationError.value = res.message
                    _isActivating.value = false
                }
            }
        }
    }

    init {
        loadShopConfig()
        loadUnits()
        val db = PaponDatabase.getInstance(application)
        repository = PaponRepository(db.paponDao())

        if (licenseManager.isDemoMode()) {
            startDemoTimerTicker()
        }

        // Anti-abuse: Check hardware device ID in cloud on launch (e.g. if user did 'Clear Storage')
        if (!licenseManager.isRealLicenseActive()) {
            viewModelScope.launch {
                licenseManager.syncDeviceDemoStatus()
                _isDemoUsed.value = licenseManager.wasDemoUsed()
                _isDemoExpired.value = licenseManager.isDemoExpired()
                if (_isDemoExpired.value && _isDemoMode.value) {
                    _isDemoMode.value = false
                    _isAppActivated.value = false
                }
            }
        }
        
        // Initialize dynamic customer cloud credentials if configured
        repository.updateCustomerCloudCredentials(_customerSupabaseUrl.value, _customerSupabaseKey.value)

        // Listen for network restoration to immediately sync offline changes
        networkMonitor.setOnNetworkRestoredCallback {
            viewModelScope.launch {
                // Connection is back! Instantly push all offline sales, products, expenses & pull remote updates
                if (_isCustomerCloudConfigured.value) {
                    syncToSupabase(silent = true)
                }
            }
        }

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            if (licenseManager.isDemoMode()) {
                if (repository.getAllProductsSync().isEmpty()) {
                    repository.seedDemoData()
                }
            }
            // Initial sync (pull remote changes and push any local data if customer cloud configured)
            if (_isCustomerCloudConfigured.value) {
                syncToSupabase(silent = true)
            }
            // Start automatic background poll (pulls changes made in Supabase every 20s for realtime updates)
            startPeriodicSync()
            // Check for in-app updates from GitHub
            checkForUpdates(silent = true)
        }
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(20_000) // 20 seconds background poll
                try {
                    if (_isCustomerCloudConfigured.value && networkMonitor.isCurrentlyOnline()) {
                        val result = repository.pullFromSupabase(force = false)
                        if (result.success) {
                            _lastSyncTime.value = System.currentTimeMillis()
                            if (result.configUpdates.isNotEmpty()) {
                                applyRemoteConfig(result.configUpdates)
                            }
                        } else if (result.isNetworkError) {
                            networkMonitor.markOffline()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("PaponVM", "Background pull error: ${e.message}")
                }
            }
        }
    }

    private fun applyRemoteConfig(updates: Map<String, String>) {
        var current = _shopConfig.value
        updates["shop_name"]?.let { if (it.isNotBlank()) current = current.copy(shopName = it) }
        updates["shop_phone"]?.let { if (it.isNotBlank()) current = current.copy(shopPhone = it) }
        updates["shop_address"]?.let { if (it.isNotBlank()) current = current.copy(shopAddress = it) }
        updates["tagline"]?.let { if (it.isNotBlank()) current = current.copy(tagline = it) }
        updates["notice"]?.let { current = current.copy(noticeMessage = it) }
        updates["theme_mode"]?.let { if (it in listOf("light", "dark", "system")) current = current.copy(themeMode = it) }
        updates["vat_percentage"]?.toDoubleOrNull()?.let { current = current.copy(vatPercentage = it, vatEnabled = it > 0) }
        if (current != _shopConfig.value) {
            _shopConfig.value = current
            saveShopConfig(current)
        }

        // Check for app version updates
        val remoteVersionCode = updates["latest_version_code"]?.toIntOrNull() ?: 1
        val remoteVersionName = updates["latest_version_name"] ?: "1.0.0"
        val updateNotes = updates["update_notes"] ?: ""
        val apkUrl = updates["apk_download_url"] ?: ""
        val isForce = updates["is_force_update"]?.toBoolean() ?: false

        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        _appUpdateInfo.value = AppUpdateInfo(
            isUpdateAvailable = remoteVersionCode > currentCode,
            currentVersionName = currentName,
            currentVersionCode = currentCode,
            latestVersionName = remoteVersionName,
            latestVersionCode = remoteVersionCode,
            updateNotes = updateNotes,
            apkDownloadUrl = apkUrl,
            isForceUpdate = isForce
        )
    }

    fun syncToSupabase(silent: Boolean = false) {
        viewModelScope.launch {
            if (!_isCustomerCloudConfigured.value) {
                if (!silent) {
                    showToast("কাস্টমার ক্লাউড সিঙ্ক সেটআপ করা নেই। Settings থেকে Supabase URL ও Key সেট করুন।")
                }
                return@launch
            }
            if (!networkMonitor.isCurrentlyOnline() && silent) {
                return@launch
            }
            _isSyncing.value = true
            try {
                val result = repository.syncWithSupabase()
                _lastSyncTime.value = System.currentTimeMillis()
                if (result.success && result.configUpdates.isNotEmpty()) {
                    applyRemoteConfig(result.configUpdates)
                }
                if (result.isNetworkError) {
                    networkMonitor.markOffline()
                }
                if (!silent) {
                    if (result.success) {
                        showToast("তথ্য সফলভাবে হালনাগাদ হয়েছে")
                    } else {
                        showToast(result.message.ifBlank { "হালনাগাদ ব্যর্থ হয়েছে" })
                    }
                }
            } catch (e: Exception) {
                networkMonitor.markOffline()
                if (!silent) showToast("ইন্টারনেট সংযোগ সমস্যা: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun saveCustomerCloudConfig(url: String, key: String, onComplete: () -> Unit = {}) {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = key.trim()
        prefs.edit()
            .putString("customer_supabase_url", cleanUrl)
            .putString("customer_supabase_key", cleanKey)
            .apply()
        _customerSupabaseUrl.value = cleanUrl
        _customerSupabaseKey.value = cleanKey
        val isConf = cleanUrl.isNotBlank() && cleanKey.isNotBlank()
        _isCustomerCloudConfigured.value = isConf
        repository.updateCustomerCloudCredentials(cleanUrl, cleanKey)
        showToast("সুপাবেস ক্লাউড সেটিংস সংরক্ষণ করা হয়েছে")
        onComplete()
    }

    fun clearCustomerCloudConfig(onComplete: () -> Unit = {}) {
        prefs.edit()
            .remove("customer_supabase_url")
            .remove("customer_supabase_key")
            .apply()
        _customerSupabaseUrl.value = ""
        _customerSupabaseKey.value = ""
        _isCustomerCloudConfigured.value = false
        repository.updateCustomerCloudCredentials("", "")
        showToast("ক্লাউড সংযোগ সফলভাবে বিচ্ছিন্ন করা হয়েছে")
        onComplete()
    }

    fun testCustomerCloudConnection(url: String, key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.testCustomerCloudConnection(url, key)
            showToast(res.second)
            onResult(res.first, res.second)
        }
    }

    fun backupToCustomerCloud(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.backupToCustomerCloud()
            _isSyncing.value = false
            if (res.first) {
                _lastSyncTime.value = System.currentTimeMillis()
            }
            showToast(res.second)
            onResult(res.first, res.second)
        }
    }

    fun restoreFromCustomerCloud(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.restoreFromCustomerCloud()
            _isSyncing.value = false
            if (res.first) {
                _lastSyncTime.value = System.currentTimeMillis()
            }
            showToast(res.second)
            onResult(res.first, res.second)
        }
    }


    // --- NAVIGATION & UI STATE ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isPinUnlocked = MutableStateFlow(true)
    val isPinUnlocked: StateFlow<Boolean> = _isPinUnlocked.asStateFlow()

    private val _lastCompletedSaleId = MutableStateFlow<Long?>(null)
    val lastCompletedSaleId: StateFlow<Long?> = _lastCompletedSaleId.asStateFlow()

    private val _lastCompletedSale = MutableStateFlow<Sale?>(null)
    val lastCompletedSale: StateFlow<Sale?> = _lastCompletedSale.asStateFlow()

    private val _lastCompletedSaleItems = MutableStateFlow<List<SaleItem>>(emptyList())
    val lastCompletedSaleItems: StateFlow<List<SaleItem>> = _lastCompletedSaleItems.asStateFlow()

    private val _quickActionsOpen = MutableStateFlow(false)
    val quickActionsOpen: StateFlow<Boolean> = _quickActionsOpen.asStateFlow()

    // --- REPOSITORY FLOWS ---
    val products: StateFlow<List<Product>> = repository.allActiveProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<Sale>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSaleItems: StateFlow<List<SaleItem>> = repository.allSaleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDue: StateFlow<Long> = repository.totalDueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val suppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchases: StateFlow<List<Purchase>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<ExpenseCategory>> = repository.allExpenseCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupLogs: StateFlow<List<BackupLog>> = repository.backupLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- STOCK VALUATION FLOWS ---
    val totalStockSaleValuePoisha: StateFlow<Long> = repository.allActiveProducts
        .map { list -> list.sumOf { (it.stockQty * it.salePricePoisha).toLong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalStockPurchaseValuePoisha: StateFlow<Long> = repository.allActiveProducts
        .map { list -> list.sumOf { (it.stockQty * it.purchasePricePoisha).toLong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // --- CART STATE (POS) ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow<Long?>(null)
    val selectedCustomerId: StateFlow<Long?> = _selectedCustomerId.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow("cash") // cash, mfs, card, due
    val selectedPaymentMethod: StateFlow<String> = _selectedPaymentMethod.asStateFlow()

    private val _discountPoisha = MutableStateFlow(0L)
    val discountPoisha: StateFlow<Long> = _discountPoisha.asStateFlow()

    private val _cashTenderedPoisha = MutableStateFlow(0L)
    val cashTenderedPoisha: StateFlow<Long> = _cashTenderedPoisha.asStateFlow()

    private val _lastCashTenderedPoisha = MutableStateFlow(0L)
    val lastCashTenderedPoisha: StateFlow<Long> = _lastCashTenderedPoisha.asStateFlow()

    private val _lastChangeReturnPoisha = MutableStateFlow(0L)
    val lastChangeReturnPoisha: StateFlow<Long> = _lastChangeReturnPoisha.asStateFlow()

    private val _posSearchQuery = MutableStateFlow("")
    val posSearchQuery: StateFlow<String> = _posSearchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow(1L)
    val selectedCategoryId: StateFlow<Long> = _selectedCategoryId.asStateFlow()

    // Parked / Held Cart
    private val _heldCarts = MutableStateFlow<List<List<CartItem>>>(emptyList())
    val heldCarts: StateFlow<List<List<CartItem>>> = _heldCarts.asStateFlow()

    // --- NOTIFICATION / SNACKBAR ---
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _quickActionsOpen.value = false
    }

    fun toggleQuickActions() {
        _quickActionsOpen.value = !_quickActionsOpen.value
    }

    fun closeQuickActions() {
        _quickActionsOpen.value = false
    }

    // --- CART ACTIONS ---
    fun addProductToCart(product: Product, quantityToAdd: Double = 1.0) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.productId == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(qty = existing.qty + quantityToAdd)
        } else {
            current.add(
                CartItem(
                    productId = product.id,
                    productName = product.nameBn,
                    unitPricePoisha = product.salePricePoisha,
                    purchasePricePoisha = product.purchasePricePoisha,
                    unitName = product.unitName,
                    qty = quantityToAdd
                )
            )
        }
        _cartItems.value = current
    }

    fun addCustomItemToCart(name: String, pricePoisha: Long, qty: Double = 1.0, unitName: String = "পিস") {
        val current = _cartItems.value.toMutableList()
        current.add(
            CartItem(
                productId = 0,
                productName = if (name.isBlank()) "খোলা পণ্য" else name,
                unitPricePoisha = pricePoisha,
                purchasePricePoisha = (pricePoisha * 0.8).toLong(),
                unitName = unitName,
                qty = qty
            )
        )
        _cartItems.value = current
    }

    fun updateCartItemQty(productId: Long, newQty: Double) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(qty = newQty)
            }
            _cartItems.value = current
        }
    }

    fun updateCartItemPriceAndQty(productId: Long, newPricePoisha: Long, newQty: Double) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = current[index].copy(unitPricePoisha = newPricePoisha, qty = newQty)
            }
            _cartItems.value = current
        }
    }

    fun removeCartItem(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.productId == productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountPoisha.value = 0L
        _cashTenderedPoisha.value = 0L
        _selectedCustomerId.value = null
        _selectedPaymentMethod.value = "cash"
    }

    fun holdCurrentCart() {
        if (_cartItems.value.isNotEmpty()) {
            val held = _heldCarts.value.toMutableList()
            held.add(_cartItems.value)
            _heldCarts.value = held
            clearCart()
            showToast("কার্ট হোল্ড রাখা হয়েছে")
        }
    }

    fun restoreHeldCart(index: Int) {
        val held = _heldCarts.value.toMutableList()
        if (index in held.indices) {
            _cartItems.value = held.removeAt(index)
            _heldCarts.value = held
            showToast("হোল্ড করা কার্ট পুনরুদ্ধার করা হয়েছে")
        }
    }

    fun setDiscount(poisha: Long) {
        _discountPoisha.value = poisha.coerceAtLeast(0)
    }

    fun setCashTendered(poisha: Long) {
        _cashTenderedPoisha.value = poisha.coerceAtLeast(0)
    }

    fun selectCustomer(customerId: Long?) {
        _selectedCustomerId.value = customerId
    }

    fun selectPaymentMethod(method: String) {
        _selectedPaymentMethod.value = method
    }

    fun setPosSearchQuery(query: String) {
        _posSearchQuery.value = query
    }

    fun selectCategory(catId: Long) {
        _selectedCategoryId.value = catId
    }

    // --- CHECKOUT / COMPLETE SALE ---
    fun checkoutSale(
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val items = _cartItems.value
        if (items.isEmpty()) {
            onError("কার্ট খালি!")
            return
        }

        val subtotal = items.sumOf { it.lineTotalPoisha }
        val discount = _discountPoisha.value
        val vat = if (_shopConfig.value.vatEnabled) ((subtotal - discount) * (_shopConfig.value.vatPercentage / 100.0)).toLong() else 0L
        val total = (subtotal - discount + vat).coerceAtLeast(0)

        val method = _selectedPaymentMethod.value
        val custId = _selectedCustomerId.value

        if (method == "due" && custId == null) {
            onError("বাকিতে বিক্রির জন্য কাস্টমার নির্বাচন আবশ্যক!")
            return
        }

        val customer = customers.value.find { it.id == custId }
        val paidAmount = when (method) {
            "due" -> 0L
            "cash" -> {
                if (_cashTenderedPoisha.value > 0) {
                    _cashTenderedPoisha.value.coerceAtMost(total)
                } else {
                    total
                }
            }
            else -> total
        }
        val dueAmount = (total - paidAmount).coerceAtLeast(0)

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val invoice = "INV-${System.currentTimeMillis() % 1000000}"
                val sale = Sale(
                    invoiceNo = invoice,
                    customerId = custId,
                    customerName = customer?.name,
                    saleDate = now,
                    subtotalPoisha = subtotal,
                    discountPoisha = discount,
                    vatPoisha = vat,
                    totalPoisha = total,
                    paidAmountPoisha = paidAmount,
                    dueAmountPoisha = dueAmount,
                    paymentMethod = method,
                    note = null
                )

                val saleItems = items.map {
                    SaleItem(
                        saleId = 0,
                        productId = it.productId,
                        productName = it.productName,
                        unitName = it.unitName,
                        qty = it.qty,
                        unitPricePoisha = it.unitPricePoisha,
                        purchasePriceAtSalePoisha = it.purchasePricePoisha,
                        discountPoisha = it.discountPoisha,
                        lineTotalPoisha = it.lineTotalPoisha
                    )
                }

                val saleId = repository.completeSale(sale, saleItems)
                _lastCompletedSaleId.value = saleId
                _lastCompletedSale.value = sale.copy(id = saleId)
                _lastCompletedSaleItems.value = saleItems
                _lastCashTenderedPoisha.value = if (method == "cash") _cashTenderedPoisha.value else 0L
                _lastChangeReturnPoisha.value = if (method == "cash" && _cashTenderedPoisha.value > total) _cashTenderedPoisha.value - total else 0L

                clearCart()
                _currentScreen.value = AppScreen.RECEIPT
                onSuccess(saleId)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "বিক্রয় সম্পন্ন করা যায়নি")
            }
        }
    }

    fun viewSaleReceipt(sale: Sale) {
        viewModelScope.launch {
            _lastCompletedSaleId.value = sale.id
            _lastCompletedSale.value = sale
            val items = allSaleItems.value.filter { it.saleId == sale.id }
            _lastCompletedSaleItems.value = items
            _lastCashTenderedPoisha.value = 0L
            _lastChangeReturnPoisha.value = 0L
            _currentScreen.value = AppScreen.RECEIPT
        }
    }

    // --- PRODUCT CRUD ---
    fun saveProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveProduct(product)
            showToast("পণ্য সফলভাবে সংরক্ষণ করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            showToast("পণ্য মুছে ফেলা হয়েছে")
        }
    }

    fun deleteSale(saleId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteSale(saleId)
            showToast("বিক্রয় রেকর্ড মুছে ফেলা হয়েছে")
            onSuccess?.invoke()
        }
    }

    fun returnSaleItems(
        saleId: Long,
        returnedItems: Map<Long, Double>,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = repository.returnSaleItems(saleId, returnedItems)
            if (success) {
                showToast("পণ্য সফলভাবে ফেরত নেওয়া হয়েছে এবং স্টক সমন্বয় করা হয়েছে")
                if (_lastCompletedSale.value?.id == saleId) {
                    val updated = repository.getSaleById(saleId)
                    _lastCompletedSale.value = updated
                    if (updated != null) {
                        _lastCompletedSaleItems.value = repository.getSaleItems(saleId)
                    }
                }
                onSuccess?.invoke()
            } else {
                showToast("পণ্য ফেরত প্রক্রিয়া করা যায়নি")
            }
        }
    }

    fun adjustStock(productId: Long, productName: String, qtyChange: Double, reason: String, note: String?) {
        viewModelScope.launch {
            repository.adjustStock(productId, productName, qtyChange, reason, note)
            showToast("স্টক সমন্বয় সম্পন্ন হয়েছে")
        }
    }

    // --- CUSTOMER & DUE COLLECTION ---
    fun saveCustomer(
        customer: Customer,
        initialDuePoisha: Long = 0L,
        initialDueNote: String? = null,
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.saveCustomer(customer, initialDuePoisha, initialDueNote)
            showToast("কাস্টমার সফলভাবে যোগ করা হয়েছে")
            onSuccess(id)
        }
    }

    fun addCustomerDue(
        customerId: Long,
        amountPoisha: Long,
        note: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.addCustomerDue(customerId, amountPoisha, note)
            showToast("বাকি সফলভাবে যোগ করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteCustomer(customerId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
            showToast("কাস্টমার ও তার খাতা মুছে ফেলা হয়েছে")
            onSuccess?.invoke()
        }
    }

    fun collectDuePayment(customerId: Long, amountPoisha: Long, note: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.collectDuePayment(customerId, amountPoisha, note)
            showToast("বাকি আদায় সফল হয়েছে")
            onSuccess()
        }
    }

    fun getCustomerLedgerFlow(customerId: Long): Flow<List<CustomerLedger>> {
        return repository.getCustomerLedger(customerId)
    }

    fun getCustomerBalanceFlow(customerId: Long): Flow<Long> {
        return repository.getCustomerBalance(customerId)
    }

    // --- SUPPLIER & PURCHASES ---
    fun saveSupplier(supplier: Supplier, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveSupplier(supplier)
            showToast("সাপ্লায়ার যোগ করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteSupplier(supplierId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteSupplier(supplierId)
            showToast("সাপ্লায়ার মুছে ফেলা হয়েছে")
            onSuccess?.invoke()
        }
    }

    fun deletePurchase(purchaseId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
            showToast("ক্রয় রেকর্ড মুছে ফেলা হয়েছে")
            onSuccess?.invoke()
        }
    }

    fun recordPurchase(
        purchase: Purchase,
        items: List<PurchaseItem>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.recordPurchase(purchase, items)
            showToast("ক্রয় এন্ট্রি সম্পন্ন হয়েছে ও স্টক বেড়েছে")
            onSuccess()
        }
    }

    // --- EXPENSES ---
    fun addExpense(categoryId: Long, categoryName: String, amountPoisha: Long, note: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val expense = Expense(
                categoryId = categoryId,
                categoryName = categoryName,
                amountPoisha = amountPoisha,
                note = note
            )
            repository.addExpense(expense)
            showToast("খরচ যোগ করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteExpense(expenseId: Long, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            showToast("খরচ মুছে ফেলা হয়েছে")
            onSuccess?.invoke()
        }
    }

    // --- BACKUP & EXPORT ---
    fun createBackup(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.createBackupJson()
            showToast("ব্যাকআপ সফল হয়েছে!")
            onSuccess(json)
        }
    }

    fun resetAllData(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.resetAllData()
            clearCart()
            showToast("২০টি স্যাম্পল পণ্য ও ডামি ডেটা সফলভাবে লোড করা হয়েছে!")
            onSuccess?.invoke()
        }
    }

    fun clearAllDummyData(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            repository.clearAllDummyData()
            clearCart()
            showToast("সকল ডামি ডেটা সম্পূর্ণ মুছে ফেলা হয়েছে!")
            onSuccess?.invoke()
        }
    }

    fun wipeAllDataCloudAndLocal(
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (password.trim().lowercase() != "dokanpro") {
            onError("ভুল পাসওয়ার্ড! সঠিক পাসওয়ার্ড লিখুন।")
            return
        }
        viewModelScope.launch {
            try {
                repository.wipeAllDataCloudAndLocal()
                clearCart()
                showToast("ক্লাউড ও লোকাল সমস্ত ডেটা সম্পূর্ণ মুছে ফেলা হয়েছে!")
                onSuccess()
            } catch (e: Exception) {
                onError("ডেটা মুছতে গিয়ে ত্রুটি হয়েছে: ${e.message}")
            }
        }
    }

    fun restoreFromBackupJson(jsonStr: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.restoreBackupFromJson(jsonStr)
            if (success) {
                showToast("ব্যাকআপ সফলভাবে পুনরুদ্ধার হয়েছে!")
            } else {
                showToast("ব্যাকআপ ফাইল সঠিক নয় বা ত্রুটি ঘটেছে")
            }
            onResult(success)
        }
    }

    fun clearSelectiveData(
        clearSales: Boolean,
        clearProducts: Boolean,
        clearCustomers: Boolean,
        clearExpenses: Boolean,
        clearPurchases: Boolean,
        beforeTimestamp: Long? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.clearSelectiveData(
                clearSales, clearProducts, clearCustomers, clearExpenses, clearPurchases, beforeTimestamp
            )
            clearCart()
            showToast("নির্বাচিত ডেটা সফলভাবে মুছে ফেলা হয়েছে")
            onComplete()
        }
    }

    fun clearAllDataOneClick(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllDummyData()
            clearCart()
            showToast("অ্যাপের সকল ডেটা ১ ক্লিকে সম্পূর্ণ পরিষ্কার করা হয়েছে")
            onComplete()
        }
    }

    fun publishNewVersion(versionCode: Int, versionName: String, updateNotes: String, apkUrl: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.publishAppVersion(versionCode, versionName, updateNotes, apkUrl)
            if (ok) {
                showToast("নতুন ভার্সন সফলভাবে রিলিজ করা হয়েছে!")
                checkForUpdates(silent = true)
            } else {
                showToast("ভার্সন রিলিজ ব্যর্থ হয়েছে")
            }
            onComplete(ok)
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                val gitUpdate = AppUpdater.checkForUpdate()
                if (gitUpdate != null) {
                    _appUpdateInfo.value = AppUpdateInfo(
                        isUpdateAvailable = true,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        latestVersionName = gitUpdate.versionName,
                        latestVersionCode = gitUpdate.versionCode,
                        updateNotes = gitUpdate.releaseNotes,
                        apkDownloadUrl = gitUpdate.downloadUrl,
                        isForceUpdate = false
                    )
                    _showUpdateDialogEvent.value = true
                    if (!silent) {
                        showToast("নতুন আপডেট পাওয়া গেছে: v${gitUpdate.versionName}")
                    }
                    _isCheckingUpdate.value = false
                    return@launch
                }

                try {
                    val res = repository.pullFromSupabase(force = true)
                    if (res.configUpdates.isNotEmpty()) {
                        applyRemoteConfig(res.configUpdates)
                    }
                } catch (_: Exception) { }

                if (!silent) {
                    if (_appUpdateInfo.value.isUpdateAvailable) {
                        _showUpdateDialogEvent.value = true
                        showToast("নতুন আপডেট পাওয়া গেছে: v${_appUpdateInfo.value.latestVersionName}")
                    } else {
                        showToast("আপনার অ্যাপটি সম্পূর্ণ আপ-টু-ডেট (v${BuildConfig.VERSION_NAME})")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!silent) {
                    showToast("আপডেট চেক ব্যর্থ: ইন্টারনেট সংযোগ পরীক্ষা করুন")
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun updatePinSettings(enabled: Boolean, pinCode: String) {
        val updated = _shopConfig.value.copy(pinEnabled = enabled, pinCode = pinCode)
        _shopConfig.value = updated
        saveShopConfig(updated)
        showToast(if (enabled) "অ্যাপ লক সক্রিয় করা হয়েছে" else "অ্যাপ লক নিষ্ক্রিয় করা হয়েছে")
    }


    // --- SETTINGS & THEME ---
    fun updateShopConfig(config: ShopConfig) {
        _shopConfig.value = config
        saveShopConfig(config)
        showToast("সেটিংস সংরক্ষিত হয়েছে")
    }

    fun toggleThemeMode() {
        val next = when (_shopConfig.value.themeMode) {
            "light" -> "dark"
            "dark" -> "light"
            else -> "dark"
        }
        val updated = _shopConfig.value.copy(themeMode = next)
        _shopConfig.value = updated
        saveShopConfig(updated)
        val modeName = if (next == "dark") "নাইট মোড" else "ডে মোড"
        showToast("$modeName সক্রিয় হয়েছে")
    }

    fun setThemeMode(mode: String) {
        val updated = _shopConfig.value.copy(themeMode = mode)
        _shopConfig.value = updated
        saveShopConfig(updated)
    }

    private fun loadShopConfig() {
        try {
            val rawShopName = prefs.getString("shop_name", "Dokan Pro") ?: "Dokan Pro"
            val shopName = if (rawShopName == "দোকান প্রো" || rawShopName.isBlank()) "Dokan Pro" else rawShopName
            val shopAddress = prefs.getString("shop_address", "বাজার রোড, ঢাকা") ?: "বাজার রোড, ঢাকা"
            val shopPhone = prefs.getString("shop_phone", "০১৭১১-০০০০০০") ?: "০১৭১১-০০০০০০"
            val tagline = prefs.getString("tagline", "আপনার বিশ্বস্ত মুদি দোকান") ?: "আপনার বিশ্বস্ত মুদি দোকান"
            val currencySymbol = prefs.getString("currency_symbol", "৳") ?: "৳"
            val useBengaliNumerals = prefs.getBoolean("use_bengali_numerals", true)
            val themeMode = prefs.getString("theme_mode", "system") ?: "system"
            val vatEnabled = prefs.getBoolean("vat_enabled", false)
            val vatPercentage = prefs.getFloat("vat_percentage", 0.0f).toDouble()
            val pinEnabled = prefs.getBoolean("pin_enabled", false)
            val pinCode = prefs.getString("pin_code", "1234") ?: "1234"
            val userRole = prefs.getString("user_role", "owner") ?: "owner"
            val allowNegativeStock = prefs.getBoolean("allow_negative_stock", true)
            val isOnboardingCompleted = prefs.getBoolean("is_onboarding_completed", false)
            val noticeMessage = prefs.getString("notice_message", "") ?: ""

            _shopConfig.value = ShopConfig(
                shopName = shopName,
                shopAddress = shopAddress,
                shopPhone = shopPhone,
                tagline = tagline,
                currencySymbol = currencySymbol,
                useBengaliNumerals = useBengaliNumerals,
                themeMode = themeMode,
                vatEnabled = vatEnabled,
                vatPercentage = vatPercentage,
                pinEnabled = pinEnabled,
                pinCode = pinCode,
                userRole = userRole,
                allowNegativeStock = allowNegativeStock,
                isOnboardingCompleted = isOnboardingCompleted,
                noticeMessage = noticeMessage
            )
        } catch (_: Exception) {}
    }

    private fun saveShopConfig(config: ShopConfig) {
        try {
            prefs.edit().apply {
                putString("shop_name", config.shopName)
                putString("shop_address", config.shopAddress)
                putString("shop_phone", config.shopPhone)
                putString("tagline", config.tagline)
                putString("currency_symbol", config.currencySymbol)
                putBoolean("use_bengali_numerals", config.useBengaliNumerals)
                putString("theme_mode", config.themeMode)
                putBoolean("vat_enabled", config.vatEnabled)
                putFloat("vat_percentage", config.vatPercentage.toFloat())
                putBoolean("pin_enabled", config.pinEnabled)
                putString("pin_code", config.pinCode)
                putString("user_role", config.userRole)
                putBoolean("allow_negative_stock", config.allowNegativeStock)
                putBoolean("is_onboarding_completed", config.isOnboardingCompleted)
                putString("notice_message", config.noticeMessage)
                apply()
            }
        } catch (_: Exception) {}
    }

    private fun loadUnits() {
        try {
            val saved = prefs.getString("custom_units_json", null)
            if (!saved.isNullOrBlank()) {
                val array = JSONArray(saved)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val u = array.getString(i).trim()
                    if (u.isNotEmpty()) list.add(u)
                }
                if (list.isNotEmpty()) {
                    _units.value = list
                    return
                }
            }
        } catch (_: Exception) {}
        _units.value = defaultUnits
    }

    private fun saveUnits(list: List<String>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it) }
            prefs.edit().putString("custom_units_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addCategory(
        nameBn: String,
        nameEn: String = "",
        iconName: String = "shopping_basket",
        onComplete: (() -> Unit)? = null
    ) {
        val cleanBn = nameBn.trim()
        if (cleanBn.isBlank()) return
        viewModelScope.launch {
            val maxOrder = categories.value.maxOfOrNull { it.sortOrder } ?: 0
            val cat = Category(
                nameBn = cleanBn,
                nameEn = nameEn.trim(),
                iconName = iconName,
                sortOrder = maxOrder + 1
            )
            repository.saveCategory(cat)
            onComplete?.invoke()
        }
    }

    fun updateCategory(category: Category, onComplete: (() -> Unit)? = null) {
        if (category.nameBn.isBlank() || category.id == 1L) return
        viewModelScope.launch {
            repository.saveCategory(category)
            onComplete?.invoke()
        }
    }

    fun deleteCategory(categoryId: Long, onComplete: (() -> Unit)? = null) {
        if (categoryId == 1L) return
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = 1L
            }
            onComplete?.invoke()
        }
    }

    fun addUnit(unit: String, onComplete: (() -> Unit)? = null) {
        val clean = unit.trim()
        if (clean.isBlank()) return
        val current = _units.value.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            _units.value = current
            saveUnits(current)
        }
        onComplete?.invoke()
    }

    fun updateUnit(oldUnit: String, newUnit: String, onComplete: (() -> Unit)? = null) {
        val clean = newUnit.trim()
        if (clean.isBlank() || clean == oldUnit) return
        val current = _units.value.toMutableList()
        val index = current.indexOf(oldUnit)
        if (index >= 0) {
            current[index] = clean
            _units.value = current
            saveUnits(current)
            viewModelScope.launch {
                repository.updateProductUnit(oldUnit, clean)
                onComplete?.invoke()
            }
        }
    }

    fun deleteUnit(unit: String, onComplete: (() -> Unit)? = null) {
        val current = _units.value.toMutableList()
        if (current.remove(unit)) {
            _units.value = current
            saveUnits(current)
        }
        onComplete?.invoke()
    }

    fun resetDefaultUnits(onComplete: (() -> Unit)? = null) {
        _units.value = defaultUnits
        saveUnits(defaultUnits)
        onComplete?.invoke()
    }

    fun verifyPin(pin: String): Boolean {
        return if (!_shopConfig.value.pinEnabled || _shopConfig.value.pinCode == pin) {
            _isPinUnlocked.value = true
            true
        } else {
            false
        }
    }

    fun lockApp() {
        if (_shopConfig.value.pinEnabled) {
            _isPinUnlocked.value = false
        }
    }
}

typealias DokanProViewModel = PaponViewModel
