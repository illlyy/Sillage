package com.termux.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent named Codex API configurations. Secrets stay in private app SharedPreferences. */
final class CodexProviderStore {
    static final String KEY_PROFILES = "api_profiles_v1";
    static final String KEY_ACTIVE = "active_api_profile_id";

    /** A model entry compatible with the useful subset of cc-switch's Codex model catalog. */
    static final class ModelConfig {
        static final String[] VALID_REASONING_EFFORTS = {
            "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra"
        };
        private static final String GENERIC_REASONING_EFFORTS = "none,minimal,low,medium,high,xhigh";
        private static final String LEGACY_REASONING_EFFORTS = "none,low,medium,high,xhigh";

        String name;
        String id;
        String description;
        String baseInstructions;
        long contextWindow;
        long autoCompactTokenLimit;
        int effectiveContextWindowPercent;
        String defaultReasoningEffort;
        String supportedReasoningEfforts;
        String defaultReasoningSummary;
        String defaultVerbosity;
        String shellType;
        String multiAgentVersion;
        String toolMode;
        /** Upstream effort used when Codex runs locally in Ultra mode; empty means highest compatible effort. */
        String ultraTransportEffort;
        boolean imageInput;
        boolean imageDetailOriginal;
        boolean reasoningSummaries;
        boolean parallelToolCalls;
        boolean verbosity;
        boolean webSearch;
        boolean includeSkillsInstructions;
        boolean responsesLite;
        boolean applyPatchTool;

        ModelConfig(String name, String id, long contextWindow) {
            this(name, id, "", "", contextWindow, 0L, 95, "high", GENERIC_REASONING_EFFORTS,
                "none", "medium", "shell_command", true, false, true, false, false, false,
                true, false, true, "", "");
            applyKnownTemplateToGenericValues();
        }

        ModelConfig(String name, String id, long contextWindow, boolean imageInput,
                    boolean parallelToolCalls, boolean reasoningSummaries) {
            this(name, id, "", "", contextWindow, 0L, 95, "high", GENERIC_REASONING_EFFORTS,
                "none", "medium", "shell_command", imageInput, false, reasoningSummaries,
                parallelToolCalls, false, false, true, false, true, "", "");
            applyKnownTemplateToGenericValues();
        }

        ModelConfig(String name, String id, long contextWindow, boolean imageInput,
                    boolean imageDetailOriginal, boolean reasoningSummaries,
                    boolean parallelToolCalls, boolean verbosity, boolean webSearch) {
            this(name, id, "", "", contextWindow, 0L, 95, "high", GENERIC_REASONING_EFFORTS,
                "none", "medium", "shell_command", imageInput, imageDetailOriginal,
                reasoningSummaries, parallelToolCalls, verbosity, webSearch, true, false, true,
                "", "");
            applyKnownTemplateToGenericValues();
        }

        ModelConfig(String name, String id, String description, String baseInstructions,
                    long contextWindow, long autoCompactTokenLimit, int effectiveContextWindowPercent,
                    String defaultReasoningEffort, String supportedReasoningEfforts,
                    String defaultReasoningSummary, String defaultVerbosity, String shellType,
                    boolean imageInput, boolean imageDetailOriginal, boolean reasoningSummaries,
                    boolean parallelToolCalls, boolean verbosity, boolean webSearch,
                    boolean includeSkillsInstructions, boolean responsesLite, boolean applyPatchTool,
                    String multiAgentVersion, String toolMode) {
            this(name, id, description, baseInstructions, contextWindow, autoCompactTokenLimit,
                effectiveContextWindowPercent, defaultReasoningEffort, supportedReasoningEfforts,
                defaultReasoningSummary, defaultVerbosity, shellType, imageInput, imageDetailOriginal,
                reasoningSummaries, parallelToolCalls, verbosity, webSearch, includeSkillsInstructions,
                responsesLite, applyPatchTool, multiAgentVersion, toolMode, "");
        }

