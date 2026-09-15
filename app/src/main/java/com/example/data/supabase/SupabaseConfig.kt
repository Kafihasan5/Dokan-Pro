package com.example.data.supabase

import com.example.BuildConfig

object SupabaseConfig {
    // Supabase credentials used exclusively for App Activation & Licensing
    const val DEFAULT_URL = "https://fbkyfxghhzjonihnntip.supabase.co"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZia3lmeGdoaHpqb25paG5udGlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk0NDEyMTYsImV4cCI6MjEwNTAxNzIxNn0.Sn_4D24ZyVRbqa34WyV9lXZ0EBlHmqwVoSU0csRSPbo"

    // STRICT PRIVACY: Shop data (sales, products, due khata, etc.) is 100% local and NEVER synced to cloud
    const val IS_SHOP_DATA_SYNC_ENABLED = false

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
