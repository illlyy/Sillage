package com.termux.app.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.termux.BuildConfig
import com.termux.R
import com.termux.app.CodexChatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object AppUpdateManager {
    const val EXTRA_INSTALL_UPDATE = "com.ilyop.codex.extra.INSTALL_UPDATE"

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
    private const val INSTALL_RETRY_INTERVAL_MS = 30L * 60L * 1000L
    private const val MAX_MANIFEST_CHARS = 256 * 1024
    private const val NOTIFICATION_CHANNEL = "fcode_app_updates"
    private const val READY_NOTIFICATION_ID = 0x4643
    private const val ERROR_NOTIFICATION_ID = 0x4644

    private val installLaunchInProgress = AtomicBoolean(false)
    private val downloadCompletionInProgress = AtomicBoolean(false)

    fun checkAutomatically(activity: ComponentActivity, language: String) {
        if (BuildConfig.DEBUG || hasTrackedUpdate(activity)) return
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
        if (userInitiated && resumePendingInstall(activity, language, userInitiated = true)) return
        if (userInitiated) toast(activity, text(language, "正在检查更新…", "Checking for updates…"))

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

    /**
     * Continues a permission hand-off or launches a verified APK. Returns true when an existing
     * update is already queued, downloading, ready, or being installed.
     */
    fun resumePendingInstall(
        activity: ComponentActivity,
        language: String,
        userInitiated: Boolean = false,
    ): Boolean {
        val prefs = prefs(activity)
        val queued = prefs.getString(KEY_QUEUED_MANIFEST, null)
            ?.let { runCatching { AppUpdateManifest.parse(it) }.getOrNull() }
        if (queued != null) {
            if (canInstallPackages(activity)) {
                prefs.edit().remove(KEY_QUEUED_MANIFEST).apply()
                enqueueDownload(activity, language, queued)
            } else if (userInitiated) {
                openUnknownSourcesSettings(activity, language)
            }
            return true
        }

        val readyManifest = prefs.getString(KEY_READY_MANIFEST, null)
            ?.let { runCatching { AppUpdateManifest.parse(it) }.getOrNull() }
        val readyFile = prefs.getString(KEY_READY_FILE, null)?.let(::File)
        if (readyManifest != null && readyFile != null) {
            if (readyManifest.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
                clearReadyState(activity, deleteFile = true)
                return false
            }
            if (!readyFile.isFile) {
                clearReadyState(activity, deleteFile = false)
                return false
            }
            if (!canInstallPackages(activity)) {
                if (userInitiated) openUnknownSourcesSettings(activity, language)
                return true
            }
            val lastLaunch = prefs.getLong(KEY_LAST_INSTALL_LAUNCH, 0L)
            if (userInitiated || System.currentTimeMillis() - lastLaunch >= INSTALL_RETRY_INTERVAL_MS) {
                launchInstaller(activity, language, readyFile, readyManifest)
            }
            return true
        }

        val pendingId = prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
        if (pendingId > 0L) {
            val status = downloadStatus(activity, pendingId)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                if (downloadCompletionInProgress.compareAndSet(false, true)) {
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { completeDownload(activity.applicationContext, pendingId) }
                            resumePendingInstall(activity, language, userInitiated)
                        } finally {
                            downloadCompletionInProgress.set(false)
                        }
                    }
                }
                return true
            }
            if (status == DownloadManager.STATUS_FAILED) {
                clearPendingDownloadState(activity, deleteFile = true)
                return false
            }
            if (userInitiated) {
                toast(activity, text(language, "更新正在下载，请查看通知栏", "The update is downloading; check notifications"))
            }
            return true
        }
        return false
    }

    internal fun isTrackedDownload(context: Context, downloadId: Long): Boolean =
        downloadId > 0L && prefs(context).getLong(KEY_PENDING_DOWNLOAD_ID, -1L) == downloadId

    internal fun completeDownload(context: Context, downloadId: Long) {
        if (!isTrackedDownload(context, downloadId)) return
        val prefs = prefs(context)
        val manifestJson = prefs.getString(KEY_PENDING_MANIFEST, null)
        val filePath = prefs.getString(KEY_PENDING_FILE, null)
        val language = prefs.getString(KEY_LANGUAGE, null).orEmpty()
        val manifest = manifestJson?.let { runCatching { AppUpdateManifest.parse(it) }.getOrNull() }
        val file = filePath?.let(::File)

        val failure = runCatching {
            require(downloadStatus(context, downloadId) == DownloadManager.STATUS_SUCCESSFUL) {
                "Download Manager reported a failed download"
            }
            require(manifest != null && file != null && file.isFile) { "Downloaded APK is missing" }
            require(file.sha256().equals(manifest.sha256, ignoreCase = true)) { "APK checksum does not match" }
            validateDownloadedPackage(context, file, manifest)
        }.exceptionOrNull()

        if (failure != null || manifest == null || file == null) {
            Log.e(TAG, "Downloaded update validation failed", failure)
            file?.delete()
            clearPendingDownloadState(context, deleteFile = false)
            postStatusNotification(
                context,
                ready = false,
                title = text(language, "Fcode 更新校验失败", "Fcode update validation failed"),
                message = text(language, "安装包已删除，请重新检查更新", "The APK was deleted; check for updates again"),
            )
            return
        }

        prefs.edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .remove(KEY_PENDING_FILE)
            .remove(KEY_PENDING_MANIFEST)
            .putString(KEY_READY_FILE, file.absolutePath)
            .putString(KEY_READY_MANIFEST, manifest.toJson())
            .apply()
        postStatusNotification(
            context,
            ready = true,
            title = text(language, "Fcode ${manifest.versionName} 已下载", "Fcode ${manifest.versionName} is ready"),
            message = text(language, "点击完成安装", "Tap to finish installing"),
        )
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
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(text(language, "发现新版本", "Update available"))
            .setMessage(message)
            .setPositiveButton(text(language, "下载并安装", "Download and install")) { _, _ ->
                prepareDownload(activity, language, manifest)
            }
            .setCancelable(!manifest.force)
        if (!manifest.force) {
            builder.setNegativeButton(text(language, "稍后", "Later"), null)
        }
        builder.show()
    }

    private fun prepareDownload(
        activity: ComponentActivity,
        language: String,
        manifest: AppUpdateManifest,
    ) {
        if (!canInstallPackages(activity)) {
            prefs(activity).edit()
                .putString(KEY_QUEUED_MANIFEST, manifest.toJson())
                .putString(KEY_LANGUAGE, language)
                .apply()
            openUnknownSourcesSettings(activity, language)
            return
        }
        enqueueDownload(activity, language, manifest)
    }

    private fun enqueueDownload(
        context: Context,
        language: String,
        manifest: AppUpdateManifest,
    ) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                toast(context, text(language, "无法创建更新目录", "Could not create the update directory"))
                return
            }
        val file = File(directory, manifest.apkFileName())
        if (file.exists() && !file.delete()) {
            toast(context, text(language, "无法替换旧的安装包", "Could not replace the previous APK"))
            return
        }

        val oldDownloadId = prefs(context).getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
        if (oldDownloadId > 0L) downloadManager.remove(oldDownloadId)
        clearReadyState(context, deleteFile = true)

        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("Fcode ${manifest.versionName}")
            .setDescription(text(language, "正在下载安装包", "Downloading update"))
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, file.name)

        val downloadId = runCatching { downloadManager.enqueue(request) }.getOrElse { error ->
            Log.e(TAG, "Could not enqueue update download", error)
            toast(context, text(language, "无法开始下载更新", "Could not start the update download"))
            return
        }
        prefs(context).edit()
            .remove(KEY_QUEUED_MANIFEST)
            .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
            .putString(KEY_PENDING_FILE, file.absolutePath)
            .putString(KEY_PENDING_MANIFEST, manifest.toJson())
            .putString(KEY_LANGUAGE, language)
            .apply()
        toast(context, text(language, "已开始下载，可在通知栏查看进度", "Download started; progress is available in notifications"))
    }

    private fun launchInstaller(
        activity: ComponentActivity,
        language: String,
        file: File,
        manifest: AppUpdateManifest,
    ) {
        if (!installLaunchInProgress.compareAndSet(false, true)) return
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.update.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            intent.clipData = ClipData.newRawUri("Fcode ${manifest.versionName}", uri)
            if (intent.resolveActivity(activity.packageManager) == null) {
                toast(activity, text(language, "系统中没有可用的安装器", "No package installer is available"))
                return
            }
            prefs(activity).edit().putLong(KEY_LAST_INSTALL_LAUNCH, System.currentTimeMillis()).apply()
            activity.startActivity(intent)
            activity.getSystemService(NotificationManager::class.java).cancel(READY_NOTIFICATION_ID)
        } catch (error: Exception) {
            Log.e(TAG, "Could not launch package installer", error)
            showMessage(
                activity,
                text(language, "无法打开安装器", "Could not open installer"),
                error.message.orEmpty(),
            )
        } finally {
            installLaunchInProgress.set(false)
        }
    }

    private fun openUnknownSourcesSettings(activity: ComponentActivity, language: String) {
        toast(
            activity,
            text(
                language,
                "请允许 Fcode 安装未知应用，返回后会自动开始下载",
                "Allow Fcode to install unknown apps; the download will start when you return",
            ),
        )
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

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

    @Suppress("DEPRECATION")
    private fun validateDownloadedPackage(
        context: Context,
        file: File,
        manifest: AppUpdateManifest,
    ) {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        require(archive.packageName == context.packageName) { "APK package name does not match" }
        require(archive.longVersionCodeCompat() == manifest.versionCode) { "APK versionCode does not match update.json" }
        require(manifest.versionCode > BuildConfig.VERSION_CODE.toLong()) { "APK is not newer than the installed app" }

        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val archiveCertificates = archive.activeCertificateDigests()
        val installedCertificates = installed.activeCertificateDigests()
        require(archiveCertificates.isNotEmpty() && archiveCertificates == installedCertificates) {
            "APK signing certificate does not match the installed app"
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.activeCertificateDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners.orEmpty()
        } else {
            this.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .toHex()
        }
    }

    private fun postStatusNotification(
        context: Context,
        ready: Boolean,
        title: String,
        message: String,
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "Fcode updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Fcode application update status"
                },
            )
        }
        val openApp = Intent(context, CodexChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_INSTALL_UPDATE, ready)
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (ready) READY_NOTIFICATION_ID else ERROR_NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_codex_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            notificationManager.notify(if (ready) READY_NOTIFICATION_ID else ERROR_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Could not post update notification", it) }
    }

    private fun hasTrackedUpdate(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.contains(KEY_QUEUED_MANIFEST) ||
            prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1L) > 0L ||
            prefs.contains(KEY_READY_MANIFEST)
    }

    private fun downloadStatus(context: Context, downloadId: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use -1
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            } ?: -1
        }.getOrDefault(-1)
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

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

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
