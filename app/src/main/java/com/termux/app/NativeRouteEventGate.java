package com.termux.app;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Thread-safe admission gate for native conversation events while route history is loading.
 *
 * <p>The caller must capture a {@link RouteToken} when the app-server notification is received.
 * Route identity must not be filled from the currently visible route later inside a main-thread
 * callback; doing that would allow a delayed event from route A to be relabelled as an event from
 * route B.</p>
 */
final class NativeRouteEventGate {
    enum Admission {
        /** The route is ready, or this is an error that must remain visible. */
        DISPATCH_NOW,
        /** The event belongs to the loading route and will be returned by markReady/failOpen. */
        DEFERRED,
        /** The event belongs to a route which is no longer current. */
        REJECTED_STALE_ROUTE,
        /** The immutable history snapshot already contains this completed protocol item. */
        REJECTED_HISTORY_COVERED,
    }

    /** Immutable, atomically captured identity of the route that received a notification. */
    static final class RouteToken {
        private final long routeEpoch;
        private final int navigationGeneration;
        private final String sourceThreadId;
        private final boolean ready;

        private RouteToken(long routeEpoch, int navigationGeneration, String sourceThreadId,
                           boolean ready) {
            this.routeEpoch = routeEpoch;
            this.navigationGeneration = navigationGeneration;
            this.sourceThreadId = sourceThreadId;
            this.ready = ready;
        }

        int getNavigationGeneration() {
            return navigationGeneration;
        }

        String getSourceThreadId() {
            return sourceThreadId;
        }

        boolean isReady() {
            return ready;
        }

        boolean belongsToSameRoute(RouteToken other) {
            return other != null
                && routeEpoch == other.routeEpoch
                && navigationGeneration == other.navigationGeneration
                && sourceThreadId.equals(other.sourceThreadId);
        }
    }

    static final class Event {
        private final long routeEpoch;
        final int navigationGeneration;
        final String sourceThreadId;
        final String function;
        final String value;
        final String protocolItemId;

        private Event(long routeEpoch, int navigationGeneration, String sourceThreadId,
                      String function, String value, String protocolItemId) {
            this.routeEpoch = routeEpoch;
            this.navigationGeneration = navigationGeneration;
            this.sourceThreadId = normalizeThread(sourceThreadId);
            this.function = safe(function);
            this.value = safe(value);
            this.protocolItemId = safe(protocolItemId).trim();
        }
    }

    /** Mutable only while held by the gate lock; frozen once when returned for replay. */
    private static final class PendingEvent {
        final long routeEpoch;
        final int navigationGeneration;
        final String sourceThreadId;
        final String function;
        final String protocolItemId;
        final StringBuilder value;

        PendingEvent(Event event) {
            routeEpoch = event.routeEpoch;
            navigationGeneration = event.navigationGeneration;
            sourceThreadId = event.sourceThreadId;
            function = event.function;
            protocolItemId = event.protocolItemId;
            value = new StringBuilder(event.value);
        }

        boolean belongsToSameMergeStream(Event event) {
            return routeEpoch == event.routeEpoch
                && navigationGeneration == event.navigationGeneration
                && sourceThreadId.equals(event.sourceThreadId)
                && function.equals(event.function)
                && protocolItemId.equals(event.protocolItemId);
        }

        Event freeze() {
            return new Event(routeEpoch, navigationGeneration, sourceThreadId, function,
                value.toString(), protocolItemId);
        }
    }

    private final ArrayList<PendingEvent> deferred = new ArrayList<>();
    private final LinkedHashSet<String> historyCoveredItemIds = new LinkedHashSet<>();
    private long routeEpoch;
    private int navigationGeneration = -1;
    private String threadId = "";
    private boolean ready;

    /** Starts a new route epoch and discards every event deferred for the previous route. */
    synchronized void resetRoute(int generation, String sourceThreadId, boolean routeReady) {
        routeEpoch++;
        navigationGeneration = generation;
        threadId = normalizeThread(sourceThreadId);
        ready = routeReady;
        deferred.clear();
        historyCoveredItemIds.clear();
    }

