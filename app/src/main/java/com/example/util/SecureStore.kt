package com.example.util

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (PINs, support keys) before they go into SharedPreferences.
 * The AES key lives in the Android Keystore and never leaves the device, so a copied
 * preferences file (backup, rooted phone, adb) does not reveal the values.
 *
 * Values written before this existed are plaintext; [getString] returns them as-is and
 * [migrate] re-saves them encrypted.
 */
object SecureStore {
    private const val TAG = "SecureStore"
    private const val KEY_ALIAS = "dokan_pro_prefs_key"
    private const val PREFIX = "enc1:"
    private const val GCM_TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "encrypt failed", t)
            throw t
        }
    }

    /** Returns the decrypted value, the legacy plaintext value, or "" if it can't be decrypted. */
    fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Key lost (e.g. data restored onto another phone): treat as not set.
            Log.w(TAG, "decrypt failed: ${t.message}")
            ""
        }
    }

    fun getString(prefs: SharedPreferences, key: String, default: String = ""): String {
        val stored = prefs.getString(key, null) ?: return default
        return decrypt(stored).ifEmpty { if (stored.startsWith(PREFIX)) default else stored }
    }

    fun putString(editor: SharedPreferences.Editor, key: String, value: String): SharedPreferences.Editor {
        if (value.isEmpty()) return editor.putString(key, "")
        return try {
            editor.putString(key, encrypt(value))
        } catch (_: Throwable) {
            // A broken Keystore must not stop the shop from working; keep the value usable.
            editor.putString(key, value)
        }
    }

    /** Re-saves plaintext values of [keys] in encrypted form. */
    fun migrate(prefs: SharedPreferences, vararg keys: String) {
        val editor = prefs.edit()
        var changed = false
        for (k in keys) {
            val v = prefs.getString(k, null) ?: continue
            if (v.isNotEmpty() && !v.startsWith(PREFIX)) {
                try {
                    editor.putString(k, encrypt(v))
                    changed = true
                } catch (_: Throwable) {
                    // Keystore unavailable on this device; leave the value readable rather than lose it.
                }
            }
        }
        if (changed) editor.apply()
    }
}
