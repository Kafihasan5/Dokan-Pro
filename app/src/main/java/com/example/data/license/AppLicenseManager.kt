package com.example.data.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.data.supabase.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LicenseInfo(
    val email: String,
    val customerName: String,
    val deviceId: String,
    val activatedAt: Long,
    val expiresAt: String? = null,
    val isLifetime: Boolean = true
)

sealed class ActivationResult {
    data class Success(val info: LicenseInfo, val message: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}

sealed class DemoStartResult {
    data class Success(val remainingMillis: Long, val message: String) : DemoStartResult()
    data class Expired(val message: String) : DemoStartResult()
    data class Error(val message: String) : DemoStartResult()
}

class AppLicenseManager(private val context: Context) {

    private val tag = "AppLicenseManager"
    private val prefs = context.getSharedPreferences("dokan_pro_license_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_ACTIVATED = "is_activated"
        private const val KEY_EMAIL = "license_email"
        private const val KEY_CUSTOMER_NAME = "customer_name"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_DEVICE_ID = "device_id"

        // 1-Day Free Demo Mode Keys
        private const val KEY_IS_DEMO_MODE = "is_demo_mode"
        private const val KEY_DEMO_EXPIRES_AT = "demo_expires_at"
        private const val KEY_DEMO_ALREADY_USED = "demo_already_used"
        const val DEMO_DURATION_MILLIS = 24 * 60 * 60 * 1000L // 1 day (24 hours)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Get or generate a persistent unique Device ID for hardware binding.
     */
    fun getDeviceId(): String {
        val cached = prefs.getString(KEY_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        val finalId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            "DEV-" + UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
        }

        prefs.edit().putString(KEY_DEVICE_ID, finalId).apply()
        return finalId
    }

    /**
     * Get human-readable device model for Supabase device registry.
     */
    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android Device"
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    /**
     * Start or verify 1-hour free demo mode using cloud anti-abuse hardware tracking.
     * Prevents re-use via 'Clear Data' or reinstalling by verifying hardware device_id with Supabase!
     */
    suspend fun startOneHourDemo(): DemoStartResult = withContext(Dispatchers.IO) {
        // 1. Fast local check: if already recorded as expired locally
        if (wasDemoUsed() && isDemoExpired()) {
            return@withContext DemoStartResult.Expired(
                "আপনার এই ডিভাইসে ১ দিনের ফ্রি ডেমো সেশন ইতিমধ্যে শেষ হয়েছে। Dokan-Pro নিয়মিত ব্যবহার করতে আজীবন লাইসেন্স সংগ্রহ করুন।"
            )
        }

        // 2. Hardware ID cloud verification with Supabase
        if (!SupabaseConfig.isConnected) {
            return@withContext DemoStartResult.Error(
                "ক্লাউড সার্ভার কনফিগারেশন সেট করা নেই।"
            )
        }

        val deviceId = getDeviceId()
        val deviceModel = getDeviceModel()
        val url = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/start_or_verify_device_demo"

        val payload = JSONObject().apply {
            put("p_device_id", deviceId)
            put("p_device_model", deviceModel)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.w(tag, "start_or_verify_device_demo failed HTTP $code: $body")
                    return@withContext DemoStartResult.Error("সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি (কোড: $code)")
                }

                val json = JSONObject(body)
                val success = json.optBoolean("success", false)
                val remainingSecs = json.optInt("remaining_seconds", 0)
                val message = json.optString("message", "")

                if (success && remainingSecs > 0) {
                    val remainingMillis = remainingSecs * 1000L
                    val expiresAt = System.currentTimeMillis() + remainingMillis
                    prefs.edit()
                        .putBoolean(KEY_IS_DEMO_MODE, true)
                        .putLong(KEY_DEMO_EXPIRES_AT, expiresAt)
                        .putBoolean(KEY_DEMO_ALREADY_USED, true)
                        .apply()
                    return@withContext DemoStartResult.Success(remainingMillis, message)
                } else {
                    // Demo expired on this hardware device! Permanently lock locally
                    prefs.edit()
                        .putBoolean(KEY_IS_DEMO_MODE, false)
                        .putLong(KEY_DEMO_EXPIRES_AT, 1L)
                        .putBoolean(KEY_DEMO_ALREADY_USED, true)
                        .apply()
                    return@withContext DemoStartResult.Expired(
                        message.ifBlank { "আপনার এই ডিভাইসে ১ দিনের ফ্রি ডেমো সেশন ইতিমধ্যে শেষ হয়েছে।" }
                    )
                }
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext DemoStartResult.Error("১ দিনের ফ্রি ডেমো ভেরিফাই করার জন্য ইন্টারনেট সংযোগ প্রয়োজন। অনুগ্রহ করে ইন্টারনেট চালু করুন।")
        } catch (e: Exception) {
            return@withContext DemoStartResult.Error("ডেমো সেশন যাচাই করতে সমস্যা হয়েছে: ${e.message}")
        }
    }

