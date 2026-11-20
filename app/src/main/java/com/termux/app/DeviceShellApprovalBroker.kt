package com.termux.app

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.termux.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal object DeviceShellApprovalBroker {
    private data class Pending(
        val id: String,
        val command: String,
        val deferred: CompletableDeferred<Boolean>,
    )

    private val lock = Any()
    private var pending: Pending? = null

    suspend fun request(context: Context, command: String, timeoutMs: Long = 120_000L): Boolean {
        val request = Pending(UUID.randomUUID().toString(), command, CompletableDeferred())
        synchronized(lock) {
            pending?.deferred?.complete(false)
            pending = request
        }
        val notificationPosted = postNotification(context, request)
        val activityOpened = maybeOpenActivity(context, request)
        if (!notificationPosted && !activityOpened) {
            resolve(request.id, false)
            return false
        }
        val approved = withTimeoutOrNull(timeoutMs) { request.deferred.await() } ?: false
        dismissNotification(context, request)
        synchronized(lock) {
            if (pending === request) pending = null
        }
        return approved
    }

    fun current(id: String): String? = synchronized(lock) { pending?.takeIf { it.id == id }?.command }

    fun resolve(id: String, approved: Boolean) {
        val request = synchronized(lock) {
            pending?.takeIf { it.id == id }?.also { pending = null }
        } ?: return
        request.deferred.complete(approved)
    }

    fun denyAll() {
        val request = synchronized(lock) { pending?.also { pending = null } } ?: return
        request.deferred.complete(false)
    }

    fun notificationsAvailable(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun maybeOpenActivity(context: Context, request: Pending): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val foreground = activityManager.runningAppProcesses
            ?.firstOrNull { it.pid == android.os.Process.myPid() }
            ?.importance ?: return false
        if (foreground > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return false
        return runCatching {
            context.startActivity(approvalIntent(context, request.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun postNotification(context: Context, request: Pending): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI 设备命令", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openApproval = PendingIntent.getActivity(
            context,
            request.id.hashCode(),
            approvalIntent(context, request.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_codex_logo)
            .setContentTitle("AI 请求执行设备命令")
            .setContentText("需要你逐字核对并确认")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openApproval)
            .addAction(0, "查看并确认", openApproval)
            .build()
        return runCatching {
            manager.notify(request.id.hashCode(), notification)
            true
        }.getOrDefault(false)
    }

    private fun dismissNotification(context: Context, request: Pending) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.cancel(request.id.hashCode()) }
    }

    private fun approvalIntent(context: Context, id: String): Intent =
        Intent(context, DeviceShellApprovalActivity::class.java).putExtra(DeviceShellApprovalActivity.EXTRA_REQUEST_ID, id)

    private const val CHANNEL_ID = "sillage_device_approval"
}
