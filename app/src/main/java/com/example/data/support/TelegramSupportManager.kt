package com.example.data.support

import android.content.Context
import android.util.Log
import com.example.ui.ShopConfig
import com.example.util.AppNotificationHelper
import com.example.util.SecureStore
import com.example.util.SoundHelper
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

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

/**
 * Live support chat. The phone never talks to Telegram directly: the Cloud Functions in
 * firebase/functions/support.js hold the bot token and relay messages. Each phone gets its own
 * support thread protected by a random key (stored encrypted), so shops can't read each other's chats.
 */
object TelegramSupportManager {

    @Volatile
    var isLiveSupportActive: Boolean = false

    private const val TAG = "SupportChat"
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundJob: Job? = null
    private val threadMutex = Mutex()
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance("asia-southeast1") }

    private const val PREFS_NAME = "dokan_telegram_support_prefs"
    private const val KEY_THREAD_ID = "support_thread_id"
    private const val KEY_THREAD_KEY = "support_thread_key"
    private const val KEY_LAST_FETCH_AT = "support_last_fetch_server_ts"
    private const val KEY_DISMISSED_NOTIF_IDS = "dismissed_notification_ids"
    private const val KEY_NOTIFS_LAST_CLEARED_AT = "notifications_last_cleared_at"
    private const val KEY_INITIAL_BROADCAST_SYNC_DONE = "initial_broadcast_sync_done"

    /** Poll fast only while the chat screen is open; otherwise every 60s keeps server cost low. */
    private const val POLL_ACTIVE_MS = 5_000L
    private const val POLL_IDLE_MS = 60_000L

    private val dismissedNotificationIds = mutableSetOf<String>()

    private val _messages = MutableStateFlow<List<SupportChatMessage>>(emptyList())
    val messages: StateFlow<List<SupportChatMessage>> = _messages.asStateFlow()

    private val _notifications = MutableStateFlow<List<SupportNotification>>(emptyList())
    val notifications: StateFlow<List<SupportNotification>> = _notifications.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Kept for the settings UI; the support group is now fixed on the server. */
    private val _configuredChatId = MutableStateFlow("server")
    val configuredChatId: StateFlow<String> = _configuredChatId.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        AppNotificationHelper.createNotificationChannel(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        SecureStore.migrate(prefs, KEY_THREAD_KEY)

        val firstInstallTime = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        // Fresh install protection: broadcasts sent before installation never pop up.
        val lastCleared = prefs.getLong(KEY_NOTIFS_LAST_CLEARED_AT, 0L)
        if (!prefs.contains(KEY_NOTIFS_LAST_CLEARED_AT) || lastCleared < firstInstallTime) {
            prefs.edit().putLong(KEY_NOTIFS_LAST_CLEARED_AT, maxOf(firstInstallTime, System.currentTimeMillis())).apply()
        }

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
                    syncAllMessages(appContext, "")
                } catch (_: Exception) {}
                // Wake up quickly if the chat screen gets opened during an idle wait.
                var waited = 0L
                while (waited < POLL_IDLE_MS && !isLiveSupportActive) {
                    delay(1_000L)
                    waited += 1_000L
                }
                if (isLiveSupportActive) delay(POLL_ACTIVE_MS)
            }
        }
    }

    /** No-op: the support group can no longer be redirected from the phone. */
    @Suppress("UNUSED_PARAMETER")
    fun setSupportChatId(context: Context, chatId: String) {}

    @Suppress("UNUSED_PARAMETER")
    fun getSupportChatId(context: Context): String = "server"

    private data class Thread(val id: String, val key: String)

    private fun storedThread(context: Context): Thread? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_THREAD_ID, "") ?: ""
        val key = SecureStore.getString(prefs, KEY_THREAD_KEY)
        return if (id.isNotBlank() && key.isNotBlank()) Thread(id, key) else null
    }

    private fun storeThread(context: Context, thread: Thread) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_THREAD_ID, thread.id)
        SecureStore.putString(editor, KEY_THREAD_KEY, thread.key).apply()
    }

    private suspend fun call(name: String, data: Map<String, Any?>): Map<*, *> {
        val result = withTimeout(30_000L) { functions.getHttpsCallableFromUrl(com.example.data.firebase.CloudAuthManager.endpoint(name)).call(data).awaitTask() }
        return result.getData() as? Map<*, *> ?: emptyMap<String, Any>()
    }

    private fun friendly(t: Throwable): String = when {
        t is FirebaseFunctionsException && t.code != FirebaseFunctionsException.Code.INTERNAL && !t.message.isNullOrBlank() -> t.message!!
        else -> "সাপোর্ট সার্ভিসে সংযোগ করা যাচ্ছে না। ইন্টারনেট চেক করুন।"
    }

    /** Returns this phone's support thread, opening one on the server the first time. */
    private suspend fun ensureThread(
        context: Context,
        deviceId: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String,
        announce: Boolean = false
    ): Thread = threadMutex.withLock {
        storedThread(context)?.let { return@withLock it }
        val res = call(
            "supportOpen",
            mapOf(
                "deviceId" to deviceId,
                "shopName" to config.shopName,
                "shopPhone" to config.shopPhone,
                "shopAddress" to config.shopAddress,
                "appVersion" to appVersion,
                "licenseStatus" to licenseStatus,
                "announce" to announce
            )
        )
        val thread = Thread(res["threadId"] as? String ?: error("no thread"), res["threadKey"] as? String ?: error("no key"))
        storeThread(context, thread)
        thread
    }

    suspend fun notifyNewUserSetup(
        context: Context,
        deviceId: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("setup_notified_globally", false) || storedThread(context) != null) return@withContext
        prefs.edit().putBoolean("setup_notified_globally", true).apply()
        try {
            ensureThread(context, deviceId, config, appVersion, licenseStatus, announce = true)
        } catch (t: Throwable) {
            Log.w(TAG, "setup notification failed: ${t.message}")
        }
    }

    suspend fun sendMessage(
        context: Context,
        deviceId: String,
        text: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ): Result<SupportChatMessage> = withContext(Dispatchers.IO) {
        val trimmed = text.trim().take(2000)
        if (trimmed.isEmpty()) return@withContext Result.failure(Exception("মেসেজ খালি হতে পারে না"))

        val pendingMsg = SupportChatMessage(sender = "user", text = trimmed, isSending = true)
        appendLocalMessage(context, pendingMsg)

        try {
            val thread = ensureThread(context, deviceId, config, appVersion, licenseStatus)
            val res = try {
                call("supportSend", mapOf("threadId" to thread.id, "threadKey" to thread.key, "text" to trimmed))
            } catch (e: FirebaseFunctionsException) {
                if (e.code != FirebaseFunctionsException.Code.PERMISSION_DENIED) throw e
                // Thread no longer valid on the server (e.g. reset): open a new one and retry once.
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(KEY_THREAD_ID).remove(KEY_THREAD_KEY).apply()
                val fresh = ensureThread(context, deviceId, config, appVersion, licenseStatus)
                call("supportSend", mapOf("threadId" to fresh.id, "threadKey" to fresh.key, "text" to trimmed))
            }
            // Use the server's id so the message isn't duplicated when it comes back in a fetch.
            val serverId = (res["id"] as? String)?.let { "srv_$it" } ?: pendingMsg.id
            val delivered = pendingMsg.copy(id = serverId, isSending = false, isFailed = false)
            _messages.value = _messages.value.map { if (it.id == pendingMsg.id) delivered else it }
            saveLocalMessages(context)
            Result.success(delivered)
        } catch (t: Throwable) {
            updateMessageStatus(context, pendingMsg.id, isSending = false, isFailed = true)
            Result.failure(Exception(friendly(t)))
        }
    }

    /** Pulls new support replies and platform broadcasts from the server. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun syncAllMessages(context: Context, deviceId: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstSync = !prefs.getBoolean(KEY_INITIAL_BROADCAST_SYNC_DONE, false)
        val lastClearedAt = prefs.getLong(KEY_NOTIFS_LAST_CLEARED_AT, 0L)
        val since = prefs.getLong(KEY_LAST_FETCH_AT, 0L)
        val thread = storedThread(context)

        _isSyncing.value = true
        try {
            val res = call(
                "supportFetch",
                mapOf("since" to since, "threadId" to thread?.id, "threadKey" to thread?.key)
            )
            val serverNow = (res["now"] as? Number)?.toLong() ?: return@withContext
            val incoming = mutableListOf<SupportChatMessage>()
            var hasNewIncoming = false
            val existingMsgIds = _messages.value.map { it.id }.toSet()
            val existingNotifIds = _notifications.value.map { it.id }.toSet()

            for (b in (res["broadcasts"] as? List<*>).orEmpty()) {
                val m = b as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                val text = (m["text"] as? String)?.trim().orEmpty()
                val ts = (m["ts"] as? Number)?.toLong() ?: continue
                if (text.isEmpty()) continue
                // Broadcasts from before install/clear, or already dismissed, never alert.
                if (isFirstSync || ts <= lastClearedAt || isNotificationDismissed(id)) {
                    synchronized(dismissedNotificationIds) { dismissedNotificationIds.add(id) }
                    continue
                }
                if (existingNotifIds.contains(id)) continue
                addNotification(context, SupportNotification(id = id, title = "📢 সার্বজনীন নোটিশ", message = text, timestamp = ts, isBroadcast = true))
                incoming.add(SupportChatMessage(id = id, sender = "support", text = "📢 [সার্বজনীন নোটিশ]\n$text", timestamp = ts))
                hasNewIncoming = true
                AppNotificationHelper.showSupportNotification(context, id.hashCode(), "📢 সার্বজনীন নোটিশ", text, isBroadcast = true)
            }

            for (raw in (res["messages"] as? List<*>).orEmpty()) {
                val m = raw as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                val text = (m["text"] as? String).orEmpty()
                val ts = (m["ts"] as? Number)?.toLong() ?: continue
                val sender = if (m["sender"] == "support") "support" else "user"
                incoming.add(SupportChatMessage(id = id, sender = sender, text = text, timestamp = ts))
                if (sender == "support" && !existingMsgIds.contains(id) && !isNotificationDismissed(id) && ts > lastClearedAt) {
                    hasNewIncoming = true
                    addNotification(context, SupportNotification(id = id, title = "দোকান প্রো কাস্টমার সাপোর্ট", message = text, timestamp = ts))
                    AppNotificationHelper.showSupportNotification(context, id.hashCode(), "দোকান প্রো কাস্টমার সাপোর্ট", text, isBroadcast = false)
                }
            }

            if (incoming.isNotEmpty()) mergeMessages(context, incoming)
            if (hasNewIncoming) withContext(Dispatchers.Main) { SoundHelper.playMessengerSound(context) }

            prefs.edit()
                .putLong(KEY_LAST_FETCH_AT, serverNow)
                .putBoolean(KEY_INITIAL_BROADCAST_SYNC_DONE, true)
                .putStringSet(KEY_DISMISSED_NOTIF_IDS, HashSet(synchronized(dismissedNotificationIds) { dismissedNotificationIds.toSet() }))
                .apply()
        } catch (t: Throwable) {
            Log.d(TAG, "support sync skipped: ${t.message}")
        } finally {
            _isSyncing.value = false
        }
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

/** Suspends until the Play Services task finishes. */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        val ex = task.exception
        when {
            ex != null -> cont.resumeWith(Result.failure(ex))
            task.isCanceled -> cont.cancel()
            else -> cont.resumeWith(Result.success(task.result))
        }
    }
}
