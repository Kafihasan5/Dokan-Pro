package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdater {
    // Connected to GitHub repository for in-app updates
    private const val GITHUB_REPO = "Kafihasan5/Dokan-Pro"
    private val RAW_VERSION_URL: String
        get() = if (GITHUB_REPO.isNotBlank()) "https://raw.githubusercontent.com/$GITHUB_REPO/main/version.json" else ""
    private val GITHUB_RELEASES_API: String
        get() = if (GITHUB_REPO.isNotBlank()) "https://api.github.com/repos/$GITHUB_REPO/releases/latest" else ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (GITHUB_REPO.isBlank()) return@withContext null
        // 1. Check version.json on GitHub (fast, no rate limits)
        try {
            val request = Request.Builder()
                .url(RAW_VERSION_URL)
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val remoteVersionCode = json.optInt("versionCode", 0)
                        val remoteVersionName = json.optString("versionName", "")
                        val downloadUrl = json.optString("downloadUrl", "")
                        val releaseNotes = json.optString("releaseNotes", "")

                        if (remoteVersionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotEmpty()) {
                            return@withContext AppUpdateInfo(
                                versionCode = remoteVersionCode,
                                versionName = remoteVersionName,
                                downloadUrl = downloadUrl,
                                releaseNotes = releaseNotes
                            )
                        } else if (remoteVersionCode <= BuildConfig.VERSION_CODE && remoteVersionCode > 0) {
                            return@withContext null
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to releases API
        }

        // 2. Fallback: GitHub Releases API
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Dokan-Pro-App")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name", "")
                        val releaseName = json.optString("name", tagName)
                        val notes = json.optString("body", "")
                        val remoteVersionCode = tagName.removePrefix("v").toIntOrNull() ?: 0

                        val assets = json.optJSONArray("assets")
                        var downloadUrl = ""
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                        }

                        if (remoteVersionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotEmpty()) {
                            return@withContext AppUpdateInfo(
                                versionCode = remoteVersionCode,
                                versionName = releaseName,
                                downloadUrl = downloadUrl,
                                releaseNotes = notes
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updateDir, "dokan_pro_update.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalRead * 100) / contentLength).toInt()
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }
                apkFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
