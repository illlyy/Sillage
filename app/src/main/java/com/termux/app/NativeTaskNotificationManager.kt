package com.termux.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat

object NativeTaskNotificationManager {
    const val EXTRA_THREAD_ID = "native_notification_thread_id"
    const val EXTRA_KIND = "native_notification_kind"
    private const val PREFS = "codex_mobile"
    private const val ENABLED = "completion_notification"
    private const val UNIFIED = "native_unified_notifications_v1"
    private const val FINGERPRINT_PREFIX = "native_task_notification_fingerprint_v1_"
    private const val CHANNEL_UPDATES = "codex_task_updates"
    private const val CHANNEL_ATTENTION = "codex_task_attention"
    private const val GROUP = "codex_native_tasks"
    @Volatile private var foregroundThreadId: String = ""

    @JvmStatic
    fun setForegroundThread(context: Context?, threadId: String?) {
        foregroundThreadId = threadId.orEmpty()
        if (foregroundThreadId.isNotBlank()) cancel(context, foregroundThreadId)
    }

    @JvmStatic
    fun reset(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(FINGERPRINT_PREFIX + threadId).apply()
        cancel(context, threadId)
    }

    @JvmStatic
    fun markSeen(context: Context, threadId: String) = cancel(context, threadId)

    @JvmStatic
    fun notifyEvent(context: Context?, threadId: String?, kind: String?, token: String?, detail: String?) {
        if (context == null || threadId.isNullOrBlank() || kind.isNullOrBlank()) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, false)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val fingerprint = NativeTaskNotificationPolicy.fingerprint(threadId, kind, token.orEmpty())
        if (prefs.getString(FINGERPRINT_PREFIX + threadId, "") == fingerprint) return
        prefs.edit().putString(FINGERPRINT_PREFIX + threadId, fingerprint).putBoolean(UNIFIED, true).apply()
        if (foregroundThreadId == threadId) {
            cancel(app, threadId)
            return
        }
        ensureChannels(app)
        val task = CodexTaskStore.current(app).firstOrNull { it.threadId == threadId }
        val taskTitle = task?.title?.takeIf { it.isNotBlank() }.orEmpty()
        val title = when (kind) {
            NativeTaskNotificationPolicy.ANSWER -> "Codex \u6b63\u5728\u7b49\u4f60\u56de\u7b54"
            NativeTaskNotificationPolicy.APPROVAL -> "Codex \u8bf7\u6c42\u4f60\u5ba1\u6279"
            NativeTaskNotificationPolicy.PLAN -> "Codex \u8ba1\u5212\u7b49\u5f85\u6267\u884c"
            NativeTaskNotificationPolicy.FAILED, NativeTaskNotificationPolicy.RESUME -> "Codex \u4efb\u52a1\u9700\u8981\u7ee7\u7eed"
            else -> "Codex \u4efb\u52a1\u5df2\u5b8c\u6210"
        }
        val fallback = when (kind) {
            NativeTaskNotificationPolicy.ANSWER -> "\u70b9\u51fb\u6253\u5f00\u5bf9\u8bdd\u5b8c\u6210\u56de\u7b54"
            NativeTaskNotificationPolicy.APPROVAL -> "\u70b9\u51fb\u67e5\u770b\u547d\u4ee4\u6216\u6587\u4ef6\u4fee\u6539\u8bf7\u6c42"
            NativeTaskNotificationPolicy.PLAN -> "\u6253\u5f00\u5bf9\u8bdd\u9009\u62e9\u6267\u884c\u3001\u66f4\u6539\u6216\u53d6\u6d88"
            NativeTaskNotificationPolicy.FAILED, NativeTaskNotificationPolicy.RESUME -> "\u6253\u5f00\u5bf9\u8bdd\u4ece\u4e2d\u65ad\u4f4d\u7f6e\u7ee7\u7eed"
            else -> "\u70b9\u51fb\u67e5\u770b\u4efb\u52a1\u7ed3\u679c"
        }
        val body = detail.orEmpty().trim().take(500).ifBlank { taskTitle.ifBlank { fallback } }
        val intent = Intent(app, CodexChatActivity::class.java)
            .putExtra(EXTRA_THREAD_ID, threadId)
            .putExtra(EXTRA_KIND, kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val requestCode = (threadId.hashCode() * 31 + kind.hashCode()) and Int.MAX_VALUE
        val pending = PendingIntent.getActivity(app, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        val attention = NativeTaskNotificationPolicy.priority(kind) >= NativeTaskNotificationPolicy.priority(NativeTaskNotificationPolicy.PLAN)
        val channel = if (attention) CHANNEL_ATTENTION else CHANNEL_UPDATES
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(app, channel) else Notification.Builder(app)
        builder.setSmallIcon(com.termux.R.drawable.ic_service_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setColor(if (kind == NativeTaskNotificationPolicy.FAILED) Color.rgb(178, 62, 62) else Color.rgb(42, 119, 81))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(GROUP)
            .setCategory(if (attention) Notification.CATEGORY_MESSAGE else Notification.CATEGORY_STATUS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setPriority(if (attention) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
        notificationManager(app).notify(notificationTag(threadId), notificationId(threadId), builder.build())
    }

    @JvmStatic
    fun cancel(context: Context?, threadId: String) {
        if (context == null || threadId.isBlank()) return
        notificationManager(context.applicationContext).cancel(notificationTag(threadId), notificationId(threadId))
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val updates = NotificationChannel(CHANNEL_UPDATES, "Codex \u4efb\u52a1\u72b6\u6001", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Codex \u4efb\u52a1\u5b8c\u6210\u548c\u5931\u8d25\u63d0\u9192"
        }
        val attention = NotificationChannel(CHANNEL_ATTENTION, "Codex \u5f85\u5904\u7406\u8bf7\u6c42", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Codex \u7b49\u5f85\u56de\u7b54\u3001\u5ba1\u6279\u6216\u8ba1\u5212\u6267\u884c\u65f6\u63d0\u9192"
        }
        notificationManager(context).createNotificationChannels(listOf(updates, attention))
    }

    private fun notificationManager(context: Context): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun notificationId(threadId: String): Int = 7600 + ((threadId.hashCode() and Int.MAX_VALUE) % 100_000)
    private fun notificationTag(threadId: String): String = "codex-task-$threadId"
    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
