package com.example.data.supabase

import com.example.BuildConfig

object SupabaseConfig {
    // Disconnected for new project. Can be provided via .env if connected in the future.
    const val DEFAULT_URL = ""
    const val DEFAULT_ANON_KEY = ""

    val url: String
        get() = try {
            val configUrl = BuildConfig.SUPABASE_URL
            if (!configUrl.isNullOrBlank()) configUrl else DEFAULT_URL
        } catch (_: Throwable) {
            DEFAULT_URL
        }

    val anonKey: String
        get() = try {
            val key = BuildConfig.SUPABASE_ANON_KEY
            if (!key.isNullOrBlank()) key else DEFAULT_ANON_KEY
        } catch (_: Throwable) {
            DEFAULT_ANON_KEY
        }

    val isConnected: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank()
}
