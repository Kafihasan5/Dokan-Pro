package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Server-verified shop sessions.
 *
 * PINs are never checked on the phone any more: the `shopLogin` Cloud Function verifies them and
 * returns a Firebase custom token whose claims ({shopCode, role, staffId}) the database rules enforce.
 * Firebase Auth keeps the session on the device, so the app keeps syncing after restarts without
 * asking for the PIN again.
 */
class CloudAuthManager {

    data class Session(
        val shopCode: String,
        val role: String,
        val staffId: String?,
        val name: String,
        val pinChangeRequired: Boolean
    )

    sealed class AuthResult {
        data class Success(val session: Session) : AuthResult()
        /** Wrong code/e-mail or PIN, or the account is locked. [message] is safe to show. */
        data class Rejected(val message: String, val code: String) : AuthResult()
        data class NetworkError(val message: String) : AuthResult()
    }

    private val tag = "CloudAuth"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(REGION) }

    companion object {
        const val REGION = "asia-southeast1"
        /** Server endpoints (Cloudflare Worker; see firebase/functions/cloudflare). Same callable protocol as Cloud Functions. */
        const val API_BASE = "https://dokan-pro-api.dokanpro.workers.dev/api"
        fun endpoint(name: String) = java.net.URL("$API_BASE/$name")
        private val BN_DIGITS = "০১২৩৪৫৬৭৮৯"
        private val WEAK_PINS = setOf("0000", "1111", "1234", "4321", "1122", "2222", "9999", "000000", "123456", "111111", "654321")

        fun normalizePin(pin: String): String =
            pin.trim().map { c -> BN_DIGITS.indexOf(c).let { if (it >= 0) ('0' + it) else c } }.joinToString("")

        fun isValidPin(pin: String): Boolean = Regex("^[0-9]{4,12}$").matches(pin)

        fun isWeakPin(pin: String): Boolean = pin in WEAK_PINS || Regex("^(\\d)\\1+$").matches(pin)
    }

    /** Current session from the signed-in user's token claims, or null. */
    suspend fun currentSession(forceRefresh: Boolean = false): Session? = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext null
        try {
            val claims = withTimeout(15000L) { user.getIdToken(forceRefresh).awaitTask() }.claims
            val shopCode = claims["shopCode"] as? String ?: return@withContext null
            val role = claims["role"] as? String ?: return@withContext null
            Session(
                shopCode = shopCode,
                role = role,
                staffId = claims["staffId"] as? String,
                name = claims["name"] as? String ?: if (role == "owner") "মালিক" else "কর্মচারী",
                pinChangeRequired = claims["pinChangeRequired"] == true
            )
        } catch (t: Throwable) {
            // Offline: the cached token may be expired; the SDK refreshes it once the network is back.
            Log.w(tag, "Could not read session claims: ${t.message}")
            null
        }
    }

    /** True when a session for [shopCode]/[role] exists on this device (no network needed). */
    fun hasLocalSession(): Boolean = auth.currentUser != null

    suspend fun login(identifier: String, rawPin: String, role: String): AuthResult =
        callForToken("shopLogin", mapOf("identifier" to identifier.trim(), "pin" to normalizePin(rawPin), "role" to role))

    /** Checks only whether a validly licensed device's owner email has a cloud shop. */
    suspend fun findExistingOwnerShop(email: String, deviceId: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            val result = withTimeout(12_000L) {
                functions.getHttpsCallableFromUrl(endpoint("findExistingOwnerShop"))
                    .call(mapOf("email" to email.trim().lowercase(), "deviceId" to deviceId.trim()))
                    .awaitTask()
            }
            (result.getData() as? Map<*, *>)?.get("hasExistingShop") as? Boolean
        } catch (t: Throwable) {
            Log.w(tag, "Owner shop lookup deferred: ${t.message}")
            null
        }
    }

    suspend fun registerShop(shopCode: String, rawPin: String, ownerEmail: String, shopName: String, licenseEmail: String = ""): AuthResult =
        callForToken(
            "registerShop",
            mapOf(
                "shopCode" to shopCode,
                "pin" to normalizePin(rawPin),
                "ownerEmail" to ownerEmail.trim(),
                "licenseEmail" to licenseEmail.trim(),
                "shopName" to shopName.trim()
            )
        )

    suspend fun changeOwnerPin(oldPin: String, newPin: String): AuthResult =
        callForToken("changeOwnerPin", mapOf("oldPin" to normalizePin(oldPin), "newPin" to normalizePin(newPin)))

    /** Returns null on success, otherwise a user-facing error. */
    suspend fun setStaffPin(staffKey: String, pin: String): String? =
        callSimple("setStaffPin", mapOf("staffId" to staffKey, "pin" to normalizePin(pin)))

    suspend fun setSharedStaffPin(pin: String?): String? =
        callSimple("setSharedStaffPin", mapOf("pin" to (pin?.let { normalizePin(it) } ?: "")))

    suspend fun removeStaff(staffKey: String): String? = callSimple("removeStaff", mapOf("staffId" to staffKey))

    fun signOut() {
        try { auth.signOut() } catch (_: Throwable) {}
    }

    private suspend fun callForToken(name: String, data: Map<String, Any>): AuthResult = withContext(Dispatchers.IO) {
        try {
            val result = withTimeout(30000L) { functions.getHttpsCallableFromUrl(endpoint(name)).call(data).awaitTask() }
            val token = (result.getData() as? Map<*, *>)?.get("token") as? String
                ?: return@withContext AuthResult.NetworkError("সার্ভার থেকে সঠিক উত্তর পাওয়া যায়নি।")
            withTimeout(20000L) { auth.signInWithCustomToken(token).awaitTask() }
            val session = currentSession(forceRefresh = false)
                ?: return@withContext AuthResult.NetworkError("লগইন সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।")
            AuthResult.Success(session)
        } catch (e: FirebaseFunctionsException) {
            mapFunctionsError(e)
        } catch (t: Throwable) {
            Log.w(tag, "$name failed: ${t.message}")
            AuthResult.NetworkError("সার্ভারের সাথে সংযোগ করা যাচ্ছে না। ইন্টারনেট সংযোগ যাচাই করুন।")
        }
    }

    private suspend fun callSimple(name: String, data: Map<String, Any>): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(30000L) { functions.getHttpsCallableFromUrl(endpoint(name)).call(data).awaitTask() }
            null
        } catch (e: FirebaseFunctionsException) {
            when (val r = mapFunctionsError(e)) {
                is AuthResult.Rejected -> r.message
                is AuthResult.NetworkError -> r.message
                else -> null
            }
        } catch (t: Throwable) {
            "সার্ভারের সাথে সংযোগ করা যাচ্ছে না। ইন্টারনেট সংযোগ যাচাই করুন।"
        }
    }

    private fun mapFunctionsError(e: FirebaseFunctionsException): AuthResult = when (e.code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.INTERNAL,
        FirebaseFunctionsException.Code.UNKNOWN ->
            AuthResult.NetworkError(e.message?.takeIf { e.code == FirebaseFunctionsException.Code.UNAVAILABLE && it.isNotBlank() }
                ?: "সার্ভারের সাথে সংযোগ করা যাচ্ছে না। ইন্টারনেট সংযোগ যাচাই করুন।")
        else -> AuthResult.Rejected(e.message ?: "অনুরোধটি গ্রহণ করা যায়নি।", e.code.name)
    }
}

/** Suspends until the Play Services task finishes (no extra coroutines-play-services dependency). */
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
