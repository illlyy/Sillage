package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Installs and runs the pinned Mihomo + MetaCubeXD optional extension.
 *
 * <p>Security invariants: controller is loopback-only, controller auth is random,
 * shipped archives are verified before extraction, and no unpinned updater exists.</p>
 */
final class MihomoManager {
    static final String CORE_VERSION = "1.19.28";
    static final String DASHBOARD_VERSION = "1.268.4";
    static final int DEFAULT_MIXED_PORT = 7890;
    static final int DEFAULT_CONTROLLER_PORT = 9090;

    private static final String TAG = "IlyopMihomo";
    private static final String CORE_ASSET = "mihomo/mihomo-android-arm64-v8-v1.19.28.gz.asset";
    private static final String CORE_SHA256 = "3bc12180b6d8affd95166da48dc772487dcf29741663077d1abc105dcd1dae69";
    private static final String UI_ASSET = "mihomo/metacubexd-v1.268.4.tgz";
    private static final String UI_SHA256 = "5758b3649bba7f715208b214dee5033543c7c70c7d0c50851b1a066ae58d382c";
    private static final long MAX_CONFIG_BYTES = 10L * 1024L * 1024L;

    interface ProgressCallback { void onProgress(String message); }


    static final class Subscription {
        final String id;
        final String name;
        final String url;
        final long updatedAt;
        final boolean active;
        Subscription(String id, String name, String url, long updatedAt, boolean active) {
            this.id = id; this.name = name; this.url = url; this.updatedAt = updatedAt; this.active = active;
        }
        boolean isRemote() { return url != null && !url.isEmpty(); }
    }

    private static MihomoManager instance;
    private static Process process;
    private static String lastLog = "";

    private final Context context;
    private final SharedPreferences prefs;
    private final File binary;
    private final File runtimeDir;
    private final File uiDir;
    private final File configFile;
    private final File markerFile;
    private final File profilesDir;

    static synchronized MihomoManager get(Context context) {
        if (instance == null) instance = new MihomoManager(context.getApplicationContext());
        return instance;
    }

