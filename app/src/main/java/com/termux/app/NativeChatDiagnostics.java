package com.termux.app;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small privacy-conscious JSONL flight recorder for native chat event/state debugging. */
final class NativeChatDiagnostics {
    private static final String TAG = "IlyopNativeChatDiag";
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "NativeChatDiagnostics");
        thread.setDaemon(true);
        return thread;
    });

    private NativeChatDiagnostics() {}

    static File file(Context context) {
        File directory = new File(context.getFilesDir(), "codex-diagnostics");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.w(TAG, "Unable to create diagnostics directory " + directory);
        }
        return new File(directory, "native-chat-events.jsonl");
    }

    static void record(Context context, String event, JSONObject details) {
        if (context == null || event == null) return;
        final Context appContext = context.getApplicationContext();
        final String serialized;
        try {
            serialized = new JSONObject()
                .put("wallTimeMs", System.currentTimeMillis())
                .put("uptimeMs", SystemClock.uptimeMillis())
                .put("event", event)
                .put("details", details == null ? new JSONObject() : details)
                .toString() + "\n";
        } catch (Exception error) {
            Log.w(TAG, "Unable to serialize native chat diagnostics", error);
            return;
        }
        // Never perform file I/O on the Compose/main thread. Streaming can emit dozens
        // of flush diagnostics per second; synchronous open/write/close was itself a
        // source of missed frames and made the recorder distort the performance data.
        WRITER.execute(() -> append(appContext, serialized));
    }

    private static void append(Context context, String serialized) {
        synchronized (LOCK) {
            try {
                File target = file(context);
                if (target.length() >= MAX_BYTES) {
                    File previous = new File(target.getParentFile(), "native-chat-events.previous.jsonl");
                    if (previous.exists() && !previous.delete()) Log.w(TAG, "Unable to remove old diagnostics");
                    if (!target.renameTo(previous)) Log.w(TAG, "Unable to rotate diagnostics");
                }
                try (FileOutputStream output = new FileOutputStream(target, true)) {
                    output.write(serialized.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception error) {
                Log.w(TAG, "Unable to record native chat diagnostics", error);
            }
        }
    }

}