    /**
     * Atomically snapshots the visible route for a newly received notification. When the
     * protocol omits its source thread, pass null/blank to bind the event to the current route.
     */
    synchronized RouteToken captureRouteToken(String explicitSourceThreadId) {
        String source = normalizeThread(explicitSourceThreadId);
        if (source.isEmpty()) source = threadId;
        return new RouteToken(routeEpoch, navigationGeneration, source, ready);
    }

    synchronized RouteToken captureRouteToken() {
        return captureRouteToken(null);
    }

    /** Read-only second-stage validation for delayed batch drains and lifecycle barriers. */
    synchronized boolean isCurrent(RouteToken token) {
        return isCurrentToken(token);
    }

    synchronized Admission offer(RouteToken token, String function, String value) {
        return offer(token, function, value, protocolItemIdFromPayload(value));
    }

    synchronized Admission offer(RouteToken token, String function, String value,
                                 String protocolItemId) {
        if (!isCurrentToken(token)) return Admission.REJECTED_STALE_ROUTE;
        return admit(new Event(token.routeEpoch, token.navigationGeneration,
            token.sourceThreadId, function, value, protocolItemId));
    }

    synchronized Admission offer(int generation, String sourceThreadId, String function,
                                 String value) {
        return offer(generation, sourceThreadId, function, value,
            protocolItemIdFromPayload(value));
    }

    synchronized Admission offer(int generation, String sourceThreadId, String function,
                                 String value, String protocolItemId) {
        return admit(new Event(routeEpoch, generation, sourceThreadId, function, value,
            protocolItemId));
    }

    private Admission admit(Event event) {
        if (!isCurrentRoute(event.routeEpoch, event.navigationGeneration, event.sourceThreadId)) {
            return Admission.REJECTED_STALE_ROUTE;
        }

        // History I/O must never hide the only useful failure signal. The integration layer may
        // additionally call failOpen() to release already-buffered conversation events.
        if ("onNativeError".equals(event.function)) return Admission.DISPATCH_NOW;

        if (!event.protocolItemId.isEmpty()
                && historyCoveredItemIds.contains(event.protocolItemId)) {
            return Admission.REJECTED_HISTORY_COVERED;
        }
        if (ready) return Admission.DISPATCH_NOW;

        appendDeferred(event);
        return Admission.DEFERRED;
    }

    /**
     * Opens the current route and returns events not already represented by its history snapshot.
     * Covered ids remain active for the rest of the route epoch, closing the small race in which
     * an overlapping completion arrives immediately after history was applied.
     */
    synchronized List<Event> markReadyAndReplay(int generation, String sourceThreadId,
                                                Set<String> completedProtocolItemIds) {
        if (!isCurrentRoute(routeEpoch, generation, sourceThreadId)) {
            return Collections.emptyList();
        }
        return openAndReplay(completedProtocolItemIds);
    }

    synchronized List<Event> markReadyAndReplay(RouteToken token,
                                                Set<String> completedProtocolItemIds) {
        if (!isCurrentToken(token)) return Collections.emptyList();
        return openAndReplay(completedProtocolItemIds);
    }

    private List<Event> openAndReplay(Set<String> completedProtocolItemIds) {
        if (completedProtocolItemIds != null) {
            for (String itemId : completedProtocolItemIds) {
                String normalized = safe(itemId).trim();
                if (!normalized.isEmpty()) historyCoveredItemIds.add(normalized);
            }
        }
        ready = true;
        return drainUncovered();
    }

    /**
     * Opens a route whose history failed to load, preserving live output instead of leaving it
     * behind a permanently closed gate. The caller should dispatch the returned events before
     * displaying the history error.
     */
    synchronized List<Event> failOpen(int generation, String sourceThreadId) {
        if (!isCurrentRoute(routeEpoch, generation, sourceThreadId)) {
            return Collections.emptyList();
        }
        ready = true;
        return drainUncovered();
    }

