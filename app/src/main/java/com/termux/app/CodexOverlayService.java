package com.termux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Foreground keep-alive service with a draggable Codex overlay and completion bubble. */
public final class CodexOverlayService extends Service {
    static final String ACTION_SHOW = "com.ilyop.codex.overlay.SHOW";
    static final String ACTION_HIDE = "com.ilyop.codex.overlay.HIDE";
    static final String ACTION_TASK_COMPLETED = "com.ilyop.codex.overlay.TASK_COMPLETED";
    static final String ACTION_SYNC_KEEP_ALIVE = "com.ilyop.codex.overlay.SYNC_KEEP_ALIVE";
    static final String PREF_NATIVE_TASK_KEEP_ALIVE = "native_task_keep_alive";
    private static final String CHANNEL_BACKGROUND = "codex_background";
    private static final String CHANNEL_COMPLETION = "codex_completion";
    private static final int FOREGROUND_ID = 7411;
    private static final int COMPLETION_ID = 7412;
    private static final long DOUBLE_TAP_MS = 280L;
    private static final long LONG_PRESS_MS = 520L;
    static final String[] GESTURE_ACTION_VALUES = {"tasks", "open", "snap", "toggle_bubble", "hide_overlay", "none"};
    static final String[] GESTURE_ACTION_LABELS = {"\u663e\u793a\u5f53\u524d\u4efb\u52a1\u5217\u8868", "\u6253\u5f00 Codex WebUI", "\u8d34\u5230\u6700\u8fd1\u5c4f\u5e55\u8fb9\u7f18", "\u663e\u793a / \u9690\u85cf\u4efb\u52a1\u6c14\u6ce1", "\u5173\u95ed\u60ac\u6d6e\u7a97", "\u4e0d\u6267\u884c\u64cd\u4f5c"};
    static String actionLabel(String value) {
        for (int i = 0; i < GESTURE_ACTION_VALUES.length; i++) if (GESTURE_ACTION_VALUES[i].equals(value)) return GESTURE_ACTION_LABELS[i];
        return GESTURE_ACTION_LABELS[5];
    }

    private WindowManager windowManager;
    private ImageView iconView;
    private LinearLayout bubbleView;
    private WindowManager.LayoutParams iconParams;
    private WindowManager.LayoutParams bubbleParams;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    static void start(Context context) {
        Intent intent = new Intent(context, CodexOverlayService.class).setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.startService(new Intent(context, CodexOverlayService.class).setAction(ACTION_HIDE));
    }

