package com.termux.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Unified, shareable file logger for the native chat/backend/installer layer.
 *
 * Complements [NativeChatDiagnostics] (the JSONL chat state-machine recorder) by persisting what
 * it does not: bridge process stderr/exits, installer stages, project store mutations, subagent
 * collection diagnostics and conversation creation. All file writes go through a single daemon
 * executor so callers (including the Compose main thread) only enqueue work. Every persisted line
 * is redacted via [CodexAppServerBridgeProtocol.redactSensitiveLogLine]; the logger never relies on
 * the caller having redacted first.
 */
object FcodeLog {
    private const val TAG = "FcodeLog"
    private const val LOG_DIR = "fcode-logs"
    private const val PREFS = "codex_mobile"
    private const val PREF_LEVEL = "fcode_log_level"

    const val LEVEL_OFF = 0
    const val LEVEL_NORMAL = 1
    const val LEVEL_DEBUG = 2
    const val LEVEL_VERBOSE = 3

    private const val MAX_TEXT_BYTES = 4L * 1024L * 1024L
    private const val MAX_EVENTS_BYTES = 2L * 1024L * 1024L
    private const val MAX_LINE_CHARS = 4000
    private const val STDERR_RING_CAPACITY = 200

    private val LOCK = Any()
    private val WRITER: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FcodeLog").apply { isDaemon = true }
    }
    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
    private val stderrRing = ArrayDeque<String>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentLevel: Int = LEVEL_NORMAL

    /** Called once from [TermuxApplication.onCreate]. */
    @JvmStatic
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        currentLevel = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(PREF_LEVEL, LEVEL_NORMAL)
        writeSession(app)
        // A text log entry on init guarantees fcode.log always exists (even when only event()
        // call sites fire), so the diagnostics page and export always have a text file.
        val levelLabel = when (currentLevel) {
            LEVEL_OFF -> "OFF"
            LEVEL_NORMAL -> "NORMAL"
            LEVEL_DEBUG -> "DEBUG"
            else -> "VERBOSE"
        }
        i("FcodeLog", "initialized level=$levelLabel build=${com.termux.BuildConfig.VERSION_NAME} (${com.termux.BuildConfig.VERSION_CODE}) debug=${com.termux.BuildConfig.DEBUG}")
        event(app, "app_startup", JSONObject()
            .put("build", com.termux.BuildConfig.VERSION_NAME)
            .put("versionCode", com.termux.BuildConfig.VERSION_CODE)
            .put("debug", com.termux.BuildConfig.DEBUG))
    }

    @JvmStatic
    fun isLoggable(level: Int): Boolean = currentLevel >= level

    @JvmStatic
    fun currentLevel(): Int = currentLevel

    @JvmStatic
    fun setLevel(context: Context, level: Int) {
        currentLevel = level.coerceIn(LEVEL_OFF, LEVEL_VERBOSE)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(PREF_LEVEL, currentLevel).apply()
        writeSession(context)
    }

    @JvmStatic
    fun v(tag: String, msg: String) = log(LEVEL_VERBOSE, Log.VERBOSE, tag, msg)

    @JvmStatic
    fun d(tag: String, msg: String) = log(LEVEL_DEBUG, Log.DEBUG, tag, msg)

    @JvmStatic
    fun i(tag: String, msg: String) = log(LEVEL_NORMAL, Log.INFO, tag, msg)

    @JvmStatic
    fun w(tag: String, msg: String) = log(LEVEL_NORMAL, Log.WARN, tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String) = log(LEVEL_NORMAL, Log.ERROR, tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String, t: Throwable?) {
        if (currentLevel < LEVEL_NORMAL) return
        val message = if (t == null) msg else {
            val trace = try {
                Log.getStackTraceString(t)
            } catch (ignored: Throwable) {
                t.toString()
            }
            "$msg\n$trace"
        }
        log(LEVEL_NORMAL, Log.ERROR, tag, message)
    }

    /**
     * Structured diagnostic event, persisted to fcode-events.jsonl. Unconditionally written
     * (except at [LEVEL_OFF]) because these are sparse lifecycle events the diagnosing device
     * must capture by default. Details should carry metadata/short ids, never secrets.
     */
    @JvmStatic
    fun event(context: Context, category: String, json: JSONObject?) {
        if (currentLevel == LEVEL_OFF) return
        val app = context.applicationContext
        val serialized: String
        try {
            serialized = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("up", SystemClock.uptimeMillis())
                .put("event", category)
                .put("details", json ?: JSONObject())
                .toString() + "\n"
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to serialize event", error)
            return
        }
        val safe = redact(serialized)
        WRITER.execute { appendEvents(app, safe) }
    }

    /** Redacted stderr line; also mirrored to the text log and logcat. */
    @JvmStatic
    fun stderrLine(tag: String, line: String) {
        val safe = redact(limit(line))
        synchronized(LOCK) {
            stderrRing.addLast(safe)
            while (stderrRing.size > STDERR_RING_CAPACITY) stderrRing.removeFirst()
        }
        log(LEVEL_NORMAL, Log.ERROR, tag, safe)
    }

    /** Snapshot of the last [STDERR_RING_CAPACITY] stderr lines, for process-exit diagnostics. */
    @JvmStatic
    fun stderrTail(): List<String> = synchronized(LOCK) { stderrRing.toList() }

    /** Truncate an over-long payload to [max] chars, appending the count of dropped chars. */
    @JvmStatic
    fun limit(value: String?, max: Int = MAX_LINE_CHARS): String {
        val text = value ?: return ""
        if (text.length <= max) return text
        return text.substring(0, max) + "…(+${text.length - max})"
    }

    @JvmStatic
    fun logDir(context: Context): File = File(context.filesDir, LOG_DIR)

    @JvmStatic
    fun textFile(context: Context): File = File(logDir(context), "fcode.log")

    @JvmStatic
    fun eventsFile(context: Context): File = File(logDir(context), "fcode-events.jsonl")

    @JvmStatic
    fun sessionFile(context: Context): File = File(logDir(context), "fcode-session.json")

    @JvmStatic
    fun files(context: Context): List<File> {
        val dir = logDir(context)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) ?: emptyList()
    }

    @JvmStatic
    fun clear(context: Context) {
        // Never delete on the caller's thread (the Compose main thread may call this).
        WRITER.execute {
            logDir(context).listFiles()?.forEach { file ->
                try {
                    if (!file.delete()) Log.w(TAG, "Unable to delete " + file.name)
                } catch (ignored: Throwable) {}
            }
        }
    }

    /** Device/app/session metadata for diagnostics and the log page header. No secrets. */
    @JvmStatic
    fun sessionInfo(context: Context): JSONObject {
        val app = context.applicationContext
        val json = JSONObject()
        try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            json.put("appId", app.packageName)
            json.put("versionName", info.versionName ?: "")
            // versionCode (int) is present since API 1; longVersionCode is API 28+ only.
            @Suppress("DEPRECATION")
            json.put("versionCode", info.versionCode)
            json.put("minSdk", app.applicationInfo.minSdkVersion)
            json.put("targetSdk", app.applicationInfo.targetSdkVersion)
        } catch (ignored: Throwable) {}
        json.put("sdkInt", Build.VERSION.SDK_INT)
        json.put("osRelease", Build.VERSION.RELEASE)
        json.put("osCodename", Build.VERSION.CODENAME)
        json.put("manufacturer", Build.MANUFACTURER)
        json.put("model", Build.MODEL)
        val abis = JSONArray()
        Build.SUPPORTED_ABIS?.forEach(abis::put)
        json.put("abis", abis)
        json.put("isArm64", Build.SUPPORTED_ABIS?.contains("arm64-v8a") == true)
        val backend = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("native_backend_type_v1", "")
        json.put("backend", backend ?: "")
        json.put("logLevel", currentLevel)
        try {
            json.put("signingCertSha1", signingCertSha1(app))
        } catch (ignored: Throwable) {}
        try {
            val stat = StatFs(app.filesDir.absolutePath)
            json.put("availableBytes", stat.availableBytes)
            json.put("totalBytes", stat.totalBytes)
        } catch (ignored: Throwable) {}
        json.put("startedAtMs", System.currentTimeMillis())
        return json
    }

    private fun log(level: Int, priority: Int, tag: String, msg: String) {
        if (currentLevel < level) return
        val safe = redact(limit(msg))
        try {
            when (priority) {
                Log.VERBOSE -> Log.v(tag, safe)
                Log.DEBUG -> Log.d(tag, safe)
                Log.INFO -> Log.i(tag, safe)
                Log.WARN -> Log.w(tag, safe)
                Log.ERROR -> Log.e(tag, safe)
            }
        } catch (ignored: Throwable) {}
        enqueueText(tag, levelLabel(priority), safe)
    }

    private fun enqueueText(tag: String, levelLabel: String, safe: String) {
        val context = appContext ?: return
        val line = timestampFormat.get()!!.format(Date()) + " " + levelLabel + " [" + tag + "] " + safe
        WRITER.execute { appendText(context, line) }
    }

    private fun appendText(context: Context, line: String) {
        synchronized(LOCK) {
            try {
                val target = textFile(context)
                rotateText(target)
                appendLine(target, line)
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to write text log", error)
            }
        }
    }

    private fun rotateText(target: File) {
        if (target.length() < MAX_TEXT_BYTES) return
        val parent = target.parentFile ?: return
        val second = File(parent, target.name + ".2")
        val first = File(parent, target.name + ".1")
        if (second.exists() && !second.delete()) Log.w(TAG, "Unable to remove " + second.name)
        if (first.exists() && !first.renameTo(second)) Log.w(TAG, "Unable to rotate " + first.name)
        if (!target.renameTo(first)) Log.w(TAG, "Unable to rotate " + target.name)
    }

    private fun appendEvents(context: Context, line: String) {
        synchronized(LOCK) {
            try {
                val target = eventsFile(context)
                if (target.length() >= MAX_EVENTS_BYTES) {
                    val parent = target.parentFile ?: return
                    val previous = File(parent, "fcode-events.previous.jsonl")
                    if (previous.exists() && !previous.delete()) Log.w(TAG, "Unable to remove old events")
                    if (!target.renameTo(previous)) Log.w(TAG, "Unable to rotate events")
                }
                appendLine(target, line)
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to write event log", error)
            }
        }
    }

    private fun appendLine(target: File, line: String) {
        val dir = target.parentFile ?: return
        if (!dir.isDirectory && !dir.mkdirs()) return
        FileOutputStream(target, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
    }

    private fun writeSession(context: Context) {
        WRITER.execute {
            synchronized(LOCK) {
                try {
                    val target = sessionFile(context)
                    val dir = target.parentFile ?: return@execute
                    if (!dir.isDirectory && !dir.mkdirs()) return@execute
                    val tmp = File(dir, target.name + ".tmp")
                    tmp.writeText(sessionInfo(context).toString(), Charsets.UTF_8)
                    if (!tmp.renameTo(target)) {
                        if (target.exists() && !target.delete()) Log.w(TAG, "Unable to remove old session")
                        if (!tmp.renameTo(target)) Log.w(TAG, "Unable to commit session")
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "Unable to write session", error)
                }
            }
        }
    }

    private fun signingCertSha1(context: Context): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val signature = info.signatures?.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
        val builder = StringBuilder()
        digest.forEach { byte ->
            if (builder.isNotEmpty()) builder.append(':')
            builder.append(String.format(Locale.US, "%02X", byte))
        }
        return builder.toString()
    }

    private fun redact(line: String): String = CodexAppServerBridgeProtocol.redactSensitiveLogLine(line)

    private fun levelLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        else -> "E"
    }
}