        ModelConfig(String name, String id, String description, String baseInstructions,
                    long contextWindow, long autoCompactTokenLimit, int effectiveContextWindowPercent,
                    String defaultReasoningEffort, String supportedReasoningEfforts,
                    String defaultReasoningSummary, String defaultVerbosity, String shellType,
                    boolean imageInput, boolean imageDetailOriginal, boolean reasoningSummaries,
                    boolean parallelToolCalls, boolean verbosity, boolean webSearch,
                    boolean includeSkillsInstructions, boolean responsesLite, boolean applyPatchTool,
                    String multiAgentVersion, String toolMode, String ultraTransportEffort) {
            this.name = clean(name);
            this.id = clean(id);
            this.description = clean(description);
            this.baseInstructions = clean(baseInstructions);
            this.contextWindow = Math.max(0L, contextWindow);
            this.autoCompactTokenLimit = Math.max(0L, autoCompactTokenLimit);
            this.effectiveContextWindowPercent = Math.max(1, Math.min(100, effectiveContextWindowPercent));
            this.supportedReasoningEfforts = normalizeEfforts(supportedReasoningEfforts);
            this.defaultReasoningEffort = normalizeDefaultEffort(defaultReasoningEffort, this.supportedReasoningEfforts, "high");
            this.defaultReasoningSummary = choice(defaultReasoningSummary, "none");
            this.defaultVerbosity = choice(defaultVerbosity, "medium");
            this.shellType = choice(shellType, "shell_command");
            this.multiAgentVersion = normalizeMultiAgentVersion(multiAgentVersion);
            if (this.multiAgentVersion.isEmpty() && supportsEffort(this.supportedReasoningEfforts, "ultra")) {
                this.multiAgentVersion = "v2";
            }
            if ("v2".equals(this.multiAgentVersion) && !supportsEffort(this.supportedReasoningEfforts, "ultra")) {
                this.supportedReasoningEfforts = normalizeEfforts(this.supportedReasoningEfforts + ",ultra");
            }
            this.toolMode = normalizeToolMode(toolMode);
            this.ultraTransportEffort = normalizeUltraTransportEffort(ultraTransportEffort);
            this.imageInput = imageInput;
            this.imageDetailOriginal = imageInput && imageDetailOriginal;
            this.reasoningSummaries = reasoningSummaries;
            this.parallelToolCalls = parallelToolCalls;
            this.verbosity = verbosity;
            this.webSearch = webSearch;
            this.includeSkillsInstructions = includeSkillsInstructions;
            this.responsesLite = responsesLite;
            this.applyPatchTool = applyPatchTool;
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
        private static String choice(String value, String fallback) {
            String clean = clean(value);
            return clean.isEmpty() ? fallback : clean;
        }
        private static String join(List<String> values) {
            StringBuilder result = new StringBuilder();
            for (String value : values) {
                if (result.length() > 0) result.append(',');
                result.append(value);
            }
            return result.toString();
        }
        static boolean isValidReasoningEffort(String value) {
            String candidate = clean(value).toLowerCase(java.util.Locale.US);
            for (String effort : VALID_REASONING_EFFORTS) if (effort.equals(candidate)) return true;
            return false;
        }
        static String invalidReasoningEffort(String raw) {
            if (clean(raw).isEmpty()) return "";
            for (String item : raw.split(",")) {
                String effort = item.trim().toLowerCase(java.util.Locale.US);
                if (!effort.isEmpty() && !isValidReasoningEffort(effort)) return effort;
            }
            return "";
        }
        static String normalizeEfforts(String raw) {
            String source = choice(raw, GENERIC_REASONING_EFFORTS);
            ArrayList<String> values = new ArrayList<>();
            for (String item : source.split(",")) {
                String effort = item.trim().toLowerCase(java.util.Locale.US);
                if (isValidReasoningEffort(effort) && !values.contains(effort)) values.add(effort);
            }
            return values.isEmpty() ? "high" : join(values);
        }
        static boolean supportsEffort(String supported, String effort) {
            String candidate = clean(effort).toLowerCase(java.util.Locale.US);
            for (String item : normalizeEfforts(supported).split(",")) if (item.equals(candidate)) return true;
            return false;
        }
        private static String normalizeDefaultEffort(String requested, String supported, String preferredFallback) {
            String candidate = clean(requested).toLowerCase(java.util.Locale.US);
            if (isValidReasoningEffort(candidate) && supportsEffort(supported, candidate)) return candidate;
            String preferred = clean(preferredFallback).toLowerCase(java.util.Locale.US);
            if (isValidReasoningEffort(preferred) && supportsEffort(supported, preferred)) return preferred;
            if (supportsEffort(supported, "high")) return "high";
            return normalizeEfforts(supported).split(",")[0];
        }
        private static String normalizeMultiAgentVersion(String value) {
            String clean = clean(value).toLowerCase(java.util.Locale.US);
            return "v1".equals(clean) || "v2".equals(clean) ? clean : "";
        }
        private static String normalizeToolMode(String value) {
            String clean = clean(value).toLowerCase(java.util.Locale.US);
            return "code_mode_only".equals(clean) ? clean : "";
        }
        static String normalizeUltraTransportEffort(String value) {
            String clean = clean(value).toLowerCase(java.util.Locale.US);
            return isValidReasoningEffort(clean) && !"ultra".equals(clean) ? clean : "";
        }

        /**
         * Ultra is a local Codex runtime mode. The HTTP API receives the model's highest
         * configured non-Ultra effort (Sol/GPT templates resolve to max; GLM commonly to xhigh).
         */
        String resolvedUltraTransportEffort() {
            if (!ultraTransportEffort.isEmpty()) return ultraTransportEffort;
            for (int i = VALID_REASONING_EFFORTS.length - 2; i >= 0; i--) {
                String candidate = VALID_REASONING_EFFORTS[i];
                if (supportsEffort(supportedReasoningEfforts, candidate)) return candidate;
            }
            return "max";
        }

        private void applyKnownTemplateToGenericValues() {
            ModelConfig template = knownTemplate(id);
            if (template == null) return;
            if (name.isEmpty() || name.equals(id)) name = template.name;
            if (description.isEmpty()) description = template.description;
            if (contextWindow == 0L) contextWindow = template.contextWindow;
            if (LEGACY_REASONING_EFFORTS.equals(supportedReasoningEfforts)
                    || GENERIC_REASONING_EFFORTS.equals(supportedReasoningEfforts)) {
                supportedReasoningEfforts = template.supportedReasoningEfforts;
                defaultReasoningEffort = template.defaultReasoningEffort;
            }
            if (multiAgentVersion.isEmpty()) multiAgentVersion = template.multiAgentVersion;
            if (toolMode.isEmpty()) toolMode = template.toolMode;
            if (ultraTransportEffort.isEmpty()) ultraTransportEffort = template.ultraTransportEffort;
            imageInput = template.imageInput;
            imageDetailOriginal = template.imageDetailOriginal;
            reasoningSummaries = template.reasoningSummaries;
            parallelToolCalls = template.parallelToolCalls;
            verbosity = template.verbosity;
            webSearch = template.webSearch;
            includeSkillsInstructions = template.includeSkillsInstructions;
            responsesLite = template.responsesLite;
            applyPatchTool = template.applyPatchTool;
            defaultVerbosity = template.defaultVerbosity;
        }

        static ModelConfig knownTemplate(String modelId) {
            if (!"gpt-5.6-sol".equalsIgnoreCase(clean(modelId))) return null;
            return new ModelConfig("GPT-5.6-Sol", "gpt-5.6-sol", "Latest frontier agentic coding model.", "",
                372000L, 0L, 95, "low", "low,medium,high,xhigh,max,ultra",
                "none", "low", "shell_command", true, true, true, true, true, true,
                false, true, true, "v2", "code_mode_only", "max");
        }

        ModelConfig copy() {
            return new ModelConfig(name, id, description, baseInstructions, contextWindow,
                autoCompactTokenLimit, effectiveContextWindowPercent, defaultReasoningEffort,
                supportedReasoningEfforts, defaultReasoningSummary, defaultVerbosity, shellType,
                imageInput, imageDetailOriginal, reasoningSummaries, parallelToolCalls, verbosity,
                webSearch, includeSkillsInstructions, responsesLite, applyPatchTool,
                multiAgentVersion, toolMode, ultraTransportEffort);
        }

        JSONObject json() throws Exception {
            JSONArray modalities = new JSONArray().put("text");
            if (imageInput) modalities.put("image");
            JSONObject value = new JSONObject().put("name", name).put("id", id)
                .put("description", description).put("baseInstructions", baseInstructions);
            if (contextWindow > 0L) value.put("contextWindow", contextWindow);
            if (autoCompactTokenLimit > 0L) value.put("autoCompactTokenLimit", autoCompactTokenLimit);
            return value.put("effectiveContextWindowPercent", effectiveContextWindowPercent)
                .put("defaultReasoningEffort", defaultReasoningEffort)
                .put("supportedReasoningEfforts", supportedReasoningEfforts)
                .put("defaultReasoningSummary", defaultReasoningSummary)
                .put("defaultVerbosity", defaultVerbosity)
                .put("shellType", shellType)
                .put("multiAgentVersion", multiAgentVersion)
                .put("toolMode", toolMode)
                .put("ultraTransportEffort", ultraTransportEffort)
                .put("imageInput", imageInput)
                .put("inputModalities", modalities)
                .put("supportsImageDetailOriginal", imageDetailOriginal)
                .put("supportsReasoningSummaries", reasoningSummaries)
                .put("supportsParallelToolCalls", parallelToolCalls)
                .put("supportsVerbosity", verbosity)
                .put("supportsWebSearch", webSearch)
                .put("includeSkillsUsageInstructions", includeSkillsInstructions)
                .put("useResponsesLite", responsesLite)
                .put("applyPatchTool", applyPatchTool);
        }

        private static boolean hasEither(JSONObject value, String camel, String snake) {
            return value.has(camel) || value.has(snake);
        }
        private static String stringEither(JSONObject value, String camel, String snake, String fallback) {
            if (value.has(camel)) return value.isNull(camel) ? "" : value.optString(camel, fallback);
            if (value.has(snake)) return value.isNull(snake) ? "" : value.optString(snake, fallback);
            return fallback;
        }
        private static boolean booleanEither(JSONObject value, String camel, String snake, boolean fallback) {
            if (value.has(camel)) return value.optBoolean(camel, fallback);
            if (value.has(snake)) return value.optBoolean(snake, fallback);
            return fallback;
        }
        private static long longEither(JSONObject value, String camel, String snake, long fallback) {
            if (value.has(camel)) return value.optLong(camel, fallback);
            if (value.has(snake)) return value.optLong(snake, fallback);
            return fallback;
        }
        private static int intEither(JSONObject value, String camel, String snake, int fallback) {
            if (value.has(camel)) return value.optInt(camel, fallback);
            if (value.has(snake)) return value.optInt(snake, fallback);
            return fallback;
        }

        static ModelConfig from(JSONObject value) {
            return from(value, false);
        }

        static ModelConfig fromPersisted(JSONObject value) {
            return from(value, true);
        }

        private static ModelConfig from(JSONObject value, boolean persisted) {
            String id = value.optString("id", value.optString("model", value.optString("slug", ""))).trim();
            ModelConfig template = knownTemplate(id);
            ModelConfig generic = new ModelConfig("", "", "", "", 0L, 0L, 95, "high",
                GENERIC_REASONING_EFFORTS, "none", "medium", "shell_command", true, false,
                true, false, false, false, true, false, true, "", "");
            ModelConfig defaults = template == null ? generic : template;
            String name = value.optString("name", value.optString("displayName",
                value.optString("display_name", defaults.name.isEmpty() ? id : defaults.name))).trim();
            String description = value.has("description") ? value.optString("description") : defaults.description;
            String baseInstructions = stringEither(value, "baseInstructions", "base_instructions", defaults.baseInstructions);
            long context = longEither(value, "contextWindow", "context_window", defaults.contextWindow);
            long compact = longEither(value, "autoCompactTokenLimit", "auto_compact_token_limit", defaults.autoCompactTokenLimit);
            int effectivePercent = intEither(value, "effectiveContextWindowPercent",
                "effective_context_window_percent", defaults.effectiveContextWindowPercent);
            String defaultEffort = stringEither(value, "defaultReasoningEffort",
                "default_reasoning_level", defaults.defaultReasoningEffort);
            String efforts = value.optString("supportedReasoningEfforts", "");
            if (efforts.isEmpty()) {
                JSONArray levels = value.optJSONArray("supported_reasoning_levels");
                ArrayList<String> parsed = new ArrayList<>();
                if (levels != null) for (int i = 0; i < levels.length(); i++) {
                    JSONObject item = levels.optJSONObject(i);
                    if (item != null && !item.optString("effort").isEmpty()) parsed.add(item.optString("effort"));
                }
                efforts = parsed.isEmpty() ? defaults.supportedReasoningEfforts : join(parsed);
            }
            String defaultSummary = stringEither(value, "defaultReasoningSummary",
                "default_reasoning_summary", defaults.defaultReasoningSummary);
            String defaultVerbosity = stringEither(value, "defaultVerbosity",
                "default_verbosity", defaults.defaultVerbosity);
            String shellType = stringEither(value, "shellType", "shell_type", defaults.shellType);
            String multiAgent = stringEither(value, "multiAgentVersion", "multi_agent_version", defaults.multiAgentVersion);
            String toolMode = stringEither(value, "toolMode", "tool_mode", defaults.toolMode);
            String ultraTransport = stringEither(value, "ultraTransportEffort", "ultra_transport_effort",
                defaults.ultraTransportEffort);
            // Older versions of the one-tap custom Ultra preset copied Sol's code_mode_only
            // setting onto every provider. Third-party models such as GLM then received no
            // ordinary collaboration/shell tool surface. Migrate only records written before
            // ultraTransportEffort existed; newly saved explicit choices remain authoritative.
            boolean legacyCustomUltraPreset = persisted && template == null
                && !hasEither(value, "ultraTransportEffort", "ultra_transport_effort")
                && "v2".equalsIgnoreCase(multiAgent)
                && supportsEffort(efforts, "ultra")
                && "code_mode_only".equalsIgnoreCase(toolMode);
            if (legacyCustomUltraPreset) toolMode = "";
            boolean imageInput = booleanEither(value, "imageInput", "supports_image_input", defaults.imageInput);
            JSONArray modalities = value.optJSONArray("inputModalities");
            if (modalities == null) modalities = value.optJSONArray("input_modalities");
            if (modalities != null) {
                imageInput = false;
                for (int i = 0; i < modalities.length(); i++) if ("image".equalsIgnoreCase(modalities.optString(i))) imageInput = true;
            }
            boolean imageDetail = booleanEither(value, "supportsImageDetailOriginal",
                "supports_image_detail_original", defaults.imageDetailOriginal);
            boolean reasoning = booleanEither(value, "supportsReasoningSummaries",
                "supports_reasoning_summary_parameter", defaults.reasoningSummaries);
            boolean parallel = booleanEither(value, "supportsParallelToolCalls",
                "supports_parallel_tool_calls", defaults.parallelToolCalls);
            boolean verbosity = booleanEither(value, "supportsVerbosity", "support_verbosity", defaults.verbosity);
            boolean webSearch = booleanEither(value, "supportsWebSearch", "supports_search_tool", defaults.webSearch);
            boolean skills = booleanEither(value, "includeSkillsUsageInstructions",
                "include_skills_usage_instructions", defaults.includeSkillsInstructions);
            boolean lite = booleanEither(value, "useResponsesLite", "use_responses_lite", defaults.responsesLite);
            boolean patch = value.has("applyPatchTool") ? value.optBoolean("applyPatchTool", defaults.applyPatchTool)
                : value.has("apply_patch_tool_type") ? !value.isNull("apply_patch_tool_type") : defaults.applyPatchTool;

            boolean legacySol = persisted && template != null
                && !hasEither(value, "multiAgentVersion", "multi_agent_version")
                && !hasEither(value, "toolMode", "tool_mode");
            if (legacySol) {
                if (context == 0L) context = template.contextWindow;
                String normalized = normalizeEfforts(efforts);
                if (LEGACY_REASONING_EFFORTS.equals(normalized) || GENERIC_REASONING_EFFORTS.equals(normalized)) {
                    efforts = template.supportedReasoningEfforts;
                    if ("high".equalsIgnoreCase(defaultEffort)) defaultEffort = template.defaultReasoningEffort;
                }
                if (name.isEmpty() || name.equalsIgnoreCase(id)) name = template.name;
                if (description.isEmpty() || description.equalsIgnoreCase(id)) description = template.description;
                if (defaultVerbosity.isEmpty() || "medium".equalsIgnoreCase(defaultVerbosity)) defaultVerbosity = template.defaultVerbosity;
                // These were the exact generic defaults written before per-model runtime metadata existed.
                if (!imageDetail) imageDetail = template.imageDetailOriginal;
                if (!parallel) parallel = template.parallelToolCalls;
                if (!verbosity) verbosity = template.verbosity;
                if (!webSearch) webSearch = template.webSearch;
                if (skills) skills = template.includeSkillsInstructions;
                if (!lite) lite = template.responsesLite;
                if (!patch) patch = template.applyPatchTool;
                multiAgent = template.multiAgentVersion;
                toolMode = template.toolMode;
            }
            return new ModelConfig(name, id, description, baseInstructions, context, compact,
                effectivePercent, defaultEffort, efforts, defaultSummary, defaultVerbosity,
                shellType, imageInput, imageDetail, reasoning, parallel, verbosity, webSearch,
                skills, lite, patch, multiAgent, toolMode, ultraTransport);
        }
    }

