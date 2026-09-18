package com.example

import android.app.Application
import com.example.data.support.TelegramSupportManager
import com.example.util.AppNotificationHelper

class DokanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (t: Throwable) {
            android.util.Log.e("DokanApplication", "FirebaseApp init error", t)
        }
        AppNotificationHelper.createNotificationChannel(this)
        TelegramSupportManager.init(this)
    }
}