    /**
     * Check if this device has already expired demo in Supabase (e.g. after 'Clear Data').
     * If expired in cloud, immediately locks the local state so demo button stays disabled.
     */
    suspend fun syncDeviceDemoStatus(): Boolean = withContext(Dispatchers.IO) {
        if (wasDemoUsed() && isDemoExpired()) return@withContext false
        if (!SupabaseConfig.isConnected) return@withContext true

        val deviceId = getDeviceId()
        val url = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/check_device_demo_status"
        val payload = JSONObject().apply {
            put("p_device_id", deviceId)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val hasUsed = json.optBoolean("has_used_demo", false)
                    val isExpired = json.optBoolean("is_expired", false)
                    if (hasUsed && isExpired) {
                        prefs.edit()
                            .putBoolean(KEY_IS_DEMO_MODE, false)
                            .putLong(KEY_DEMO_EXPIRES_AT, 1L)
                            .putBoolean(KEY_DEMO_ALREADY_USED, true)
                            .apply()
                        return@withContext false
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext true
    }

    /**
     * Checks if current session is in demo mode.
     */
    fun isDemoMode(): Boolean {
        if (!prefs.getBoolean(KEY_IS_DEMO_MODE, false)) return false
        val expiresAt = prefs.getLong(KEY_DEMO_EXPIRES_AT, 0L)
        val isExpired = System.currentTimeMillis() >= expiresAt
        if (isExpired) {
            prefs.edit().putBoolean(KEY_IS_DEMO_MODE, false).apply()
            return false
        }
        return true
    }

    /**
     * Returns remaining demo duration in milliseconds.
     */
    fun getRemainingDemoMillis(): Long {
        if (!prefs.getBoolean(KEY_IS_DEMO_MODE, false)) return 0L
        val expiresAt = prefs.getLong(KEY_DEMO_EXPIRES_AT, 0L)
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /**
     * Checks whether the 1-hour demo was already initiated previously on this device.
     */
    fun wasDemoUsed(): Boolean {
        return prefs.getBoolean(KEY_DEMO_ALREADY_USED, false)
    }

    /**
     * Checks if the 1-hour demo session has expired.
     */
    fun isDemoExpired(): Boolean {
        val expiresAt = prefs.getLong(KEY_DEMO_EXPIRES_AT, 0L)
        return wasDemoUsed() && (expiresAt > 0L && System.currentTimeMillis() >= expiresAt)
    }

    /**
     * Checks if real paid license is active (not demo).
     */
    fun isRealLicenseActive(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false) && !prefs.getBoolean(KEY_IS_DEMO_MODE, false)
    }

    /**
     * Activate device as an authorized employee/staff member of a shop.
     * Employees do not need a paid license or demo mode since the owner's shop is already licensed.
     */
    fun activateAsStaff(shopCode: String, staffName: String) {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVATED, true)
            .putString(KEY_EMAIL, "staff@$shopCode")
            .putString(KEY_CUSTOMER_NAME, "কর্মচারী: ${staffName.ifBlank { "স্টাফ" }}")
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .remove(KEY_DEMO_EXPIRES_AT)
            .apply()
    }

    /**
     * Activate device for an owner restoring an existing shop via Master PIN.
     */
    fun activateAsRestoredOwner(email: String, shopCode: String) {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVATED, true)
            .putString(KEY_EMAIL, email.ifBlank { "owner@$shopCode" })
            .putString(KEY_CUSTOMER_NAME, "দোকান মালিক")
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .remove(KEY_DEMO_EXPIRES_AT)
            .apply()
    }

    /**
     * Clear demo mode state when user activates with a real license.
     */
    fun clearDemoState() {
        prefs.edit()
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .remove(KEY_DEMO_EXPIRES_AT)
            .apply()
    }

    /**
     * Deactivate local license and demo states (used when employee or owner logs out).
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVATED, false)
            .remove(KEY_EMAIL)
            .remove(KEY_CUSTOMER_NAME)
            .remove(KEY_ACTIVATED_AT)
            .remove(KEY_EXPIRES_AT)
            .putBoolean(KEY_IS_DEMO_MODE, false)
            .remove(KEY_DEMO_EXPIRES_AT)
            .apply()
    }

    /**
     * Checks whether the app is currently activated locally (or running in valid 1-hour demo).
     * Offline-first: returns true instantly from local storage without network calls!
     */
    fun isActivated(): Boolean {
        // Real purchased license
        if (prefs.getBoolean(KEY_IS_ACTIVATED, false)) {
            return true
        }
        // Active 1-hour demo mode
        if (isDemoMode()) {
            return true
        }
        return false
    }

