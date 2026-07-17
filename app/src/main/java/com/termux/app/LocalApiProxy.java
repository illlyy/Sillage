package com.termux.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loopback HTTP/SSE proxy so the musl Codex binary can use Android's Java DNS and TLS stack. */
final class LocalApiProxy {
    static final String CODEX_WIRE_API = "responses";
    private static final String TAG = "IlyopApiProxy";
    static final int UPSTREAM_FIRST_BYTE_TIMEOUT_MS = 60_000;
    static final int UPSTREAM_IDLE_TIMEOUT_MS = 300_000;
    static final int MAX_CONCURRENT_UPSTREAM_REQUESTS = 32;
    private final String upstreamBase;
    private final String apiFormat;
    private final boolean routeThroughMihomo;
    private final int mihomoPort;
    private final boolean forwardReasoningContext;
    private final Map<String, String> ultraTransportEfforts;
    private final boolean preventRecursiveSubagents;
    private final java.util.concurrent.Semaphore requestSlots = new java.util.concurrent.Semaphore(MAX_CONCURRENT_UPSTREAM_REQUESTS, true);
    private final java.util.concurrent.atomic.AtomicInteger workerIds = new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean running;
    private ServerSocket server;

    LocalApiProxy(String upstreamBase) {
        this(upstreamBase, "openai_responses", false, MihomoManager.DEFAULT_MIXED_PORT, false, Collections.emptyMap(), false);
    }
    LocalApiProxy(String upstreamBase, String apiFormat) {
        this(upstreamBase, apiFormat, false, MihomoManager.DEFAULT_MIXED_PORT, false, Collections.emptyMap(), false);
    }
    LocalApiProxy(String upstreamBase, String apiFormat, boolean routeThroughMihomo, int mihomoPort) {
        this(upstreamBase, apiFormat, routeThroughMihomo, mihomoPort, false, Collections.emptyMap(), false);
    }
    LocalApiProxy(String upstreamBase, String apiFormat, boolean routeThroughMihomo, int mihomoPort,
                  boolean forwardReasoningContext) {
        this(upstreamBase, apiFormat, routeThroughMihomo, mihomoPort, forwardReasoningContext, Collections.emptyMap(), false);
    }
    LocalApiProxy(String upstreamBase, String apiFormat, boolean routeThroughMihomo, int mihomoPort,
                  boolean forwardReasoningContext, Map<String, String> ultraTransportEfforts) {
        this(upstreamBase, apiFormat, routeThroughMihomo, mihomoPort, forwardReasoningContext,
            ultraTransportEfforts, false);
    }

    LocalApiProxy(String upstreamBase, String apiFormat, boolean routeThroughMihomo, int mihomoPort,
                  boolean forwardReasoningContext, Map<String, String> ultraTransportEfforts,
                  boolean preventRecursiveSubagents) {
        this.upstreamBase = trimSlash(upstreamBase);
        this.apiFormat = apiFormat == null ? "openai_responses" : apiFormat;
        this.routeThroughMihomo = routeThroughMihomo;
        this.mihomoPort = mihomoPort;
        this.forwardReasoningContext = forwardReasoningContext;
        this.ultraTransportEfforts = normalizeUltraTransportEfforts(ultraTransportEfforts);
        this.preventRecursiveSubagents = preventRecursiveSubagents;
    }

    int start() throws Exception {
        server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        running = true;
        new Thread(this::acceptLoop, "IlyopApiProxyAccept").start();
        Log.i(TAG, "Proxy " + server.getLocalPort() + " -> " + upstreamBase + (routeThroughMihomo ? " via Mihomo 127.0.0.1:" + mihomoPort : ""));
        return server.getLocalPort();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
    }

