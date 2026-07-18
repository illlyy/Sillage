package com.termux.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Coalesces adjacent high-frequency native stream events before they reach the main thread.
 *
 * The app-server can emit one notification per token. Posting every notification through
 * Activity.runOnUiThread() still floods the main looper even though the Activity performs a
 * second-stage visual throttle. This queue preserves event order while joining adjacent events
 * of the same kind, so the UI receives a few meaningful chunks instead of hundreds of tiny ones.
 */
final class NativeStreamEventBatcher {
    static final class Event {
        final String function;
        final String value;

        Event(String function, String value) {
            this.function = function;
            this.value = value;
        }
    }

    private static final class PendingEvent {
        final String function;
        final StringBuilder value = new StringBuilder();

        PendingEvent(String function, String initialValue) {
            this.function = function;
            value.append(initialValue);
        }
    }

    private final ArrayList<PendingEvent> pending = new ArrayList<>();
    private int pendingChars;

    synchronized boolean offer(String function, String value) {
        String safeValue = value == null ? "" : value;
        int lastIndex = pending.size() - 1;
        if (lastIndex >= 0 && pending.get(lastIndex).function.equals(function)) {
            pending.get(lastIndex).value.append(safeValue);
        } else {
            pending.add(new PendingEvent(function, safeValue));
        }
        pendingChars += safeValue.length();
        return pendingChars >= 4096;
    }

    synchronized List<Event> drain() {
        if (pending.isEmpty()) return java.util.Collections.emptyList();
        ArrayList<Event> result = new ArrayList<>(pending.size());
        for (PendingEvent event : pending) {
            result.add(new Event(event.function, event.value.toString()));
        }
        pending.clear();
        pendingChars = 0;
        return result;
    }

    synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    synchronized void clear() {
        pending.clear();
        pendingChars = 0;
    }
}
