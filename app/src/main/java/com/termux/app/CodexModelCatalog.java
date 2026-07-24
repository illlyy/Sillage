package com.termux.app;

import android.system.Os;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Builds the local Codex model catalog consumed by the bundled app-server. */
final class CodexModelCatalog {
    private static final String TAG = "IlyopModelCatalog";
    private static final String DEFAULT_INSTRUCTIONS =
        "You are Codex, a coding agent. You and the user share the same workspace and collaborate to achieve the user's goals.";

    private CodexModelCatalog() {}

    static JSONObject build(List<CodexProviderStore.ModelConfig> models) throws Exception {
        JSONArray entries = new JSONArray();
        int priority = 0;
        if (models != null) for (CodexProviderStore.ModelConfig model : models) {
            if (model == null || model.id == null || model.id.trim().isEmpty()) continue;
            entries.put(buildEntry(model, priority++));
        }
        return new JSONObject().put("models", entries);
    }

    static JSONObject buildEntry(CodexProviderStore.ModelConfig model, int priority) throws Exception {
        long contextWindow = model.contextWindow > 0 ? model.contextWindow : 128000L;
        String supported = CodexProviderStore.ModelConfig.normalizeEfforts(model.supportedReasoningEfforts);
        String defaultEffort = model.defaultReasoningEffort;
        if (!CodexProviderStore.ModelConfig.supportsEffort(supported, defaultEffort)) {
            defaultEffort = CodexProviderStore.ModelConfig.supportsEffort(supported, "high")
                ? "high" : supported.split(",")[0];
        }
        JSONArray reasoningLevels = new JSONArray();
        for (String effort : supported.split(",")) {
            reasoningLevels.put(new JSONObject().put("effort", effort).put("description", effortDescription(effort)));
        }
        JSONArray modalities = new JSONArray().put("text");
        if (model.imageInput) modalities.put("image");
        String displayName = displayName(model);
        String description = model.description == null || model.description.trim().isEmpty()
            ? displayName : model.description.trim();
        String instructions = model.baseInstructions == null || model.baseInstructions.trim().isEmpty()
            ? DEFAULT_INSTRUCTIONS : model.baseInstructions.trim();

        JSONObject entry = new JSONObject()
            .put("slug", model.id.trim())
            .put("display_name", displayName)
            .put("description", description)
            .put("base_instructions", instructions)
            .put("include_skills_usage_instructions", model.includeSkillsInstructions)
            .put("default_reasoning_level", defaultEffort)
            .put("supported_reasoning_levels", reasoningLevels)
            .put("shell_type", model.shellType)
            .put("tool_mode", emptyAsNull(model.toolMode))
            .put("multi_agent_version", model.multiAgentVersion.isEmpty() ? "disabled" : model.multiAgentVersion)
            .put("visibility", "list")
            .put("supported_in_api", true)
            .put("priority", priority)
            .put("supports_reasoning_summaries", model.reasoningSummaries)
            .put("supports_reasoning_summary_parameter", model.reasoningSummaries)
            .put("default_reasoning_summary", model.defaultReasoningSummary)
            .put("support_verbosity", model.verbosity)
            .put("default_verbosity", model.verbosity ? model.defaultVerbosity : JSONObject.NULL)
            .put("apply_patch_tool_type", model.applyPatchTool ? "freeform" : JSONObject.NULL)
            .put("web_search_tool_type", model.webSearch && model.imageInput ? "text_and_image" : "text")
            .put("truncation_policy", new JSONObject().put("mode", "bytes").put("limit", 10000))
            .put("supports_parallel_tool_calls", model.parallelToolCalls)
            .put("supports_image_detail_original", model.imageDetailOriginal)
            .put("context_window", contextWindow)
            .put("max_context_window", contextWindow)
            .put("effective_context_window_percent", model.effectiveContextWindowPercent)
            .put("experimental_supported_tools", new JSONArray())
            .put("input_modalities", modalities)
            .put("supports_search_tool", model.webSearch)
            .put("use_responses_lite", model.responsesLite)
            .put("additional_speed_tiers", new JSONArray())
            .put("service_tiers", new JSONArray());
        if (model.autoCompactTokenLimit > 0L) entry.put("auto_compact_token_limit", model.autoCompactTokenLimit);
        return entry;
    }

    /**
     * Serializes catalog replacement across chat/home entry points and avoids an fsync when the
     * generated catalog is byte-for-byte identical to the file already consumed by Codex.
     */
    static synchronized File writeAtomic(File catalogFile, List<CodexProviderStore.ModelConfig> models) {
        File temporary = new File(catalogFile.getParentFile(), catalogFile.getName() + ".tmp");
        try {
            JSONObject root = build(models);
            if (root.getJSONArray("models").length() == 0) {
                if (catalogFile.isFile() && !catalogFile.delete()) Log.w(TAG, "Unable to remove empty model catalog");
                return null;
            }
            byte[] content = root.toString(2).getBytes(StandardCharsets.UTF_8);
            File parent = catalogFile.getParentFile();
            if (parent != null) parent.mkdirs();
            if (hasSameContent(catalogFile, content)) return catalogFile;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(content);
                output.getFD().sync();
            }
            Os.rename(temporary.getAbsolutePath(), catalogFile.getAbsolutePath());
            return catalogFile;
        } catch (Exception error) {
            Log.e(TAG, "Failed to write model catalog", error);
            if (temporary.isFile() && !temporary.delete()) Log.w(TAG, "Unable to remove temporary model catalog");
            return null;
        }
    }

    static boolean hasSameContent(File file, byte[] expected) throws Exception {
        if (!file.isFile() || file.length() != expected.length) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[Math.min(8192, Math.max(1, expected.length))];
            int offset = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (offset + count > expected.length) return false;
                for (int index = 0; index < count; index++) {
                    if (buffer[index] != expected[offset + index]) return false;
                }
                offset += count;
            }
            return offset == expected.length;
        }
    }

    private static Object emptyAsNull(String value) {
        return value == null || value.trim().isEmpty() ? JSONObject.NULL : value.trim();
    }

    private static String displayName(CodexProviderStore.ModelConfig model) {
        return model.name == null || model.name.trim().isEmpty() ? model.id.trim() : model.name.trim();
    }

    private static String effortDescription(String effort) {
        if ("none".equals(effort)) return "不思考";
        if ("minimal".equals(effort)) return "最少思考";
        if ("low".equals(effort)) return "低";
        if ("medium".equals(effort)) return "中";
        if ("high".equals(effort)) return "高";
        if ("xhigh".equals(effort)) return "超高";
        if ("max".equals(effort)) return "最高思考强度";
        if ("ultra".equals(effort)) return "Ultra 多代理模式";
        return effort;
    }
}
