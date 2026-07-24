package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Build;
import android.system.Os;
import android.widget.Toast;

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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Installs the small Codex runtime after the user explicitly requests it. */
final class CodexInstaller {
    private static final String CODEX_URL =
        "https://github.com/openai/codex/releases/latest/download/codex-aarch64-unknown-linux-musl.tar.gz";

    private CodexInstaller() {}

    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        File codex = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
        if (codex.canExecute()) {
            ensureLayout();
            whenDone.run();
            return;
        }

        if (!supportsArm64()) {
            new AlertDialog.Builder(activity)
                .setTitle("暂不支持此设备")
                .setMessage("当前测试版仅支持 ARM64（arm64-v8a）。\n检测到：" + String.join(", ", Build.SUPPORTED_ABIS))
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
        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("正在安装 Codex CLI");
        progress.setMessage("正在连接 GitHub…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            File archive = new File(activity.getCacheDir(), "codex-arm64.tar.gz.part");
            try {
                ensureLayout();
                String sha256 = download(activity, progress, archive);
                activity.runOnUiThread(() -> {
                    progress.setIndeterminate(true);
                    progress.setMessage("正在解压 Codex CLI…");
                });
                File destination = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
                extractCodex(archive, destination);
                if (!destination.setExecutable(true, false) && !destination.canExecute()) {
                    throw new IOException("无法设置 Codex 可执行权限");
                }
                archive.delete();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, "安装完成，SHA-256: " + sha256.substring(0, 12) + "…", Toast.LENGTH_LONG).show();
                    whenDone.run();
                });
            } catch (Exception e) {
                archive.delete();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(activity)
                        .setTitle("安装失败")
                        .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .setNegativeButton("关闭", null)
                        .setPositiveButton("重试", (d, w) -> install(activity, whenDone))
                        .show();
                });
            }
        }, "CodexInstaller").start();
    }

    private static String download(Activity activity, ProgressDialog progress, File output) throws Exception {
        MihomoManager mihomo = MihomoManager.get(activity);
        boolean throughMihomo = activity.getSharedPreferences("codex_mobile", Activity.MODE_PRIVATE)
            .getBoolean("mihomo_route_api", false);
        if (throughMihomo) mihomo.start();
        URL target = new URL(CODEX_URL);
        HttpURLConnection connection = (HttpURLConnection) (throughMihomo
            ? target.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mihomo.mixedPort())))
            : target.openConnection());
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "Codex-Mobile-Android/0.1");
        connection.setInstanceFollowRedirects(true);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IOException("下载服务器返回 HTTP " + status);
        long total = connection.getContentLengthLong();
        activity.runOnUiThread(() -> {
            progress.setIndeterminate(total <= 0);
            progress.setMax(1000);
            progress.setMessage("正在下载官方 ARM64 Codex…");
        });
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long received = 0;
        byte[] buffer = new byte[128 * 1024];
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            int count;
            int lastProgress = -1;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                received += count;
                if (total > 0) {
                    int value = (int) Math.min(1000, received * 1000 / total);
                    if (value != lastProgress) {
                        lastProgress = value;
                        final int shown = value;
                        final long bytes = received;
                        activity.runOnUiThread(() -> {
                            progress.setProgress(shown);
                            progress.setMessage(String.format(Locale.US, "正在下载官方 ARM64 Codex… %.1f / %.1f MB", bytes / 1048576.0, total / 1048576.0));
                        });
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b));
        return hex.toString();
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

        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Preparing Termux development environment");
        progress.setMessage("Updating repositories and installing common development tools. This may take several minutes...");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

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
                    Toast.makeText(activity, "Termux development environment is ready", Toast.LENGTH_LONG).show();
                    whenDone.run();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(activity)
                        .setTitle("Development environment setup incomplete")
                        .setMessage(error.getMessage() + "\n\nCodex is ready. Use Install / Update Codex CLI later to retry environment setup.")
                        .setNegativeButton("Retry later", (dialog, which) -> whenDone.run())
                        .setPositiveButton("Retry now", (dialog, which) -> setupDevelopmentExtensions(activity, whenDone))
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
