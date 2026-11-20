package com.termux.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import androidx.annotation.Keep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs with the UID of the Shizuku server (normally Android's shell user). */
public final class DeviceControlUserService extends IDeviceControlService.Stub {
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final Object COMMAND_LOCK = new Object();
    private final Context context;

    public DeviceControlUserService() {
        this(null);
    }

    @Keep
    public DeviceControlUserService(Context context) {
        this.context = context;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String getStatus() {
        JSONObject result = new JSONObject();
        try {
            result.put("ok", true);
            result.put("uid", Os.getuid());
            result.put("pid", Os.getpid());
            result.put("sdk", android.os.Build.VERSION.SDK_INT);
            result.put("manufacturer", android.os.Build.MANUFACTURER);
            result.put("model", android.os.Build.MODEL);
            ShellResult display = run(Arrays.asList("/system/bin/sh", "-c", "wm size; wm density"), 5000);
            ShellResult focus = run(Arrays.asList("/system/bin/sh", "-c",
                "dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 4"), 5000);
            ShellResult battery = run(Arrays.asList("/system/bin/sh", "-c",
                "dumpsys battery 2>/dev/null | grep -E 'level:|status:|temperature:|powered:' | head -n 12"), 5000);
            ShellResult wifi = run(Arrays.asList("/system/bin/sh", "-c",
                "cmd wifi status 2>/dev/null | head -n 4"), 5000);
            ShellResult volume = run(Arrays.asList("/system/bin/sh", "-c",
                "cmd media_session volume --get 2>/dev/null | tail -n 2"), 5000);
            result.put("display", display.output.trim());
            result.put("focus", focus.output.trim());
            result.put("battery", battery.output.trim());
            result.put("wifi", wifi.output.trim());
            result.put("volume", volume.output.trim());
        } catch (Throwable error) {
            result = error(error);
        }
        return result.toString();
    }

    @Override
    public String dumpUiHierarchy(int maxNodes) {
        synchronized (COMMAND_LOCK) {
            File file = new File("/data/local/tmp/sillage-ui-" + Os.getpid() + "-" + System.nanoTime() + ".xml");
            try {
                ShellResult dump = null;
                for (int attempt = 0; attempt < 3 && !file.isFile(); attempt++) {
                    if (attempt > 0) Thread.sleep(350);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    dump = run(Arrays.asList("/system/bin/uiautomator", "dump", "--compressed", file.getAbsolutePath()), 20000);
                }
                if (!file.isFile()) {
                    JSONObject out = new JSONObject();
                    out.put("ok", false);
                    out.put("message", dump == null || dump.output.isEmpty() ? "uiautomator produced no output" : dump.output);
                    out.put("exitCode", dump == null ? -1 : dump.exitCode);
                    return out.toString();
                }
                byte[] bytes = readLimited(file, MAX_OUTPUT_BYTES);
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("xml", new String(bytes, StandardCharsets.UTF_8));
                out.put("truncated", bytes.length >= MAX_OUTPUT_BYTES);
                return out.toString();
            } catch (Throwable error) {
                return error(error).toString();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    @Override
    public ParcelFileDescriptor captureScreenshot() {
        synchronized (COMMAND_LOCK) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                ParcelFileDescriptor readSide = pipe[0];
                ParcelFileDescriptor writeSide = pipe[1];
                Process process;
                try {
                    process = new ProcessBuilder("/system/bin/screencap", "-p").start();
                } catch (Throwable startError) {
                    readSide.close();
                    writeSide.close();
                    return null;
                }
                Thread watchdog = new Thread(() -> waitForProcess(process, 10_000L), "sillage-screenshot-watchdog");
                watchdog.start();
                Thread writer = new Thread(() -> {
                    AtomicBoolean truncated = new AtomicBoolean(false);
                    try (InputStream input = process.getInputStream();
                         ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                        byte[] buffer = new byte[64 * 1024];
                        int total = 0;
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            if (total >= 24 * 1024 * 1024) {
                                truncated.set(true);
                                break;
                            }
                            int allowed = Math.min(read, 24 * 1024 * 1024 - total);
                            output.write(buffer, 0, allowed);
                            total += allowed;
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (process.isAlive()) process.destroyForcibly();
                    }
                }, "sillage-screenshot-writer");
                writer.start();
                return readSide;
            } catch (Throwable error) {
                return null;
            }
        }
    }

    @Override
    public boolean tap(int x, int y, long durationMs) {
        if (durationMs >= 250L) {
            long duration = Math.min(5000L, durationMs);
            return runSimple(Arrays.asList("/system/bin/input", "swipe",
                String.valueOf(x), String.valueOf(y), String.valueOf(x), String.valueOf(y), String.valueOf(duration)), 15000);
        }
        return runSimple(Arrays.asList("/system/bin/input", "tap", String.valueOf(x), String.valueOf(y)), 10000);
    }

    @Override
    public boolean swipe(int fromX, int fromY, int toX, int toY, long durationMs) {
        long duration = Math.max(50, Math.min(5000, durationMs));
        return runSimple(Arrays.asList("/system/bin/input", "swipe",
            String.valueOf(fromX), String.valueOf(fromY), String.valueOf(toX), String.valueOf(toY), String.valueOf(duration)), 15000);
    }

    @Override
    public boolean key(String keyName) {
        String key = keyName == null ? "" : keyName.trim().toUpperCase(Locale.US);
        if (!key.matches("[A-Z0-9_]+")) return false;
        if (!key.startsWith("KEYCODE_")) key = "KEYCODE_" + key;
        return runSimple(Arrays.asList("/system/bin/input", "keyevent", key), 10000);
    }

    @Override
    public String launchApp(String packageName, String activityName) {
        JSONObject out = new JSONObject();
        try {
            String pkg = packageName == null ? "" : packageName.trim();
            String activity = activityName == null ? "" : activityName.trim();
            if (!pkg.matches("[A-Za-z0-9_.]+")) throw new IllegalArgumentException("Invalid package name");
            if (!activity.isEmpty() && !activity.matches("[A-Za-z0-9_.$/]+")) throw new IllegalArgumentException("Invalid activity name");
            List<String> command = new ArrayList<>();
            command.add("/system/bin/sh");
            command.add("-c");
            if (activity.isEmpty()) {
                command.add("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
            } else {
                String component = activity.startsWith(pkg) ? activity : pkg + "/" + activity;
                command.add("am start -W -n " + component);
            }
            ShellResult result = run(command, 20000);
            out.put("ok", result.exitCode == 0);
            out.put("exitCode", result.exitCode);
            out.put("output", result.output);
            out.put("timedOut", result.timedOut);
        } catch (Throwable error) {
            out = error(error);
        }
        return out.toString();
    }

    @Override
    public String listApps(String query, int limit) {
        try {
            if (context == null) throw new IllegalStateException("UserService context unavailable");
            PackageManager pm = context.getPackageManager();
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
            List<JSONObject> matches = new ArrayList<>();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                String label = String.valueOf(pm.getApplicationLabel(info));
                String pkg = info.packageName == null ? "" : info.packageName;
                boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (!needle.isEmpty() && !label.toLowerCase(Locale.US).contains(needle)
                    && !pkg.toLowerCase(Locale.US).contains(needle)) continue;
                JSONObject item = new JSONObject();
                item.put("label", label);
                item.put("package_name", pkg);
                item.put("system", system);
                item.put("enabled", info.enabled);
                matches.add(item);
            }
            matches.sort((a, b) -> {
                int sc = Boolean.compare(a.optBoolean("system"), b.optBoolean("system"));
                if (sc != 0) return sc;
                int lc = a.optString("label").compareToIgnoreCase(b.optString("label"));
                return lc != 0 ? lc : a.optString("package_name").compareTo(b.optString("package_name"));
            });
            int capped = Math.max(1, Math.min(200, limit));
            JSONArray apps = new JSONArray();
            for (int i = 0; i < matches.size() && i < capped; i++) apps.put(matches.get(i));
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("query", query == null ? "" : query);
            out.put("total", matches.size());
            out.put("apps", apps);
            return out.toString();
        } catch (Throwable error) {
            return error(error).toString();
        }
    }

    @Override
    public String execShell(String command, int timeoutSeconds) {
        JSONObject out = new JSONObject();
        synchronized (COMMAND_LOCK) {
            try {
                if (command == null || command.isEmpty()) throw new IllegalArgumentException("Command is empty");
                int timeout = Math.max(1, Math.min(60, timeoutSeconds));
                ShellResult result = run(Arrays.asList("/system/bin/sh", "-c", command), timeout * 1000L);
                out.put("ok", result.exitCode == 0 && !result.timedOut);
                out.put("exitCode", result.exitCode);
                out.put("timedOut", result.timedOut);
                out.put("truncated", result.truncated);
                out.put("output", result.output);
            } catch (Throwable error) {
                out = error(error);
            }
        }
        return out.toString();
    }

    private boolean runSimple(List<String> command, long timeoutMs) {
        synchronized (COMMAND_LOCK) {
            try {
                ShellResult result = run(command, timeoutMs);
                return result.exitCode == 0 && !result.timedOut;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static void waitForProcess(Process process, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (process.isAlive() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (process.isAlive()) {
            process.destroy();
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static ShellResult run(List<String> command, long timeoutMs) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicBoolean truncated = new AtomicBoolean(false);
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream input = process.getInputStream()) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = MAX_OUTPUT_BYTES - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(read, remaining));
                    if (read > remaining) truncated.set(true);
                }
            } catch (IOException ignored) {
            }
        }, "sillage-shell-reader");
        reader.start();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        boolean timedOut = false;
        int exitCode;
        while (true) {
            try {
                exitCode = process.exitValue();
                break;
            } catch (IllegalThreadStateException alive) {
                if (System.nanoTime() >= deadline) {
                    timedOut = true;
                    process.destroy();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    if (process.isAlive()) process.destroyForcibly();
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException ignored) {
                        exitCode = -1;
                    }
                    break;
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    exitCode = -1;
                    break;
                }
            }
        }
        reader.join(2000);
        return new ShellResult(exitCode, bytes.toString("UTF-8"), timedOut, truncated.get());
    }

    private static byte[] readLimited(File file, int limit) throws IOException {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0 && total < limit) {
                int allowed = Math.min(read, limit - total);
                output.write(buffer, 0, allowed);
                total += allowed;
            }
            return output.toByteArray();
        }
    }

    private static JSONObject error(Throwable error) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false);
            out.put("message", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static final class ShellResult {
        final int exitCode;
        final String output;
        final boolean timedOut;
        final boolean truncated;

        ShellResult(int exitCode, String output, boolean timedOut, boolean truncated) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
            this.truncated = truncated;
        }
    }
}
