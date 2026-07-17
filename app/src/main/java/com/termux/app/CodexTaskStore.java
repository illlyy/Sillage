package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Small persistent registry of user-started (root) Codex turns for the overlay UI. */
final class CodexTaskStore {
    private static final String PREFS = "codex_mobile";
    private static final String KEY = "overlay_tasks_v1";
    private static final int MAX_TASKS = 100;

    static final String RUNNING = "running";
    static final String COMPLETED = "completed";
    static final String FAILED = "failed";

    static final class Task {
        final String threadId;
        final String title;
        final String state;
        final long updatedAt;

        Task(String threadId, String title, String state, long updatedAt) {
            this.threadId = threadId;
            this.title = title;
            this.state = state;
            this.updatedAt = updatedAt;
        }
    }

    private CodexTaskStore() {}

    static synchronized void markRunning(Context context, String threadId, String title) {
        if (threadId == null || threadId.isEmpty()) return;
        update(context, threadId, cleanTitle(title, threadId), RUNNING);
    }

    static synchronized void updateTitle(Context context, String threadId, String title) {
        if (threadId == null || threadId.isEmpty() || title == null || title.trim().isEmpty()) return;
        List<Task> tasks = read(context);
        String state = COMPLETED;
        for (Task task : tasks) if (threadId.equals(task.threadId)) { state = task.state; break; }
        update(context, threadId, cleanTitle(title, threadId), state);
    }

    static synchronized void delete(Context context, String threadId) {
        if (threadId == null || threadId.isEmpty()) return;
        List<Task> tasks = read(context);
        tasks.removeIf(task -> threadId.equals(task.threadId));
        write(context, tasks);
    }

    static synchronized void markCompleted(Context context, String threadId, boolean failed) {
        if (threadId == null || threadId.isEmpty()) return;
        List<Task> tasks = read(context);
        String title = fallbackTitle(threadId);
        for (Task task : tasks) if (threadId.equals(task.threadId)) { title = task.title; break; }
        update(context, threadId, title, failed ? FAILED : COMPLETED);
    }

    /** A missing process-scoped runtime means persisted running entries are orphaned. */
    static synchronized void markInterruptedTasks(Context context) {
        List<Task> tasks = read(context);
        boolean changed = false;
        ArrayList<Task> reconciled = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (RUNNING.equals(task.state)) {
                reconciled.add(new Task(task.threadId, task.title, FAILED, task.updatedAt));
                changed = true;
            } else {
                reconciled.add(task);
            }
        }
        if (changed) write(context, reconciled);
    }

    static synchronized List<Task> current(Context context) {
        List<Task> tasks = read(context);
        Collections.sort(tasks, (left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        return tasks;
    }

    private static void update(Context context, String threadId, String title, String state) {
        List<Task> tasks = read(context);
        tasks.removeIf(task -> threadId.equals(task.threadId));
        tasks.add(new Task(threadId, title, state, System.currentTimeMillis()));
        tasks.sort((left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        if (tasks.size() > MAX_TASKS) tasks = new ArrayList<>(tasks.subList(0, MAX_TASKS));
        write(context, tasks);
    }

    private static List<Task> read(Context context) {
        ArrayList<Task> tasks = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String threadId = item.optString("threadId", "");
                if (threadId.isEmpty()) continue;
                tasks.add(new Task(threadId, item.optString("title", fallbackTitle(threadId)),
                    item.optString("state", RUNNING), item.optLong("updatedAt", 0L)));
            }
        } catch (Exception ignored) {}
        return tasks;
    }

    private static void write(Context context, List<Task> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (Task task : tasks) array.put(new JSONObject()
                .put("threadId", task.threadId).put("title", task.title)
                .put("state", task.state).put("updatedAt", task.updatedAt));
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply();
    }

    private static String cleanTitle(String title, String threadId) {
        if (title == null) return fallbackTitle(threadId);
        String clean = title.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return fallbackTitle(threadId);
        return clean.length() > 52 ? clean.substring(0, 51) + "…" : clean;
    }

    private static String fallbackTitle(String threadId) {
        int length = threadId == null ? 0 : threadId.length();
        return "Codex 任务 · " + (length <= 8 ? threadId : threadId.substring(0, 8));
    }
}
