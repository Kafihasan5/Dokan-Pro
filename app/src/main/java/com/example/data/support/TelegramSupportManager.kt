package com.example.data.support

import android.content.Context
import com.example.data.supabase.SupabaseConfig
import com.example.ui.ShopConfig
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SupportChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "support"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSending: Boolean = false,
    val isFailed: Boolean = false
)

object TelegramSupportManager {

    // Dedicated Telegram Bot for Dokan Pro Live Support
    const val BOT_TOKEN = "8677361782:AAFsMQlvxzOljDyaRbNxbrbDv8jTWFkadvY"
    private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot$BOT_TOKEN"

    // Default Support Supergroup Chat ID (Prefixed with -100 for supergroups).
    // Can also be updated dynamically in-app or via settings.
    private const val PREFS_NAME = "dokan_telegram_support_prefs"
    private const val KEY_SUPPORT_CHAT_ID = "support_group_chat_id"
    private const val KEY_TOPIC_PREFIX = "topic_id_"

    // Default Support Supergroup Chat ID provided by user:
    const val DEFAULT_SUPPORT_CHAT_ID = "-1003954086612"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableStateFlow<List<SupportChatMessage>>(emptyList())
    val messages: StateFlow<List<SupportChatMessage>> = _messages.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _configuredChatId = MutableStateFlow<String>(DEFAULT_SUPPORT_CHAT_ID)
    val configuredChatId: StateFlow<String> = _configuredChatId.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SUPPORT_CHAT_ID, "") ?: ""
        _configuredChatId.value = if (saved.isNotBlank()) saved else DEFAULT_SUPPORT_CHAT_ID
        loadLocalMessages(context)
    }

    fun setSupportChatId(context: Context, chatId: String) {
        val clean = chatId.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SUPPORT_CHAT_ID, clean).apply()
        _configuredChatId.value = if (clean.isNotBlank()) clean else DEFAULT_SUPPORT_CHAT_ID
    }

    fun getSupportChatId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SUPPORT_CHAT_ID, "") ?: ""
        return if (saved.isNotBlank()) saved else DEFAULT_SUPPORT_CHAT_ID
    }

    private fun getStoredTopicId(context: Context, deviceId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_TOPIC_PREFIX$deviceId", 0L)
    }

    private fun storeTopicId(context: Context, deviceId: String, topicId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_TOPIC_PREFIX$deviceId", topicId).apply()
    }

    suspend fun getOrCreateForumTopic(
        context: Context,
        deviceId: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val existing = getStoredTopicId(context, deviceId)
        if (existing > 0) {
            return@withContext Result.success(existing)
        }

        val chatId = getSupportChatId(context)
        if (chatId.isBlank()) {
            return@withContext Result.failure(Exception("সাপোর্ট টেলিগ্রাম গ্রুপ আইডি কনফিগার করা হয়নি"))
        }

        try {
            val shopLabel = config.shopName.ifBlank { "দোকান" }
            val shortDev = if (deviceId.length >= 6) deviceId.takeLast(6) else deviceId
            val topicName = "🏪 $shopLabel [$shortDev]"

            val topicPayload = JSONObject().apply {
                put("chat_id", chatId)
                put("name", topicName)
            }

            val request = Request.Builder()
                .url("$TELEGRAM_API_BASE/createForumTopic")
                .post(topicPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("টপিক তৈরি ব্যর্থ: $respBody"))
            }

            val json = JSONObject(respBody)
            if (!json.optBoolean("ok", false)) {
                return@withContext Result.failure(Exception(json.optString("description", "টপিক তৈরি করা যায়নি")))
            }

            val resultObj = json.getJSONObject("result")
            val newTopicId = resultObj.getLong("message_thread_id")
            storeTopicId(context, deviceId, newTopicId)

            // Send introductory merchant profile card to the newly created topic
            sendInitialProfileCard(chatId, newTopicId, deviceId, config, appVersion, licenseStatus)

            // Register topic into Supabase support_threads table if connected
            registerThreadToSupabase(deviceId, newTopicId, config, appVersion, licenseStatus)

            Result.success(newTopicId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendInitialProfileCard(
        chatId: String,
        topicId: Long,
        deviceId: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ) {
        try {
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
            val cardText = """
                🛍️ <b>নতুন গ্রাহক সাপোর্ট রিকোয়েস্ট</b>
                ━━━━━━━━━━━━━━━━━━━━
                🏪 <b>দোকান:</b> ${config.shopName.ifBlank { "নাম দেওয়া হয়নি" }}
                📞 <b>মোবাইল:</b> ${config.shopPhone.ifBlank { "ফোন দেওয়া হয়নি" }}
                📍 <b>ঠিকানা:</b> ${config.shopAddress.ifBlank { "ঠিকানা দেওয়া হয়নি" }}
                🔑 <b>ডিভাইস আইডি:</b> <code>$deviceId</code>
                📱 <b>অ্যাপ সংস্করণ:</b> v$appVersion
                🛡️ <b>লাইসেন্স:</b> $licenseStatus
                ⏰ <b>সময়:</b> $dateStr
                ━━━━━━━━━━━━━━━━━━━━
                <i>গ্রাহক অ্যাপ থেকে চ্যাট শুরু করেছেন। এই টপিকে রিপ্লাই দিলে তা সরাসরি গ্রাহকের অ্যাপে চলে যাবে।</i>
            """.trimIndent()

            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("message_thread_id", topicId)
                put("text", cardText)
                put("parse_mode", "HTML")
            }

            val req = Request.Builder()
                .url("$TELEGRAM_API_BASE/sendMessage")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    suspend fun sendMessage(
        context: Context,
        deviceId: String,
        text: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ): Result<SupportChatMessage> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(Exception("মেসেজ খালি হতে পারে না"))

        val pendingMsg = SupportChatMessage(
            sender = "user",
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            isSending = true
        )

        // Add to local UI flow immediately
        appendLocalMessage(context, pendingMsg)

        val chatId = getSupportChatId(context)
        if (chatId.isBlank()) {
            updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
            return@withContext Result.failure(Exception("সাপোর্ট গ্রুপ চ্যাট আইডি কনফিগার করা হয়নি। দয়া করে সেটিংস থেকে আইডি সেট করুন।"))
        }

        val topicResult = getOrCreateForumTopic(context, deviceId, config, appVersion, licenseStatus)
        if (topicResult.isFailure) {
            updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
            return@withContext Result.failure(topicResult.exceptionOrNull() ?: Exception("টপিক তৈরিতে ত্রুটি"))
        }

        val topicId = topicResult.getOrThrow()

        try {
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("message_thread_id", topicId)
                put("text", "👤 <b>গ্রাহক:</b>\n$trimmed")
                put("parse_mode", "HTML")
            }

            val req = Request.Builder()
                .url("$TELEGRAM_API_BASE/sendMessage")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
                return@withContext Result.failure(Exception("মেসেজ পাঠাতে ব্যর্থ: $body"))
            }

            // Successfully sent to Telegram
            val delivered = pendingMsg.copy(isSending = false, isFailed = false)
            updateMessage(context, delivered)

            // Also post to Supabase support_messages table if connected
            postMessageToSupabase(deviceId, topicId, "user", trimmed)

            Result.success(delivered)
        } catch (e: Exception) {
            updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
            Result.failure(e)
        }
    }

    suspend fun syncMessagesFromSupabase(context: Context, deviceId: String) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConnected) return@withContext
        _isSyncing.value = true

        try {
            val url = "${SupabaseConfig.url}/rest/v1/support_messages?device_id=eq.$deviceId&order=created_at.asc"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()

            if (resp.isSuccessful && body.isNotBlank() && body.startsWith("[")) {
                val array = JSONArray(body)
                val fetched = mutableListOf<SupportChatMessage>()
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val sender = obj.optString("sender", "support")
                    val msgText = obj.optString("message_text", "")
                    val createdAtStr = obj.optString("created_at", "")
                    val timeMillis = try {
                        sdf.parse(createdAtStr.take(19))?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    fetched.add(
                        SupportChatMessage(
                            id = id,
                            sender = sender,
                            text = msgText,
                            timestamp = timeMillis,
                            isSending = false,
                            isFailed = false
                        )
                    )
                }

                if (fetched.isNotEmpty()) {
                    mergeMessages(context, fetched)
                }
            }
        } catch (_: Exception) {
        } finally {
            _isSyncing.value = false
        }
    }

    private fun registerThreadToSupabase(
        deviceId: String,
        topicId: Long,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ) {
        if (!SupabaseConfig.isConnected) return
        try {
            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("topic_id", topicId)
                put("shop_name", config.shopName)
                put("shop_phone", config.shopPhone)
                put("app_version", appVersion)
                put("license_status", licenseStatus)
            }

            val req = Request.Builder()
                .url("${SupabaseConfig.url}/rest/v1/support_threads")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun postMessageToSupabase(deviceId: String, topicId: Long, sender: String, text: String) {
        if (!SupabaseConfig.isConnected) return
        try {
            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("topic_id", topicId)
                put("sender", sender)
                put("message_text", text)
            }

            val req = Request.Builder()
                .url("${SupabaseConfig.url}/rest/v1/support_messages")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun loadLocalMessages(context: Context) {
        try {
            val file = File(context.filesDir, "support_chat_cache.json")
            if (!file.exists()) return
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SupportChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SupportChatMessage(
                        id = obj.getString("id"),
                        sender = obj.getString("sender"),
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp"),
                        isSending = false,
                        isFailed = obj.optBoolean("isFailed", false)
                    )
                )
            }
            _messages.value = list
        } catch (_: Exception) {}
    }

    private fun saveLocalMessages(context: Context) {
        try {
            val file = File(context.filesDir, "support_chat_cache.json")
            val array = JSONArray()
            _messages.value.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender", msg.sender)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("isFailed", msg.isFailed)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    private fun appendLocalMessage(context: Context, msg: SupportChatMessage) {
        _messages.value = _messages.value + msg
        saveLocalMessages(context)
    }

    private fun updateMessage(context: Context, updated: SupportChatMessage) {
        _messages.value = _messages.value.map { if (it.id == updated.id) updated else it }
        saveLocalMessages(context)
    }

    private fun updateMessageStatus(context: Context, id: String, isSending: Boolean, isFailed: Boolean) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(isSending = isSending, isFailed = isFailed) else it
        }
        saveLocalMessages(context)
    }

    private fun mergeMessages(context: Context, fetched: List<SupportChatMessage>) {
        val existingMap = _messages.value.associateBy { it.id }.toMutableMap()
        fetched.forEach { existingMap[it.id] = it }
        _messages.value = existingMap.values.sortedBy { it.timestamp }
        saveLocalMessages(context)
    }
}
