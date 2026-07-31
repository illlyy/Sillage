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
        final NativeRouteEventGate.RouteToken routeToken;

        Event(String function, String value, NativeRouteEventGate.RouteToken routeToken) {
            this.function = function;
            this.value = value;
            this.routeToken = routeToken;
        }
    }

    private static final class PendingEvent {
        final String function;
        final StringBuilder value = new StringBuilder();
        final NativeRouteEventGate.RouteToken routeToken;

        PendingEvent(String function, String initialValue,
                     NativeRouteEventGate.RouteToken routeToken) {
            this.function = function;
            this.routeToken = routeToken;
            value.append(initialValue);
        }
    }

    private final ArrayList<PendingEvent> pending = new ArrayList<>();
    private int pendingChars;

    synchronized boolean offer(String function, String value) {
        return offer(function, value, null, true);
    }

    synchronized boolean offer(String function, String value,
                               NativeRouteEventGate.RouteToken routeToken) {
        return offer(function, value, routeToken, true);
    }

    /** Add an ordering barrier whose payload must remain a standalone event. */
    synchronized boolean offerSeparate(String function, String value) {
        return offer(function, value, null, false);
    }

    synchronized boolean offerSeparate(String function, String value,
                                       NativeRouteEventGate.RouteToken routeToken) {
        return offer(function, value, routeToken, false);
    }

    private boolean offer(String function, String value, NativeRouteEventGate.RouteToken routeToken,
                          boolean mergeAdjacent) {
        String safeValue = value == null ? "" : value;
        int lastIndex = pending.size() - 1;
        PendingEvent previous = lastIndex >= 0 ? pending.get(lastIndex) : null;
        if (mergeAdjacent && previous != null && previous.function.equals(function)
                && sameRoute(previous.routeToken, routeToken)) {
            previous.value.append(safeValue);
        } else {
            pending.add(new PendingEvent(function, safeValue, routeToken));
        }
        pendingChars += safeValue.length();
        return pendingChars >= 4096;
    }

    synchronized List<Event> drain() {
        if (pending.isEmpty()) return java.util.Collections.emptyList();
        ArrayList<Event> result = new ArrayList<>(pending.size());
        for (PendingEvent event : pending) {
            result.add(new Event(event.function, event.value.toString(), event.routeToken));
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

    /** Drops stale conversation events while preserving global backend/error notifications. */
    synchronized void clearRoutedEvents() {
        if (pending.isEmpty()) return;
        int retainedChars = 0;
        for (int index = pending.size() - 1; index >= 0; index--) {
            PendingEvent event = pending.get(index);
            if (event.routeToken != null) {
                pending.remove(index);
            } else {
                retainedChars += event.value.length();
            }
        }
        pendingChars = retainedChars;
    }

    private static boolean sameRoute(NativeRouteEventGate.RouteToken left,
                                     NativeRouteEventGate.RouteToken right) {
        if (left == null || right == null) return left == right;
        return left.belongsToSameRoute(right);
    }
}