    static final class Profile {
        static final int DEFAULT_ULTRA_SUBAGENT_LIMIT = 3;
        static final int DEFAULT_NORMAL_SUBAGENT_LIMIT = 6;
        static final int MAX_SUBAGENT_LIMIT = 64;

        String id, name, note, baseUrl, apiKey, model, apiFormat;
        boolean proxyEnabled, proxyWebUi, proxyTermux, forwardReasoningContext;
        boolean customSubagentStability;
        int ultraSubagentLimit, normalSubagentLimit;
        final ArrayList<ModelConfig> models;

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model) {
            this(id, name, note, baseUrl, apiKey, model, "auto", null, false, false, false);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model, String apiFormat) {
            this(id, name, note, baseUrl, apiKey, model, apiFormat, null, false, false, false);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model,
                String apiFormat, List<ModelConfig> models) {
            this(id, name, note, baseUrl, apiKey, model, apiFormat, models, false, false, false);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model,
                String apiFormat, List<ModelConfig> models, boolean proxyEnabled,
                boolean proxyWebUi, boolean proxyTermux) {
            this(id, name, note, baseUrl, apiKey, model, apiFormat, models, proxyEnabled,
                proxyWebUi, proxyTermux, false);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model,
                String apiFormat, List<ModelConfig> models, boolean proxyEnabled,
                boolean proxyWebUi, boolean proxyTermux, boolean forwardReasoningContext) {
            this(id, name, note, baseUrl, apiKey, model, apiFormat, models, proxyEnabled,
                proxyWebUi, proxyTermux, forwardReasoningContext,
                DEFAULT_ULTRA_SUBAGENT_LIMIT, DEFAULT_NORMAL_SUBAGENT_LIMIT);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model,
                String apiFormat, List<ModelConfig> models, boolean proxyEnabled,
                boolean proxyWebUi, boolean proxyTermux, boolean forwardReasoningContext,
                int ultraSubagentLimit, int normalSubagentLimit) {
            this(id, name, note, baseUrl, apiKey, model, apiFormat, models, proxyEnabled,
                proxyWebUi, proxyTermux, forwardReasoningContext, ultraSubagentLimit,
                normalSubagentLimit, true);
        }

