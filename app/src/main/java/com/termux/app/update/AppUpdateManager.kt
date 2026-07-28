package com.termux.app.update

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.termux.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object AppUpdateManager {
    private const val TAG = "FcodeAppUpdate"
    private const val PREFS = "fcode_app_update"
    private const val KEY_LAST_AUTOMATIC_CHECK = "last_automatic_check"
    private const val KEY_QUEUED_MANIFEST = "queued_manifest"
    private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
    private const val KEY_PENDING_FILE = "pending_file"
    private const val KEY_PENDING_MANIFEST = "pending_manifest"
    private const val KEY_READY_FILE = "ready_file"
    private const val KEY_READY_MANIFEST = "ready_manifest"
    private const val KEY_LAST_INSTALL_LAUNCH = "last_install_launch"
    private const val KEY_LANGUAGE = "language"
    private const val AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_MANIFEST_CHARS = 256 * 1024
    private const val READY_NOTIFICATION_ID = 0x4643
    private const val ERROR_NOTIFICATION_ID = 0x4644

    fun checkAutomatically(activity: ComponentActivity, language: String) {
        if (BuildConfig.DEBUG) return
        clearLegacyDownloadState(activity)
        val prefs = prefs(activity)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_AUTOMATIC_CHECK, 0L) < AUTOMATIC_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_AUTOMATIC_CHECK, now).apply()
        checkForUpdates(activity, language, userInitiated = false)
    }

    fun checkForUpdates(
        activity: ComponentActivity,
        language: String,
        userInitiated: Boolean = true,
    ) {
        val clearedLegacyDownload = clearLegacyDownloadState(activity)
        if (userInitiated) {
            toast(
                activity,
                if (clearedLegacyDownload) {
                    text(
                        language,
                        "已清理旧下载任务，正在通过 GitHub 检查更新…",
                        "The old download task was cleared; checking GitHub for updates…",
                    )
                } else {
                    text(language, "正在检查更新…", "Checking for updates…")
                },
            )
        }

        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(::fetchManifest) }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            result.onSuccess { manifest ->
                when {
                    manifest.minSdk > Build.VERSION.SDK_INT -> {
                        if (userInitiated) {
                            showMessage(
                                activity,
                                text(language, "暂不支持此设备", "This device is not supported"),
                                text(
                                    language,
                                    "新版本需要 Android API ${manifest.minSdk} 或更高版本。",
                                    "The new version requires Android API ${manifest.minSdk} or newer.",
                                ),
                            )
                        }
                    }
                    manifest.versionCode <= BuildConfig.VERSION_CODE.toLong() -> {
                        if (userInitiated) {
                            toast(
                                activity,
                                text(
                                    language,
                                    "已经是最新版本（${BuildConfig.VERSION_NAME}）",
                                    "Fcode ${BuildConfig.VERSION_NAME} is up to date",
                                ),
                            )
                        }
                    }
                    else -> showUpdateDialog(activity, language, manifest)
                }
            }.onFailure { error ->
                Log.w(TAG, "Update check failed", error)
                if (userInitiated) {
                    showMessage(
                        activity,
                        text(language, "检查更新失败", "Update check failed"),
                        text(
                            language,
                            "无法连接 GitHub，请检查网络后重试。\n${error.message.orEmpty()}",
                            "Could not reach GitHub. Check your connection and try again.\n${error.message.orEmpty()}",
                        ).trim(),
                    )
                }
            }
        }
    }

    /** Clears DownloadManager state left by releases that predate browser-based downloads. */
    fun clearLegacyDownloadState(context: Context): Boolean {
        val preferences = prefs(context)
        val pendingId = preferences.getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
        val hadLegacyState = pendingId > 0L ||
            preferences.contains(KEY_QUEUED_MANIFEST) ||
            preferences.contains(KEY_PENDING_FILE) ||
            preferences.contains(KEY_PENDING_MANIFEST) ||
            preferences.contains(KEY_READY_FILE) ||
            preferences.contains(KEY_READY_MANIFEST)

        if (!hadLegacyState) return false

        if (pendingId > 0L) {
            runCatching {
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(pendingId)
            }
        }
        clearPendingDownloadState(context, deleteFile = true)
        clearReadyState(context, deleteFile = true)
        preferences.edit()
            .remove(KEY_QUEUED_MANIFEST)
            .remove(KEY_LANGUAGE)
            .remove(KEY_LAST_INSTALL_LAUNCH)
            .apply()
        context.getSystemService(NotificationManager::class.java).run {
            cancel(READY_NOTIFICATION_ID)
            cancel(ERROR_NOTIFICATION_ID)
        }
        return true
    }

    private fun showUpdateDialog(
        activity: ComponentActivity,
        language: String,
        manifest: AppUpdateManifest,
    ) {
        val notes = formatChangelog(manifest.changelog).ifBlank {
            text(language, "本次更新包含功能改进和问题修复。", "This update includes improvements and bug fixes.")
        }
        val message = buildString {
            append(text(language, "当前版本", "Current version"))
            append(": ${BuildConfig.VERSION_NAME}\n")
            append(text(language, "最新版本", "Latest version"))
            append(": ${manifest.versionName}\n\n")
            append(text(language, "更新内容", "What's new"))
            append("\n")
            append(notes)
            append("\n\n")
            append(
                text(
                    language,
                    "点击后将使用默认浏览器打开 GitHub 下载链接。下载完成后，请从浏览器的下载列表打开 APK 安装。",
                    "The GitHub download link will open in your default browser. When the download finishes, open the APK from the browser's downloads to install it.",
                ),
            )
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(text(language, "发现新版本", "Update available"))
            .setMessage(message)
            .setPositiveButton(text(language, "浏览器下载", "Download in browser")) { _, _ ->
                openDownloadInBrowser(activity, language, manifest)
            }
            .setCancelable(!manifest.force)
        if (!manifest.force) {
            builder.setNegativeButton(text(language, "稍后", "Later"), null)
        }
        builder.show()
    }

    internal fun browserDownloadIntent(manifest: AppUpdateManifest): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(manifest.apkUrl))
            .addCategory(Intent.CATEGORY_BROWSABLE)

    private fun openDownloadInBrowser(
        activity: ComponentActivity,
        language: String,
        manifest: AppUpdateManifest,
    ) {
        try {
            activity.startActivity(browserDownloadIntent(manifest))
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Could not open update in a browser", error)
            showMessage(
                activity,
                text(language, "无法打开浏览器", "Could not open a browser"),
                text(
                    language,
                    "设备中没有可用的浏览器。你可以手动打开：\n${manifest.apkUrl}",
                    "No browser is available on this device. Open this link manually:\n${manifest.apkUrl}",
                ),
            )
        }
    }

    private fun fetchManifest(): AppUpdateManifest {
        val manifestUri = URI(BuildConfig.UPDATE_MANIFEST_URL)
        require(manifestUri.scheme.equals("https", ignoreCase = true)) { "Update manifest URL must use HTTPS" }
        val connection = URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Fcode/${BuildConfig.VERSION_NAME} Android")
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "GitHub returned HTTP $responseCode" }
            val body = connection.inputStream.use { it.readUtf8Limited(MAX_MANIFEST_CHARS) }
            return AppUpdateManifest.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun clearPendingDownloadState(context: Context, deleteFile: Boolean) {
        val prefs = prefs(context)
        if (deleteFile) prefs.getString(KEY_PENDING_FILE, null)?.let(::File)?.delete()
        prefs.edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .remove(KEY_PENDING_FILE)
            .remove(KEY_PENDING_MANIFEST)
            .apply()
    }

    private fun clearReadyState(context: Context, deleteFile: Boolean) {
        val prefs = prefs(context)
        if (deleteFile) prefs.getString(KEY_READY_FILE, null)?.let(::File)?.delete()
        prefs.edit()
            .remove(KEY_READY_FILE)
            .remove(KEY_READY_MANIFEST)
            .remove(KEY_LAST_INSTALL_LAUNCH)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun formatChangelog(value: String): String = value
        .lineSequence()
        .map { it.replace(Regex("^#{1,6}\\s+"), "") }
        .joinToString("\n")
        .trim()

    private fun text(language: String, zh: String, en: String): String =
        if (language.equals("en", ignoreCase = true)) en else zh

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun showMessage(activity: ComponentActivity, title: String, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun InputStream.readUtf8Limited(maxChars: Int): String {
        bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= maxChars) { "Update manifest is too large" }
                result.append(buffer, 0, count)
            }
            return result.toString()
        }
    }
}
