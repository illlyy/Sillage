package com.termux.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Repairs OpenAI-compatible Responses SSE streams that emit deltas before output_item.added. */
final class ResponsesSseNormalizer {
    interface EventSink {
        void emit(byte[] value) throws Exception;
    }

    static final class Stats {
        int upstreamEvents;
        int outputEvents;
        int syntheticAddedItems;
        int syntheticCompletedItems;
        int suppressedLateItems;
        int canonicalizedAddedItems;
        int deltasWithoutItemId;
        final Map<String, Integer> eventTypes = new LinkedHashMap<>();

        String eventTypeSummary() {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Integer> entry : eventTypes.entrySet()) {
                if (out.length() > 0) out.append(',');
                out.append(entry.getKey()).append('=').append(entry.getValue());
                if (out.length() > 500) break;
            }
            return out.toString();
        }
    }

    private static final class ItemState {
        final String id;
        final String type;
        final int outputIndex;
        final StringBuilder text = new StringBuilder();

        ItemState(String id, String type, int outputIndex) {
            this.id = id;
            this.type = type;
            this.outputIndex = outputIndex;
        }
    }

    private final EventSink sink;
    private final Stats stats = new Stats();
    private final Set<String> addedItemIds = new HashSet<>();
    private final Set<String> completedItemIds = new HashSet<>();
    private final Map<String, ItemState> syntheticItems = new LinkedHashMap<>();
    private final Map<String, Integer> outputIndexes = new HashMap<>();
    private String activeSyntheticItemId;

    private ResponsesSseNormalizer(EventSink sink) {
        this.sink = sink;
    }

    static Stats normalize(InputStream source, EventSink sink) throws Exception {
        ResponsesSseNormalizer normalizer = new ResponsesSseNormalizer(sink);
        normalizer.read(source);
        return normalizer.stats;
    }

    private void read(InputStream source) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
        String line;
        String pendingEventType = "";
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
                pendingEventType = trimmed.substring(6).trim();
                continue;
            }
            if (!trimmed.startsWith("data:")) continue;
            String data = trimmed.substring(5).trim();
            if (data.isEmpty()) continue;
            if ("[DONE]".equals(data)) {
                closeAllSyntheticItems();
                emitRaw("data: [DONE]\n\n");
                continue;
            }
            JSONObject event;
            try { event = new JSONObject(data); }
            catch (Exception ignored) {
                emitRaw((pendingEventType.isEmpty() ? "" : "event: " + pendingEventType + "\n")
                    + "data: " + data + "\n\n");
                pendingEventType = "";
                continue;
            }
            if (!pendingEventType.isEmpty()) event.put("type", pendingEventType);
            pendingEventType = "";
            stats.upstreamEvents++;
            String recordedType = event.optString("type", "<missing>");
            stats.eventTypes.put(recordedType, stats.eventTypes.containsKey(recordedType)
                ? stats.eventTypes.get(recordedType) + 1 : 1);
            accept(event);
        }
        closeAllSyntheticItems();
    }

    private void accept(JSONObject event) throws Exception {
        String type = event.optString("type", "");
        if ("response.output_item.added".equals(type)) {
            onItemAdded(event);
            return;
        }
        if ("response.output_item.done".equals(type)) {
            onItemDone(event);
            return;
        }
        if ("response.completed".equals(type) || "response.failed".equals(type)
                || "response.incomplete".equals(type)) {
            closeAllSyntheticItems();
            emitEvent(event);
            return;
        }

        String itemType = itemTypeForEvent(type);
        if (itemType != null) {
            String itemId = event.optString("item_id", event.optString("itemId", ""));
            if (itemId.isEmpty()) {
                stats.deltasWithoutItemId++;
            } else {
                int outputIndex = event.optInt("output_index",
                    outputIndexes.containsKey(itemId) ? outputIndexes.get(itemId) : outputIndexes.size());
                ensureItemAdded(itemId, itemType, outputIndex);
                appendContent(itemId, type, event);
            }
        }
        emitEvent(event);
    }

    private void onItemAdded(JSONObject event) throws Exception {
        JSONObject item = event.optJSONObject("item");
        String itemId = item == null ? "" : item.optString("id", "");
        if (itemId.isEmpty()) {
            emitEvent(event);
            return;
        }
        if (addedItemIds.contains(itemId) || completedItemIds.contains(itemId)) {
            stats.suppressedLateItems++;
            return;
        }
        closeActiveSyntheticBefore(itemId);
        int outputIndex = event.optInt("output_index", outputIndexes.size());
        outputIndexes.put(itemId, outputIndex);
        addedItemIds.add(itemId);
        String itemType = item.optString("type", "");
        if ("reasoning".equals(itemType) || "message".equals(itemType)) {
            JSONObject canonical = new JSONObject(event.toString());
            canonical.put("item", canonicalAddedItem(itemId, itemType));
            emitEvent(canonical);
            stats.canonicalizedAddedItems++;
        } else {
            emitEvent(event);
        }
    }

    private void onItemDone(JSONObject event) throws Exception {
        JSONObject item = event.optJSONObject("item");
        String itemId = item == null ? "" : item.optString("id", "");
        if (itemId.isEmpty()) {
            emitEvent(event);
            return;
        }
        if (completedItemIds.contains(itemId)) {
            stats.suppressedLateItems++;
            return;
        }
        if (!addedItemIds.contains(itemId)) {
            int outputIndex = event.optInt("output_index", outputIndexes.size());
            closeActiveSyntheticBefore(itemId);
            outputIndexes.put(itemId, outputIndex);
            addedItemIds.add(itemId);
            emitSyntheticAdded(itemId, item == null ? "other" : item.optString("type", "other"),
                outputIndex, item);
        }
        emitEvent(event);
        completedItemIds.add(itemId);
        syntheticItems.remove(itemId);
        if (itemId.equals(activeSyntheticItemId)) activeSyntheticItemId = null;
    }

    private void ensureItemAdded(String itemId, String itemType, int outputIndex) throws Exception {
        if (addedItemIds.contains(itemId) || completedItemIds.contains(itemId)) return;
        closeActiveSyntheticBefore(itemId);
        outputIndexes.put(itemId, outputIndex);
        addedItemIds.add(itemId);
        ItemState state = new ItemState(itemId, itemType, outputIndex);
        syntheticItems.put(itemId, state);
        activeSyntheticItemId = itemId;
        emitSyntheticAdded(itemId, itemType, outputIndex, null);
        stats.syntheticAddedItems++;
    }

    private void emitSyntheticAdded(String itemId, String itemType, int outputIndex,
                                    JSONObject completedItem) throws Exception {
        JSONObject item;
        if ("reasoning".equals(itemType) || "message".equals(itemType)) {
            item = canonicalAddedItem(itemId, itemType);
        } else if (completedItem != null) {
            item = new JSONObject(completedItem.toString());
            if (item.has("status")) item.put("status", "in_progress");
            if ("function_call".equals(itemType)) item.put("arguments", "");
            if ("custom_tool_call".equals(itemType)) item.put("input", "");
        } else {
            item = new JSONObject().put("id", itemId).put("type", itemType);
        }
        emitEvent(new JSONObject().put("type", "response.output_item.added")
            .put("output_index", outputIndex).put("item", item));
    }

    private static JSONObject canonicalAddedItem(String itemId, String itemType) throws Exception {
        if ("reasoning".equals(itemType)) {
            return new JSONObject().put("id", itemId).put("type", "reasoning")
                .put("summary", new JSONArray());
        }
        return new JSONObject().put("id", itemId).put("type", "message")
            .put("status", "in_progress").put("role", "assistant")
            .put("content", new JSONArray());
    }

    private void appendContent(String itemId, String eventType, JSONObject event) {
        ItemState state = syntheticItems.get(itemId);
        if (state == null) return;
        if (eventType.endsWith(".delta")) state.text.append(event.optString("delta", ""));
        else if (eventType.endsWith(".done") && state.text.length() == 0) {
            state.text.append(event.optString("text", ""));
        }
    }

    private void closeActiveSyntheticBefore(String nextItemId) throws Exception {
        if (activeSyntheticItemId == null || activeSyntheticItemId.equals(nextItemId)) return;
        closeSyntheticItem(activeSyntheticItemId);
    }

    private void closeAllSyntheticItems() throws Exception {
        for (String itemId : new ArrayList<>(syntheticItems.keySet())) closeSyntheticItem(itemId);
    }

    private void closeSyntheticItem(String itemId) throws Exception {
        ItemState state = syntheticItems.remove(itemId);
        if (state == null || completedItemIds.contains(itemId)) return;
        JSONObject item;
        if ("reasoning".equals(state.type)) {
            item = new JSONObject().put("id", state.id).put("type", "reasoning")
                .put("summary", new JSONArray().put(new JSONObject()
                    .put("type", "summary_text").put("text", state.text.toString())));
        } else {
            item = new JSONObject().put("id", state.id).put("type", "message")
                .put("status", "completed").put("role", "assistant")
                .put("content", new JSONArray().put(new JSONObject()
                    .put("type", "output_text").put("text", state.text.toString())
                    .put("annotations", new JSONArray())));
        }
        emitEvent(new JSONObject().put("type", "response.output_item.done")
            .put("output_index", state.outputIndex).put("item", item));
        completedItemIds.add(itemId);
        if (itemId.equals(activeSyntheticItemId)) activeSyntheticItemId = null;
        stats.syntheticCompletedItems++;
    }

    private static String itemTypeForEvent(String eventType) {
        if (eventType.startsWith("response.output_text") || eventType.startsWith("response.content_part")) {
            return "message";
        }
        if (eventType.startsWith("response.reasoning_summary")
                || eventType.startsWith("response.reasoning_text")) {
            return "reasoning";
        }
        return null;
    }

    private void emitEvent(JSONObject event) throws Exception {
        String type = event.optString("type", "message");
        emitRaw("event: " + type + "\n" + "data: " + event + "\n\n");
    }

    private void emitRaw(String value) throws Exception {
        sink.emit(value.getBytes(StandardCharsets.UTF_8));
        stats.outputEvents++;
    }
}