        Profile(String id, String name, String note, String baseUrl, String apiKey, String model,
                String apiFormat, List<ModelConfig> models, boolean proxyEnabled,
                boolean proxyWebUi, boolean proxyTermux, boolean forwardReasoningContext,
                int ultraSubagentLimit, int normalSubagentLimit, boolean customSubagentStability) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model == null ? "" : model;
            this.apiFormat = (apiFormat == null || apiFormat.isEmpty()) ? "auto" : apiFormat;
            this.proxyEnabled = proxyEnabled;
            this.proxyWebUi = proxyEnabled && proxyWebUi;
            this.proxyTermux = proxyEnabled && proxyTermux;
            this.forwardReasoningContext = forwardReasoningContext;
            this.customSubagentStability = customSubagentStability;
            this.ultraSubagentLimit = normalizeSubagentLimit(ultraSubagentLimit, DEFAULT_ULTRA_SUBAGENT_LIMIT);
            this.normalSubagentLimit = normalizeSubagentLimit(normalSubagentLimit, DEFAULT_NORMAL_SUBAGENT_LIMIT);
            this.models = new ArrayList<>();
            if (models != null) for (ModelConfig item : models) if (item != null) this.models.add(item.copy());
        }

        JSONObject json() throws Exception {
            JSONArray catalog = new JSONArray();
            for (ModelConfig item : models) catalog.put(item.json());
            return new JSONObject().put("id", id).put("name", name).put("note", note)
                .put("baseUrl", baseUrl).put("apiKey", apiKey).put("model", model)
                .put("apiFormat", apiFormat).put("models", catalog)
                .put("proxyEnabled", proxyEnabled).put("proxyWebUi", proxyWebUi)
                .put("proxyTermux", proxyTermux)
                .put("forwardReasoningContext", forwardReasoningContext)
                .put("customSubagentStability", customSubagentStability)
                .put("ultraSubagentLimit", ultraSubagentLimit)
                .put("normalSubagentLimit", normalSubagentLimit);
        }