    static void syncKeepAlive(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        boolean overlay = prefs.getBoolean("overlay_enabled", false);
        boolean nativeTask = prefs.getBoolean(PREF_NATIVE_TASK_KEEP_ALIVE, true)
            && CodexNativeRuntime.exists() && CodexTaskStore.hasRunningTasks(app);
        Intent intent = new Intent(app, CodexOverlayService.class).setAction(ACTION_SYNC_KEEP_ALIVE);
        if (overlay || nativeTask) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent);
            else app.startService(intent);
        } else {
            app.stopService(intent);
        }
    }

    static void notifyTaskCompleted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        if (prefs.getBoolean("overlay_enabled", false)) {
            Intent intent = new Intent(context, CodexOverlayService.class).setAction(ACTION_TASK_COMPLETED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } else if (prefs.getBoolean("completion_notification", false)) {
            showCompletionNotification(context);
        }
    }

    static void showCompletionNotification(Context context) {
        if (context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
                .getBoolean("native_unified_notifications_v1", false)) return;
        ensureChannels(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent pending = PendingIntent.getActivity(context, 7412, openCodexIntent(context), pendingFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, CHANNEL_COMPLETION) : new Notification.Builder(context);
        builder.setSmallIcon(com.termux.R.drawable.ic_service_notification)
            .setContentTitle("Codex \u4efb\u52a1\u5df2\u5b8c\u6210")
            .setContentText(CodexNativeRuntime.exists() ? "\u70b9\u51fb\u8fd4\u56de\u539f\u751f\u5bf9\u8bdd\u67e5\u770b\u7ed3\u679c" : "\u70b9\u51fb\u8fd4\u56de WebUI \u67e5\u770b\u7ed3\u679c")
            .setColor(Color.rgb(42, 119, 81))
            .setAutoCancel(true)
            .setContentIntent(pending);
        manager.notify(COMPLETION_ID, builder.build());
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureChannels(this);
        startForeground(FOREGROUND_ID, foregroundNotification());
        acquireKeepAliveLocks();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SYNC_KEEP_ALIVE : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            prefs.edit().putBoolean("overlay_enabled", false).apply();
            removeOverlayViews();
        }
        boolean overlayEnabled = prefs.getBoolean("overlay_enabled", false);
        boolean nativeTaskKeepAlive = prefs.getBoolean(PREF_NATIVE_TASK_KEEP_ALIVE, true)
            && CodexNativeRuntime.exists() && CodexTaskStore.hasRunningTasks(this);
        if (!overlayEnabled && !nativeTaskKeepAlive) {
            if (ACTION_TASK_COMPLETED.equals(action) && prefs.getBoolean("completion_notification", false)) {
                showCompletionNotification(this);
            }
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        if (overlayEnabled) showOverlay(); else removeOverlayViews();
        startForeground(FOREGROUND_ID, foregroundNotification());
        if (ACTION_TASK_COMPLETED.equals(action)) {
            if (overlayEnabled && prefs.getBoolean("completion_bubble", true)) showTaskBubble(true);
            if (prefs.getBoolean("completion_notification", false)) showCompletionNotification(this);
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.postDelayed(() -> { if (iconView != null) { restoreSafePosition(); updateBubblePosition(); } }, 180L);
    }

    private Notification foregroundNotification() {
        boolean nativeTask = prefs.getBoolean(PREF_NATIVE_TASK_KEEP_ALIVE, true)
            && CodexNativeRuntime.exists() && CodexTaskStore.hasRunningTasks(this);
        PendingIntent pending = PendingIntent.getActivity(this, 7411, openCodexIntent(this), pendingFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_BACKGROUND) : new Notification.Builder(this);
        return builder.setSmallIcon(com.termux.R.drawable.ic_service_notification)
            .setContentTitle(nativeTask ? "Codex \u539f\u751f\u4efb\u52a1\u6b63\u5728\u540e\u53f0\u8fd0\u884c" : "Codex \u6b63\u5728\u540e\u53f0\u8fd0\u884c")
            .setContentText(nativeTask ? "\u70b9\u51fb\u8fd4\u56de\u539f\u751f\u5bf9\u8bdd\uff1b\u5df2\u542f\u7528 CPU \u548c Wi-Fi \u4fdd\u6301" : "\u5df2\u4fdd\u6301 WebUI \u8fde\u63a5\u548c\u5185\u7f6e\u4ee3\u7406")
            .setColor(Color.rgb(42, 119, 81))
            .setOngoing(true)
            .setContentIntent(pending)
            .build();
    }


    private static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel background = new NotificationChannel(CHANNEL_BACKGROUND,
            "Codex \u540e\u53f0\u8fd0\u884c", NotificationManager.IMPORTANCE_LOW);
        background.setDescription("\u4fdd\u6301 WebUI\u3001Termux \u548c\u5185\u7f6e\u4ee3\u7406\u8fde\u63a5");
        NotificationChannel completion = new NotificationChannel(CHANNEL_COMPLETION,
            "Codex \u4efb\u52a1\u5b8c\u6210", NotificationManager.IMPORTANCE_DEFAULT);
        completion.setDescription("Codex \u4efb\u52a1\u5b8c\u6210\u63d0\u9192");
        manager.createNotificationChannel(background);
        manager.createNotificationChannel(completion);
    }

    private void showOverlay() {
        if (iconView != null || !Settings.canDrawOverlays(this)) return;
        iconView = new ImageView(this);
        iconView.setImageResource(com.termux.R.drawable.ic_codex_logo);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setElevation(dp(8));
        int size = dp(58);
        iconParams = new WindowManager.LayoutParams(size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        iconParams.gravity = Gravity.TOP | Gravity.START;
        restoreSafePosition();
        iconView.setContentDescription("Codex \u60ac\u6d6e\u7403");
        iconView.setOnTouchListener(new GestureTouchListener(true));
        try { windowManager.addView(iconView, iconParams); }
        catch (Exception error) { iconView = null; }
    }

    private void showTaskBubble(boolean autoDismiss) {
        if (iconView == null) return;
        removeBubble();
        bubbleView = new LinearLayout(this);
        bubbleView.setOrientation(LinearLayout.VERTICAL);
        bubbleView.setPadding(dp(16), dp(13), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 248, 242)); background.setCornerRadius(dp(20));
        bubbleView.setBackground(background); bubbleView.setElevation(dp(8));
        TextView header = taskText("\u5f53\u524d\u4efb\u52a1", 15, Color.rgb(42, 119, 81), true);
        bubbleView.addView(header);
        List<CodexTaskStore.Task> tasks = CodexTaskStore.current(this);
        if (tasks.isEmpty()) {
            TextView empty = taskText("\u6682\u65e0\u4efb\u52a1\u8bb0\u5f55\n\u4ece WebUI \u53d1\u8d77\u4efb\u52a1\u540e\u4f1a\u663e\u793a\u5728\u8fd9\u91cc", 13, Color.DKGRAY, false);
            empty.setPadding(0, dp(9), 0, 0); bubbleView.addView(empty);
        } else {
            int shown = Math.min(5, tasks.size());
            for (int i = 0; i < shown; i++) {
                CodexTaskStore.Task task = tasks.get(i);
                String state = CodexTaskStore.RUNNING.equals(task.state) ? "\u25cf \u8fd0\u884c\u4e2d" : (CodexTaskStore.FAILED.equals(task.state) ? "! \u5931\u8d25" : "\u2713 \u5df2\u5b8c\u6210");
                TextView row = taskText(task.title + "\n" + state + " \u00b7 " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(task.updatedAt)), 13, Color.rgb(55, 55, 53), false);
                row.setMaxLines(3); row.setPadding(0, dp(9), 0, 0); bubbleView.addView(row);
            }
        }
        TextView hint = taskText("\u70b9\u51fb\u6216\u957f\u6309\u6c14\u6ce1\u53ef\u6267\u884c\u81ea\u5b9a\u4e49\u64cd\u4f5c", 11, Color.GRAY, false);
        hint.setPadding(0, dp(10), 0, 0); bubbleView.addView(hint);
        bubbleView.setContentDescription("Codex \u5f53\u524d\u4efb\u52a1\u5217\u8868");
        bubbleView.setOnTouchListener(new GestureTouchListener(false));
        bubbleParams = new WindowManager.LayoutParams(dp(286), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START; updateBubblePosition();
        try { windowManager.addView(bubbleView, bubbleParams); } catch (Exception error) { bubbleView = null; return; }
        if (autoDismiss) handler.postDelayed(this::removeBubble, 5200L);
    }

    private TextView taskText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }

    private void updateBubblePosition() {
        if (bubbleParams == null || iconParams == null) return;
        Point screen = screenSize(); int width = dp(286), margin = dp(8);
        boolean right = iconParams.x + iconParams.width / 2 > screen.x / 2;
        bubbleParams.x = right ? iconParams.x - width - dp(10) : iconParams.x + iconParams.width + dp(10);
        bubbleParams.x = clamp(bubbleParams.x, margin, screen.x - width - margin);
        int height = bubbleView != null && bubbleView.getHeight() > 0 ? bubbleView.getHeight() : dp(230);
        bubbleParams.y = clamp(iconParams.y, safeTop(), screen.y - safeBottom() - height);
        if (bubbleView != null && bubbleView.isAttachedToWindow()) try { windowManager.updateViewLayout(bubbleView, bubbleParams); } catch (Exception ignored) {}
    }

    private void removeBubble() {
        if (bubbleView != null) { try { windowManager.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; bubbleParams = null; }
    }

    private static Intent openCodexIntent(Context context) {
        Intent intent;
        if (CodexNativeRuntime.exists()) {
            intent = new Intent(context, CodexChatActivity.class);
        } else {
            intent = new Intent(context, CodexHomeActivity.class).setAction(CodexHomeActivity.ACTION_OPEN_WEBUI);
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private void openCodex() { startActivity(openCodexIntent(this)); }

    private void performGesture(String key, String fallback) {
        String action = prefs.getString(key, fallback);
        if ("tasks".equals(action)) showTaskBubble(false);
        else if ("open".equals(action)) { removeBubble(); openCodex(); }
        else if ("snap".equals(action)) snapToEdge();
        else if ("toggle_bubble".equals(action)) { if (bubbleView == null) showTaskBubble(false); else removeBubble(); }
        else if ("hide_overlay".equals(action)) {
            prefs.edit().putBoolean("overlay_enabled", false).apply();
            removeOverlayViews();
            if (!CodexNativeRuntime.exists() || !prefs.getBoolean(PREF_NATIVE_TASK_KEEP_ALIVE, true)
                    || !CodexTaskStore.hasRunningTasks(this)) stopSelfSafely();
            else startForeground(FOREGROUND_ID, foregroundNotification());
        }
    }

    private final class GestureTouchListener implements View.OnTouchListener {
        final boolean icon; int startX, startY; float downX, downY; boolean dragging, longPressed; long lastTap; Runnable single;
        final Runnable longPress;
        GestureTouchListener(boolean icon) {
            this.icon = icon;
            this.longPress = () -> { if (!dragging) { longPressed = true; performGesture(icon ? "overlay_ball_long_action" : "overlay_bubble_long_action", icon ? "snap" : "open"); } };
        }
        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (icon) { startX = iconParams.x; startY = iconParams.y; }
                    downX = event.getRawX(); downY = event.getRawY(); dragging = false; longPressed = false;
                    handler.postDelayed(longPress, LONG_PRESS_MS); return true;
                case MotionEvent.ACTION_MOVE:
                    if (!icon) return true;
                    int dx = Math.round(event.getRawX() - downX), dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) + Math.abs(dy) > dp(8)) { dragging = true; handler.removeCallbacks(longPress); removeBubble(); }
                    if (dragging) { Point screen = screenSize(); iconParams.x = clamp(startX + dx, dp(8), screen.x - iconParams.width - dp(8)); iconParams.y = clamp(startY + dy, safeTop(), screen.y - safeBottom() - iconParams.height); updateIcon(); }
                    return true;
                case MotionEvent.ACTION_CANCEL: handler.removeCallbacks(longPress); return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (dragging) { if (prefs.getBoolean("overlay_edge_snap", true)) snapToEdge(); else savePosition(); return true; }
                    if (longPressed) return true;
                    long now = System.currentTimeMillis();
                    if (now - lastTap <= DOUBLE_TAP_MS) { if (single != null) handler.removeCallbacks(single); lastTap = 0; performGesture(icon ? "overlay_ball_double_action" : "overlay_bubble_double_action", icon ? "open" : "toggle_bubble"); }
                    else { lastTap = now; single = () -> { if (lastTap == now) { lastTap = 0; performGesture(icon ? "overlay_ball_tap_action" : "overlay_bubble_tap_action", icon ? "tasks" : "open"); } }; handler.postDelayed(single, DOUBLE_TAP_MS); }
                    return true;
                default: return false;
            }
        }
    }

    private void snapToEdge() {
        Point screen = screenSize(); iconParams.x = iconParams.x + iconParams.width / 2 < screen.x / 2 ? dp(8) : screen.x - iconParams.width - dp(8);
        updateIcon(); savePosition();
    }

    private void restoreSafePosition() {
        if (iconParams == null) return;
        Point screen = screenSize(); boolean right = prefs.getBoolean("overlay_edge_right", true);
        iconParams.x = right ? screen.x - iconParams.width - dp(8) : dp(8);
        float fraction = prefs.getFloat("overlay_y_fraction", -1f); int range = Math.max(1, screen.y - safeTop() - safeBottom() - iconParams.height);
        iconParams.y = fraction >= 0 ? safeTop() + Math.round(fraction * range) : prefs.getInt("overlay_y", safeTop() + dp(140));
        iconParams.y = clamp(iconParams.y, safeTop(), screen.y - safeBottom() - iconParams.height); updateIcon(); savePosition();
    }

    private void savePosition() {
        Point screen = screenSize(); int range = Math.max(1, screen.y - safeTop() - safeBottom() - iconParams.height);
        float fraction = Math.max(0f, Math.min(1f, (iconParams.y - safeTop()) / (float) range));
        prefs.edit().putBoolean("overlay_edge_right", iconParams.x + iconParams.width / 2 >= screen.x / 2).putFloat("overlay_y_fraction", fraction).putInt("overlay_x", iconParams.x).putInt("overlay_y", iconParams.y).apply();
    }
    private void updateIcon() { if (iconView != null && iconView.isAttachedToWindow()) try { windowManager.updateViewLayout(iconView, iconParams); } catch (Exception ignored) {} updateBubblePosition(); }
    private Point screenSize() { Point point = new Point(); windowManager.getDefaultDisplay().getRealSize(point); return point; }
    private int safeTop() { return dp(8) + systemDimension("status_bar_height"); }
    private int safeBottom() { return dp(8) + systemDimension("navigation_bar_height"); }
    private int systemDimension(String name) { int id = getResources().getIdentifier(name, "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : 0; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }

    private void acquireKeepAliveLocks() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CodexMobile:Overlay");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) {}
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CodexMobile:OverlayWifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception ignored) {}
    }

    private void releaseKeepAliveLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        wakeLock = null; wifiLock = null;
    }

    private void removeOverlayViews() {
        removeBubble();
        if (iconView != null) {
            try { windowManager.removeView(iconView); } catch (Exception ignored) {}
            iconView = null;
        }
    }

    private void stopSelfSafely() {
        removeOverlayViews();
        releaseKeepAliveLocks();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopSelfSafely();
        super.onDestroy();
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