    /**
     * Returns cached license details.
     */
    fun getLicenseInfo(): LicenseInfo? {
        if (!isActivated()) return null
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        if (email.isBlank()) return null

        return LicenseInfo(
            email = email,
            customerName = prefs.getString(KEY_CUSTOMER_NAME, "") ?: "",
            deviceId = getDeviceId(),
            activatedAt = prefs.getLong(KEY_ACTIVATED_AT, System.currentTimeMillis()),
            expiresAt = prefs.getString(KEY_EXPIRES_AT, null),
            isLifetime = prefs.getString(KEY_EXPIRES_AT, null).isNullOrBlank()
        )
    }

    /**
     * Activate the app using the customer's purchase email via Supabase RPC.
     */
    suspend fun activateWithEmail(email: String): ActivationResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext ActivationResult.Error("অনুগ্রহ করে একটি সঠিক ইমেইল এড্রেস লিখুন।")
        }

        if (!SupabaseConfig.isConnected) {
            return@withContext ActivationResult.Error(
                "Supabase ক্লাউড কনফিগারেশন সেট করা নেই। .env ফাইলে SUPABASE_URL এবং SUPABASE_ANON_KEY যুক্ত করুন।"
            )
        }

        val deviceId = getDeviceId()
        val deviceModel = getDeviceModel()
        val url = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/activate_app_license"

        val payload = JSONObject().apply {
            put("p_email", cleanEmail)
            put("p_device_id", deviceId)
            put("p_device_model", deviceModel)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.w(tag, "Activation RPC failed HTTP $code: $body")
                    val msg = try {
                        val errObj = JSONObject(body)
                        errObj.optString("message", "অ্যাক্টিভেশন ব্যর্থ হয়েছে (সার্ভার কোড: $code)")
                    } catch (_: Exception) {
                        "অ্যাক্টিভেশন ব্যর্থ হয়েছে (সার্ভার কোড: $code)"
                    }
                    return@withContext ActivationResult.Error(msg)
                }

                val json = JSONObject(body)
                val success = json.optBoolean("success", false)
                val message = json.optString("message", "")

                if (success) {
                    val custName = json.optString("customer_name", "")
                    val expiresAt = if (json.isNull("expires_at")) null else json.optString("expires_at", null)
                    val now = System.currentTimeMillis()

                    // Save verified activation to local persistent storage
                    prefs.edit()
                        .putBoolean(KEY_IS_ACTIVATED, true)
                        .putString(KEY_EMAIL, cleanEmail)
                        .putString(KEY_CUSTOMER_NAME, custName)
                        .putLong(KEY_ACTIVATED_AT, now)
                        .putString(KEY_EXPIRES_AT, expiresAt)
                        .putBoolean(KEY_IS_DEMO_MODE, false)
                        .remove(KEY_DEMO_EXPIRES_AT)
                        .apply()

                    val info = LicenseInfo(
                        email = cleanEmail,
                        customerName = custName,
                        deviceId = deviceId,
                        activatedAt = now,
                        expiresAt = expiresAt,
                        isLifetime = expiresAt.isNullOrBlank()
                    )
                    return@withContext ActivationResult.Success(info, message)
                } else {
                    return@withContext ActivationResult.Error(message.ifBlank { "অ্যাক্টিভেশন সম্পন্ন করা যায়নি।" })
                }
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext ActivationResult.Error("ইন্টারনেট সংযোগ পাওয়া যায়নি। অ্যাক্টিভ করার জন্য ইন্টারনেট সংযোগ চালু করুন।")
        } catch (e: java.io.IOException) {
            return@withContext ActivationResult.Error("সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি: ${e.message}")
        } catch (e: Exception) {
            return@withContext ActivationResult.Error("অপ্রত্যাশিত ত্রুটি: ${e.message}")
        }
    }

    /**
     * Clear local activation (e.g. for testing or transferring license)
     */
    fun clearLocalActivation() {
        prefs.edit()
            .remove(KEY_IS_ACTIVATED)
            .remove(KEY_EMAIL)
            .remove(KEY_CUSTOMER_NAME)
            .remove(KEY_ACTIVATED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_IS_DEMO_MODE)
            .remove(KEY_DEMO_EXPIRES_AT)
            .apply()
    }
}