        static Profile from(JSONObject value) {
            ArrayList<ModelConfig> models = new ArrayList<>();
            JSONArray catalog = value.optJSONArray("models");
            // Also accept cc-switch-shaped data if a future importer writes it directly.
            if (catalog == null) {
                JSONObject modelCatalog = value.optJSONObject("modelCatalog");
                if (modelCatalog != null) catalog = modelCatalog.optJSONArray("models");
            }
            if (catalog != null) {
                for (int i = 0; i < catalog.length(); i++) {
                    JSONObject item = catalog.optJSONObject(i);
                    if (item == null) continue;
                    ModelConfig parsed = ModelConfig.fromPersisted(item);
                    if (!parsed.id.isEmpty()) models.add(parsed);
                }
            }
            return new Profile(value.optString("id"), value.optString("name"), value.optString("note"),
                value.optString("baseUrl"), value.optString("apiKey"), value.optString("model"),
                value.optString("apiFormat", "auto"), models,
                value.optBoolean("proxyEnabled", false),
                value.optBoolean("proxyWebUi", false),
                value.optBoolean("proxyTermux", false),
                value.optBoolean("forwardReasoningContext", false),
                value.optInt("ultraSubagentLimit", DEFAULT_ULTRA_SUBAGENT_LIMIT),
                value.optInt("normalSubagentLimit", DEFAULT_NORMAL_SUBAGENT_LIMIT),
                value.optBoolean("customSubagentStability", true));
        }

