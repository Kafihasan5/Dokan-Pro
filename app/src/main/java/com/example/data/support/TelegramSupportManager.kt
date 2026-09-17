package com.example.data.support

import android.content.Context
import com.example.data.supabase.SupabaseConfig
import com.example.ui.ShopConfig
import com.example.util.AppNotificationHelper
import com.example.util.Formatters
import com.example.util.SoundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

data class SupportNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBroadcast: Boolean = false,
    val isRead: Boolean = false
)

object TelegramSupportManager {

    @Volatile
    var isLiveSupportActive: Boolean = false

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundJob: Job? = null

    // Dedicated Telegram Bot for Dokan Pro Live Support
    const val BOT_TOKEN = "8677361782:AAFsMQlvxzOljDyaRbNxbrbDv8jTWFkadvY"
    private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot$BOT_TOKEN"

    private const val PREFS_NAME = "dokan_telegram_support_prefs"
    private const val KEY_SUPPORT_CHAT_ID = "support_group_chat_id"
    private const val KEY_TOPIC_PREFIX = "topic_id_"
    private const val KEY_DISMISSED_NOTIF_IDS = "dismissed_notification_ids"
    private const val KEY_NOTIFS_LAST_CLEARED_AT = "notifications_last_cleared_at"

    private val dismissedNotificationIds = mutableSetOf<String>()

    // Default Support Supergroup Chat ID
    const val DEFAULT_SUPPORT_CHAT_ID = "-1003954086612"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableStateFlow<List<SupportChatMessage>>(emptyList())
    val messages: StateFlow<List<SupportChatMessage>> = _messages.asStateFlow()

    private val _notifications = MutableStateFlow<List<SupportNotification>>(emptyList())
    val notifications: StateFlow<List<SupportNotification>> = _notifications.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _configuredChatId = MutableStateFlow<String>(DEFAULT_SUPPORT_CHAT_ID)
    val configuredChatId: StateFlow<String> = _configuredChatId.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        AppNotificationHelper.createNotificationChannel(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SUPPORT_CHAT_ID, "") ?: ""
        _configuredChatId.value = if (saved.isNotBlank()) saved else DEFAULT_SUPPORT_CHAT_ID

        val savedDismissed = prefs.getStringSet(KEY_DISMISSED_NOTIF_IDS, emptySet()) ?: emptySet()
        synchronized(dismissedNotificationIds) {
            dismissedNotificationIds.clear()
            dismissedNotificationIds.addAll(savedDismissed)
        }

