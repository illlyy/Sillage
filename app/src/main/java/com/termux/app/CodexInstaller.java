package com.termux.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import javax.net.ssl.SSLException;
import java.util.zip.GZIPInputStream;

/** Installs the small Codex runtime after the user explicitly requests it. */
final class CodexInstaller {
    private static final String TAG = "CodexInstaller";
    private static final String CODEX_URL =
        "https://github.com/openai/codex/releases/latest/download/codex-aarch64-unknown-linux-musl.tar.gz";
    private static final int PRIMARY = Color.rgb(108, 74, 58);
    private static final int PRIMARY_SOFT = Color.rgb(243, 225, 213);
    private static final int TEXT = Color.rgb(31, 31, 31);
    private static final int MUTED = Color.rgb(95, 83, 73);
    private static final int TRACK = Color.rgb(226, 207, 191);

    private CodexInstaller() {}

    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        File codex = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
        if (codex.canExecute()) {
            ensureLayout();
            whenDone.run();
            return;
        }

        if (!supportsArm64()) {
            new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_codex_logo)
                .setTitle("暂不支持此设备")
                .setMessage("当前 Codex 安装包仅支持 ARM64（arm64-v8a）。\n\n设备架构：" + String.join(", ", Build.SUPPORTED_ABIS))
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        install(activity, whenDone);
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    private static void install(final Activity activity, final Runnable whenDone) {
        if (!canShowUi(activity)) return;
        final InstallerDialog progress = InstallerDialog.show(
            activity,
            "正在安装 Codex",
            "安装包来自 OpenAI 官方 Release，完成前请保持应用在前台。"
        );

        new Thread(() -> {
            File archive = new File(activity.getCacheDir(), "codex-arm64.tar.gz.part");
            try {
                ensureLayout();
                progress.showStage("连接下载服务器", "正在获取官方 ARM64 安装包…");
                download(activity, progress, archive);
                progress.showStage("正在安装", "正在解压并写入应用私有目录…");
                File destination = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
                extractCodex(archive, destination);
                if (!destination.setExecutable(true, false) && !destination.canExecute()) {
                    throw new IOException("无法设置 Codex 可执行权限");
                }
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!canShowUi(activity)) return;
                    Toast.makeText(activity, "Codex CLI 已安装", Toast.LENGTH_SHORT).show();
                    if (whenDone != null) whenDone.run();
                });
            } catch (Exception e) {
                Log.e(TAG, "Codex installation failed: " + e.getClass().getSimpleName());
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!canShowUi(activity)) return;
                    new MaterialAlertDialogBuilder(activity)
                        .setIcon(R.drawable.ic_codex_logo)
                        .setTitle("Codex 安装未完成")
                        .setMessage(userFacingInstallError(e) + "\n\n重试不会影响已有会话、项目或配置。")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("重试", (d, w) ->
                            activity.getWindow().getDecorView().post(() -> install(activity, whenDone)))
                        .show();
                });
            } finally {
                if (archive.exists() && !archive.delete()) {
                    Log.w(TAG, "Could not remove the temporary Codex archive");
                }
            }
        }, "CodexInstaller").start();
    }

    private static String download(Activity activity, InstallerDialog progress, File output) throws Exception {
        MihomoManager mihomo = MihomoManager.get(activity);
        boolean throughMihomo = activity.getSharedPreferences("codex_mobile", Activity.MODE_PRIVATE)
            .getBoolean("mihomo_route_api", false);
        if (throughMihomo) mihomo.start();
        URL target = new URL(CODEX_URL);
        HttpURLConnection connection = (HttpURLConnection) (throughMihomo
            ? target.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mihomo.mixedPort())))
            : target.openConnection());
        try {
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "Codex-Mobile-Android/0.1");
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("下载服务器返回 HTTP " + status);
            long total = connection.getContentLengthLong();
            progress.beginDownload(total);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long received = 0;
            byte[] buffer = new byte[128 * 1024];
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                int count;
                int lastPercent = -1;
                long lastUnknownUpdate = 0L;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    received += count;
                    if (total > 0) {
                        int percent = (int) Math.min(100, received * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.updateDownload(received, total, percent);
                        }
                    } else if (received - lastUnknownUpdate >= 512 * 1024) {
                        lastUnknownUpdate = received;
                        progress.updateDownload(received, -1L, -1);
                    }
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void extractCodex(File archive, File destination) throws IOException {
        File temp = new File(destination.getParentFile(), "codex.new");
        if (temp.exists()) temp.delete();
        boolean found = false;
        try (InputStream raw = new BufferedInputStream(new FileInputStream(archive));
             GZIPInputStream tar = new GZIPInputStream(raw, 128 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
            byte[] header = new byte[512];
            while (readFully(tar, header)) {
                if (isZeroBlock(header)) break;
                String name = readString(header, 0, 100);
                long size = readOctal(header, 124, 12);
                boolean target = name.equals("codex") || name.endsWith("/codex") || name.contains("codex-aarch64-unknown-linux-musl");
                if (target && header[156] != '5') {
                    copyExactly(tar, out, size);
                    found = true;
                } else {
                    skipExactly(tar, size);
                }
                long padding = (512 - (size % 512)) % 512;
                skipExactly(tar, padding);
                if (found) break;
            }
        }
        if (!found || temp.length() < 1024 * 1024) {
            temp.delete();
            throw new IOException("下载包中没有找到 Codex 可执行文件");
        }
        if (destination.exists() && !destination.delete()) throw new IOException("无法替换旧版本 Codex");
        if (!temp.renameTo(destination)) throw new IOException("无法提交 Codex 安装文件");
    }

    private static void ensureLayout() {
        new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH).mkdirs();
        new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH).mkdirs();
        new File(TermuxConstants.TERMUX_HOME_DIR_PATH).mkdirs();
        new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "projects").mkdirs();
    }

    static void setupStorageSymlinks(final android.content.Context context) {
        Toast.makeText(context, "测试版暂未启用共享存储映射", Toast.LENGTH_SHORT).show();
    }

    static void setupDevelopmentExtensions(final Activity activity, final Runnable whenDone) {
        File marker = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".codex-mobile-environment-v1");
        if (marker.exists()) {
            whenDone.run();
            return;
        }

        if (!canShowUi(activity)) return;
        final InstallerDialog progress = InstallerDialog.show(
            activity,
            "正在准备开发环境",
            "将安装 Git、Python、Node.js 等常用工具，这可能需要几分钟。"
        );
        progress.showStage("正在安装工具", "更新软件源并配置 Termux 开发环境…");

        new Thread(() -> {
            File log = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "codex-environment-setup.log");
            try {
                File script = writeEnvironmentSetupScript(marker);
                ProcessBuilder builder = new ProcessBuilder(
                    TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash", script.getAbsolutePath());
                builder.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
                builder.environment().put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
                builder.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
                builder.redirectErrorStream(true);
                Process setupProcess = builder.start();
                try (InputStream input = setupProcess.getInputStream();
                     FileOutputStream output = new FileOutputStream(log, true)) {
                    byte[] buffer = new byte[8 * 1024];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count > 0) output.write(buffer, 0, count);
                    }
                }
                int exitCode = setupProcess.waitFor();
                if (exitCode != 0) throw new IOException("Environment setup exited with code " + exitCode + ". Log: " + log.getAbsolutePath());
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!canShowUi(activity)) return;
                    Toast.makeText(activity, "Termux 开发环境已就绪", Toast.LENGTH_LONG).show();
                    if (whenDone != null) whenDone.run();
                });
            } catch (Exception error) {
                Log.e(TAG, "Development environment setup failed: " + error.getClass().getSimpleName());
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!canShowUi(activity)) return;
                    new MaterialAlertDialogBuilder(activity)
                        .setIcon(R.drawable.ic_codex_logo)
                        .setTitle("开发环境未完成")
                        .setMessage("工具安装过程中出现问题。Codex CLI 仍可正常使用，你可以稍后再试。")
                        .setNegativeButton("稍后", (dialog, which) -> { if (whenDone != null) whenDone.run(); })
                        .setPositiveButton("重试", (dialog, which) ->
                            activity.getWindow().getDecorView().post(() -> setupDevelopmentExtensions(activity, whenDone)))
                        .show();
                });
            }
        }, "CodexEnvironmentInstaller").start();
    }

    private static File writeEnvironmentSetupScript(File marker) throws IOException {
        File script = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".codex-mobile-setup-environment.sh");
        File tempMarker = new File(marker.getAbsolutePath() + ".tmp");
        String contents = "#!/data/data/com.ilyop.codex/xusr/bin/bash\n" +
            "set -euo pipefail\n" +
            "export DEBIAN_FRONTEND=noninteractive\n" +
            "export APT_LISTCHANGES_FRONTEND=none\n" +
            "printf 'deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main\n' > \"$PREFIX/etc/apt/sources.list\"\n" +
            "apt-get update\n" +
            "apt-get -o Dpkg::Options::=--force-confold upgrade -y\n" +
            "apt-get install -y git curl wget openssh python nodejs-lts clang make cmake ninja pkg-config jq ripgrep fd findutils coreutils diffutils patch tar unzip zip gzip sed grep less nano vim tmux procps openssl\n" +
            "for package in rust golang man; do apt-get install -y \"$package\" || printf 'Optional package unavailable: %s\\n' \"$package\"; done\n" +
            "python -m pip install --upgrade setuptools wheel || true\n" +
            "printf 'ready\n' > '" + tempMarker.getAbsolutePath() + "'\n" +
            "mv '" + tempMarker.getAbsolutePath() + "' '" + marker.getAbsolutePath() + "'\n";
        try (FileOutputStream out = new FileOutputStream(script)) {
            out.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        if (!script.setExecutable(true, false) && !script.canExecute()) {
            throw new IOException("Cannot mark environment setup script executable");
        }
        return script;
    }

    private static boolean canShowUi(Activity activity) {
        return activity != null && !activity.isFinishing()
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    private static String userFacingInstallError(Exception error) {
        if (error instanceof SocketTimeoutException) {
            return "连接下载服务器超时，请检查网络后重试。";
        }
        if (error instanceof UnknownHostException) {
            return "无法连接下载服务器，请检查网络或代理设置。";
        }
        if (error instanceof SSLException) {
            return "安全连接建立失败，请检查系统时间和网络环境。";
        }
        String message = error.getMessage();
        if (message != null && message.startsWith("下载服务器返回 HTTP ")) {
            return message + "，请稍后重试。";
        }
        if ("下载包中没有找到 Codex 可执行文件".equals(message)
            || "无法设置 Codex 可执行权限".equals(message)
            || "无法替换旧版本 Codex".equals(message)
            || "无法提交 Codex 安装文件".equals(message)
            || "压缩包意外结束".equals(message)) {
            return message + "。";
        }
        return "下载或安装过程中出现问题，请检查网络和可用存储空间后重试。";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable roundedBackground(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static TextView dialogText(Activity activity, String value, float sizeSp, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        return text;
    }

    private static final class InstallerDialog {
        private final Activity activity;
        private final Dialog dialog;
        private final TextView stage;
        private final TextView detail;
        private final TextView percent;
        private final ProgressBar progress;

        private InstallerDialog(
            Activity activity,
            Dialog dialog,
            TextView stage,
            TextView detail,
            TextView percent,
            ProgressBar progress
        ) {
            this.activity = activity;
            this.dialog = dialog;
            this.stage = stage;
            this.detail = detail;
            this.percent = percent;
            this.progress = progress;
        }

        static InstallerDialog show(Activity activity, String titleValue, String subtitleValue) {
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            int horizontalPadding = dp(activity, 24);
            content.setPadding(horizontalPadding, dp(activity, 22), horizontalPadding, dp(activity, 20));

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout iconPlate = new FrameLayout(activity);
            iconPlate.setBackground(roundedBackground(PRIMARY_SOFT, dp(activity, 18)));
            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.ic_codex_logo);
            icon.setContentDescription("Codex");
            int iconSize = dp(activity, 42);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iconPlate.addView(icon, iconParams);
            header.addView(iconPlate, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));

            LinearLayout heading = new LinearLayout(activity);
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.setPadding(dp(activity, 16), 0, 0, 0);
            TextView title = dialogText(activity, titleValue, 21.0f, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            heading.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView subtitle = dialogText(activity, subtitleValue, 13.0f, MUTED);
            subtitle.setLineSpacing(dp(activity, 2), 1.0f);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = dp(activity, 6);
            heading.addView(subtitle, subtitleParams);
            header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            content.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView stage = dialogText(activity, "正在准备", 15.0f, TEXT);
            stage.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stageParams.topMargin = dp(activity, 24);
            content.addView(stage, stageParams);

            ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(true);
            progress.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY));
            progress.setProgressTintList(ColorStateList.valueOf(PRIMARY));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
            progress.setContentDescription("安装进度");
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 8));
            progressParams.topMargin = dp(activity, 12);
            content.addView(progress, progressParams);

            LinearLayout metadata = new LinearLayout(activity);
            metadata.setOrientation(LinearLayout.HORIZONTAL);
            metadata.setGravity(Gravity.CENTER_VERTICAL);
            TextView detail = dialogText(activity, "正在准备安装…", 12.5f, MUTED);
            detail.setSingleLine(false);
            metadata.addView(detail, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            TextView percent = dialogText(activity, "—", 12.5f, MUTED);
            percent.setGravity(Gravity.END);
            percent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            percentParams.leftMargin = dp(activity, 12);
            metadata.addView(percent, percentParams);
            LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metadataParams.topMargin = dp(activity, 9);
            content.addView(metadata, metadataParams);

            Dialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(content)
                .setCancelable(false)
                .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return new InstallerDialog(activity, dialog, stage, detail, percent, progress);
        }

        void showStage(String stageValue, String detailValue) {
            onUi(() -> {
                stage.setText(stageValue);
                detail.setText(detailValue);
                percent.setText("—");
                progress.setIndeterminate(true);
            });
        }

        void beginDownload(long totalBytes) {
            onUi(() -> {
                stage.setText("正在下载 Codex");
                progress.setMax(100);
                progress.setProgress(0);
                progress.setIndeterminate(totalBytes <= 0L);
                detail.setText(totalBytes > 0L ? "0 B / " + formatBytes(totalBytes) : "正在接收官方安装包…");
                percent.setText(totalBytes > 0L ? "0%" : "下载中");
            });
        }

        void updateDownload(long received, long total, int percentValue) {
            onUi(() -> {
                if (total > 0L && percentValue >= 0) {
                    progress.setIndeterminate(false);
                    progress.setProgress(percentValue);
                    detail.setText(formatBytes(received) + " / " + formatBytes(total));
                    percent.setText(percentValue + "%");
                } else {
                    progress.setIndeterminate(true);
                    detail.setText("已下载 " + formatBytes(received));
                    percent.setText("下载中");
                }
            });
        }

        void dismiss() {
            Runnable dismissAction = () -> {
                if (dialog.isShowing()) dialog.dismiss();
            };
            if (Looper.myLooper() == Looper.getMainLooper()) dismissAction.run();
            else activity.runOnUiThread(dismissAction);
        }

        private void onUi(Runnable action) {
            activity.runOnUiThread(() -> {
                if (canShowUi(activity) && dialog.isShowing()) action.run();
            });
        }
    }

    private static boolean readFully(InputStream in, byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            int count = in.read(data, offset, data.length - offset);
            if (count < 0) return offset != 0 ? false : false;
            offset += count;
        }
        return true;
    }
    private static boolean isZeroBlock(byte[] data) { for (byte b : data) if (b != 0) return false; return true; }
    private static String readString(byte[] data, int offset, int length) {
        int end = offset; while (end < offset + length && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }
    private static long readOctal(byte[] data, int offset, int length) {
        long value = 0; int end = offset + length;
        for (int i = offset; i < end; i++) { byte b = data[i]; if (b >= '0' && b <= '7') value = (value << 3) + (b - '0'); }
        return value;
    }
    private static void copyExactly(InputStream in, BufferedOutputStream out, long count) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        while (count > 0) { int n = in.read(buffer, 0, (int)Math.min(buffer.length, count)); if (n < 0) throw new IOException("压缩包意外结束"); out.write(buffer, 0, n); count -= n; }
        out.flush();
    }
    private static void skipExactly(InputStream in, long count) throws IOException {
        byte[] buffer = new byte[8192];
        while (count > 0) { int n = in.read(buffer, 0, (int)Math.min(buffer.length, count)); if (n < 0) throw new IOException("压缩包意外结束"); count -= n; }
    }
}