        static int normalizeSubagentLimit(int value, int fallback) {
            if (value < 1 || value > MAX_SUBAGENT_LIMIT) return fallback;
            return value;
        }

        int ultraConcurrentThreadLimit() {
            return ultraSubagentLimit + 1; // Codex V2 counts the root agent as a concurrency slot.
        }

        java.util.Map<String, String> ultraTransportEfforts() {
            java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
            for (ModelConfig item : models) {
                if (item == null || item.id.isEmpty()
                        || !ModelConfig.supportsEffort(item.supportedReasoningEfforts, "ultra")) continue;
                result.put(item.id.toLowerCase(java.util.Locale.US), item.resolvedUltraTransportEffort());
            }
            return result;
        }

        boolean hasV2Models() {
            for (ModelConfig item : models) if (item != null && "v2".equalsIgnoreCase(item.multiAgentVersion)) return true;
            return false;
        }

        boolean hasCustomV2Models() {
            for (ModelConfig item : models) if (item != null && "v2".equalsIgnoreCase(item.multiAgentVersion)
                    && ModelConfig.knownTemplate(item.id) == null) return true;
            return false;
        }

        static void appendAgentConfig(StringBuilder toml, Profile profile) {
            int ultra = profile == null ? DEFAULT_ULTRA_SUBAGENT_LIMIT : profile.ultraSubagentLimit;
            int normal = profile == null ? DEFAULT_NORMAL_SUBAGENT_LIMIT : profile.normalSubagentLimit;
            ultra = normalizeSubagentLimit(ultra, DEFAULT_ULTRA_SUBAGENT_LIMIT);
            normal = normalizeSubagentLimit(normal, DEFAULT_NORMAL_SUBAGENT_LIMIT);
            boolean enableV2 = profile != null && profile.hasV2Models();
            boolean stableCustomV2 = enableV2 && profile.customSubagentStability && profile.hasCustomV2Models();
            if (enableV2) toml.append("suppress_unstable_features_warning = true\n\n");
            toml.append("[features.multi_agent_v2]\n");
            toml.append("enabled = ").append(enableV2).append("\n");
            toml.append("max_concurrent_threads_per_session = ").append(ultra + 1).append("\n");
            if (stableCustomV2) {
                String rootHint = "You are the root agent. For reliability on a custom model, only the root agent may create subagents. "
                    + "Use spawn_agent only for bounded, non-overlapping tasks and create no more than " + ultra + " children. "
                    + "Do not ask children to delegate. Use wait_agent in intervals no longer than 30000 ms; retry a timed-out wait at most twice, "
                    + "then use interrupt_agent on the stuck child, report the timeout, and continue with available results. Never wait indefinitely.";
                String childHint = "You are a child agent. Do not call spawn_agent and do not create descendants. Complete the assigned bounded task directly. "
                    + "Do not wait for other agents. If blocked, an upstream request times out, or a tool fails repeatedly, promptly report the blocker "
                    + "to the parent and finish instead of hanging. All agents share the same working directory, so avoid overlapping edits.";
                toml.append("min_wait_timeout_ms = 5000\n");
                toml.append("default_wait_timeout_ms = 30000\n");
                toml.append("max_wait_timeout_ms = 120000\n");
                toml.append("hide_spawn_agent_metadata = false\n");
                // Codex 0.144.x rejects expose_spawn_agent_model_overrides as an unknown feature field.
                // Its default is already false, so omit it to keep generated config backward-compatible.
                toml.append("root_agent_usage_hint_text = ").append(JSONObject.quote(rootHint)).append("\n");
                toml.append("subagent_usage_hint_text = ").append(JSONObject.quote(childHint)).append("\n");
            }
            toml.append("\n");
            if (!enableV2) {
                toml.append("[agents]\n");
                toml.append("max_threads = ").append(normal).append("\n\n");
            }
        }
    }

    private final SharedPreferences prefs;
    CodexProviderStore(SharedPreferences prefs) { this.prefs = prefs; migrateLegacy(); }

    List<Profile> all() {
        ArrayList<Profile> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_PROFILES, "[]"));
            for (int i = 0; i < array.length(); i++) result.add(Profile.from(array.getJSONObject(i)));
        } catch (Exception ignored) {}
        return result;
    }

    Profile find(String id) { for (Profile p : all()) if (p.id.equals(id)) return p; return null; }

    Profile active() {
        Profile value = find(prefs.getString(KEY_ACTIVE, ""));
        if (value != null) return value;
        List<Profile> all = all();
        return all.isEmpty() ? null : all.get(0);
    }

    boolean isActive(Profile p) { return p != null && p.id.equals(prefs.getString(KEY_ACTIVE, "")); }

    void save(Profile profile) {
        List<Profile> values = all();
        boolean replaced = false;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).id.equals(profile.id)) {
                values.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(profile);
        write(values);
    }

    void activate(Profile profile) {
        save(profile);
        prefs.edit().putString(KEY_ACTIVE, profile.id)
            .putString("base_url", profile.baseUrl).putString("api_key", profile.apiKey)
            .putString("model", profile.model).apply();
    }

    void delete(Profile profile) {
        List<Profile> values = all();
        for (int i = values.size() - 1; i >= 0; i--) if (values.get(i).id.equals(profile.id)) values.remove(i);
        write(values);
        if (isActive(profile)) {
            prefs.edit().remove(KEY_ACTIVE).apply();
            if (!values.isEmpty()) activate(values.get(0));
        }
    }

    String newId() { return UUID.randomUUID().toString(); }

    private void write(List<Profile> values) {
        JSONArray array = new JSONArray();
        try {
            for (Profile p : values) array.put(p.json());
            prefs.edit().putString(KEY_PROFILES, array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void migrateLegacy() {
        if (prefs.contains(KEY_PROFILES)) return;
        String url = prefs.getString("base_url", "");
        String key = prefs.getString("api_key", "");
        String model = prefs.getString("model", "");
        if (url.isEmpty() && key.isEmpty()) {
            prefs.edit().putString(KEY_PROFILES, "[]").apply();
            return;
        }
        Profile profile = new Profile(newId(), "Default", "", url, key, model);
        save(profile);
        activate(profile);
    }
}
