package com.termux.app;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CodexModelPipelineTest {
    @Test
    public void reasoningEffortsAreCanonicalAndValidated() {
        assertEquals("none,minimal,high,max,ultra",
            CodexProviderStore.ModelConfig.normalizeEfforts(" none,MINIMAL,high,max,ultra,max,bad "));
        assertEquals("bad", CodexProviderStore.ModelConfig.invalidReasoningEffort("low,bad,ultra"));
        assertTrue(CodexProviderStore.ModelConfig.supportsEffort("low,max,ultra", "ULTRA"));
        assertFalse(CodexProviderStore.ModelConfig.supportsEffort("low,max", "ultra"));
    }

    @Test
    public void solTemplateFillsSparseModelsAndMigratesLegacyDefaults() throws Exception {
        CodexProviderStore.ModelConfig sparse = CodexProviderStore.ModelConfig.from(
            new JSONObject().put("id", "gpt-5.6-sol"));
        assertEquals(372000L, sparse.contextWindow);
        assertEquals("low", sparse.defaultReasoningEffort);
        assertEquals("low,medium,high,xhigh,max,ultra", sparse.supportedReasoningEfforts);
        assertEquals("v2", sparse.multiAgentVersion);
        assertEquals("code_mode_only", sparse.toolMode);
        assertTrue(sparse.responsesLite);
        assertTrue(sparse.parallelToolCalls);
        assertTrue(sparse.verbosity);
        assertEquals("low", sparse.defaultVerbosity);

        CodexProviderStore.ModelConfig legacy = CodexProviderStore.ModelConfig.fromPersisted(new JSONObject()
            .put("id", "gpt-5.6-sol")
            .put("defaultReasoningEffort", "high")
            .put("supportedReasoningEfforts", "none,low,medium,high,xhigh"));
        assertEquals("low", legacy.defaultReasoningEffort);
        assertTrue(legacy.supportedReasoningEfforts.endsWith("max,ultra"));
        assertEquals("v2", legacy.multiAgentVersion);
        assertTrue(legacy.parallelToolCalls);
        assertTrue(legacy.verbosity);
        assertTrue(legacy.responsesLite);
        assertFalse(legacy.includeSkillsInstructions);
    }

    @Test
    public void explicitSolFieldsWinOverTemplate() throws Exception {
        CodexProviderStore.ModelConfig custom = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "gpt-5.6-sol")
            .put("contextWindow", 200000)
            .put("defaultReasoningEffort", "medium")
            .put("supportedReasoningEfforts", "low,medium,high")
            .put("multiAgentVersion", "v1")
            .put("toolMode", JSONObject.NULL)
            .put("supportsParallelToolCalls", false));
        assertEquals(200000L, custom.contextWindow);
        assertEquals("medium", custom.defaultReasoningEffort);
        assertEquals("low,medium,high", custom.supportedReasoningEfforts);
        assertEquals("v1", custom.multiAgentVersion);
        assertEquals("", custom.toolMode);
        assertFalse(custom.parallelToolCalls);
    }

    @Test
    public void solCatalogContainsUltraRuntimeMetadata() throws Exception {
        CodexProviderStore.ModelConfig sol = CodexProviderStore.ModelConfig.from(
            new JSONObject().put("id", "gpt-5.6-sol"));
        JSONObject entry = CodexModelCatalog.buildEntry(sol, 0);
        assertEquals(372000L, entry.getLong("context_window"));
        assertEquals("low", entry.getString("default_reasoning_level"));
        assertEquals("v2", entry.getString("multi_agent_version"));
        assertEquals("code_mode_only", entry.getString("tool_mode"));
        JSONArray levels = entry.getJSONArray("supported_reasoning_levels");
        assertEquals(6, levels.length());
        assertEquals("ultra", levels.getJSONObject(5).getString("effort"));
        assertTrue(entry.getBoolean("use_responses_lite"));
    }

    @Test
    public void responsesTransportMapsUltraButPreservesContext() throws Exception {
        JSONObject source = new JSONObject()
            .put("model", "gpt-5.6-sol")
            .put("max_output_tokens", 1234)
            .put("reasoning", new JSONObject().put("effort", "ultra")
                .put("context", new JSONObject().put("encrypted_content", "opaque")))
            .put("input", "hello");
        JSONObject normalized = new JSONObject(new String(
            LocalApiProxy.normalizeResponsesRequestForUpstream(source.toString().getBytes(StandardCharsets.UTF_8), true),
            StandardCharsets.UTF_8));
        assertEquals("max", normalized.getJSONObject("reasoning").getString("effort"));
        assertTrue(normalized.getJSONObject("reasoning").has("context"));
        JSONObject filtered = new JSONObject(new String(
            LocalApiProxy.normalizeResponsesRequestForUpstream(source.toString().getBytes(StandardCharsets.UTF_8), false),
            StandardCharsets.UTF_8));
        assertEquals("max", filtered.getJSONObject("reasoning").getString("effort"));
        assertFalse(filtered.getJSONObject("reasoning").has("context"));

        JSONObject chat = new JSONObject(new String(
            ChatCompletionsAdapter.responsesRequestToChat(source.toString().getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8));
        assertEquals("max", chat.getString("reasoning_effort"));
        assertEquals(1234, chat.getInt("max_completion_tokens"));
        assertFalse(chat.has("max_tokens"));
    }

    @Test
    public void ultraTransportUsesEachModelsHighestNativeEffort() throws Exception {
        CodexProviderStore.ModelConfig glm = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "glm-5.2")
            .put("supportedReasoningEfforts", "none,low,medium,high,xhigh,ultra")
            .put("multiAgentVersion", "v2"));
        assertEquals("", glm.ultraTransportEffort);
        assertEquals("xhigh", glm.resolvedUltraTransportEffort());

        CodexProviderStore.ModelConfig sol = CodexProviderStore.ModelConfig.from(
            new JSONObject().put("id", "gpt-5.6-sol"));
        assertEquals("max", sol.ultraTransportEffort);
        assertEquals("max", sol.resolvedUltraTransportEffort());

        CodexProviderStore.ModelConfig explicit = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "custom-v2")
            .put("supportedReasoningEfforts", "low,medium,high,ultra")
            .put("ultraTransportEffort", "medium"));
        assertEquals("medium", explicit.resolvedUltraTransportEffort());
        assertEquals("medium", CodexProviderStore.ModelConfig.fromPersisted(explicit.json())
            .resolvedUltraTransportEffort());
    }

    @Test
    public void coreTranslatedUltraIsRemappedForGlmOnResponsesAndChat() throws Exception {
        java.util.Map<String, String> glmTransport =
            java.util.Collections.singletonMap("glm-5.2", "xhigh");
        JSONObject coreRequest = new JSONObject()
            .put("model", "glm-5.2")
            // Codex Core turns local Ultra into max before the Android proxy receives it.
            .put("reasoning", new JSONObject().put("effort", "max")
                .put("context", new JSONObject().put("encrypted_content", "opaque")))
            .put("max_output_tokens", 64)
            .put("input", "hello");
        byte[] normalizedBytes = LocalApiProxy.normalizeResponsesRequestForUpstream(
            coreRequest.toString().getBytes(StandardCharsets.UTF_8), false, glmTransport);
        JSONObject normalized = new JSONObject(new String(normalizedBytes, StandardCharsets.UTF_8));
        assertEquals("xhigh", normalized.getJSONObject("reasoning").getString("effort"));
        assertFalse(normalized.getJSONObject("reasoning").has("context"));

        JSONObject chat = new JSONObject(new String(
            ChatCompletionsAdapter.responsesRequestToChat(normalizedBytes), StandardCharsets.UTF_8));
        assertEquals("xhigh", chat.getString("reasoning_effort"));

        coreRequest.getJSONObject("reasoning").put("effort", "high");
        JSONObject ordinary = new JSONObject(new String(
            LocalApiProxy.normalizeResponsesRequestForUpstream(
                coreRequest.toString().getBytes(StandardCharsets.UTF_8), true, glmTransport),
            StandardCharsets.UTF_8));
        assertEquals("high", ordinary.getJSONObject("reasoning").getString("effort"));
        assertTrue(ordinary.getJSONObject("reasoning").has("context"));
    }

    @Test
    public void deepSeekReasoningContentIsGroupedWithParallelToolCalls() throws Exception {
        ChatCompletionsAdapter.clearHistoryForTests();
        JSONArray input = new JSONArray()
            .put(new JSONObject().put("type", "reasoning").put("summary", new JSONArray()
                .put(new JSONObject().put("type", "summary_text").put("text", "inspect with agents"))))
            .put(new JSONObject().put("type", "function_call").put("call_id", "call_a")
                .put("name", "spawn_agent").put("arguments", "{\"task\":\"a\"}"))
            .put(new JSONObject().put("type", "function_call").put("call_id", "call_b")
                .put("name", "spawn_agent").put("arguments", "{\"task\":\"b\"}"))
            .put(new JSONObject().put("type", "function_call_output").put("call_id", "call_a").put("output", "done a"))
            .put(new JSONObject().put("type", "function_call_output").put("call_id", "call_b").put("output", "done b"));
        JSONObject chat = new JSONObject(new String(ChatCompletionsAdapter.responsesRequestToChat(
            new JSONObject().put("model", "deepseek-v4-pro").put("input", input).toString()
                .getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        JSONArray messages = chat.getJSONArray("messages");
        JSONObject assistant = findAssistantToolMessage(messages);
        assertEquals("inspect with agents", assistant.getString("reasoning_content"));
        assertEquals(2, assistant.getJSONArray("tool_calls").length());
        assertEquals("call_a", assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        assertEquals("call_b", assistant.getJSONArray("tool_calls").getJSONObject(1).getString("id"));
        assertEquals("tool", messages.getJSONObject(1).getString("role"));
        assertEquals("tool", messages.getJSONObject(2).getString("role"));
    }

    @Test
    public void deepSeekReasoningContentIsRestoredAcrossPreviousResponseToolTurns() throws Exception {
        ChatCompletionsAdapter.clearHistoryForTests();
        JSONObject upstream = new JSONObject().put("model", "deepseek-v4-pro")
            .put("choices", new JSONArray().put(new JSONObject().put("message", new JSONObject()
                .put("role", "assistant").put("content", JSONObject.NULL)
                .put("reasoning_content", "original private reasoning")
                .put("tool_calls", new JSONArray()
                    .put(new JSONObject().put("index", 0).put("id", "call_hist_a").put("type", "function")
                        .put("function", new JSONObject().put("name", "spawn_agent").put("arguments", "{\"task\":\"a\"}")))
                    .put(new JSONObject().put("index", 1).put("id", "call_hist_b").put("type", "function")
                        .put("function", new JSONObject().put("name", "wait_agent").put("arguments", "{\"id\":\"a\"}")))))));
        ChatCompletionsAdapter.ChatResult adapted = ChatCompletionsAdapter.chatResponseToResponses(
            upstream.toString().getBytes(StandardCharsets.UTF_8), "application/json", "deepseek-v4-pro");
        JSONObject completed = completedResponse(adapted);

        JSONArray outputs = new JSONArray()
            // Codex previous_response_id follow-ups can include only call_id on the call item.
            .put(new JSONObject().put("type", "function_call").put("call_id", "call_hist_a"))
            .put(new JSONObject().put("type", "function_call_output").put("call_id", "call_hist_a").put("output", "spawned"))
            .put(new JSONObject().put("type", "function_call_output").put("call_id", "call_hist_b").put("output", "finished"));
        JSONObject followup = new JSONObject().put("model", "deepseek-v4-pro")
            .put("previous_response_id", completed.getString("id")).put("input", outputs);
        byte[] chatBytes = ChatCompletionsAdapter.responsesRequestToChat(
            followup.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject chat = new JSONObject(new String(chatBytes, StandardCharsets.UTF_8));
        assertEquals("assistantToolTurns=1 reasoningContent=1 placeholders=0",
            ChatCompletionsAdapter.toolReasoningSummary(chatBytes));
        JSONObject assistant = findAssistantToolMessage(chat.getJSONArray("messages"));
        assertEquals("original private reasoning", assistant.getString("reasoning_content"));
        assertEquals(2, assistant.getJSONArray("tool_calls").length());
        assertEquals("spawn_agent", assistant.getJSONArray("tool_calls").getJSONObject(0)
            .getJSONObject("function").getString("name"));
        assertEquals("wait_agent", assistant.getJSONArray("tool_calls").getJSONObject(1)
            .getJSONObject("function").getString("name"));

        // Subagent requests can omit previous_response_id. A unique call_id must still recover history.
        followup.remove("previous_response_id");
        JSONObject fallbackChat = new JSONObject(new String(ChatCompletionsAdapter.responsesRequestToChat(
            followup.toString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        assertEquals("original private reasoning",
            findAssistantToolMessage(fallbackChat.getJSONArray("messages")).getString("reasoning_content"));
    }

    @Test
    public void chatAdapterPreservesInterAgentTaskPayloads() throws Exception {
        JSONObject agentMessage = new JSONObject().put("type", "agent_message")
            .put("author", "/root").put("recipient", "/root/child_b")
            .put("content", new JSONArray()
                .put(new JSONObject().put("type", "input_text")
                    .put("text", "Message Type: NEW_TASK\nPayload:\n"))
                .put(new JSONObject().put("type", "encrypted_content")
                    .put("encrypted_content", "Reply with only B")));
        JSONObject request = new JSONObject().put("model", "deepseek-v4-pro")
            .put("input", new JSONArray().put(agentMessage));
        byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
        assertEquals(1, ChatCompletionsAdapter.agentMessageCount(requestBytes));

        JSONObject chat = new JSONObject(new String(
            ChatCompletionsAdapter.responsesRequestToChat(requestBytes), StandardCharsets.UTF_8));
        JSONArray messages = chat.getJSONArray("messages");
        assertEquals(1, messages.length());
        assertEquals("user", messages.getJSONObject(0).getString("role"));
        String content = messages.getJSONObject(0).getString("content");
        assertTrue(content.contains("Message Type: NEW_TASK"));
        assertTrue(content.contains("Reply with only B"));
    }

    @Test
    public void profileBuildsPerModelUltraTransportMap() throws Exception {
        java.util.ArrayList<CodexProviderStore.ModelConfig> models = new java.util.ArrayList<>();
        models.add(CodexProviderStore.ModelConfig.from(new JSONObject().put("id", "glm-5.2")
            .put("supportedReasoningEfforts", "low,medium,high,xhigh,ultra")));
        models.add(CodexProviderStore.ModelConfig.from(new JSONObject().put("id", "gpt-5.6-sol")));
        CodexProviderStore.Profile profile = new CodexProviderStore.Profile(
            "id", "name", "", "https://example.com/v1", "secret", "glm-5.2", "auto", models);
        assertEquals("xhigh", profile.ultraTransportEfforts().get("glm-5.2"));
        assertEquals("max", profile.ultraTransportEfforts().get("gpt-5.6-sol"));
    }

    @Test
    public void thirdPartyUltraFlattensCollaborationToolsAndRestoresCallNamespace() throws Exception {
        JSONObject request = new JSONObject().put("model", "glm-5.2")
            .put("reasoning", new JSONObject().put("effort", "max"))
            .put("input", new JSONArray()
                .put(new JSONObject().put("type", "message").put("role", "assistant")
                    .put("content", new JSONArray().put(
                        new JSONObject().put("type", "output_text").put("text", "inherited prefill"))))
                .put(new JSONObject().put("type", "agent_message")
                .put("content", new JSONArray()
                    .put(new JSONObject().put("type", "input_text").put("text", "Message Type: NEW_TASK\nPayload:\n"))
                    .put(new JSONObject().put("type", "encrypted_content").put("encrypted_content", "Reply only A")))))
            .put("tools", new JSONArray().put(new JSONObject()
                .put("type", "namespace").put("name", "collaboration")
                .put("tools", new JSONArray()
                    .put(new JSONObject().put("type", "function").put("name", "spawn_agent")
                        .put("description", "spawn").put("parameters", new JSONObject()))
                    .put(new JSONObject().put("type", "function").put("name", "wait_agent")
                        .put("description", "wait").put("parameters", new JSONObject())))));
        byte[] normalizedBytes = LocalApiProxy.normalizeResponsesRequestForUpstream(
            request.toString().getBytes(StandardCharsets.UTF_8), false,
            java.util.Collections.singletonMap("glm-5.2", "xhigh"));
        JSONObject normalized = new JSONObject(new String(normalizedBytes, StandardCharsets.UTF_8));
        assertEquals("xhigh", normalized.getJSONObject("reasoning").getString("effort"));
        assertEquals(2, normalized.getJSONArray("tools").length());
        assertEquals("spawn_agent", normalized.getJSONArray("tools").getJSONObject(0).getString("name"));
        assertEquals("spawn_agent,wait_agent", LocalApiProxy.requestToolSummary(normalizedBytes));
        JSONArray normalizedInput = normalized.getJSONArray("input");
        assertEquals("assistant", normalizedInput.getJSONObject(0).getString("role"));
        JSONObject normalizedAgentMessage = normalizedInput.getJSONObject(normalizedInput.length() - 1);
        assertEquals("message", normalizedAgentMessage.getString("type"));
        assertEquals("user", normalizedAgentMessage.getString("role"));
        assertTrue(normalizedAgentMessage.getJSONArray("content").getJSONObject(0)
            .getString("text").contains("Reply only A"));

        JSONObject event = new JSONObject().put("type", "response.output_item.done")
            .put("item", new JSONObject().put("type", "function_call")
                .put("call_id", "call-1").put("name", "spawn_agent").put("arguments", "{}"));
        String sse = "event: response.output_item.done\n" + "data: " + event + "\n\n";
        String rewritten = new String(LocalApiProxy.rewriteCollaborationToolCalls(
            sse.getBytes(StandardCharsets.UTF_8), "text/event-stream"), StandardCharsets.UTF_8);
        String data = rewritten.split("\n")[1].substring("data: ".length());
        JSONObject rewrittenEvent = new JSONObject(data);
        assertEquals("collaboration", rewrittenEvent.getJSONObject("item").getString("namespace"));
        assertEquals("spawn_agent", rewrittenEvent.getJSONObject("item").getString("name"));
    }

    @Test
    public void stabilityModeHardRemovesSpawnAgentFromChildRequests() throws Exception {
        java.util.List<String[]> childHeaders = java.util.Arrays.asList(
            new String[]{"content-type", "application/json"},
            new String[]{"x-codex-parent-thread-id", "parent-thread"});
        assertTrue(LocalApiProxy.isSubagentRequest(childHeaders));
        assertTrue(LocalApiProxy.isSubagentRequest(java.util.Collections.singletonList(
            new String[]{"x-openai-subagent", "collab_spawn"})));
        assertFalse(LocalApiProxy.isSubagentRequest(java.util.Collections.singletonList(
            new String[]{"content-type", "application/json"})));

        JSONObject namespaced = new JSONObject().put("tools", new JSONArray()
            .put(new JSONObject().put("type", "namespace").put("name", "collaboration")
                .put("tools", new JSONArray()
                    .put(new JSONObject().put("type", "function").put("name", "spawn_agent"))
                    .put(new JSONObject().put("type", "function").put("name", "wait_agent"))))
            .put(new JSONObject().put("type", "function").put("name", "exec_command")));
        LocalApiProxy.ToolRemovalResult filtered = LocalApiProxy.removeSpawnAgentTool(
            namespaced.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(filtered.removed);
        String summary = LocalApiProxy.requestToolSummary(filtered.body);
        assertFalse(summary.contains("spawn_agent"));
        assertTrue(summary.contains("collaboration.wait_agent"));
        assertTrue(summary.contains("exec_command"));

        JSONObject flattened = new JSONObject().put("tools", new JSONArray()
            .put(new JSONObject().put("type", "function").put("name", "spawn_agent"))
            .put(new JSONObject().put("type", "function").put("name", "wait_agent")));
        LocalApiProxy.ToolRemovalResult flatFiltered = LocalApiProxy.removeSpawnAgentTool(
            flattened.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(flatFiltered.removed);
        assertEquals("wait_agent", LocalApiProxy.requestToolSummary(flatFiltered.body));
    }

    @Test
    public void codexWireApiIsAlwaysResponsesRegardlessOfUpstreamFormat() {
        assertEquals("responses", LocalApiProxy.CODEX_WIRE_API);
        assertNotEquals("openai_chat", LocalApiProxy.CODEX_WIRE_API);
        assertNotEquals("openai_responses", LocalApiProxy.CODEX_WIRE_API);
    }

    @Test
    public void chatTokenFieldAndFallbackStatusesAreStrict() throws Exception {
        JSONObject legacy = new JSONObject().put("model", "custom-model")
            .put("max_output_tokens", 42).put("input", "hello");
        JSONObject chat = new JSONObject(new String(
            ChatCompletionsAdapter.responsesRequestToChat(legacy.toString().getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8));
        assertEquals(42, chat.getInt("max_tokens"));
        assertFalse(chat.has("max_completion_tokens"));
        assertTrue(LocalApiProxy.shouldFallbackToChat(404));
        assertTrue(LocalApiProxy.shouldFallbackToChat(405));
        assertTrue(LocalApiProxy.shouldFallbackToChat(501));
        assertFalse(LocalApiProxy.shouldFallbackToChat(401));
        assertFalse(LocalApiProxy.shouldFallbackToChat(429));
        assertFalse(LocalApiProxy.shouldFallbackToChat(500));
    }

    @Test
    public void reasoningContextForwardingIsPersistedAndDefaultsOff() throws Exception {
        CodexProviderStore.Profile legacy = CodexProviderStore.Profile.from(new JSONObject()
            .put("id", "legacy").put("name", "Legacy"));
        assertFalse(legacy.forwardReasoningContext);

        CodexProviderStore.Profile enabled = new CodexProviderStore.Profile(
            "enabled", "Enabled", "", "https://example.com/v1", "secret", "gpt-5.6-sol",
            "auto", java.util.Collections.emptyList(), false, false, false, true);
        CodexProviderStore.Profile restored = CodexProviderStore.Profile.from(enabled.json());
        assertTrue(restored.forwardReasoningContext);
    }

    @Test
    public void invalidDefaultEffortFallsBackInsideSupportedSet() throws Exception {
        CodexProviderStore.ModelConfig model = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "custom")
            .put("defaultReasoningEffort", "ultra")
            .put("supportedReasoningEfforts", "low,high"));
        assertEquals("high", model.defaultReasoningEffort);
    }

    @Test
    public void turnRuntimeDiagnosticReportsEffectiveUltraMode() throws Exception {
        File session = File.createTempFile("codex-turn", ".jsonl");
        try (FileWriter writer = new FileWriter(session)) {
            writer.write(new JSONObject().put("type", "session_meta")
                .put("payload", new JSONObject().put("source", "vscode")).toString() + "\n");
            writer.write(new JSONObject().put("type", "turn_context")
                .put("payload", new JSONObject().put("turn_id", "turn-1")
                    .put("model", "gpt-5.6-sol").put("effort", "ultra")
                    .put("multi_agent_version", "v2").put("multi_agent_mode", "proactive"))
                .toString() + "\n");
        }
        String diagnostic = CodexAppServerBridge.readTurnRuntimeDiagnostic(session, "turn-1");
        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("effort=ultra"));
        assertTrue(diagnostic.contains("multiAgentVersion=v2"));
        assertTrue(diagnostic.contains("multiAgentMode=proactive"));
        assertTrue(session.delete());
    }

    @Test
    public void childRuntimeDiagnosticKeepsOriginalDepthSource() throws Exception {
        File session = File.createTempFile("codex-child-turn", ".jsonl");
        try (FileWriter writer = new FileWriter(session)) {
            writer.write(new JSONObject().put("type", "session_meta")
                .put("payload", new JSONObject().put("source", new JSONObject()
                    .put("subagent", new JSONObject().put("thread_spawn", new JSONObject()
                        .put("depth", 1).put("parent_thread_id", "parent"))))).toString() + "\n");
            writer.write(new JSONObject().put("type", "session_meta")
                .put("payload", new JSONObject().put("source", "vscode")).toString() + "\n");
            writer.write(new JSONObject().put("type", "turn_context")
                .put("payload", new JSONObject().put("turn_id", "child-turn")
                    .put("model", "glm-5.2").put("effort", "ultra")
                    .put("multi_agent_version", "v2").put("multi_agent_mode", "proactive"))
                .toString() + "\n");
        }
        String diagnostic = CodexAppServerBridge.readTurnRuntimeDiagnostic(session, "child-turn");
        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("source=subagent(depth=1)"));
        assertFalse(diagnostic.contains("source=vscode"));
        assertTrue(session.delete());
    }

    @Test
    public void proxyToolInventoryLogContainsNamesButNotPrompt() throws Exception {
        JSONObject request = new JSONObject()
            .put("input", "private prompt must not appear")
            .put("tools", new JSONArray()
                .put(new JSONObject().put("type", "function").put("name", "exec_command"))
                .put(new JSONObject().put("type", "namespace").put("name", "collaboration")
                    .put("tools", new JSONArray()
                        .put(new JSONObject().put("type", "function").put("name", "spawn_agent"))
                        .put(new JSONObject().put("type", "function").put("name", "wait_agent")))));
        String summary = LocalApiProxy.requestToolSummary(
            request.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(summary.contains("exec_command"));
        assertTrue(summary.contains("collaboration.spawn_agent"));
        assertTrue(summary.contains("collaboration.wait_agent"));
        assertFalse(summary.contains("private prompt"));
    }

    @Test
    public void bridgeLogSummaryNeverIncludesPayloadOrSecrets() throws Exception {
        JSONObject message = new JSONObject().put("id", "request-1").put("result", new JSONObject()
            .put("apiKey", "secret-value").put("prompt", "private prompt"));
        String summary = CodexAppServerBridge.messageSummary(message);
        assertTrue(summary.contains("id=request-1"));
        assertTrue(summary.contains("result=true"));
        assertFalse(summary.contains("secret-value"));
        assertFalse(summary.contains("private prompt"));
        String stderr = CodexAppServerBridge.redactSensitiveLogLine(
            "authorization: Bearer sk-supersecret123 api_key=another-secret");
        assertFalse(stderr.contains("sk-supersecret123"));
        assertFalse(stderr.contains("another-secret"));
        assertTrue(stderr.contains("[REDACTED]"));
    }

    @Test
    public void providerOverridePreservesModelAndEffort() throws Exception {
        JSONObject config = new JSONObject().put("model", "gpt-5.6-sol")
            .put("model_reasoning_effort", "ultra")
            .put("model_provider", "old");
        JSONObject request = new JSONObject().put("method", "thread/start")
            .put("params", new JSONObject().put("config", config));
        CodexAppServerBridge.applyAndroidProviderOverrides(request);
        JSONObject params = request.getJSONObject("params");
        assertEquals("ilyop_android", params.getString("modelProvider"));
        assertEquals("gpt-5.6-sol", config.getString("model"));
        assertEquals("ultra", config.getString("model_reasoning_effort"));
        assertFalse(config.has("model_provider"));

        JSONObject turn = new JSONObject().put("method", "turn/start")
            .put("params", new JSONObject().put("model", "gpt-5.6-sol").put("effort", "high"));
        CodexAppServerBridge.applyAndroidProviderOverrides(turn);
        assertEquals("high", turn.getJSONObject("params").getString("effort"));
    }
    @Test
    public void catalogWritesExplicitDisabledWhenMultiAgentIsOff() throws Exception {
        CodexProviderStore.ModelConfig ordinary = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "ordinary")
            .put("supportedReasoningEfforts", "low,medium,high"));
        JSONObject entry = CodexModelCatalog.buildEntry(ordinary, 0);
        assertEquals("disabled", entry.getString("multi_agent_version"));
    }

    @Test
    public void customModelsCanOptIntoUltraV2Metadata() throws Exception {
        CodexProviderStore.ModelConfig custom = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "glm-5.2")
            .put("defaultReasoningEffort", "high")
            .put("supportedReasoningEfforts", "low,medium,high,xhigh,ultra")
            .put("toolMode", "code_mode_only"));
        JSONObject entry = CodexModelCatalog.buildEntry(custom, 0);
        assertEquals("v2", entry.getString("multi_agent_version"));
        assertEquals("code_mode_only", entry.getString("tool_mode"));
        assertTrue(CodexProviderStore.ModelConfig.supportsEffort(custom.supportedReasoningEfforts, "ultra"));

        CodexProviderStore.ModelConfig v2Only = CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "custom-v2")
            .put("supportedReasoningEfforts", "low,medium,high")
            .put("multiAgentVersion", "v2"));
        assertEquals("v2", v2Only.multiAgentVersion);
        assertTrue(CodexProviderStore.ModelConfig.supportsEffort(v2Only.supportedReasoningEfforts, "ultra"));
    }

    @Test
    public void legacyCustomUltraPresetStopsForcingSolOnlyCodeMode() throws Exception {
        JSONObject legacyValue = new JSONObject()
            .put("id", "glm-5.2")
            .put("supportedReasoningEfforts", "low,medium,high,xhigh,ultra")
            .put("multiAgentVersion", "v2")
            .put("toolMode", "code_mode_only");
        CodexProviderStore.ModelConfig migrated =
            CodexProviderStore.ModelConfig.fromPersisted(legacyValue);
        assertEquals("", migrated.toolMode);
        assertEquals("xhigh", migrated.resolvedUltraTransportEffort());

        // Once the new field exists, code_mode_only is an explicit user choice and is preserved.
        legacyValue.put("ultraTransportEffort", "");
        CodexProviderStore.ModelConfig explicit =
            CodexProviderStore.ModelConfig.fromPersisted(legacyValue);
        assertEquals("code_mode_only", explicit.toolMode);
    }

    @Test
    public void customV2StabilityPreventsRecursiveDelegationAndBoundsWaits() throws Exception {
        java.util.ArrayList<CodexProviderStore.ModelConfig> customModels = new java.util.ArrayList<>();
        customModels.add(CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "deepseek-v4-pro")
            .put("supportedReasoningEfforts", "low,medium,high,xhigh,ultra")
            .put("multiAgentVersion", "v2")));
        CodexProviderStore.Profile stable = new CodexProviderStore.Profile(
            "stable", "Stable", "", "https://api.deepseek.com", "secret", "deepseek-v4-pro",
            "openai_chat", customModels, false, false, false, true, 3, 6, true);
        assertTrue(stable.hasV2Models());
        assertTrue(stable.hasCustomV2Models());
        assertTrue(CodexProviderStore.Profile.from(stable.json()).customSubagentStability);
        StringBuilder stableToml = new StringBuilder();
        CodexProviderStore.Profile.appendAgentConfig(stableToml, stable);
        String stableText = stableToml.toString();
        assertTrue(stableText.contains("max_concurrent_threads_per_session = 4"));
        assertTrue(stableText.contains("min_wait_timeout_ms = 5000"));
        assertTrue(stableText.contains("default_wait_timeout_ms = 30000"));
        assertTrue(stableText.contains("max_wait_timeout_ms = 120000"));
        assertTrue(stableText.contains("Do not call spawn_agent"));
        assertTrue(stableText.contains("Never wait indefinitely"));
        assertTrue(stableText.contains("hide_spawn_agent_metadata = false"));
        assertFalse(stableText.contains("expose_spawn_agent_model_overrides"));

        CodexProviderStore.Profile disabled = new CodexProviderStore.Profile(
            "disabled", "Disabled", "", "https://api.deepseek.com", "secret", "deepseek-v4-pro",
            "openai_chat", customModels, false, false, false, true, 3, 6, false);
        StringBuilder disabledToml = new StringBuilder();
        CodexProviderStore.Profile.appendAgentConfig(disabledToml, disabled);
        assertFalse(disabledToml.toString().contains("subagent_usage_hint_text"));
        assertFalse(CodexProviderStore.Profile.from(disabled.json()).customSubagentStability);

        java.util.ArrayList<CodexProviderStore.ModelConfig> solModels = new java.util.ArrayList<>();
        solModels.add(CodexProviderStore.ModelConfig.from(new JSONObject().put("id", "gpt-5.6-sol")));
        CodexProviderStore.Profile sol = new CodexProviderStore.Profile(
            "sol", "Sol", "", "https://example.com/v1", "secret", "gpt-5.6-sol",
            "auto", solModels, false, false, false, false, 3, 6, true);
        assertTrue(sol.hasV2Models());
        assertFalse(sol.hasCustomV2Models());
        StringBuilder solToml = new StringBuilder();
        CodexProviderStore.Profile.appendAgentConfig(solToml, sol);
        assertFalse(solToml.toString().contains("subagent_usage_hint_text"));
    }

    @Test
    public void localProxyBoundsCustomModelStallsAndConcurrency() {
        assertEquals(300_000, LocalApiProxy.UPSTREAM_IDLE_TIMEOUT_MS);
        assertEquals(32, LocalApiProxy.MAX_CONCURRENT_UPSTREAM_REQUESTS);
        assertFalse(new LocalApiProxy("https://example.com/v1").hasActiveRequests());
    }

    @Test
    public void onlyFinalAnswerAgentMessagesScheduleMissingTurnCompletion() throws Exception {
        JSONObject params = new JSONObject().put("item", new JSONObject()
            .put("type", "agentMessage").put("phase", "commentary"));
        assertFalse(CodexAppServerBridge.isFinalAgentMessage(params));

        params.getJSONObject("item").remove("phase");
        assertFalse(CodexAppServerBridge.isFinalAgentMessage(params));

        params.getJSONObject("item").put("phase", "final_answer");
        assertTrue(CodexAppServerBridge.isFinalAgentMessage(params));

        params.getJSONObject("item").put("type", "collabAgentToolCall");
        assertFalse(CodexAppServerBridge.isFinalAgentMessage(params));
    }

    @Test
    public void missingTurnFallbackWaitsForUpstreamAndIdleGracePeriod() {
        int requiredIdleChecks = CodexAppServerBridge.MISSING_TURN_COMPLETION_IDLE_CHECKS;
        assertTrue(requiredIdleChecks >= 2);
        assertFalse(CodexAppServerBridge.shouldSynthesizeMissingTurnCompletion(false, false, requiredIdleChecks));
        assertFalse(CodexAppServerBridge.shouldSynthesizeMissingTurnCompletion(true, true, requiredIdleChecks));
        assertFalse(CodexAppServerBridge.shouldSynthesizeMissingTurnCompletion(true, true, requiredIdleChecks + 100));
        assertFalse(CodexAppServerBridge.shouldSynthesizeMissingTurnCompletion(true, false, requiredIdleChecks - 1));
        assertTrue(CodexAppServerBridge.shouldSynthesizeMissingTurnCompletion(true, false, requiredIdleChecks));
    }

    @Test
    public void agentLimitsPersistAndMapToOfficialCodexSettings() throws Exception {
        CodexProviderStore.Profile legacy = CodexProviderStore.Profile.from(new JSONObject()
            .put("id", "legacy").put("name", "Legacy"));
        assertEquals(3, legacy.ultraSubagentLimit);
        assertEquals(6, legacy.normalSubagentLimit);

        CodexProviderStore.Profile configured = new CodexProviderStore.Profile(
            "custom", "Custom", "", "https://example.com/v1", "secret", "glm-5.2",
            "auto", java.util.Collections.emptyList(), false, false, false, false, 7, 11);
        CodexProviderStore.Profile restored = CodexProviderStore.Profile.from(configured.json());
        assertEquals(7, restored.ultraSubagentLimit);
        assertEquals(8, restored.ultraConcurrentThreadLimit());
        assertEquals(11, restored.normalSubagentLimit);

        String[] overrides = CodexAppServerBridge.agentConfigOverrides(7, 11);
        assertArrayEquals(new String[]{
            "features.multi_agent_v2.enabled=false",
            "features.multi_agent_v2.max_concurrent_threads_per_session=8",
            "agents.max_threads=11"
        }, overrides);

        StringBuilder toml = new StringBuilder();
        CodexProviderStore.Profile.appendAgentConfig(toml, configured);
        String text = toml.toString();
        assertTrue(text.contains("[features.multi_agent_v2]"));
        assertTrue(text.contains("enabled = false"));
        assertTrue(text.contains("max_concurrent_threads_per_session = 8"));
        assertTrue(text.contains("[agents]"));
        assertTrue(text.contains("max_threads = 11"));

        java.util.ArrayList<CodexProviderStore.ModelConfig> v2Models = new java.util.ArrayList<>();
        v2Models.add(CodexProviderStore.ModelConfig.from(new JSONObject()
            .put("id", "glm-5.2")
            .put("supportedReasoningEfforts", "low,medium,high,xhigh,ultra")
            .put("multiAgentVersion", "v2")));
        CodexProviderStore.Profile v2Profile = new CodexProviderStore.Profile(
            "v2", "V2", "", "https://example.com/v1", "secret", "glm-5.2",
            "auto", v2Models, false, false, false, false, 7, 11);
        assertTrue(v2Profile.hasV2Models());
        assertArrayEquals(new String[]{
            "suppress_unstable_features_warning=true",
            "features.multi_agent_v2.enabled=true",
            "features.multi_agent_v2.max_concurrent_threads_per_session=8"
        }, CodexAppServerBridge.agentConfigOverrides(7, 11, true));
        StringBuilder v2Toml = new StringBuilder();
        CodexProviderStore.Profile.appendAgentConfig(v2Toml, v2Profile);
        assertTrue(v2Toml.toString().contains("enabled = true"));
        assertFalse(v2Toml.toString().contains("[agents]"));
    }

    @Test
    public void chatSseAdapterEmitsFirstTextDeltaBeforeUpstreamCompletes() throws Exception {
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream upstream = new PipedOutputStream(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch firstDelta = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread converter = new Thread(() -> {
            try {
                ChatCompletionsAdapter.streamChatResponseToResponses(input, "text/event-stream",
                    "demo", Collections.emptySet(), value -> {
                        synchronized (output) { output.write(value); }
                        if (new String(value, StandardCharsets.UTF_8)
                                .contains("response.output_text.delta")) firstDelta.countDown();
                    });
            } catch (Throwable error) { failure.set(error); }
        });
        converter.start();

        JSONObject first = chatChunk(new JSONObject().put("content", "Hel"), JSONObject.NULL);
        upstream.write(sseData(first).getBytes(StandardCharsets.UTF_8));
        upstream.flush();
        assertTrue("first delta must arrive before upstream EOF", firstDelta.await(2, TimeUnit.SECONDS));
        String partial;
        synchronized (output) { partial = output.toString("UTF-8"); }
        assertTrue(partial.contains("\"delta\":\"Hel\""));
        assertFalse(partial.contains("response.completed"));

        JSONObject second = chatChunk(new JSONObject().put("content", "lo"), "stop");
        upstream.write(sseData(second).getBytes(StandardCharsets.UTF_8));
        upstream.write(sseData(new JSONObject().put("model", "demo")
            .put("choices", new JSONArray()).put("usage", new JSONObject()
                .put("prompt_tokens", 3).put("completion_tokens", 2).put("total_tokens", 5)))
            .getBytes(StandardCharsets.UTF_8));
        upstream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        upstream.close();
        converter.join(2_000L);
        assertFalse("converter must finish", converter.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());

        byte[] completedBytes;
        synchronized (output) { completedBytes = output.toByteArray(); }
        String events = new String(completedBytes, StandardCharsets.UTF_8);
        assertTrue(events.indexOf("\"delta\":\"Hel\"") < events.indexOf("\"delta\":\"lo\""));
        JSONObject completed = completedResponse(completedBytes);
        assertEquals("Hello", completed.getJSONArray("output").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0).getString("text"));
        assertEquals(5, completed.getJSONObject("usage").getInt("total_tokens"));
    }

    @Test
    public void chatSseAdapterStreamsReasoningBeforeTextWithValidItemLifecycle() throws Exception {
        StringBuilder sse = new StringBuilder();
        sse.append(sseData(chatChunk(new JSONObject().put("reasoning_content", "think "), JSONObject.NULL)));
        sse.append(sseData(chatChunk(new JSONObject().put("reasoning_content", "more"), JSONObject.NULL)));
        sse.append(sseData(chatChunk(new JSONObject().put("content", "answer"), "stop")));
        sse.append("data: [DONE]\n\n");
        StreamCapture capture = convertChatStream(sse.toString(), "text/event-stream", Collections.emptySet());
        String events = capture.text();
        int reasoningDelta = events.indexOf("response.reasoning_summary_text.delta");
        int reasoningDone = events.indexOf("response.reasoning_summary_text.done");
        int textDelta = events.indexOf("response.output_text.delta");
        assertTrue(reasoningDelta >= 0 && reasoningDelta < reasoningDone && reasoningDone < textDelta);
        JSONObject completed = completedResponse(capture.bytes());
        JSONArray output = completed.getJSONArray("output");
        assertEquals("reasoning", output.getJSONObject(0).getString("type"));
        assertEquals("think more", output.getJSONObject(0).getJSONArray("summary")
            .getJSONObject(0).getString("text"));
        assertEquals("answer", output.getJSONObject(1).getJSONArray("content")
            .getJSONObject(0).getString("text"));
    }

    @Test
    public void chatSseAdapterKeepsParallelToolFragmentsSeparatedAndOrdered() throws Exception {
        JSONArray firstCalls = new JSONArray()
            .put(chatToolDelta(1, "call_b", "wait_agent", "{\"id\":\""))
            .put(chatToolDelta(0, "call_a", "spawn_agent", "{\"input\":\""));
        JSONArray secondCalls = new JSONArray()
            .put(chatToolDelta(0, "", "", "x\"}"))
            .put(chatToolDelta(1, "", "", "a\"}"));
        String sse = sseData(chatChunk(new JSONObject().put("tool_calls", firstCalls), JSONObject.NULL))
            + sseData(chatChunk(new JSONObject().put("tool_calls", secondCalls), "tool_calls"))
            + "data: [DONE]\n\n";
        StreamCapture capture = convertChatStream(sse, "text/event-stream",
            new HashSet<>(Collections.singletonList("spawn_agent")));
        JSONObject completed = completedResponse(capture.bytes());
        JSONArray output = completed.getJSONArray("output");
        assertEquals(2, output.length());
        assertEquals("custom_tool_call", output.getJSONObject(0).getString("type"));
        assertEquals("call_a", output.getJSONObject(0).getString("call_id"));
        assertEquals("x", output.getJSONObject(0).getString("input"));
        assertEquals("function_call", output.getJSONObject(1).getString("type"));
        assertEquals("call_b", output.getJSONObject(1).getString("call_id"));
        assertEquals("{\"id\":\"a\"}", output.getJSONObject(1).getString("arguments"));
    }

    @Test
    public void responsesNormalizerSynthesizesMissingItemsBeforeProviderDeltas() throws Exception {
        JSONObject reasoningPart = new JSONObject().put("type", "response.reasoning_summary_part.added")
            .put("item_id", "rs_1").put("output_index", 0).put("summary_index", 0)
            .put("part", new JSONObject().put("type", "summary_text").put("text", ""));
        JSONObject reasoningDelta = new JSONObject().put("type", "response.reasoning_summary_text.delta")
            .put("item_id", "rs_1").put("output_index", 0).put("summary_index", 0)
            .put("delta", "think");
        JSONObject textDelta = new JSONObject().put("type", "response.output_text.delta")
            .put("item_id", "msg_1").put("output_index", 1).put("content_index", 0)
            .put("delta", "Hello");
        JSONObject lateAdded = new JSONObject().put("type", "response.output_item.added")
            .put("output_index", 1).put("item", new JSONObject().put("id", "msg_1")
                .put("type", "message").put("role", "assistant").put("status", "in_progress")
                .put("content", new JSONArray()));
        JSONObject message = new JSONObject().put("id", "msg_1").put("type", "message")
            .put("role", "assistant").put("status", "completed")
            .put("content", new JSONArray().put(new JSONObject().put("type", "output_text")
                .put("text", "Hello").put("annotations", new JSONArray())));
        JSONObject done = new JSONObject().put("type", "response.output_item.done")
            .put("output_index", 1).put("item", message);
        JSONObject completed = new JSONObject().put("type", "response.completed")
            .put("response", new JSONObject().put("id", "resp_1")
                .put("usage", new JSONObject().put("input_tokens", 1)
                    .put("output_tokens", 1).put("total_tokens", 2)));
        String source = sseEventNameOnly(reasoningPart) + sseEventNameOnly(reasoningDelta)
            + sseEventNameOnly(textDelta) + sseEventNameOnly(lateAdded)
            + sseEventNameOnly(done) + sseEventNameOnly(completed) + "data: [DONE]\n\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ResponsesSseNormalizer.Stats stats = ResponsesSseNormalizer.normalize(
            new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), output::write);
        String normalized = output.toString("UTF-8");

        int reasoningAdded = normalized.indexOf("\"id\":\"rs_1\",\"type\":\"reasoning\"");
        int reasoningPartPosition = normalized.indexOf("response.reasoning_summary_part.added");
        int reasoningDone = normalized.indexOf("response.output_item.done", reasoningPartPosition);
        int messageAdded = normalized.indexOf("\"id\":\"msg_1\",\"type\":\"message\"");
        int textPosition = normalized.indexOf("response.output_text.delta");
        assertTrue(reasoningAdded >= 0 && reasoningAdded < reasoningPartPosition);
        assertTrue(reasoningDone > reasoningPartPosition && reasoningDone < messageAdded);
        assertTrue(messageAdded >= 0 && messageAdded < textPosition);
        assertEquals(2, stats.syntheticAddedItems);
        assertEquals(1, stats.syntheticCompletedItems);
        assertEquals(1, stats.suppressedLateItems);
        assertEquals(0, stats.deltasWithoutItemId);
    }

    @Test
    public void chatAdapterHandlesUsageOnlyDoneMalformedSseAndBufferedJson() throws Exception {
        JSONObject usage = new JSONObject().put("prompt_tokens", 7)
            .put("completion_tokens", 4).put("total_tokens", 11);
        String sse = "\n: keep-alive\n\ndata: not-json\n\n"
            + sseData(new JSONObject().put("model", "demo").put("choices", new JSONArray())
                .put("usage", usage))
            + "data: [DONE]\n\n";
        StreamCapture streamed = convertChatStream(sse, "application/octet-stream", Collections.emptySet());
        assertTrue(streamed.stats.streaming);
        assertEquals(1, streamed.stats.upstreamEvents);
        assertEquals(11, completedResponse(streamed.bytes()).getJSONObject("usage").getInt("total_tokens"));

        JSONObject json = new JSONObject().put("model", "demo")
            .put("choices", new JSONArray().put(new JSONObject().put("message",
                new JSONObject().put("role", "assistant").put("content", "buffered"))))
            .put("usage", usage);
        StreamCapture buffered = convertChatStream(json.toString(), "application/json", Collections.emptySet());
        assertFalse(buffered.stats.streaming);
        assertEquals(1, buffered.stats.outputChunks);
        JSONObject completed = completedResponse(buffered.bytes());
        assertEquals("buffered", completed.getJSONArray("output").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0).getString("text"));
    }

    private static JSONObject chatChunk(JSONObject delta, Object finishReason) throws Exception {
        JSONObject choice = new JSONObject().put("delta", delta);
        if (finishReason != null) choice.put("finish_reason", finishReason);
        return new JSONObject().put("id", "chatcmpl_demo").put("model", "demo")
            .put("choices", new JSONArray().put(choice));
    }

    private static JSONObject chatToolDelta(int index, String id, String name, String arguments)
            throws Exception {
        JSONObject function = new JSONObject().put("arguments", arguments);
        if (!name.isEmpty()) function.put("name", name);
        JSONObject result = new JSONObject().put("index", index).put("function", function);
        if (!id.isEmpty()) result.put("id", id);
        return result;
    }

    private static String sseData(JSONObject value) {
        return "data: " + value + "\n\n";
    }

    private static String sseEventNameOnly(JSONObject value) throws Exception {
        JSONObject data = new JSONObject(value.toString());
        String type = data.optString("type", "message");
        data.remove("type");
        return "event: " + type + "\n" + "data: " + data + "\n\n";
    }


    private static StreamCapture convertChatStream(String source, String contentType,
                                                    java.util.Set<String> customTools) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChatCompletionsAdapter.StreamStats stats = ChatCompletionsAdapter.streamChatResponseToResponses(
            new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), contentType, "demo",
            customTools, output::write);
        return new StreamCapture(output.toByteArray(), stats);
    }

    private static final class StreamCapture {
        private final byte[] bytes;
        final ChatCompletionsAdapter.StreamStats stats;
        StreamCapture(byte[] bytes, ChatCompletionsAdapter.StreamStats stats) {
            this.bytes = bytes;
            this.stats = stats;
        }
        byte[] bytes() { return bytes; }
        String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }

    private static JSONObject findAssistantToolMessage(JSONArray messages) throws Exception {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            JSONArray calls = message.optJSONArray("tool_calls");
            if ("assistant".equals(message.optString("role")) && calls != null && calls.length() > 0) return message;
        }
        fail("Assistant tool-call message missing");
        return null;
    }

    private static JSONObject completedResponse(byte[] body) throws Exception {
        String[] lines = new String(body, StandardCharsets.UTF_8).split("\r?\n");
        for (String line : lines) {
            if (!line.startsWith("data: ")) continue;
            JSONObject event = new JSONObject(line.substring(6));
            if ("response.completed".equals(event.optString("type"))) return event.getJSONObject("response");
        }
        fail("response.completed event missing");
        return null;
    }

    private static JSONObject completedResponse(ChatCompletionsAdapter.ChatResult result) throws Exception {
        return completedResponse(result.body);
    }


    @Test
    public void historicalSubagentActivityKeepsThreadIdentityAndWorkingState() throws Exception {
        String threadId = "019f6f24-83f6-70f2-80b6-0af81033832a";
        JSONObject card = CodexAppServerBridge.historySubagentActivityCard(new JSONObject()
            .put("type", "sub_agent_activity")
            .put("event_id", "call_52ab2f42")
            .put("agent_thread_id", threadId)
            .put("agent_path", "/root/list_files")
            .put("kind", "started"));

        assertEquals("collabAgentToolCall", card.getString("type"));
        assertEquals("subAgentActivity", card.getString("tool"));
        assertEquals(threadId, card.getString("agentThreadId"));
        assertEquals("list_files", card.getString("agentName"));
        assertEquals("working", card.getString("status"));
    }

    @Test
    public void spawnedAgentHistoryCardKeepsTaskAndCallIdentity() throws Exception {
        JSONObject card = CodexAppServerBridge.historyToolCard(
            new JSONObject().put("id", "call_spawn_1").put("name", "spawn_agent")
                .put("arguments", new JSONObject().put("task_name", "inspector")
                    .put("message", "Inspect the project")),
            new JSONObject().put("task_name", "/root/inspector").put("nickname", "Newton").toString());

        assertEquals("call_spawn_1", card.getString("id"));
        assertEquals("Inspect the project", card.getString("task"));
        assertEquals("Newton", card.getString("agentName"));
        assertEquals("working", card.getString("status"));
    }

    @Test
    public void subagentRolloutStatusChangesOnlyAfterTaskComplete() throws Exception {
        assertEquals("waiting", CodexAppServerBridge.subagentSessionStatus(null));
        File session = File.createTempFile("subagent-status", ".jsonl");
        try {
            try (FileWriter writer = new FileWriter(session)) {
                writer.write(new JSONObject().put("type", "event_msg")
                    .put("payload", new JSONObject().put("type", "task_started")).toString() + "\n");
            }
            assertEquals("working", CodexAppServerBridge.subagentSessionStatus(session));
            try (FileWriter writer = new FileWriter(session, true)) {
                writer.write(new JSONObject().put("type", "event_msg")
                    .put("payload", new JSONObject().put("type", "task_complete")).toString() + "\n");
            }
            assertEquals("done", CodexAppServerBridge.subagentSessionStatus(session));
        } finally {
            assertTrue(session.delete() || !session.exists());
        }
    }

    @Test
    public void historicalReasoningDurationUsesRecordTimestamps() {
        assertEquals(2L, CodexAppServerBridge.historyReasoningDurationSeconds(1_000L, 3_500L, true));
        assertEquals(1L, CodexAppServerBridge.historyReasoningDurationSeconds(0L, 0L, true));
        assertEquals(0L, CodexAppServerBridge.historyReasoningDurationSeconds(1_000L, 3_500L, false));
    }

    @Test
    public void nativeEventsOnlyTargetTheVisibleConversation() throws Exception {
        JSONObject a = new JSONObject().put("threadId", "thread-a");
        JSONObject b = new JSONObject().put("threadId", "thread-b");
        assertTrue(CodexAppServerBridge.isVisibleThreadEvent(a, "thread-a"));
        assertFalse(CodexAppServerBridge.isVisibleThreadEvent(b, "thread-a"));
        assertFalse(CodexAppServerBridge.isVisibleThreadEvent(a, null));
        assertTrue(CodexAppServerBridge.isVisibleThreadEvent(new JSONObject(), "thread-a"));
    }


}
