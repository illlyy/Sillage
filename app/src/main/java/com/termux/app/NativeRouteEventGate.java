package com.termux.app;

import org.json.JSONObject;
import org.json.JSONArray;

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

        Event freeze(String frozenValue, String frozenProtocolItemId) {
            return new Event(routeEpoch, navigationGeneration, sourceThreadId, function,
                frozenValue, frozenProtocolItemId);
        }
    }

    private static final class ProtocolLine {
        final String kind;
        final String turnId;
        final String itemId;
        final String assistantText;

        ProtocolLine(String kind, String turnId, String itemId, String assistantText) {
            this.kind = kind;
            this.turnId = turnId;
            this.itemId = itemId;
            this.assistantText = assistantText;
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
        return markReadyAndReplay(generation, sourceThreadId, completedProtocolItemIds, null);
    }

    synchronized List<Event> markReadyAndReplay(int generation, String sourceThreadId,
                                                Set<String> completedProtocolItemIds,
                                                String historyTailAssistantText) {
        if (!isCurrentRoute(routeEpoch, generation, sourceThreadId)) {
            return Collections.emptyList();
        }
        return openAndReplay(completedProtocolItemIds, historyTailAssistantText);
    }

    synchronized List<Event> markReadyAndReplay(RouteToken token,
                                                Set<String> completedProtocolItemIds) {
        return markReadyAndReplay(token, completedProtocolItemIds, null);
    }

    synchronized List<Event> markReadyAndReplay(RouteToken token,
                                                Set<String> completedProtocolItemIds,
                                                String historyTailAssistantText) {
        if (!isCurrentToken(token)) return Collections.emptyList();
        return openAndReplay(completedProtocolItemIds, historyTailAssistantText);
    }

    private List<Event> openAndReplay(Set<String> completedProtocolItemIds,
                                      String historyTailAssistantText) {
        if (completedProtocolItemIds != null) {
            for (String itemId : completedProtocolItemIds) {
                String normalized = safe(itemId).trim();
                if (!normalized.isEmpty()) historyCoveredItemIds.add(normalized);
            }
        }
        Set<String> legacyCoveredAssistantTurns = findLegacyCoveredAssistantTurns(
            historyTailAssistantText);
        ready = true;
        return drainUncovered(legacyCoveredAssistantTurns);
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
        return drainUncovered(Collections.emptySet());
    }

    synchronized List<Event> failOpen(RouteToken token) {
        if (!isCurrentToken(token)) return Collections.emptyList();
        ready = true;
        return drainUncovered(Collections.emptySet());
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

    private List<Event> drainUncovered(Set<String> legacyCoveredAssistantTurns) {
        if (deferred.isEmpty()) return Collections.emptyList();
        ArrayList<Event> replay = new ArrayList<>(deferred.size());
        for (PendingEvent event : deferred) {
            if (!event.protocolItemId.isEmpty()
                    && historyCoveredItemIds.contains(event.protocolItemId)) continue;
            // resetRoute() normally clears stale entries. Keep this validation here as a second
            // line of defence if replay and navigation race on different threads.
            if (isCurrentRoute(event.routeEpoch, event.navigationGeneration, event.sourceThreadId)) {
                String originalValue = event.value.toString();
                String filteredValue = filterLegacyAssistantLines(
                    event, originalValue, legacyCoveredAssistantTurns);
                // Empty lifecycle barriers (for example onTurnComplete) are legitimate. Only an
                // originally non-empty JSON/JSONL event can become empty because every line was
                // deliberately filtered.
                if (!originalValue.isEmpty() && filteredValue.isEmpty()) continue;
                String replayItemId = filteredValue == originalValue
                    ? event.protocolItemId
                    : protocolItemIdFromPayload(filteredValue);
                replay.add(event.freeze(filteredValue, replayItemId));
            }
        }
        deferred.clear();
        return replay;
    }

    /**
     * Older app-server histories do not carry protocol item ids. Use the authoritative completed
     * assistant text only as a narrow proof that one buffered turn is already represented by the
     * history tail. A missing turn id is intentionally not guessed.
     */
    private Set<String> findLegacyCoveredAssistantTurns(String historyTailAssistantText) {
        String normalizedHistoryTail = normalizeAssistantText(historyTailAssistantText);
        if (normalizedHistoryTail.isEmpty()) return Collections.emptySet();
        LinkedHashSet<String> turns = new LinkedHashSet<>();
        for (PendingEvent event : deferred) {
            // An explicitly supplied event-level identity is authoritative even when an old/raw
            // payload happens not to repeat it inside the JSON object.
            if (!event.protocolItemId.isEmpty()) continue;
            String raw = event.value.toString();
            int start = 0;
            while (start < raw.length()) {
                int newline = raw.indexOf('\n', start);
                int contentEnd = newline >= 0 ? newline : raw.length();
                int jsonEnd = contentEnd > start && raw.charAt(contentEnd - 1) == '\r'
                    ? contentEnd - 1 : contentEnd;
                ProtocolLine line = parseProtocolLine(raw.substring(start, jsonEnd).trim());
                if (line != null
                        && "assistantCompleted".equals(line.kind)
                        && line.itemId.isEmpty()
                        && !line.turnId.isEmpty()
                        && normalizedHistoryTail.equals(normalizeAssistantText(line.assistantText))) {
                    turns.add(line.turnId);
                }
                start = newline >= 0 ? newline + 1 : raw.length();
            }
        }
        return turns.isEmpty() ? Collections.emptySet() : turns;
    }

    /** Removes only proven-overlapping assistant JSON/JSONL lines, preserving every other line. */
    private static String filterLegacyAssistantLines(PendingEvent event, String raw,
                                                     Set<String> coveredTurns) {
        if (coveredTurns == null || coveredTurns.isEmpty() || !event.protocolItemId.isEmpty()
                || raw.isEmpty()) return raw;
        StringBuilder filtered = null;
        int start = 0;
        while (start < raw.length()) {
            int newline = raw.indexOf('\n', start);
            int contentEnd = newline >= 0 ? newline : raw.length();
            int segmentEnd = newline >= 0 ? newline + 1 : raw.length();
            int jsonEnd = contentEnd > start && raw.charAt(contentEnd - 1) == '\r'
                ? contentEnd - 1 : contentEnd;
            ProtocolLine line = parseProtocolLine(raw.substring(start, jsonEnd).trim());
            boolean remove = line != null
                && line.itemId.isEmpty()
                && coveredTurns.contains(line.turnId)
                && isLegacyAssistantKind(line.kind);
            if (remove) {
                if (filtered == null) {
                    filtered = new StringBuilder(raw.length());
                    filtered.append(raw, 0, start);
                }
            } else if (filtered != null) {
                filtered.append(raw, start, segmentEnd);
            }
            start = segmentEnd;
        }
        return filtered == null ? raw : filtered.toString();
    }

    private static boolean isLegacyAssistantKind(String kind) {
        return "assistantDelta".equals(kind)
            || "assistantStarted".equals(kind)
            || "assistantCompleted".equals(kind);
    }

    private static ProtocolLine parseProtocolLine(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject payload = new JSONObject(raw);
            JSONObject body = payload.optJSONObject("params");
            if (body == null) body = payload;
            JSONObject item = body.optJSONObject("item");
            if (item == null) item = body.optJSONObject("details");
            if (item == null && body != payload) item = payload.optJSONObject("item");
            if (item == null && body != payload) item = payload.optJSONObject("details");

            String rawKind = firstString(payload, "kind", "event_kind");
            if (rawKind.isEmpty()) rawKind = firstString(body, "kind", "event_kind");
            if (rawKind.isEmpty()) rawKind = firstString(payload, "method", "event", "type");
            if (rawKind.isEmpty()) rawKind = firstString(body, "method", "event", "type");
            String kind = canonicalAssistantKind(rawKind, item);
            String turnId = protocolTurnId(payload, body);
            String itemId = protocolItemId(payload, body, item);
            String assistantText = "assistantCompleted".equals(kind)
                ? assistantItemText(item) : "";
            return new ProtocolLine(kind, turnId, itemId, assistantText);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String canonicalAssistantKind(String rawKind, JSONObject item) {
        String normalized = normalizeProtocolName(rawKind);
        if ("assistantdelta".equals(normalized)) return "assistantDelta";
        if ("assistantstarted".equals(normalized) || "assistantstart".equals(normalized)) {
            return "assistantStarted";
        }
        if ("assistantcompleted".equals(normalized) || "assistantcomplete".equals(normalized)) {
            return "assistantCompleted";
        }
        String itemType = item == null ? "" : normalizeProtocolName(
            firstString(item, "type", "item_type"));
        if ("agentmessage".equals(itemType)) {
            if ("itemstarted".equals(normalized) || "itemstart".equals(normalized)) {
                return "assistantStarted";
            }
            if ("itemcompleted".equals(normalized) || "itemcomplete".equals(normalized)) {
                return "assistantCompleted";
            }
        }
        return rawKind;
    }

    private static String protocolTurnId(JSONObject payload, JSONObject body) {
        String turnId = firstString(body, "turnId", "turn_id");
        if (turnId.isEmpty() && body != payload) {
            turnId = firstString(payload, "turnId", "turn_id");
        }
        JSONObject turn = body.optJSONObject("turn");
        if (turn == null && body != payload) turn = payload.optJSONObject("turn");
        if (turnId.isEmpty() && turn != null) {
            turnId = firstString(turn, "id", "turnId", "turn_id");
        }
        return turnId;
    }

    private static String protocolItemId(JSONObject payload, JSONObject body, JSONObject item) {
        String itemId = firstString(body, "itemId", "item_id", "protocolItemId");
        if (itemId.isEmpty() && body != payload) {
            itemId = firstString(payload, "itemId", "item_id", "protocolItemId");
        }
        if (itemId.isEmpty() && item != null) {
            itemId = firstString(item, "id", "itemId", "item_id", "protocolItemId");
        }
        return itemId;
    }

    private static String assistantItemText(JSONObject item) {
        if (item == null) return "";
        Object text = item.opt("text");
        if (text instanceof String && !((String) text).isEmpty()) return (String) text;
        Object content = item.opt("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONObject) {
            Object nestedText = ((JSONObject) content).opt("text");
            return nestedText instanceof String ? (String) nestedText : "";
        }
        if (content instanceof JSONArray) {
            StringBuilder combined = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int index = 0; index < parts.length(); index++) {
                JSONObject part = parts.optJSONObject(index);
                if (part == null) continue;
                Object partText = part.opt("text");
                if (partText instanceof String) combined.append((String) partText);
            }
            return combined.toString();
        }
        return "";
    }

    private static String normalizeAssistantText(String value) {
        return safe(value)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\t ]+(?=\\n|$)", "")
            .trim();
    }

    private static String normalizeProtocolName(String value) {
        return safe(value)
            .replace("_", "")
            .replace("-", "")
            .replace("/", "")
            .replace(".", "")
            .toLowerCase(java.util.Locale.ROOT);
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