    synchronized List<Event> failOpen(RouteToken token) {
        if (!isCurrentToken(token)) return Collections.emptyList();
        ready = true;
        return drainUncovered();
    }

    synchronized boolean isReady(int generation, String sourceThreadId) {
        return ready && isCurrentRoute(routeEpoch, generation, sourceThreadId);
    }

    synchronized int deferredCount() {
        return deferred.size();
    }

    private boolean isCurrentToken(RouteToken token) {
        return token != null && isCurrentRoute(token.routeEpoch, token.navigationGeneration,
            token.sourceThreadId);
    }

    private boolean isCurrentRoute(long candidateEpoch, int generation, String sourceThreadId) {
        String candidateThread = normalizeThread(sourceThreadId);
        return candidateEpoch == routeEpoch
            && generation == navigationGeneration
            && !threadId.isEmpty()
            && threadId.equals(candidateThread);
    }

    private void appendDeferred(Event event) {
        int lastIndex = deferred.size() - 1;
        if (lastIndex >= 0 && isMergeable(event.function)) {
            PendingEvent previous = deferred.get(lastIndex);
            if (previous.belongsToSameMergeStream(event)) {
                previous.value.append(event.value);
                return;
            }
        }
        deferred.add(new PendingEvent(event));
    }

    private List<Event> drainUncovered() {
        if (deferred.isEmpty()) return Collections.emptyList();
        ArrayList<Event> replay = new ArrayList<>(deferred.size());
        for (PendingEvent event : deferred) {
            if (!event.protocolItemId.isEmpty()
                    && historyCoveredItemIds.contains(event.protocolItemId)) continue;
            // resetRoute() normally clears stale entries. Keep this validation here as a second
            // line of defence if replay and navigation race on different threads.
            if (isCurrentRoute(event.routeEpoch, event.navigationGeneration, event.sourceThreadId)) {
                replay.add(event.freeze());
            }
        }
        deferred.clear();
        return replay;
    }

    private static boolean isMergeable(String function) {
        return "onDelta".equals(function)
            || "onPlanDelta".equals(function)
            || "onReasoningDelta".equals(function)
            || "onCommandDelta".equals(function)
            || "onCommandDeltaV2".equals(function)
            || "onProtocolDelta".equals(function);
    }

    /** Extracts the stable item identity carried by normalized protocol JSON/JSONL payloads. */
    static String protocolItemIdFromPayload(String value) {
        String raw = safe(value).trim();
        if (raw.isEmpty()) return "";
        String commonId = null;
        for (String line : raw.split("\\r?\\n")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) continue;
            String itemId;
            try {
                itemId = protocolItemId(new JSONObject(candidate));
            } catch (Exception ignored) {
                return "";
            }
            if (itemId.isEmpty()) return "";
            if (commonId == null) commonId = itemId;
            else if (!commonId.equals(itemId)) return "";
        }
        return commonId == null ? "" : commonId;
    }

    private static String protocolItemId(JSONObject payload) {
        String direct = firstString(payload, "itemId", "item_id", "protocolItemId");
        if (!direct.isEmpty()) return direct;
        JSONObject item = payload.optJSONObject("item");
        if (item != null) {
            String nested = firstString(item, "id", "itemId", "item_id", "protocolItemId");
            if (!nested.isEmpty()) return nested;
        }
        JSONObject details = payload.optJSONObject("details");
        if (details != null) {
            String nested = firstString(details, "itemId", "item_id", "protocolItemId");
            if (!nested.isEmpty()) return nested;
            JSONObject detailItem = details.optJSONObject("item");
            if (detailItem != null) {
                nested = firstString(detailItem, "id", "itemId", "item_id", "protocolItemId");
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static String firstString(JSONObject value, String... keys) {
        for (String key : keys) {
            String candidate = value.optString(key, "").trim();
            if (!candidate.isEmpty() && !"null".equals(candidate)) return candidate;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeThread(String value) {
        return safe(value).trim();
    }
}