    private MihomoManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        this.binary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "mihomo");
        this.runtimeDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".config/mihomo");
        this.uiDir = new File(runtimeDir, "ui");
        this.configFile = new File(runtimeDir, "config.yaml");
        this.markerFile = new File(runtimeDir, ".codex-mobile-bundle");
        this.profilesDir = new File(runtimeDir, "profiles");
    }

    boolean isSupported() {
        for (String abi : Build.SUPPORTED_ABIS) if ("arm64-v8a".equals(abi)) return true;
        return false;
    }

    boolean isInstalled() {
        if (!binary.canExecute() || !new File(uiDir, "index.html").isFile() || !markerFile.isFile()) return false;
        try {
            String marker = readText(markerFile).trim();
            return marker.equals(CORE_VERSION + "+" + DASHBOARD_VERSION);
        } catch (Exception ignored) { return false; }
    }

    synchronized boolean isRunning() {
        if (process != null) {
            try {
                process.exitValue();
                process = null;
            } catch (IllegalThreadStateException running) {
                return true;
            }
        }
        int persistedPid = prefs.getInt("mihomo_pid", -1);
        return isMihomoPid(persistedPid) && isPortOpen(mixedPort()) && isPortOpen(controllerPort());
    }

    int mixedPort() { return validPort(prefs.getInt("mihomo_mixed_port", DEFAULT_MIXED_PORT), DEFAULT_MIXED_PORT); }
    int controllerPort() { return validPort(prefs.getInt("mihomo_controller_port", DEFAULT_CONTROLLER_PORT), DEFAULT_CONTROLLER_PORT); }
    File configFile() { return configFile; }
    String dashboardUrl() { return "http://127.0.0.1:" + controllerPort() + "/ui/"; }
    synchronized String lastLog() { return lastLog; }

    String controllerSecret() {
        String existing = prefs.getString("mihomo_controller_secret", "");
        if (existing != null && existing.length() >= 24) return existing;
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        prefs.edit().putString("mihomo_controller_secret", generated).commit();
        return generated;
    }

    void install(ProgressCallback callback) throws Exception {
        if (!isSupported()) throw new IOException("当前内置内核仅支持 ARM64（arm64-v8a）");
        notify(callback, "正在准备私有安装目录…");
        runtimeDir.mkdirs();
        profilesDir.mkdirs();
        TermuxConstants.TERMUX_BIN_PREFIX_DIR.mkdirs();
        stop();

        File coreArchive = new File(context.getCacheDir(), "mihomo-core-v" + CORE_VERSION + ".gz.part");
        File corePart = new File(binary.getAbsolutePath() + ".part");
        File uiArchive = new File(context.getCacheDir(), "metacubexd-v" + DASHBOARD_VERSION + ".tgz.part");
        File uiStaging = new File(runtimeDir, ".ui-staging");
        try {
            notify(callback, "正在校验 Mihomo v" + CORE_VERSION + "…");
            copyVerifiedAsset(CORE_ASSET, coreArchive, CORE_SHA256);
            notify(callback, "正在解压内核…");
            gunzip(coreArchive, corePart);
            if (corePart.length() < 10L * 1024L * 1024L) throw new IOException("内核文件异常小，已中止安装");
            if (!corePart.setExecutable(true, false) && !corePart.canExecute()) throw new IOException("无法设置 Mihomo 执行权限");
            replaceFile(corePart, binary);
            if (!binary.setExecutable(true, false) && !binary.canExecute()) throw new IOException("Mihomo 不可执行");

            notify(callback, "正在校验 MetaCubeXD v" + DASHBOARD_VERSION + "…");
            copyVerifiedAsset(UI_ASSET, uiArchive, UI_SHA256);
            deleteInsideRuntime(uiStaging);
            if (!uiStaging.mkdirs()) throw new IOException("无法创建面板临时目录");
            notify(callback, "正在安装网页管理面板…");
            extractDashboard(uiArchive, uiStaging);
            if (!new File(uiStaging, "index.html").isFile()) throw new IOException("面板归档缺少 index.html");
            writeDashboardEndpoint(uiStaging);
            deleteInsideRuntime(uiDir);
            if (!uiStaging.renameTo(uiDir)) throw new IOException("无法启用网页管理面板");

            if (!configFile.isFile()) writeText(configFile, defaultConfig());
            copyNotice("mihomo/LICENSE-GPL-3.0.txt", new File(runtimeDir, "LICENSE-MIHOMO-GPL-3.0.txt"));
            copyNotice("mihomo/LICENSE-METACUBEXD.txt", new File(runtimeDir, "LICENSE-METACUBEXD.txt"));
            copyNotice("mihomo/THIRD_PARTY_NOTICES.txt", new File(runtimeDir, "THIRD_PARTY_NOTICES.txt"));
            notify(callback, "正在检查配置和内核…");
            validateConfig(configFile);
            writeText(markerFile, CORE_VERSION + "+" + DASHBOARD_VERSION + "\n");
            prefs.edit().putBoolean("mihomo_installed", true).apply();
        } finally {
            coreArchive.delete(); corePart.delete(); uiArchive.delete();
            if (uiStaging.exists()) deleteInsideRuntime(uiStaging);
        }
    }

    synchronized void start() throws Exception {
        if (isRunning()) return;
        if (!isInstalled()) throw new IOException("请先安装 Mihomo 内核与面板");
        final int mixed = ensureMixedPort();
        final int controller = ensureControllerPort();
        validateConfig(configFile);
        writeDashboardEndpoint(uiDir);
        String secret = controllerSecret();
        ProcessBuilder builder = new ProcessBuilder(
            binary.getAbsolutePath(),
            "-d", runtimeDir.getAbsolutePath(),
            "-f", configFile.getAbsolutePath(),
            "-ext-ctl", "127.0.0.1:" + controller,
            "-ext-ui", uiDir.getAbsolutePath(),
            "-secret", secret
        );
        builder.directory(runtimeDir);
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        builder.environment().put("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
        builder.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
        lastLog = "正在启动…";
        final Process launched = builder.start();
        process = launched;
        try { Thread.sleep(80L); int pid = findMihomoPid(); if (pid > 0) prefs.edit().putInt("mihomo_pid", pid).commit(); } catch (Throwable ignored) {}
        new Thread(() -> readProcessLog(launched), "MihomoOutput").start();
        if (!waitForPort(mixed, 8_000L)) {
            String detail = lastLog();
            stop();
            throw new IOException("Mihomo 未能监听 127.0.0.1:" + mixed + (detail.isEmpty() ? "" : "\n" + detail));
        }
        prefs.edit().putLong("mihomo_last_started_at", System.currentTimeMillis()).apply();
    }

    synchronized void stop() {
        Process active = process;
        process = null;
        int persistedPid = prefs.getInt("mihomo_pid", -1);
        prefs.edit().remove("mihomo_pid").commit();
        if (active != null) {
            try { active.destroy(); } catch (Exception ignored) {}
            for (int i = 0; i < 20; i++) {
                try { active.exitValue(); break; }
                catch (IllegalThreadStateException running) {
                    try { Thread.sleep(100L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { active.destroyForcibly(); } catch (Exception ignored) {}
            }
        }
        if (isMihomoPid(persistedPid)) {
            try { android.os.Process.killProcess(persistedPid); } catch (Exception ignored) {}
        }
    }

    synchronized List<Subscription> subscriptions() {
        try { ensureLegacySubscription(); } catch (Exception error) { Log.w(TAG, "Profile migration failed", error); }
        ArrayList<Subscription> result = new ArrayList<>();
        String activeId = prefs.getString("mihomo_active_subscription", "");
        try {
            JSONArray array = new JSONArray(prefs.getString("mihomo_subscriptions", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty() || !profileFile(id).isFile()) continue;
                result.add(new Subscription(id, profileDisplayName(id, item.optString("name", "Profile")),
                    item.optString("url", ""), item.optLong("updatedAt", 0L), id.equals(activeId)));
            }
        } catch (Exception error) { Log.w(TAG, "Invalid subscription metadata", error); }
        return result;
    }

    synchronized Subscription addSubscription(String name, String url) throws Exception {
        if (!isInstalled()) throw new IOException("Mihomo is not installed");
        String normalizedName = name == null || name.trim().isEmpty() ? "\u8ba2\u9605\u914d\u7f6e" : name.trim();
        String id = UUID.randomUUID().toString();
        File incoming = new File(profilesDir, id + ".incoming");
        File target = profileFile(id);
        try {
            downloadSubscription(url, incoming);
            validateConfig(incoming);
            replaceFile(incoming, target);
            upsertSubscription(id, normalizedName, url, System.currentTimeMillis());
            activateSubscription(id);
            return findSubscription(id);
        } finally { incoming.delete(); }
    }

    synchronized Subscription importSubscription(String name, InputStream source) throws Exception {
        if (!isInstalled()) throw new IOException("Mihomo is not installed");
        String id = UUID.randomUUID().toString();
        String normalizedName = name == null || name.trim().isEmpty() ? "\u672c\u5730\u914d\u7f6e" : name.trim();
        File incoming = new File(profilesDir, id + ".incoming");
        try {
            copyLimited(source, incoming, MAX_CONFIG_BYTES);
            validateConfig(incoming);
            replaceFile(incoming, profileFile(id));
            upsertSubscription(id, normalizedName, "", System.currentTimeMillis());
            activateSubscription(id);
            return findSubscription(id);
        } finally { incoming.delete(); }
    }

    synchronized void activateSubscription(String id) throws Exception {
        Subscription profile = findSubscription(id);
        if (profile == null) throw new IOException("Subscription not found");
        File source = profileFile(id);
        validateConfig(source);
        boolean restart = isRunning();
        stop();
        File incoming = new File(runtimeDir, "config.yaml.incoming");
        try {
            copyFile(source, incoming);
            replaceFile(incoming, configFile);
            prefs.edit().putString("mihomo_active_subscription", id).apply();
        } finally { incoming.delete(); }
        if (restart) start();
    }

    synchronized void updateSubscription(String id) throws Exception {
        Subscription profile = findSubscription(id);
        if (profile == null) throw new IOException("Subscription not found");
        if (!profile.isRemote()) throw new IOException("Local profiles cannot be updated from URL");
        File incoming = new File(profilesDir, id + ".incoming");
        try {
            downloadSubscription(profile.url, incoming);
            validateConfig(incoming);
            replaceFile(incoming, profileFile(id));
            upsertSubscription(id, profile.name, profile.url, System.currentTimeMillis());
            if (profile.active) activateSubscription(id);
        } finally { incoming.delete(); }
    }

    synchronized void deleteSubscription(String id) throws Exception {
        List<Subscription> all = subscriptions();
        Subscription target = null;
        for (Subscription item : all) if (item.id.equals(id)) target = item;
        if (target == null) return;
        if (target.active && all.size() == 1) throw new IOException("Keep at least one active profile");
        removeSubscriptionMetadata(id);
        profileFile(id).delete();
        if (target.active) {
            for (Subscription item : subscriptions()) { activateSubscription(item.id); break; }
        }
    }

    synchronized Subscription activeSubscription() {
        try { ensureLegacySubscription(); } catch (Exception error) { Log.w(TAG, "Profile migration failed", error); }
        String id = prefs.getString("mihomo_active_subscription", "");
        return findSubscription(id);
    }

    private void ensureLegacySubscription() throws Exception {
        if (!isInstalled() || !configFile.isFile()) return;
        JSONArray existing = new JSONArray(prefs.getString("mihomo_subscriptions", "[]"));
        if (existing.length() > 0) return;
        profilesDir.mkdirs();
        String id = "local-default";
        copyFile(configFile, profileFile(id));
        upsertSubscription(id, "\u5f53\u524d\u914d\u7f6e", "", configFile.lastModified());
        prefs.edit().putString("mihomo_active_subscription", id).apply();
    }

    private Subscription findSubscription(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Subscription item : subscriptionsWithoutMigration()) if (item.id.equals(id)) return item;
        return null;
    }

    private List<Subscription> subscriptionsWithoutMigration() {
        ArrayList<Subscription> result = new ArrayList<>();
        String activeId = prefs.getString("mihomo_active_subscription", "");
        try {
            JSONArray array = new JSONArray(prefs.getString("mihomo_subscriptions", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) continue;
                result.add(new Subscription(id, profileDisplayName(id, item.optString("name", "Profile")), item.optString("url", ""),
                    item.optLong("updatedAt", 0L), id.equals(activeId)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void upsertSubscription(String id, String name, String url, long updatedAt) throws Exception {
        JSONArray old = new JSONArray(prefs.getString("mihomo_subscriptions", "[]"));
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id"))) {
                next.put(subscriptionJson(id, name, url, updatedAt)); replaced = true;
            } else next.put(item);
        }
        if (!replaced) next.put(subscriptionJson(id, name, url, updatedAt));
        prefs.edit().putString("mihomo_subscriptions", next.toString()).commit();
    }

    private void removeSubscriptionMetadata(String id) throws Exception {
        JSONArray old = new JSONArray(prefs.getString("mihomo_subscriptions", "[]"));
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) next.put(item);
        }
        prefs.edit().putString("mihomo_subscriptions", next.toString()).commit();
    }

    private static String profileDisplayName(String id, String name) {
        if ("local-default".equals(id) && "Current config".equals(name)) return "\u5f53\u524d\u914d\u7f6e";
        return name;
    }

    private static JSONObject subscriptionJson(String id, String name, String url, long updatedAt) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("url", url == null ? "" : url).put("updatedAt", updatedAt);
    }

    private File profileFile(String id) { return new File(profilesDir, id.replaceAll("[^a-zA-Z0-9._-]", "_") + ".yaml"); }

    private void downloadSubscription(String subscriptionUrl, File output) throws Exception {
        URL parsed = new URL(subscriptionUrl);
        String protocol = parsed.getProtocol().toLowerCase(Locale.US);
        if (!"https".equals(protocol) && !"http".equals(protocol)) throw new IOException("Subscription URL must use HTTP or HTTPS");
        boolean throughMihomo = prefs.getBoolean("mihomo_route_api", false) && isRunning();
        HttpURLConnection connection = (HttpURLConnection) (throughMihomo
            ? parsed.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mixedPort())))
            : parsed.openConnection());
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/yaml, text/plain, application/yaml, */*");
        connection.setRequestProperty("User-Agent", "Codex-Mobile-Mihomo/" + CORE_VERSION);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) { connection.disconnect(); throw new IOException("Subscription HTTP " + code); }
        try (InputStream input = connection.getInputStream()) { copyLimited(input, output, MAX_CONFIG_BYTES); }
        finally { connection.disconnect(); }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) output.write(buffer, 0, count);
        }
    }

    void importConfig(InputStream source) throws Exception {
        if (!isInstalled()) throw new IOException("请先安装 Mihomo");
        File incoming = new File(runtimeDir, "config.yaml.incoming");
        try {
            copyLimited(source, incoming, MAX_CONFIG_BYTES);
            validateConfig(incoming);
            stop();
            replaceFile(incoming, configFile);
            prefs.edit().putLong("mihomo_config_updated_at", System.currentTimeMillis()).apply();
        } finally { incoming.delete(); }
    }

    void updateFromSubscription(String subscriptionUrl) throws Exception {
        URL parsed = new URL(subscriptionUrl);
        String protocol = parsed.getProtocol().toLowerCase(Locale.US);
        if (!"https".equals(protocol) && !"http".equals(protocol)) throw new IOException("订阅地址必须是 HTTP 或 HTTPS");
        boolean throughMihomo = prefs.getBoolean("mihomo_route_api", false);
        if (throughMihomo && !isRunning()) start();
        HttpURLConnection connection = (HttpURLConnection) (throughMihomo
            ? parsed.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mixedPort())))
            : parsed.openConnection());
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "text/yaml, text/plain, application/yaml, */*");
        connection.setRequestProperty("User-Agent", "Codex-Mobile-Mihomo/" + CORE_VERSION);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("订阅下载失败：HTTP " + code);
        }
        try (InputStream input = connection.getInputStream()) { importConfig(input); }
        finally { connection.disconnect(); }
        prefs.edit().putString("mihomo_subscription_url", subscriptionUrl).apply();
    }

    private int ensureMixedPort() throws Exception {
        int preferred = mixedPort();
        int selected = selectAvailablePort(preferred);
        if (selected != preferred) prefs.edit().putInt("mihomo_mixed_port", selected).apply();
        String current = readText(configFile);
        StringBuilder normalized = new StringBuilder();
        boolean wroteMixedPort = false;
        for (String line : current.split("\r?\n", -1)) {
            String trimmed = line.trim();
            boolean topLevel = line.equals(trimmed);
            if (topLevel && trimmed.startsWith("mixed-port:")) {
                if (!wroteMixedPort) normalized.append("mixed-port: ").append(selected).append('\n');
                wroteMixedPort = true;
            } else if (topLevel && (trimmed.startsWith("port:") || trimmed.startsWith("socks-port:") ||
                    trimmed.startsWith("redir-port:") || trimmed.startsWith("tproxy-port:"))) {
                String key = trimmed.substring(0, trimmed.indexOf(':'));
                normalized.append(key).append(": 0\n");
            } else if (topLevel && trimmed.startsWith("allow-lan:")) {
                normalized.append("allow-lan: false\n");
            } else {
                normalized.append(line).append('\n');
            }
        }
        if (!wroteMixedPort) normalized.insert(0, "mixed-port: " + selected + "\n");
        String updated = normalized.toString();
        if (!updated.equals(current)) writeText(configFile, updated);
        return selected;
    }

    private int ensureControllerPort() {
        int preferred = controllerPort();
        int selected = selectAvailablePort(preferred);
        if (selected != preferred) prefs.edit().putInt("mihomo_controller_port", selected).apply();
        return selected;
    }

    private static int selectAvailablePort(int preferred) {
        if (isPortAvailable(preferred)) return preferred;
        for (int offset = 1; offset <= 100; offset++) {
            int candidate = preferred + offset;
            if (candidate > 65535) break;
            if (isPortAvailable(candidate)) return candidate;
        }
        return preferred;
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private void validateConfig(File candidate) throws Exception {
        CommandResult result = runCommand(binary.getAbsolutePath(), "-t", "-d", runtimeDir.getAbsolutePath(), "-f", candidate.getAbsolutePath());
        if (result.exitCode != 0) throw new IOException("配置检查失败：" + compact(result.output));
    }

    private void extractDashboard(File archive, File destination) throws Exception {
        // The dashboard is an APK-bundled, checksum-pinned asset. Extract it directly so the
        // offline proxy extension does not depend on a separately installed Termux `tar` binary.
        String root = destination.getCanonicalPath() + File.separator;
        String pendingPath = null;
        try (GZIPInputStream input = new GZIPInputStream(
            new BufferedInputStream(new FileInputStream(archive)), 64 * 1024)) {
            byte[] header = new byte[512];
            while (readTarHeader(input, header)) {
                if (isZeroTarBlock(header)) break;
                String name = tarString(header, 0, 100);
                String prefix = tarString(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long size = tarOctal(header, 124, 12);
                byte type = header[156];

                if (type == 'x' || type == 'L') {
                    if (size > 1024 * 1024) throw new IOException("Dashboard archive metadata is too large");
                    byte[] metadata = new byte[(int) size];
                    readTarExactly(input, metadata, 0, metadata.length);
                    pendingPath = type == 'L'
                        ? new String(metadata, java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", "").trim()
                        : paxPath(metadata);
                    skipTarExactly(input, tarPadding(size));
                    continue;
                }

                if (pendingPath != null && !pendingPath.isEmpty()) name = pendingPath;
                pendingPath = null;
                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty()) {
                    skipTarExactly(input, size + tarPadding(size));
                    continue;
                }

                File output = new File(destination, name);
                String outputPath = output.getCanonicalPath();
                if (!outputPath.startsWith(root)) throw new SecurityException("Dashboard archive escapes its install directory");
                if (type == '5') {
                    if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Cannot create " + output);
                    skipTarExactly(input, size);
                } else if (type == 0 || type == '0') {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                        copyTarExactly(input, out, size);
                    }
                } else {
                    // The web bundle needs only regular files/directories; ignore links/devices.
                    skipTarExactly(input, size);
                }
                skipTarExactly(input, tarPadding(size));
            }
        }
    }

    private static boolean readTarHeader(InputStream input, byte[] header) throws IOException {
        int offset = 0;
        while (offset < header.length) {
            int count = input.read(header, offset, header.length - offset);
            if (count < 0) return offset == 0 ? false : throwUnexpectedTarEnd();
            offset += count;
        }
        return true;
    }

    private static boolean throwUnexpectedTarEnd() throws IOException {
        throw new IOException("Dashboard archive ended unexpectedly");
    }

    private static boolean isZeroTarBlock(byte[] data) {
        for (byte value : data) if (value != 0) return false;
        return true;
    }

    private static String tarString(byte[] data, int offset, int length) {
        int end = offset;
        while (end < offset + length && data[end] != 0) end++;
        return new String(data, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long tarOctal(byte[] data, int offset, int length) throws IOException {
        long value = 0;
        boolean found = false;
        for (int i = offset; i < offset + length; i++) {
            int digit = data[i] & 0xff;
            if (digit == 0 || digit == ' ') continue;
            if (digit < '0' || digit > '7') throw new IOException("Invalid dashboard archive size");
            value = (value << 3) + digit - '0';
            found = true;
        }
        return found ? value : 0;
    }

    private static long tarPadding(long size) { return (512 - size % 512) % 512; }

    private static void readTarExactly(InputStream input, byte[] output, int offset, int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            int read = input.read(output, offset, remaining);
            if (read < 0) throw new IOException("Dashboard archive ended unexpectedly");
            offset += read;
            remaining -= read;
        }
    }

    private static void copyTarExactly(InputStream input, java.io.OutputStream output, long count) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = count;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Dashboard archive ended unexpectedly");
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipTarExactly(InputStream input, long count) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = count;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Dashboard archive ended unexpectedly");
            remaining -= read;
        }
    }

    private static String paxPath(byte[] metadata) {
        String text = new String(metadata, java.nio.charset.StandardCharsets.UTF_8);
        for (String record : text.split("\n")) {
            int separator = record.indexOf(' ');
            String value = separator >= 0 ? record.substring(separator + 1) : record;
            if (value.startsWith("path=")) return value.substring(5);
        }
        return null;
    }

    private void writeDashboardEndpoint(File dashboardDir) throws IOException {
        String endpoint = "http://127.0.0.1:" + controllerPort();
        String secret = controllerSecret().replace("\\", "\\\\").replace("'", "\\'");
        String script = "window.__METACUBEXD_CONFIG__ = { defaultBackendURL: '" + endpoint + "' };\n" +
            "window.metacubexd = { endpoint: { url: '" + endpoint + "', secret: '" + secret + "' } };\n";
        writeText(new File(dashboardDir, "config.js"), script);
    }

    private void copyVerifiedAsset(String asset, File output, String expected) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(context.getAssets().open(asset));
             FileOutputStream raw = new FileOutputStream(output);
             BufferedOutputStream out = new BufferedOutputStream(raw)) {
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                digest.update(buffer, 0, count); out.write(buffer, 0, count);
            }
        }
        String actual = hex(digest.digest());
        if (!expected.equals(actual)) { output.delete(); throw new SecurityException("内置资产校验失败：" + asset); }
    }

    private static void gunzip(File archive, File output) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archive)));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) out.write(buffer, 0, count);
        }
    }

    private void copyNotice(String asset, File destination) throws IOException {
        try (InputStream input = context.getAssets().open(asset); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) output.write(buffer, 0, count);
        }
    }

    private static void copyLimited(InputStream input, File output, long limit) throws IOException {
        long total = 0;
        try (BufferedInputStream in = new BufferedInputStream(input); BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[32 * 1024]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > limit) throw new IOException("配置文件超过 10 MB 限制");
                out.write(buffer, 0, count);
            }
        }
        if (total == 0) throw new IOException("配置文件为空");
    }

    private static void replaceFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        File backup = new File(destination.getAbsolutePath() + ".backup");
        backup.delete();
        if (destination.exists() && !destination.renameTo(backup)) throw new IOException("无法备份 " + destination.getName());
        if (!source.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination);
            throw new IOException("无法替换 " + destination.getName());
        }
        backup.delete();
    }

    private void deleteInsideRuntime(File target) throws IOException {
        String root = runtimeDir.getCanonicalPath() + File.separator;
        String path = target.getCanonicalPath();
        if (!path.startsWith(root)) throw new SecurityException("拒绝删除运行目录以外的路径");
        deleteRecursively(target);
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("无法删除 " + file.getAbsolutePath());
    }

    private CommandResult runCommand(String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(runtimeDir);
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        builder.environment().put("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
        builder.environment().put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
        Process running = builder.start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = running.getInputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0 && bytes.size() < 256 * 1024) bytes.write(buffer, 0, Math.min(count, 256 * 1024 - bytes.size()));
            }
        }
        int exit = running.waitFor();
        return new CommandResult(exit, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    private void readProcessLog(Process active) {
        try (BufferedInputStream input = new BufferedInputStream(active.getInputStream())) {
            ByteArrayOutputStream line = new ByteArrayOutputStream(); int value;
            while ((value = input.read()) != -1) {
                if (value == '\n') { appendLog(new String(line.toByteArray(), StandardCharsets.UTF_8)); line.reset(); }
                else if (line.size() < 8192) line.write(value);
            }
            if (line.size() > 0) appendLog(new String(line.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) { Log.w(TAG, "Mihomo log reader stopped", error); }
        synchronized (this) {
            if (process == active) process = null;
            if (process == null) prefs.edit().remove("mihomo_pid").apply();
        }
    }

    private static synchronized void appendLog(String line) {
        Log.i(TAG, line);
        String combined = lastLog + (lastLog.isEmpty() ? "" : "\n") + line;
        if (combined.length() > 12_000) combined = combined.substring(combined.length() - 12_000);
        lastLog = combined;
    }

    private static int findMihomoPid() {
        File proc = new File("/proc");
        File[] entries = proc.listFiles();
        if (entries == null) return -1;
        int newest = -1;
        for (File entry : entries) {
            try {
                int pid = Integer.parseInt(entry.getName());
                if (pid > newest && isMihomoPid(pid)) newest = pid;
            } catch (NumberFormatException ignored) {}
        }
        return newest;
    }

    private static boolean isMihomoPid(int pid) {
        if (pid <= 0) return false;
        File cmdline = new File("/proc/" + pid + "/cmdline");
        if (!cmdline.isFile()) return false;
        try {
            String command = readText(cmdline).replace('\0', ' ');
            return command.contains("/xusr/bin/mihomo");
        } catch (Exception ignored) { return false; }
    }

    private static boolean isPortOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 180);
            return true;
        } catch (IOException ignored) { return false; }
    }

    private static boolean waitForPort(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return true;
            } catch (IOException ignored) {
                try { Thread.sleep(120L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }

    private static String defaultConfig() {
        return "mixed-port: " + DEFAULT_MIXED_PORT + "\n" +
            "allow-lan: false\n" +
            "mode: rule\n" +
            "log-level: info\n" +
            "ipv6: true\n\n" +
            "proxies: []\n\n" +
            "proxy-groups:\n" +
            "  - name: PROXY\n" +
            "    type: select\n" +
            "    proxies:\n" +
            "      - DIRECT\n\n" +
            "rules:\n" +
            "  - MATCH,DIRECT\n";
    }

    private static int validPort(int value, int fallback) { return value >= 1024 && value <= 65535 ? value : fallback; }
    private static void notify(ProgressCallback callback, String message) { if (callback != null) callback.onProgress(message); }
    private static String compact(String value) { String text = value == null ? "" : value.trim(); return text.length() > 1400 ? text.substring(text.length() - 1400) : text; }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(bytes.length * 2); for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value & 0xff)); return out.toString(); }
    private static String readText(File file) throws IOException { try (FileInputStream input = new FileInputStream(file)) { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n = input.read(b)) != -1) if (n > 0) out.write(b, 0, n); return new String(out.toByteArray(), StandardCharsets.UTF_8); } }
    private static void writeText(File file, String value) throws IOException { File parent = file.getParentFile(); if (parent != null) parent.mkdirs(); try (FileOutputStream output = new FileOutputStream(file)) { output.write(value.getBytes(StandardCharsets.UTF_8)); } }

    private static final class CommandResult {
        final int exitCode; final String output;
        CommandResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
    }
}