    boolean hasActiveRequests() {
        return requestSlots.availablePermits() < MAX_CONCURRENT_UPSTREAM_REQUESTS;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                if (!requestSlots.tryAcquire()) {
                    Log.w(TAG, "Rejecting upstream request: all " + MAX_CONCURRENT_UPSTREAM_REQUESTS + " proxy slots are busy");
                    writeProxyErrorAndClose(socket, 503, "proxy_overloaded", "Too many concurrent model requests");
                    continue;
                }
                int workerId = workerIds.incrementAndGet();
                int active = MAX_CONCURRENT_UPSTREAM_REQUESTS - requestSlots.availablePermits();
                if (active >= 8) Log.w(TAG, "activeUpstreamRequests=" + active + " limit=" + MAX_CONCURRENT_UPSTREAM_REQUESTS);
                new Thread(() -> {
                    try { handle(socket); }
                    finally { requestSlots.release(); }
                }, "IlyopApiProxyRequest-" + workerId).start();
            } catch (Exception e) {
                if (running) Log.e(TAG, "accept", e);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket) {
            boolean responseStarted = false;
            try {
            client.setSoTimeout(120_000);
            BufferedInputStream input = new BufferedInputStream(client.getInputStream());
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            List<String[]> headers = new ArrayList<>();
            int contentLength = 0;
            boolean requestChunked = false;
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if ("content-length".equalsIgnoreCase(name)) contentLength = Integer.parseInt(value);
                if ("transfer-encoding".equalsIgnoreCase(name) && value.toLowerCase(Locale.US).contains("chunked")) requestChunked = true;
                headers.add(new String[]{name, value});
            }
            byte[] body;
            if (requestChunked) body = readChunkedBody(input);
            else {
                body = new byte[contentLength];
                readExactly(input, body);
            }
            boolean requestResponses = pathWithoutQuery(path).endsWith("/responses") && "POST".equalsIgnoreCase(method);
            String requestId = Long.toHexString(System.nanoTime());
            boolean subagentRequest = requestResponses && preventRecursiveSubagents && isSubagentRequest(headers);
            String requestModel = requestResponses ? requestModel(body) : "";
            String internalEffort = requestResponses ? requestEffort(body) : "";
            int internalAgentMessages = requestResponses ? ChatCompletionsAdapter.agentMessageCount(body) : 0;
            String ultraTransportEffort = requestResponses ? ultraTransportEffort(requestModel) : "";
            boolean flattenCollaboration = requestResponses
                && shouldFlattenCollaborationNamespace(requestModel, ultraTransportEfforts);
            byte[] responsesBody = requestResponses
                ? normalizeResponsesRequestForUpstream(body, forwardReasoningContext, ultraTransportEfforts) : body;
            boolean agentMessagesNormalized = internalAgentMessages > 0
                && ChatCompletionsAdapter.agentMessageCount(responsesBody) == 0;
            if (internalAgentMessages > 0) Log.i(TAG, "id=" + requestId
                + " interAgentMessages=" + internalAgentMessages
                + " normalizedForThirdParty=" + agentMessagesNormalized);
            boolean spawnToolRemoved = false;
            if (subagentRequest) {
                ToolRemovalResult filtered = removeSpawnAgentTool(responsesBody);
                responsesBody = filtered.body;
                spawnToolRemoved = filtered.removed;
                Log.i(TAG, "id=" + requestId + " subagent=true recursiveSpawnToolRemoved=" + spawnToolRemoved);
            }
            String wireEffort = requestResponses ? requestEffort(responsesBody) : "";
            String toolSummary = requestResponses ? requestToolSummary(responsesBody) : "none";
            boolean adaptChat = "openai_chat".equals(apiFormat) && requestResponses;
            String targetPath = adaptChat ? "/chat/completions" : (path.startsWith("/") ? path : "/" + path);
            java.util.Set<String> customTools = adaptChat ? ChatCompletionsAdapter.customToolNames(responsesBody) : java.util.Collections.emptySet();
            body = adaptChat ? ChatCompletionsAdapter.responsesRequestToChat(responsesBody) : responsesBody;
            if (adaptChat) Log.i(TAG, "id=" + requestId + " chatHistory " + ChatCompletionsAdapter.toolReasoningSummary(body)
                + " agentMessages=" + internalAgentMessages);
            logRoute(requestId, requestModel, internalEffort, wireEffort, ultraTransportEffort,
                toolSummary, adaptChat ? "chat" : "responses", false, 0);
            HttpURLConnection connection = openUpstreamConnection(method, targetPath, headers, body, adaptChat ? "Responses->Chat" : "");
            int code = connection.getResponseCode();
            // Headers arrived. From this point a reasoning model may legitimately spend
            // longer between SSE events, so switch from the short connection/first-byte
            // watchdog to the normal streaming idle timeout.
            connection.setReadTimeout(UPSTREAM_IDLE_TIMEOUT_MS);

            // Many OpenAI-compatible providers (including DeepSeek endpoints) expose only
            // /chat/completions. If a selected Responses endpoint is absent, retry through
            // the compatibility adapter instead of surfacing a confusing localhost 404.
            if (!adaptChat && requestResponses && shouldFallbackToChat(code)) {
                InputStream rejected = connection.getErrorStream();
                if (rejected != null) rejected.close();
                connection.disconnect();
                adaptChat = true;
                customTools = ChatCompletionsAdapter.customToolNames(responsesBody);
                body = ChatCompletionsAdapter.responsesRequestToChat(responsesBody);
                Log.i(TAG, "id=" + requestId + " chatHistory " + ChatCompletionsAdapter.toolReasoningSummary(body)
                    + " agentMessages=" + internalAgentMessages);
                logRoute(requestId, requestModel, internalEffort, wireEffort, ultraTransportEffort,
                    toolSummary, "chat", true, code);
                connection = openUpstreamConnection(method, "/chat/completions", headers, body, "Responses->Chat fallback");
                code = connection.getResponseCode();
            }

            String contentType = connection.getHeaderField("Content-Type");
            InputStream response;
            try { response = code >= 400 ? connection.getErrorStream() : connection.getInputStream(); }
            catch (Exception e) { response = connection.getErrorStream(); }
            if (adaptChat && code >= 200 && code < 300) {
                if (response == null) throw new IllegalStateException("Chat upstream returned an empty response stream");
                String adaptedModel = requestModel;
                if (adaptedModel.isEmpty()) try { adaptedModel = new JSONObject(new String(body, StandardCharsets.UTF_8)).optString("model"); } catch (Exception ignored) {}
                responseStarted = true;
                OutputStream clientOut = new BufferedOutputStream(client.getOutputStream());
                writeAscii(clientOut, "HTTP/1.1 " + code + " " + reason(code) + "\r\n");
                writeAscii(clientOut, "Content-Type: text/event-stream; charset=utf-8\r\n");
                writeAscii(clientOut, "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
                clientOut.flush();
                long conversionStartedNanos = System.nanoTime();
                final long[] firstOutputNanos = new long[]{0L};
                final boolean rewriteNamespaces = flattenCollaboration;
                ChatCompletionsAdapter.StreamStats streamStats;
                try {
                    streamStats = ChatCompletionsAdapter.streamChatResponseToResponses(
                        response, contentType, adaptedModel, customTools, value -> {
                            byte[] output = rewriteNamespaces
                                ? rewriteCollaborationToolCalls(value, "text/event-stream") : value;
                            if (firstOutputNanos[0] == 0L) firstOutputNanos[0] = System.nanoTime();
                            writeChunk(clientOut, output, output.length);
                        });
                } finally {
                    response.close();
                }
                writeAscii(clientOut, "0\r\n\r\n");
                clientOut.flush();
                long completedNanos = System.nanoTime();
                long firstUpstreamMs = streamStats.firstUpstreamEventNanos == 0L ? -1L
                    : nanosToMillis(streamStats.firstUpstreamEventNanos - conversionStartedNanos);
                long firstOutputMs = firstOutputNanos[0] == 0L ? -1L
                    : nanosToMillis(firstOutputNanos[0] - conversionStartedNanos);
                Log.i(TAG, "id=" + requestId + " chatConversion="
                    + (streamStats.streaming ? "stream" : "buffered-json")
                    + " contentType=" + safeContentType(contentType)
                    + " firstUpstreamMs=" + firstUpstreamMs + " firstOutputMs=" + firstOutputMs
                    + " upstreamEvents=" + streamStats.upstreamEvents
                    + " outputChunks=" + streamStats.outputChunks
                    + " totalMs=" + nanosToMillis(completedNanos - conversionStartedNanos));
            } else {
                responseStarted = true;
                OutputStream clientOut = new BufferedOutputStream(client.getOutputStream());
                writeAscii(clientOut, "HTTP/1.1 " + code + " " + reason(code) + "\r\n");
                if (contentType != null) writeAscii(clientOut, "Content-Type: " + contentType + "\r\n");
                writeAscii(clientOut, "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
                if (response != null) {
                    try {
                        boolean responsesEventStream = requestResponses && code >= 200 && code < 300
                            && contentType != null
                            && contentType.toLowerCase(Locale.US).contains("event-stream");
                        if (responsesEventStream) {
                            long normalizationStartedNanos = System.nanoTime();
                            final boolean rewriteNamespaces = flattenCollaboration;
                            ResponsesSseNormalizer.Stats normalizationStats = ResponsesSseNormalizer.normalize(
                                response, value -> {
                                    byte[] output = rewriteNamespaces
                                        ? rewriteCollaborationToolCalls(value, "text/event-stream") : value;
                                    writeChunk(clientOut, output, output.length);
                                });
                            Log.i(TAG, "id=" + requestId + " responsesNormalization contentType="
                                + safeContentType(contentType)
                                + " upstreamEvents=" + normalizationStats.upstreamEvents
                                + " outputEvents=" + normalizationStats.outputEvents
                                + " syntheticAdded=" + normalizationStats.syntheticAddedItems
                                + " syntheticCompleted=" + normalizationStats.syntheticCompletedItems
                                + " suppressedLate=" + normalizationStats.suppressedLateItems
                                + " canonicalizedAdded=" + normalizationStats.canonicalizedAddedItems
                                + " missingItemId=" + normalizationStats.deltasWithoutItemId
                                + " totalMs=" + nanosToMillis(System.nanoTime() - normalizationStartedNanos)
                                + " types=" + normalizationStats.eventTypeSummary());
                        } else if (flattenCollaboration && code >= 200 && code < 300) {
                            streamWithCollaborationNamespaces(response, contentType, clientOut);
                        } else {
                            byte[] buffer = new byte[16 * 1024]; int count;
                            while ((count = response.read(buffer)) >= 0) {
                                if (count == 0) continue;
                                writeChunk(clientOut, buffer, count);
                            }
                        }
                    } finally {
                        response.close();
                    }
                }
                writeAscii(clientOut, "0\r\n\r\n"); clientOut.flush();
            }
            connection.disconnect();
            } catch (java.net.SocketTimeoutException timeout) {
                Log.w(TAG, (responseStarted ? "upstream stream idle timeout after " + UPSTREAM_IDLE_TIMEOUT_MS
                    : "upstream first-byte timeout after " + UPSTREAM_FIRST_BYTE_TIMEOUT_MS)
                    + "ms responseStarted=" + responseStarted);
                if (!responseStarted) writeProxyError(client, 504, "upstream_timeout", "Model upstream stopped responding");
            } catch (Exception e) {
                Log.e(TAG, "proxy request failed responseStarted=" + responseStarted, e);
                if (!responseStarted) writeProxyError(client, 502, "proxy_error", "Model proxy request failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "proxy socket failed", e);
        }
    }

    private HttpURLConnection openUpstreamConnection(String method, String targetPath, List<String[]> headers,
                                                            byte[] body, String adaptation) throws Exception {
        URL target = new URL(upstreamBase + targetPath);
        Log.d(TAG, method + " " + target + (adaptation.isEmpty() ? "" : " [" + adaptation + "]"));
        HttpURLConnection connection = (HttpURLConnection) (routeThroughMihomo
            ? target.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mihomoPort)))
            : target.openConnection());
        connection.setRequestMethod(method);
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(UPSTREAM_FIRST_BYTE_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        for (String[] header : headers) {
            String lower = header[0].toLowerCase(Locale.US);
            if (lower.equals("host") || lower.equals("connection") || lower.equals("content-length") || lower.equals("transfer-encoding")) continue;
            connection.setRequestProperty(header[0], header[1]);
        }
        if (body.length > 0) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
        }
        return connection;
    }

    private static String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int c;
        while ((c = input.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bytes.write(c);
        }
        if (c == -1 && bytes.size() == 0) return null;
        return bytes.toString("ISO-8859-1");
    }
    private static byte[] readChunkedBody(InputStream input) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(input);
            if (sizeLine == null) throw new Exception("missing chunk size");
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) sizeLine = sizeLine.substring(0, semicolon);
            int size = Integer.parseInt(sizeLine.trim(), 16);
            if (size == 0) {
                while (true) { String trailer = readLine(input); if (trailer == null || trailer.isEmpty()) break; }
                break;
            }
            byte[] chunk = new byte[size];
            readExactly(input, chunk);
            body.write(chunk);
            readLine(input);
        }
        return body.toByteArray();
    }

    private static void readExactly(InputStream input, byte[] body) throws Exception {
        int offset = 0; while (offset < body.length) { int n = input.read(body, offset, body.length-offset); if (n < 0) throw new Exception("short body"); offset += n; }
    }
    static boolean isSubagentRequest(List<String[]> headers) {
        if (headers == null) return false;
        for (String[] header : headers) {
            if (header == null || header.length < 2 || header[0] == null) continue;
            String name = header[0].trim().toLowerCase(Locale.US);
            String value = header[1] == null ? "" : header[1].trim();
            if (!value.isEmpty() && ("x-codex-parent-thread-id".equals(name)
                    || "x-openai-subagent".equals(name))) return true;
        }
        return false;
    }

    static final class ToolRemovalResult {
        final byte[] body;
        final boolean removed;
        ToolRemovalResult(byte[] body, boolean removed) {
            this.body = body;
            this.removed = removed;
        }
    }

    static ToolRemovalResult removeSpawnAgentTool(byte[] body) {
        if (body == null || body.length == 0) return new ToolRemovalResult(body, false);
        try {
            JSONObject payload = new JSONObject(new String(body, StandardCharsets.UTF_8));
            org.json.JSONArray tools = payload.optJSONArray("tools");
            if (tools == null) return new ToolRemovalResult(body, false);
            org.json.JSONArray filtered = new org.json.JSONArray();
            boolean removed = false;
            for (int i = 0; i < tools.length(); i++) {
                Object raw = tools.opt(i);
                JSONObject tool = raw instanceof JSONObject ? (JSONObject) raw : null;
                if (tool == null) {
                    filtered.put(raw);
                    continue;
                }
                if (isSpawnAgentTool(tool)) {
                    removed = true;
                    continue;
                }
                org.json.JSONArray children = tool.optJSONArray("tools");
                if (children == null) {
                    filtered.put(tool);
                    continue;
                }
                org.json.JSONArray filteredChildren = new org.json.JSONArray();
                boolean childRemoved = false;
                for (int j = 0; j < children.length(); j++) {
                    Object rawChild = children.opt(j);
                    JSONObject child = rawChild instanceof JSONObject ? (JSONObject) rawChild : null;
                    if (child != null && isSpawnAgentTool(child)) {
                        childRemoved = true;
                        removed = true;
                    } else {
                        filteredChildren.put(rawChild);
                    }
                }
                if (!childRemoved) {
                    filtered.put(tool);
                } else if (filteredChildren.length() > 0) {
                    JSONObject cloned = new JSONObject(tool.toString());
                    cloned.put("tools", filteredChildren);
                    filtered.put(cloned);
                }
            }
            if (!removed) return new ToolRemovalResult(body, false);
            payload.put("tools", filtered);
            return new ToolRemovalResult(payload.toString().getBytes(StandardCharsets.UTF_8), true);
        } catch (Exception ignored) {
            return new ToolRemovalResult(body, false);
        }
    }

    private static boolean isSpawnAgentTool(JSONObject tool) {
        String name = tool.optString("name", "").trim().toLowerCase(Locale.US);
        return "spawn_agent".equals(name) || name.endsWith(".spawn_agent");
    }

    static boolean shouldFallbackToChat(int statusCode) {
        return statusCode == 404 || statusCode == 405 || statusCode == 501;
    }

    static byte[] normalizeResponsesRequestForUpstream(byte[] body) {
        return normalizeResponsesRequestForUpstream(body, false, Collections.emptyMap());
    }

    static byte[] normalizeResponsesRequestForUpstream(byte[] body, boolean forwardReasoningContext) {
        return normalizeResponsesRequestForUpstream(body, forwardReasoningContext, Collections.emptyMap());
    }

    static byte[] normalizeResponsesRequestForUpstream(byte[] body, boolean forwardReasoningContext,
                                                        Map<String, String> ultraTransportEfforts) {
        if (body.length == 0) return body;
        try {
            JSONObject payload = new JSONObject(new String(body, StandardCharsets.UTF_8));
            JSONObject reasoning = payload.optJSONObject("reasoning");
            boolean changed = false;
            String configuredTransport = ultraTransportEffort(payload.optString("model"), ultraTransportEfforts);
            if (reasoning != null) {
                String effort = reasoning.optString("effort").trim().toLowerCase(Locale.US);
                if ("ultra".equals(effort)) {
                    reasoning.put("effort", configuredTransport.isEmpty() ? "max" : configuredTransport);
                    changed = true;
                } else if ("max".equals(effort) && !configuredTransport.isEmpty()
                        && !"max".equals(configuredTransport)) {
                    // Current Codex Core converts local Ultra to max before this proxy sees the request.
                    // Models whose native ceiling is xhigh/high must be mapped once more at this boundary.
                    reasoning.put("effort", configuredTransport);
                    changed = true;
                }
            }
            if (reasoning != null && !forwardReasoningContext && reasoning.has("context")) {
                reasoning.remove("context");
                changed = true;
            }
            boolean thirdPartyCollaboration = shouldFlattenCollaborationNamespace(
                payload.optString("model"), ultraTransportEfforts);
            if (thirdPartyCollaboration && normalizeInterAgentMessages(payload)) changed = true;
            if (thirdPartyCollaboration && flattenCollaborationTools(payload)) changed = true;
            return changed ? payload.toString().getBytes(StandardCharsets.UTF_8) : body;
        } catch (Exception ignored) {
            // Non-JSON request bodies must pass through byte-for-byte.
        }
        return body;
    }

    private static final java.util.Set<String> COLLABORATION_TOOL_NAMES =
        new java.util.HashSet<>(java.util.Arrays.asList(
            "spawn_agent", "send_message", "followup_task",
            "wait_agent", "interrupt_agent", "list_agents"));

    private static boolean shouldFlattenCollaborationNamespace(String model,
                                                                Map<String, String> ultraMappings) {
        if (model == null || ultraMappings == null || ultraMappings.isEmpty()) return false;
        String normalized = model.trim().toLowerCase(Locale.US);
        String key = normalized;
        if (!ultraMappings.containsKey(key)) {
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0) key = normalized.substring(slash + 1);
        }
        if (!ultraMappings.containsKey(key)) return false;
        // OpenAI reasoning families support native Responses namespaces. Third-party models
        // generally accept only ordinary function tools, so flatten their collaboration namespace.
        return !(key.startsWith("gpt-") || key.startsWith("o1")
            || key.startsWith("o3") || key.startsWith("o4"));
    }

    static boolean normalizeInterAgentMessages(JSONObject payload) throws Exception {
        Object input = payload.opt("input");
        if (!(input instanceof org.json.JSONArray)) return false;
        org.json.JSONArray source = (org.json.JSONArray) input;
        org.json.JSONArray normalized = new org.json.JSONArray();
        boolean changed = false;
        for (int i = 0; i < source.length(); i++) {
            Object raw = source.opt(i);
            JSONObject item = raw instanceof JSONObject ? (JSONObject) raw : null;
            if (item == null || !"agent_message".equals(item.optString("type"))) {
                normalized.put(raw);
                continue;
            }
            String text = interAgentMessageText(item.optJSONArray("content"));
            if (text.isEmpty()) {
                normalized.put(item);
                continue;
            }
            JSONObject message = new JSONObject().put("type", "message").put("role", "user")
                .put("content", new org.json.JSONArray().put(
                    new JSONObject().put("type", "input_text").put("text", text)));
            normalized.put(message);
            changed = true;
        }
        if (changed) payload.put("input", normalized);
        return changed;
    }

    private static String interAgentMessageText(org.json.JSONArray content) {
        if (content == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.optJSONObject(i);
            if (part == null) continue;
            String type = part.optString("type");
            String text = "encrypted_content".equals(type)
                ? part.optString("encrypted_content", part.optString("text"))
                : part.optString("text");
            if (!text.isEmpty()) result.append(text);
        }
        return result.toString();
    }

    static boolean flattenCollaborationTools(JSONObject payload) throws Exception {
        org.json.JSONArray tools = payload.optJSONArray("tools");
        if (tools == null) return false;
        org.json.JSONArray flattened = new org.json.JSONArray();
        boolean changed = false;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null || !"namespace".equals(tool.optString("type"))
                    || !"collaboration".equals(tool.optString("name"))) {
                flattened.put(tools.get(i));
                continue;
            }
            org.json.JSONArray children = tool.optJSONArray("tools");
            if (children == null) {
                flattened.put(tool);
                continue;
            }
            changed = true;
            for (int j = 0; j < children.length(); j++) {
                JSONObject child = children.optJSONObject(j);
                if (child != null) flattened.put(new JSONObject(child.toString()));
            }
        }
        if (changed) payload.put("tools", flattened);
        return changed;
    }

    static byte[] rewriteCollaborationToolCalls(byte[] body, String contentType) {
        if (body == null || body.length == 0) return body;
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            if ((contentType != null && contentType.toLowerCase(Locale.US).contains("event-stream"))
                    || text.startsWith("event:") || text.startsWith("data:")) {
                StringBuilder out = new StringBuilder();
                String[] lines = text.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = rewriteSseDataLine(lines[i]);
                    out.append(line);
                    if (i < lines.length - 1) out.append('\n');
                }
                return out.toString().getBytes(StandardCharsets.UTF_8);
            }
            JSONObject payload = new JSONObject(text);
            addCollaborationNamespaces(payload);
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) { return body; }
    }

    private static String rewriteSseDataLine(String line) {
        if (line == null || !line.startsWith("data:")) return line;
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return line;
        try {
            JSONObject payload = new JSONObject(data);
            addCollaborationNamespaces(payload);
            return "data: " + payload;
        } catch (Exception ignored) { return line; }
    }

    private static void addCollaborationNamespaces(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if ("function_call".equals(object.optString("type"))
                    && COLLABORATION_TOOL_NAMES.contains(object.optString("name"))
                    && !object.has("namespace")) {
                object.put("namespace", "collaboration");
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) addCollaborationNamespaces(object.opt(keys.next()));
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            for (int i = 0; i < array.length(); i++) addCollaborationNamespaces(array.opt(i));
        }
    }

    private static void streamWithCollaborationNamespaces(InputStream response, String contentType,
                                                            OutputStream clientOut) throws Exception {
        if (contentType != null && contentType.toLowerCase(Locale.US).contains("event-stream")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                byte[] bytes = (rewriteSseDataLine(line) + "\n").getBytes(StandardCharsets.UTF_8);
                writeChunk(clientOut, bytes, bytes.length);
            }
            return;
        }
        byte[] rewritten = rewriteCollaborationToolCalls(readAll(response), contentType);
        writeChunk(clientOut, rewritten, rewritten.length);
    }

    private static String requestModel(byte[] body) {
        try { return new JSONObject(new String(body, StandardCharsets.UTF_8)).optString("model"); }
        catch (Exception ignored) { return ""; }
    }

    private static String requestEffort(byte[] body) {
        try {
            JSONObject reasoning = new JSONObject(new String(body, StandardCharsets.UTF_8)).optJSONObject("reasoning");
            return reasoning == null ? "" : reasoning.optString("effort");
        } catch (Exception ignored) { return ""; }
    }

    static String requestToolSummary(byte[] body) {
        try {
            org.json.JSONArray tools = new JSONObject(new String(body, StandardCharsets.UTF_8))
                .optJSONArray("tools");
            if (tools == null || tools.length() == 0) return "none";
            ArrayList<String> names = new ArrayList<>();
            int total = 0;
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.optJSONObject(i);
                if (tool == null) continue;
                String namespace = tool.optString("name");
                org.json.JSONArray nested = tool.optJSONArray("tools");
                if (nested != null) {
                    for (int j = 0; j < nested.length(); j++) {
                        JSONObject child = nested.optJSONObject(j);
                        if (child == null || child.optString("name").isEmpty()) continue;
                        total++;
                        if (names.size() < 24) names.add(namespace + "." + child.optString("name"));
                    }
                } else if (!namespace.isEmpty()) {
                    total++;
                    if (names.size() < 24) names.add(namespace);
                }
            }
            StringBuilder joined = new StringBuilder();
            for (String name : names) {
                if (joined.length() > 0) joined.append(',');
                joined.append(name);
            }
            return joined.length() == 0 ? "none" : joined.toString()
                + (total > names.size() ? ",...(" + total + ")" : "");
        } catch (Exception ignored) { return "unparsed"; }
    }

    private String ultraTransportEffort(String model) {
        return ultraTransportEffort(model, ultraTransportEfforts);
    }

    private static String ultraTransportEffort(String model, Map<String, String> mappings) {
        if (mappings == null || mappings.isEmpty() || model == null) return "";
        String normalized = model.trim().toLowerCase(Locale.US);
        String value = mappings.get(normalized);
        if (value == null) {
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0) value = mappings.get(normalized.substring(slash + 1));
        }
        return value == null ? "" : value;
    }

    private static Map<String, String> normalizeUltraTransportEfforts(Map<String, String> mappings) {
        if (mappings == null || mappings.isEmpty()) return Collections.emptyMap();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String model = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.US);
            String effort = CodexProviderStore.ModelConfig.normalizeUltraTransportEffort(entry.getValue());
            if (!model.isEmpty() && !effort.isEmpty()) result.put(model, effort);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void logRoute(String requestId, String model, String coreEffort, String wireEffort,
                                 String ultraTransportEffort, String toolSummary, String format,
                                 boolean fallback, int fallbackStatus) {
        String effort = coreEffort.isEmpty() ? "none" : coreEffort;
        String outbound = wireEffort.isEmpty() ? "none" : wireEffort;
        Log.i(TAG, "id=" + requestId + " model=" + (model.isEmpty() ? "unknown" : model)
            + " coreEffort=" + effort + " outboundEffort=" + outbound
            + (ultraTransportEffort.isEmpty() ? "" : " ultraTransport=" + ultraTransportEffort)
            + " tools=" + toolSummary + " api=" + format + " fallback=" + fallback
            + (fallback ? " status=" + fallbackStatus : ""));
    }

    private static long nanosToMillis(long nanos) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos)); }
    private static String safeContentType(String value) {
        if (value == null || value.trim().isEmpty()) return "missing";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
    private static String pathWithoutQuery(String value) { int q=value.indexOf('?'); return q<0?value:value.substring(0,q); }
    private static byte[] readAll(InputStream input) throws Exception { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n; while((n=input.read(b))>=0)if(n>0)out.write(b,0,n); return out.toByteArray(); }
    private static void writeChunk(OutputStream out, byte[] value, int count) throws Exception { writeAscii(out,Integer.toHexString(count)+"\r\n");out.write(value,0,count);writeAscii(out,"\r\n");out.flush(); }
    private static void writeProxyError(Socket client, int status, String type, String message) {
        try {
            byte[] body = new JSONObject().put("error", new JSONObject().put("type", type).put("message", message))
                .toString().getBytes(StandardCharsets.UTF_8);
            writeResponse(client, status, "application/json; charset=utf-8", body);
        } catch (Exception ignored) {}
    }
    private static void writeProxyErrorAndClose(Socket socket, int status, String type, String message) {
        try (Socket client = socket) { writeProxyError(client, status, type, message); }
        catch (Exception ignored) {}
    }
    private static void writeResponse(Socket client,int code,String contentType,byte[] body)throws Exception { OutputStream out=new BufferedOutputStream(client.getOutputStream());writeAscii(out,"HTTP/1.1 "+code+" "+reason(code)+"\r\nContent-Type: "+contentType+"\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n");writeChunk(out,body,body.length);writeAscii(out,"0\r\n\r\n");out.flush(); }
    private static void writeAscii(OutputStream out, String value) throws Exception { out.write(value.getBytes(StandardCharsets.ISO_8859_1)); }
    private static String trimSlash(String value) { while (value.endsWith("/")) value = value.substring(0, value.length()-1); return value; }
    private static String reason(int code) { return code >= 200 && code < 300 ? "OK" : code >= 400 ? "Error" : "Response"; }
}
