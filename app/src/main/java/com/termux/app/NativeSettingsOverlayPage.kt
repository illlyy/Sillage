package com.termux.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.WebView
import android.widget.Toast
import com.termux.BuildConfig
import com.termux.R
import com.termux.app.update.AppUpdateManager
import com.termux.shared.termux.TermuxConstants
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tick02


private data class OverlayGestureSetting(
    val title: String,
    val key: String,
    val fallback: String,
)

@Composable
internal fun OverlaySettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    var overlayEnabled by remember {
        mutableStateOf(prefs.getBoolean("overlay_enabled", false) && permissionGranted)
    }
    var edgeSnap by remember { mutableStateOf(prefs.getBoolean("overlay_edge_snap", true)) }
    var completionBubble by remember { mutableStateOf(prefs.getBoolean("completion_bubble", true)) }
    var completionNotification by remember { mutableStateOf(prefs.getBoolean("completion_notification", false)) }
    var keepAlive by remember {
        mutableStateOf(prefs.getBoolean(CodexOverlayService.PREF_NATIVE_TASK_KEEP_ALIVE, true))
    }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    var selectedGesture by remember { mutableStateOf<OverlayGestureSetting?>(null) }
    val gestureValues = remember {
        mutableStateMapOf(
            "overlay_ball_tap_action" to prefs.getString("overlay_ball_tap_action", "tasks").orEmpty(),
            "overlay_ball_double_action" to prefs.getString("overlay_ball_double_action", "open").orEmpty(),
            "overlay_ball_long_action" to prefs.getString("overlay_ball_long_action", "snap").orEmpty(),
            "overlay_bubble_tap_action" to prefs.getString("overlay_bubble_tap_action", "open").orEmpty(),
            "overlay_bubble_double_action" to prefs.getString("overlay_bubble_double_action", "toggle_bubble").orEmpty(),
            "overlay_bubble_long_action" to prefs.getString("overlay_bubble_long_action", "open").orEmpty(),
        )
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionGranted = canDrawOverlays(context)
        if (permissionGranted) {
            prefs.edit().putBoolean("overlay_enabled", true).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = true
            CodexOverlayService.start(context)
            Toast.makeText(context, tr(lang, "Codex 悬浮窗已开启", "Codex floating window enabled"), Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean("overlay_enabled", false).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = false
            Toast.makeText(context, tr(lang, "尚未获得悬浮窗权限", "Floating window permission was not granted"), Toast.LENGTH_SHORT).show()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        completionNotification = granted
        prefs.edit().putBoolean("completion_notification", granted).apply()
        if (!granted) Toast.makeText(context, tr(lang, "需要通知权限才能发送任务提醒", "Notification permission is required for task alerts"), Toast.LENGTH_SHORT).show()
    }
    val batterySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryUnrestricted = isBatteryUnrestricted(context)
    }

    fun enableOverlay() {
        permissionGranted = canDrawOverlays(context)
        if (permissionGranted) {
            prefs.edit().putBoolean("overlay_enabled", true).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = true
            CodexOverlayService.start(context)
            Toast.makeText(context, tr(lang, "Codex 悬浮窗已开启", "Codex floating window enabled"), Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putBoolean("overlay_enable_pending", true).apply()
        runCatching {
            overlayPermissionLauncher.launch(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure {
            prefs.edit().putBoolean("overlay_enable_pending", false).apply()
            Toast.makeText(context, tr(lang, "无法打开悬浮窗权限设置", "Unable to open floating window permission settings"), Toast.LENGTH_SHORT).show()
        }
    }

    fun disableOverlay() {
        prefs.edit().putBoolean("overlay_enabled", false).putBoolean("overlay_enable_pending", false).apply()
        overlayEnabled = false
        CodexOverlayService.stop(context)
        Toast.makeText(context, tr(lang, "悬浮窗已关闭", "Floating window disabled"), Toast.LENGTH_SHORT).show()
    }

    SettingsScaffold(
        tr(lang, "悬浮窗与后台", "Floating window & background"),
        tr(lang, "悬浮球、任务提醒、手势与后台连接", "Bubble, task alerts, gestures and background connection"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                OverlayStatusCard(
                    lang = lang,
                    enabled = overlayEnabled,
                    permissionGranted = permissionGranted,
                    onAction = { if (overlayEnabled) disableOverlay() else enableOverlay() },
                )
            }
            item { SettingsSection(tr(lang, "悬浮球", "Floating bubble")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "启用悬浮图标", "Enable floating icon"),
                    if (permissionGranted) tr(lang, "在其他应用上层显示 Codex 悬浮球", "Show the Codex bubble above other apps")
                    else tr(lang, "开启时将跳转系统页面申请显示权限", "Enabling opens the system overlay permission page"),
                    overlayEnabled,
                ) { enabled -> if (enabled) enableOverlay() else disableOverlay() }
            }
            item {
                ToggleSettingsRow(
                    HugeIcons.ArrowRight01,
                    tr(lang, "拖动后自动贴边", "Snap to edge after dragging"),
                    tr(lang, "松手后吸附到最近边缘，并按屏幕高度比例保存位置", "Attach to the nearest edge and preserve the vertical position"),
                    edgeSnap,
                ) {
                    edgeSnap = it
                    prefs.edit().putBoolean("overlay_edge_snap", it).apply()
                }
            }
            item { SettingsSection(tr(lang, "悬浮球手势", "Bubble gestures")) }
            items(overlayBallGestureSettings(lang), key = { it.key }) { setting ->
                NavigationSettingsRow(
                    HugeIcons.Sparkles,
                    setting.title,
                    overlayActionLabel(lang, gestureValues[setting.key] ?: setting.fallback),
                ) { selectedGesture = setting }
            }
            item { SettingsSection(tr(lang, "任务气泡手势", "Task bubble gestures")) }
            items(overlayTaskBubbleGestureSettings(lang), key = { it.key }) { setting ->
                NavigationSettingsRow(
                    HugeIcons.Code,
                    setting.title,
                    overlayActionLabel(lang, gestureValues[setting.key] ?: setting.fallback),
                ) { selectedGesture = setting }
            }
            item { SettingsSection(tr(lang, "任务提醒", "Task alerts")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "悬浮任务气泡", "Floating task bubble"),
                    tr(lang, "任务完成时在悬浮图标旁显示完成气泡", "Show a completion bubble beside the floating icon"),
                    completionBubble,
                ) {
                    completionBubble = it
                    prefs.edit().putBoolean("completion_bubble", it).apply()
                }
            }
            item {
                ToggleSettingsRow(
                    HugeIcons.Refresh03,
                    tr(lang, "系统任务通知", "System task notifications"),
                    tr(lang, "任务完成、失败、待答、待审批或等待执行计划时发送可点击通知", "Send tappable alerts for completion, failure, questions, approvals, and pending plans"),
                    completionNotification,
                ) { enabled ->
                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        completionNotification = enabled
                        prefs.edit().putBoolean("completion_notification", enabled).apply()
                    }
                }
            }
            item { SettingsSection(tr(lang, "后台连接", "Background connection")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Refresh03,
                    tr(lang, "任务后台保持", "Background task keep-alive"),
                    tr(lang, "任务运行时保持 CPU 与网络连接；悬浮窗开启时服务会常驻", "Keep CPU and network active while tasks run; the service stays active with the overlay"),
                    keepAlive,
                ) {
                    keepAlive = it
                    prefs.edit().putBoolean(CodexOverlayService.PREF_NATIVE_TASK_KEEP_ALIVE, it).apply()
                    CodexOverlayService.syncKeepAlive(context)
                }
            }
            item {
                NavigationSettingsRow(
                    HugeIcons.Settings03,
                    tr(lang, "电池优化", "Battery optimization"),
                    if (batteryUnrestricted) tr(lang, "已允许后台不受限", "Background use is unrestricted")
                    else tr(lang, "受系统限制 · 建议允许后台运行", "System restricted · allowing background use is recommended"),
                ) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || batteryUnrestricted) {
                        Toast.makeText(context, tr(lang, "Codex 已允许后台不受限", "Codex is already unrestricted in the background"), Toast.LENGTH_SHORT).show()
                    } else {
                        val request = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { batterySettingsLauncher.launch(request) }
                            .onFailure {
                                batterySettingsLauncher.launch(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                ) {
                    Text(
                        tr(lang, "悬浮窗开启后会显示低优先级常驻通知，并使用 CPU 与 Wi‑Fi 保持来降低切到后台后断开连接的概率。", "When enabled, the floating window uses a low-priority foreground notification plus CPU and Wi-Fi locks to reduce background disconnects."),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    selectedGesture?.let { setting ->
        SettingsChoiceDialog(
            title = setting.title,
            options = overlayActionOptions(lang),
            selected = gestureValues[setting.key] ?: setting.fallback,
            onDismiss = { selectedGesture = null },
        ) { value ->
            gestureValues[setting.key] = value
            prefs.edit().putString(setting.key, value).apply()
            selectedGesture = null
        }
    }
}

@Composable
private fun OverlayStatusCard(
    lang: String,
    enabled: Boolean,
    permissionGranted: Boolean,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = .36f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            HugeIcons.Sparkles,
                            null,
                            Modifier.size(27.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            enabled -> tr(lang, "悬浮窗正在运行", "Floating window is running")
                            permissionGranted -> tr(lang, "悬浮窗已关闭", "Floating window is off")
                            else -> tr(lang, "需要显示权限", "Display permission required")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            enabled -> tr(lang, "可拖动悬浮球并查看任务状态", "Drag the bubble and inspect task status")
                            permissionGranted -> tr(lang, "权限已就绪，可以随时开启", "Permission is ready; enable it at any time")
                            else -> tr(lang, "系统需要允许 Fcode 显示在其他应用上层", "Android must allow Fcode to appear above other apps")
                        },
                        Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when {
                        enabled -> tr(lang, "关闭悬浮窗", "Disable floating window")
                        permissionGranted -> tr(lang, "开启悬浮窗", "Enable floating window")
                        else -> tr(lang, "授权并开启", "Grant permission & enable")
                    },
                )
            }
        }
    }
}

private fun overlayBallGestureSettings(lang: String): List<OverlayGestureSetting> = listOf(
    OverlayGestureSetting(tr(lang, "单击悬浮球", "Tap floating bubble"), "overlay_ball_tap_action", "tasks"),
    OverlayGestureSetting(tr(lang, "双击悬浮球", "Double-tap floating bubble"), "overlay_ball_double_action", "open"),
    OverlayGestureSetting(tr(lang, "长按悬浮球", "Long-press floating bubble"), "overlay_ball_long_action", "snap"),
)

private fun overlayTaskBubbleGestureSettings(lang: String): List<OverlayGestureSetting> = listOf(
    OverlayGestureSetting(tr(lang, "单击任务气泡", "Tap task bubble"), "overlay_bubble_tap_action", "open"),
    OverlayGestureSetting(tr(lang, "双击任务气泡", "Double-tap task bubble"), "overlay_bubble_double_action", "toggle_bubble"),
    OverlayGestureSetting(tr(lang, "长按任务气泡", "Long-press task bubble"), "overlay_bubble_long_action", "open"),
)

private fun overlayActionOptions(lang: String): List<Pair<String, String>> = listOf(
    "tasks" to tr(lang, "显示当前任务列表", "Show current tasks"),
    "open" to tr(lang, "打开 Codex", "Open Codex"),
    "snap" to tr(lang, "贴到最近屏幕边缘", "Snap to nearest edge"),
    "toggle_bubble" to tr(lang, "显示或隐藏任务气泡", "Show or hide task bubble"),
    "hide_overlay" to tr(lang, "关闭悬浮窗", "Disable floating window"),
    "none" to tr(lang, "不执行操作", "Do nothing"),
)

private fun overlayActionLabel(lang: String, value: String): String =
    overlayActionOptions(lang).firstOrNull { it.first == value }?.second
        ?: tr(lang, "不执行操作", "Do nothing")

internal fun canDrawOverlays(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)

internal fun isBatteryUnrestricted(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true
    val power = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    return power?.isIgnoringBatteryOptimizations(context.packageName) == true
}


