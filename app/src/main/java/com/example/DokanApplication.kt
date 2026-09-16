package com.example

import android.app.Application
import com.example.data.support.TelegramSupportManager
import com.example.util.AppNotificationHelper

class DokanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotificationHelper.createNotificationChannel(this)
        TelegramSupportManager.init(this)
    }
}