        loadLocalMessages(appContext)
        loadLocalNotifications(appContext)
        startBackgroundPolling(appContext)
    }

    fun startBackgroundPolling(context: Context) {
        if (backgroundJob?.isActive == true) return
        val appContext = context.applicationContext
        backgroundJob = backgroundScope.launch {
            while (isActive) {
                try {
                    val deviceId = com.example.data.license.AppLicenseManager(appContext).getDeviceId()
                    syncAllMessages(appContext, deviceId)
                } catch (_: Exception) {}
                delay(5000)
            }
        }
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

        val chatId = getSupportChatId(context).ifBlank { DEFAULT_SUPPORT_CHAT_ID }
        if (chatId.isBlank()) {
            return@withContext Result.failure(Exception("সাপোর্ট সার্ভিস সাময়িকভাবে অনুপলব্ধ"))
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

    suspend fun notifyNewUserSetup(
        context: Context,
        deviceId: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyNotified = prefs.getBoolean("setup_notified_$deviceId", false)
        if (alreadyNotified) return@withContext

        try {
            // Pre-create forum topic for this device so it has a valid topic ID ready
            val topicResult = getOrCreateForumTopic(context, deviceId, config, appVersion, licenseStatus)
            val topicId = topicResult.getOrNull() ?: 0L

            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
            val shopName = config.shopName.ifBlank { "দোকান" }
            val shopPhone = config.shopPhone.ifBlank { "ফোন দেওয়া হয়নি" }
            val shopAddress = config.shopAddress.ifBlank { "ঠিকানা দেওয়া হয়নি" }

            val generalMessage = """
                🎉 <b>নতুন গ্রাহক অ্যাপ সেটআপ সম্পন্ন করেছেন!</b>
                ━━━━━━━━━━━━━━━━━━━━
                🏪 <b>দোকান:</b> $shopName
                📞 <b>মোবাইল:</b> $shopPhone
                📍 <b>ঠিকানা:</b> $shopAddress
                🔑 <b>ডিভাইস আইডি:</b> <code>$deviceId</code>
                📱 <b>অ্যাপ সংস্করণ:</b> v$appVersion
                🛡️ <b>লাইসেন্স:</b> $licenseStatus
                ⏰ <b>সময়:</b> $dateStr
                ━━━━━━━━━━━━━━━━━━━━
                <i>নিচের বাটনে ক্লিক করে সরাসরি এই গ্রাহকের চ্যাট টপিকে প্রবেশ করুন ও মেসেজ পাঠান।</i>
            """.trimIndent()

            val cleanChatId = DEFAULT_SUPPORT_CHAT_ID.removePrefix("-100")
            val topicUrl = if (topicId > 0) "https://t.me/c/$cleanChatId/$topicId" else "https://t.me/c/$cleanChatId"

            val payload = JSONObject().apply {
                put("chat_id", DEFAULT_SUPPORT_CHAT_ID)
                put("text", generalMessage)
                put("parse_mode", "HTML")
                put("reply_markup", JSONObject().apply {
                    put("inline_keyboard", JSONArray().apply {
                        put(JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "💬 চ্যাট টপিক ওপেন করুন")
                                put("url", topicUrl)
                            })
                        })
                    })
                })
            }

            val req = Request.Builder()
                .url("$TELEGRAM_API_BASE/sendMessage")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                prefs.edit().putBoolean("setup_notified_$deviceId", true).apply()
            }
            resp.close()
        } catch (_: Exception) {}
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

        val chatId = getSupportChatId(context).ifBlank { DEFAULT_SUPPORT_CHAT_ID }
        if (chatId.isBlank()) {
            updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
            return@withContext Result.failure(Exception("সাপোর্ট সার্ভিস সাময়িকভাবে অনুপলব্ধ"))
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

    suspend fun syncMessagesFromTelegram(context: Context, deviceId: String) = withContext(Dispatchers.IO) {
        val topicId = getStoredTopicId(context, deviceId)
        _isSyncing.value = true

        try {
            val req = Request.Builder()
                .url("$TELEGRAM_API_BASE/getUpdates?offset=-100&allowed_updates=[\"message\"]")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()

            if (resp.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("ok", false)) {
                    val results = json.optJSONArray("result") ?: JSONArray()
                    val incomingReplies = mutableListOf<SupportChatMessage>()
                    var hasNewIncoming = false

                    val existingMsgIds = _messages.value.map { it.id }.toSet()
                    val existingNotifIds = _notifications.value.map { it.id }.toSet()

                    for (i in 0 until results.length()) {
                        val update = results.getJSONObject(i)
                        val message = update.optJSONObject("message") ?: continue

                        val fromObj = message.optJSONObject("from")
                        val isBot = fromObj?.optBoolean("is_bot", false) ?: false
                        if (isBot) continue

                        val text = message.optString("text", "").trim()
                        if (text.isEmpty()) continue

                        val threadId = message.optLong("message_thread_id", -1L)
                        val replyTo = message.optJSONObject("reply_to_message")
                        val replyThreadId = replyTo?.optLong("message_thread_id", -1L) ?: -1L
                        val dateSec = message.optLong("date", System.currentTimeMillis() / 1000)

                        // 1. Check for Broadcast Messages from General Tab (#all, /all, @all, !all, all:, broadcast, notice, নোটিশ, ইত্যাদি)
                        val trimmedLower = text.trim().lowercase(Locale.ROOT)
                        val isBroadcastCmd = trimmedLower.startsWith("/all") ||
                                trimmedLower.startsWith("#all") ||
                                trimmedLower.startsWith("@all") ||
                                trimmedLower.startsWith("!all") ||
                                trimmedLower.startsWith("all:") ||
                                trimmedLower.startsWith("all ") ||
                                trimmedLower.startsWith("/broadcast") ||
                                trimmedLower.startsWith("#broadcast") ||
                                trimmedLower.startsWith("/notice") ||
                                trimmedLower.startsWith("#notice") ||
                                trimmedLower.startsWith("notice:") ||
                                trimmedLower.startsWith("/সব") ||
                                trimmedLower.startsWith("#সব") ||
                                trimmedLower.startsWith("সব:") ||
                                trimmedLower.startsWith("নোটিশ") ||
                                trimmedLower.startsWith("ঘোষণা")

                        val isGeneralTab = (threadId <= 1L)

                        if (isBroadcastCmd || isGeneralTab) {
                            val cleanNotice = when {
                                trimmedLower.startsWith("/all") -> text.substringAfter("/all", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("#all") -> text.substringAfter("#all", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("@all") -> text.substringAfter("@all", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("!all") -> text.substringAfter("!all", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("all:") -> text.substringAfter("all:", "").trim()
                                trimmedLower.startsWith("all ") -> text.substringAfter("all ", "").trim()
                                trimmedLower.startsWith("/broadcast") -> text.substringAfter("/broadcast", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("#broadcast") -> text.substringAfter("#broadcast", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("/notice") -> text.substringAfter("/notice", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("#notice") -> text.substringAfter("#notice", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("notice:") -> text.substringAfter("notice:", "").trim()
                                trimmedLower.startsWith("/সব") -> text.substringAfter("/সব", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("#সব") -> text.substringAfter("#সব", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("সব:") -> text.substringAfter("সব:", "").trim()
                                trimmedLower.startsWith("নোটিশ") -> text.substringAfter("নোটিশ", "").trim().removePrefix(":").trim()
                                trimmedLower.startsWith("ঘোষণা") -> text.substringAfter("ঘোষণা", "").trim().removePrefix(":").trim()
                                else -> text
                            }.ifBlank { text }

                            val rawMsgId = message.optInt("message_id", 1)
                            val bId = "broadcast_${message.optLong("message_id")}"
                            val msgTimeMs = dateSec * 1000L
                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            val lastClearedAt = prefs.getLong(KEY_NOTIFS_LAST_CLEARED_AT, 0L)
                            val isDismissed = isNotificationDismissed(bId) || (msgTimeMs <= lastClearedAt)

                            if (cleanNotice.isNotEmpty() && !existingNotifIds.contains(bId) && !isDismissed) {
                                val notif = SupportNotification(
                                    id = bId,
                                    title = "📢 সার্বজনীন নোটিশ",
                                    message = cleanNotice,
                                    timestamp = msgTimeMs,
                                    isBroadcast = true,
                                    isRead = false
                                )
                                addNotification(context, notif)
                                incomingReplies.add(
                                    SupportChatMessage(
                                        id = bId,
                                        sender = "support",
                                        text = "📢 [সার্বজনীন নোটিশ]\n$cleanNotice",
                                        timestamp = msgTimeMs,
                                        isSending = false,
                                        isFailed = false
                                    )
                                )
                                hasNewIncoming = true

                                // Post system notification in phone's notification bar
                                AppNotificationHelper.showSupportNotification(
                                    context = context,
                                    notificationId = rawMsgId,
                                    title = "📢 সার্বজনীন নোটিশ",
                                    message = cleanNotice,
                                    isBroadcast = true
                                )
                            }
                            continue
                        }

                        // 2. Check for Direct Customer Topic Replies
                        val isOurTopic = (topicId > 0L && (threadId == topicId || replyThreadId == topicId))
                        if (!isOurTopic) continue

                        val msgId = "tg_${message.optLong("message_id")}"
                        val msgTimeMs = dateSec * 1000L
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val lastClearedAt = prefs.getLong(KEY_NOTIFS_LAST_CLEARED_AT, 0L)
                        val isTopicNotifDismissed = isNotificationDismissed(msgId) || (msgTimeMs <= lastClearedAt)

                        val isNew = !existingMsgIds.contains(msgId)

                        val chatMsg = SupportChatMessage(
                            id = msgId,
                            sender = "support",
                            text = text,
                            timestamp = msgTimeMs,
                            isSending = false,
                            isFailed = false
                        )
                        incomingReplies.add(chatMsg)

                        if (isNew && !isTopicNotifDismissed && !existingNotifIds.contains(msgId)) {
                            hasNewIncoming = true
                            val notif = SupportNotification(
                                id = msgId,
                                title = "দোকান প্রো কাস্টমার সাপোর্ট",
                                message = text,
                                timestamp = msgTimeMs,
                                isBroadcast = false,
                                isRead = false
                            )
                            addNotification(context, notif)

                            // Post system notification in phone's notification bar
                            val rawMsgId = message.optInt("message_id", (System.currentTimeMillis() % 100000).toInt())
                            AppNotificationHelper.showSupportNotification(
                                context = context,
                                notificationId = rawMsgId,
                                title = "দোকান প্রো কাস্টমার সাপোর্ট",
                                message = text,
                                isBroadcast = false
                            )
                        }
                    }

                    if (incomingReplies.isNotEmpty()) {
                        mergeMessages(context, incomingReplies)
                    }

                    // Play Messenger chime if new message received
                    if (hasNewIncoming) {
                        withContext(Dispatchers.Main) {
                            SoundHelper.playMessengerSound(context)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncAllMessages(context: Context, deviceId: String) = withContext(Dispatchers.IO) {
        syncMessagesFromTelegram(context, deviceId)
        if (SupabaseConfig.isConnected) {
            syncMessagesFromSupabase(context, deviceId)
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

    private fun loadLocalNotifications(context: Context) {
        try {
            val file = File(context.filesDir, "support_notifications.json")
            if (!file.exists()) return
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SupportNotification>()
            var unread = 0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val isRead = obj.optBoolean("isRead", false)
                if (!isRead) unread++
                list.add(
                    SupportNotification(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        message = obj.getString("message"),
                        timestamp = obj.getLong("timestamp"),
                        isBroadcast = obj.optBoolean("isBroadcast", false),
                        isRead = isRead
                    )
                )
            }
            _notifications.value = list
            _unreadNotificationCount.value = unread
        } catch (_: Exception) {}
    }

    private fun saveLocalNotifications(context: Context) {
        try {
            val file = File(context.filesDir, "support_notifications.json")
            val array = JSONArray()
            _notifications.value.forEach { notif ->
                val obj = JSONObject().apply {
                    put("id", notif.id)
                    put("title", notif.title)
                    put("message", notif.message)
                    put("timestamp", notif.timestamp)
                    put("isBroadcast", notif.isBroadcast)
                    put("isRead", notif.isRead)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    private fun addNotification(context: Context, notif: SupportNotification) {
        val current = _notifications.value.filter { it.id != notif.id }
        _notifications.value = listOf(notif) + current
        _unreadNotificationCount.value = _notifications.value.count { !it.isRead }
        saveLocalNotifications(context)
    }

    fun isNotificationDismissed(id: String): Boolean {
        return synchronized(dismissedNotificationIds) {
            dismissedNotificationIds.contains(id)
        }
    }

    fun dismissNotification(context: Context, id: String) {
        val appContext = context.applicationContext
        synchronized(dismissedNotificationIds) {
            dismissedNotificationIds.add(id)
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_DISMISSED_NOTIF_IDS, HashSet(dismissedNotificationIds)).apply()

        _notifications.value = _notifications.value.filter { it.id != id }
        _unreadNotificationCount.value = _notifications.value.count { !it.isRead }
        saveLocalNotifications(appContext)

        val rawMsgId = id.substringAfter("_").toIntOrNull() ?: 1
        AppNotificationHelper.cancelNotification(appContext, rawMsgId)
    }

    fun markAllNotificationsRead(context: Context) {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadNotificationCount.value = 0
        saveLocalNotifications(context)
    }

    fun clearAllNotifications(context: Context) {
        val appContext = context.applicationContext
        val currentIds = _notifications.value.map { it.id }
        synchronized(dismissedNotificationIds) {
            dismissedNotificationIds.addAll(currentIds)
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_DISMISSED_NOTIF_IDS, HashSet(dismissedNotificationIds))
            .putLong(KEY_NOTIFS_LAST_CLEARED_AT, System.currentTimeMillis())
            .apply()

        _notifications.value = emptyList()
        _unreadNotificationCount.value = 0
        saveLocalNotifications(appContext)

        AppNotificationHelper.clearAllNotifications(appContext)
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
        var hasNew = false
        fetched.forEach {
            if (!existingMap.containsKey(it.id)) {
                existingMap[it.id] = it
                hasNew = true
            }
        }
        if (hasNew) {
            _messages.value = existingMap.values.sortedBy { it.timestamp }
            saveLocalMessages(context)
        }
    }
}
