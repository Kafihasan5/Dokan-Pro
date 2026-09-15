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
     * Checks whether the app is currently activated locally.
     * Offline-first: returns true instantly from local storage without network calls!
     */
    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVATED, false)
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
            .apply()
    }
}
