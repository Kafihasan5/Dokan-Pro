package com.example

import android.app.Application
import android.util.Log
import com.example.data.support.TelegramSupportManager
import com.example.util.AppNotificationHelper
import java.io.File

class DokanApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Global crash guard to capture and log any unhandled thread errors
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DokanCrashGuard", "CRITICAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            try {
                val crashLog = File(filesDir, "last_crash.log")
                crashLog.writeText("Time: ${System.currentTimeMillis()}\nThread: ${thread.name}\nError: ${throwable.stackTraceToString()}")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (t: Throwable) {
            Log.e("DokanApplication", "FirebaseApp init error", t)
        }
        AppNotificationHelper.createNotificationChannel(this)
        TelegramSupportManager.init(this)
    }
}
