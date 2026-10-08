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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SupportChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "support"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSending: Boolean = false,
    val isFailed: Boolean = false,
    /** "photo" or "video" when the message carries media. */
    val mediaType: String? = null,
    /** Local copy of the media once uploaded or downloaded. */
    val localPath: String? = null
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

    /** Server limit for one photo/video. */
    const val MAX_MEDIA_BYTES = 10L * 1024 * 1024
    private const val MAX_PHOTO_SIDE = 1600
    private val downloadsInFlight = mutableSetOf<String>()
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private fun mediaDir(context: Context): File =
        File(context.filesDir, "support_media").apply { mkdirs() }

    private fun mediaLabel(type: String?) = if (type == "video") "🎬 ভিডিও" else "📷 ছবি"

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
                mapOf("since" to since, "threadId" to thread?.id, "threadKey" to thread?.key, "media" to true)
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
                val media = m["media"] as? String
                if (text.isEmpty() && media == null) continue
                val notifText = text.ifEmpty { mediaLabel(media) }
                // Broadcasts from before install/clear, or already dismissed, never alert.
                if (isFirstSync || ts <= lastClearedAt || isNotificationDismissed(id)) {
                    synchronized(dismissedNotificationIds) { dismissedNotificationIds.add(id) }
                    continue
                }
                if (existingNotifIds.contains(id)) continue
                addNotification(context, SupportNotification(id = id, title = "📢 সার্বজনীন নোটিশ", message = notifText, timestamp = ts, isBroadcast = true))
                incoming.add(SupportChatMessage(id = id, sender = "support", text = "📢 [সার্বজনীন নোটিশ]\n$text".trimEnd(), timestamp = ts, mediaType = media))
                hasNewIncoming = true
                AppNotificationHelper.showSupportNotification(context, id.hashCode(), "📢 সার্বজনীন নোটিশ", notifText, isBroadcast = true)
            }

            for (raw in (res["messages"] as? List<*>).orEmpty()) {
                val m = raw as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                val text = (m["text"] as? String).orEmpty()
                val ts = (m["ts"] as? Number)?.toLong() ?: continue
                val sender = if (m["sender"] == "support") "support" else "user"
                val media = m["media"] as? String
                incoming.add(SupportChatMessage(id = id, sender = sender, text = text, timestamp = ts, mediaType = media))
                if (sender == "support" && !existingMsgIds.contains(id) && !isNotificationDismissed(id) && ts > lastClearedAt) {
                    hasNewIncoming = true
                    val notifText = text.ifBlank { "${mediaLabel(media)} পাঠানো হয়েছে" }
                    addNotification(context, SupportNotification(id = id, title = "দোকান প্রো কাস্টমার সাপোর্ট", message = notifText, timestamp = ts))
                    AppNotificationHelper.showSupportNotification(context, id.hashCode(), "দোকান প্রো কাস্টমার সাপোর্ট", notifText, isBroadcast = false)
                }
            }

            if (incoming.isNotEmpty()) mergeMessages(context, incoming)
            downloadMissingMedia(context)
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

    /**
     * Sends a photo or video from the gallery. Photos are shrunk to at most 1600px (JPEG);
     * videos must already be under [MAX_MEDIA_BYTES].
     */
    suspend fun sendMedia(
        context: Context,
        deviceId: String,
        uri: Uri,
        caption: String,
        config: ShopConfig,
        appVersion: String,
        licenseStatus: String
    ): Result<SupportChatMessage> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/")
        val bytes = try {
            if (isVideo) {
                val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                } ?: -1L
                if (size > MAX_MEDIA_BYTES) {
                    return@withContext Result.failure(Exception("ভিডিও ১০ MB এর বেশি। ছোট ভিডিও (৩০ সেকেন্ডের মতো) পাঠান।"))
                }
                val data = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("read")
                if (data.size > MAX_MEDIA_BYTES) {
                    return@withContext Result.failure(Exception("ভিডিও ১০ MB এর বেশি। ছোট ভিডিও (৩০ সেকেন্ডের মতো) পাঠান।"))
                }
                data
            } else {
                compressPhoto(context, uri) ?: error("decode")
            }
        } catch (t: Throwable) {
            return@withContext Result.failure(Exception("ফাইলটি খোলা যায়নি।"))
        }

        val ext = if (isVideo) (if (mime.contains("webm")) "webm" else "mp4") else "jpg"
        val local = File(mediaDir(context), "out_${UUID.randomUUID()}.$ext").apply { writeBytes(bytes) }
        val pending = SupportChatMessage(
            sender = "user",
            text = caption.trim().take(1000),
            isSending = true,
            mediaType = if (isVideo) "video" else "photo",
            localPath = local.absolutePath
        )
        appendLocalMessage(context, pending)

        try {
            val thread = ensureThread(context, deviceId, config, appVersion, licenseStatus)
            val serverId = try {
                upload(thread, bytes, pending.text)
            } catch (e: UploadException) {
                if (e.status != 403) throw e
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(KEY_THREAD_ID).remove(KEY_THREAD_KEY).apply()
                upload(ensureThread(context, deviceId, config, appVersion, licenseStatus), bytes, pending.text)
            }
            val delivered = pending.copy(id = "srv_$serverId", isSending = false, isFailed = false)
            _messages.value = _messages.value.map { if (it.id == pending.id) delivered else it }
            saveLocalMessages(context)
            Result.success(delivered)
        } catch (t: Throwable) {
            updateMessageStatus(context, pending.id, isSending = false, isFailed = true)
            Result.failure(Exception((t as? UploadException)?.message ?: friendly(t)))
        }
    }

    private class UploadException(val status: Int, message: String) : Exception(message)

    private fun upload(thread: Thread, bytes: ByteArray, caption: String): String {
        val request = Request.Builder()
            .url(com.example.data.firebase.CloudAuthManager.endpoint("supportUpload"))
            .header("X-Thread-Id", thread.id)
            .header("X-Thread-Key", thread.key)
            .header("X-Caption", URLEncoder.encode(caption, "UTF-8"))
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newCall(request).execute().use { res ->
            val body = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
            if (!res.isSuccessful) {
                val msg = body.optJSONObject("error")?.optString("message").orEmpty()
                throw UploadException(res.code, msg.takeIf { it.isNotBlank() && it != "INTERNAL" } ?: "ফাইল পাঠানো যায়নি। আবার চেষ্টা করুন।")
            }
            return body.optJSONObject("result")?.optString("id").orEmpty().ifBlank { throw UploadException(500, "ফাইল পাঠানো যায়নি।") }
        }
    }

    private fun compressPhoto(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        var quality = 85
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }
            quality -= 15
        } while (out.size > MAX_MEDIA_BYTES && quality > 20)
        return out
    }

    /** Downloads photos/videos that arrived from support and aren't on the phone yet. */
    private suspend fun downloadMissingMedia(context: Context) {
        val thread = storedThread(context)
        val missing = _messages.value.filter { it.mediaType != null && it.localPath == null && !it.isSending }
        for (msg in missing) {
            val claimed = synchronized(downloadsInFlight) { downloadsInFlight.add(msg.id) }
            if (!claimed) continue
            try {
                val builder = Request.Builder()
                    .url(com.example.data.firebase.CloudAuthManager.endpoint("supportMedia"))
                    .header("X-Message-Id", msg.id)
                if (thread != null) builder.header("X-Thread-Id", thread.id).header("X-Thread-Key", thread.key)
                http.newCall(builder.get().build()).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val type = res.header("Content-Type").orEmpty()
                    val ext = when {
                        type.contains("png") -> "png"
                        type.contains("webp") -> "webp"
                        type.startsWith("image/") -> "jpg"
                        type.contains("webm") -> "webm"
                        else -> "mp4"
                    }
                    val file = File(mediaDir(context), "in_${msg.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.$ext")
                    val tmp = File(file.path + ".part")
                    res.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return@use
                    tmp.renameTo(file)
                    _messages.value = _messages.value.map { if (it.id == msg.id) it.copy(localPath = file.absolutePath) else it }
                    saveLocalMessages(context)
                }
            } catch (t: Throwable) {
                Log.d(TAG, "media download failed: ${t.message}")
            } finally {
                synchronized(downloadsInFlight) { downloadsInFlight.remove(msg.id) }
            }
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
                        isFailed = obj.optBoolean("isFailed", false),
                        mediaType = obj.optString("mediaType", "").ifBlank { null },
                        localPath = obj.optString("localPath", "").ifBlank { null }?.takeIf { File(it).exists() }
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
                    msg.mediaType?.let { put("mediaType", it) }
                    msg.localPath?.let { put("localPath", it) }
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
