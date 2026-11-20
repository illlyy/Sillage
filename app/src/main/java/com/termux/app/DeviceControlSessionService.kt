package com.termux.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.termux.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceControlSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                DeviceControlRuntime.stopSession("screen_off")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        startForeground(NOTIFICATION_ID, notification("AI 正在操作本机"))
        showOverlay()
        updateJob = scope.launch {
            while (isActive) {
                updateOverlay()
                val state = DeviceControlRuntime.state.value
                if (!state.sessionActive) {
                    stopSelf()
                    break
                }
                delay(1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            DeviceControlRuntime.stopSession("user")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        updateJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        removeOverlay()
        scope.cancel()
        if (DeviceControlRuntime.state.value.sessionActive) DeviceControlRuntime.onSessionServiceDestroyed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val view = TextView(this).apply {
            text = "AI 控制中 · 停止"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(30, 18, 30, 18)
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.rgb(180, 38, 55))
                setStroke(1, Color.argb(120, 255, 255, 255))
            }
            setOnClickListener { DeviceControlRuntime.stopSession("user") }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 70
        }
        runCatching {
            manager.addView(view, params)
            overlayView = view
        }
    }

    private fun updateOverlay() {
        val state = DeviceControlRuntime.state.value
        val seconds = ((state.sessionExpiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
        overlayView?.text = "AI 控制中 %d:%02d · 停止".format(seconds / 60, seconds % 60)
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_codex_logo)
        .setContentTitle("Sillage 设备控制")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                1001,
                Intent(this, NativeSettingsActivity::class.java).putExtra(EXTRA_OPEN_DEVICE_CONTROL, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .addAction(
            0,
            "停止",
            PendingIntent.getService(
                this,
                1002,
                Intent(this, DeviceControlSessionService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI 设备控制", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示 AI 设备控制会话状态"
            }
        )
    }

    companion object {
        const val EXTRA_OPEN_DEVICE_CONTROL = "open_device_control"
        private const val CHANNEL_ID = "sillage_device_session"
        private const val NOTIFICATION_ID = 10971
        private const val ACTION_STOP = "com.termux.app.DEVICE_CONTROL_STOP"

        fun start(context: Context, minutes: Int) {
            val intent = Intent(context, DeviceControlSessionService::class.java).putExtra("minutes", minutes)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DeviceControlSessionService::class.java)) }
        }
    }
}
