package com.termux.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;

/** Transport used by the unmodified Codex Desktop renderer in Android WebView. */
final class CodexDesktopBridge {
    private static final String TAG = "CodexDesktopIPC";
    private final Activity activity;
    private static final String PREFERENCES_NAME = "codex_desktop_bridge";
    private static final String PERSISTED_ATOM_STATE_KEY = "persisted_atom_state";
    private static final String GLOBAL_STATE_KEY = "global_state";
    private static final String SETTINGS_STATE_KEY = "settings_state";
    private static final String WORKSPACE_ROOTS_KEY = "workspace_roots";
    private static final String ACTIVE_WORKSPACE_ROOTS_KEY = "active_workspace_roots";

    private final WebView webView;
    private final SharedPreferences preferences;
    private final SharedPreferences mobilePreferences;
    private CodexAppServerBridge appServerBridge;
    private final ArrayList<String> pendingAppServerMessages = new ArrayList<>();
    private boolean appServerFlushScheduled;

    CodexDesktopBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.preferences = activity.getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);
        this.mobilePreferences = activity.getSharedPreferences("codex_mobile", Activity.MODE_PRIVATE);
    }

    @JavascriptInterface public String uploadFiles(String json) {
        JSONArray result = new JSONArray();
        try {
            JSONArray input = new JSONArray(json);
            File uploadRoot = new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, "codex-uploads");
            uploadRoot.mkdirs();
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.getJSONObject(i);
                String label = item.optString("name", "upload").replace('/', '_').replace('\\', '_');
                File output = File.createTempFile("attachment-", "-" + label, uploadRoot);
                byte[] bytes = Base64.decode(item.getString("data"), Base64.DEFAULT);
                try (FileOutputStream stream = new FileOutputStream(output)) { stream.write(bytes); }
                String mimeType = item.optString("type", "application/octet-stream");
                result.put(new JSONObject()
                    .put("label", label)
                    .put("path", output.getAbsolutePath())
                    .put("fsPath", output.getAbsolutePath())
                    .put("mimeType", mimeType)
                    .put("type", mimeType));
            }
            return new JSONObject().put("files", result).toString();
        } catch (Exception e) {
            Log.e(TAG, "Native file upload failed", e);
            return "{\"files\":[]}";
        }
    }

    @JavascriptInterface public String readSharedFiles(String json) {
        JSONArray result = new JSONArray();
        try {
            JSONObject payload = new JSONObject(json);
            JSONArray input = payload.optJSONArray("files");
            File shareRoot = new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, "codex-shares").getCanonicalFile();
            long totalBytes = 0L;
            if (input != null) for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.optJSONObject(i);
                if (item == null) continue;
                File file = new File(item.optString("path", "")).getCanonicalFile();
                String prefix = shareRoot.getPath() + File.separator;
                if (!file.isFile() || !file.getPath().startsWith(prefix)) continue;
                totalBytes += file.length();
                if (file.length() > 40L * 1024L * 1024L || totalBytes > 80L * 1024L * 1024L) {
                    throw new IllegalArgumentException("Shared files exceed preview limit");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) file.length());
                try (FileInputStream stream = new FileInputStream(file)) {
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = stream.read(buffer)) >= 0) if (count > 0) bytes.write(buffer, 0, count);
                }
                result.put(new JSONObject()
                    .put("name", item.optString("label", file.getName()))
                    .put("type", item.optString("mimeType", "application/octet-stream"))
                    .put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)));
            }
            return new JSONObject().put("files", result).put("text", payload.optString("text", "")).toString();
        } catch (Exception error) {
            Log.e(TAG, "Could not read staged Android share", error);
            try { return new JSONObject().put("files", result).put("error", error.getMessage()).toString(); }
            catch (Exception ignored) { return "{\"files\":[]}"; }
        }
    }

    void deliverAndroidShare(String payload, boolean newConversation) {
        try {
            if (newConversation) {
                emitToView(new JSONObject().put("type", "navigate-to-route").put("path", "/")
                    .put("state", new JSONObject().put("focusComposerNonce", System.currentTimeMillis())));
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not navigate for Android share", error);
        }
        final String quoted = JSONObject.quote(payload == null ? "{}" : payload);
        webView.postDelayed(() -> webView.evaluateJavascript(
            "window.__codexAndroidShare ? window.__codexAndroidShare(" + quoted + ") : false", null),
            newConversation ? 650L : 180L);
    }

    void setAppServerBridge(CodexAppServerBridge bridge) { this.appServerBridge = bridge; }

    @JavascriptInterface public void postMessage(String json) {
        try {
            JSONObject message = new JSONObject(json);
            String type = message.optString("type");
            String channel = message.optString("channel");
            Log.i(TAG, "FROM_RENDERER type=" + type + " channel=" + channel);
            if ("ipc-renderer-invoke".equals(type)) {
                Object result = handleInvoke(channel, message.optJSONArray("args"));
                JSONObject reply = new JSONObject()
                    .put("type", "ipc-renderer-invoke-result")
                    .put("requestId", message.optString("requestId"))
                    .put("ok", true)
                    .put("result", result == null ? JSONObject.NULL : result);
                send(reply);
            } else if ("workspace-directory-entries-request".equals(type)) {
                handleWorkspaceDirectoryEntries(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid renderer IPC", e);
        }
    }

    private void handleWorkspaceDirectoryEntries(JSONObject message) {
        String requestId = message.optString("requestId");
        try {
            Object directoryValue = message.opt("directoryPath");
            String requested = directoryValue instanceof String ? ((String) directoryValue).trim() : "";
            File home = new File(defaultProjectRoot()).getCanonicalFile();
            File directory = new File(requested.isEmpty() ? home.getAbsolutePath() : requested).getCanonicalFile();
            String homePrefix = home.getAbsolutePath() + File.separator;
            if (!directory.equals(home) && !directory.getAbsolutePath().startsWith(homePrefix)) {
                throw new IllegalArgumentException("Choose a directory inside " + home);
            }
            if (!directory.isDirectory()) throw new IllegalArgumentException("Directory not found: " + directory);

            File[] children = directory.listFiles();
            if (children == null) throw new IllegalStateException("Cannot read directory: " + directory);
            Arrays.sort(children, Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(file -> file.getName().toLowerCase()));

            boolean directoriesOnly = message.optBoolean("directoriesOnly", false);
            JSONArray entries = new JSONArray();
            for (File child : children) {
                if (directoriesOnly && !child.isDirectory()) continue;
                entries.put(new JSONObject()
                    .put("name", child.getName())
                    .put("path", child.getAbsolutePath())
                    .put("type", child.isDirectory() ? "directory" : "file"));
            }
            File parent = directory.equals(home) ? null : directory.getParentFile();
            JSONObject result = new JSONObject()
                .put("directoryPath", directory.getAbsolutePath())
                .put("parentPath", parent == null ? JSONObject.NULL : parent.getAbsolutePath())
                .put("entries", entries);
            send(new JSONObject()
                .put("type", "workspace-directory-entries-result")
                .put("requestId", requestId)
                .put("ok", true)
                .put("result", result));
        } catch (Exception error) {
            try {
                send(new JSONObject()
                    .put("type", "workspace-directory-entries-result")
                    .put("requestId", requestId)
                    .put("ok", false)
                    .put("errorMessage", error.getMessage()));
            } catch (Exception nested) {
                Log.e(TAG, "Failed to return directory listing error", nested);
            }
        }
    }

    private String defaultProjectRoot() {
        if (mobilePreferences.getBoolean("custom_project_root_enabled", true)) {
            String configured = mobilePreferences.getString("custom_project_root", "/storage/emulated/0/");
            File directory = new File(configured == null ? "/storage/emulated/0/" : configured);
            if (directory.isDirectory() || directory.mkdirs()) return directory.getAbsolutePath();
        }
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    private Object handleInvoke(String channel, JSONArray args) throws Exception {
        // The browser shim already supplies app info, theme and other synchronous Electron APIs.
        // Unknown calls deliberately resolve to null for now so the real renderer can finish booting;
        // every request is logged and will be mapped to Codex app-server incrementally.
        if ("codex_desktop:message-from-view".equals(channel) && args != null && args.length() > 0) {
            JSONObject payload = args.optJSONObject(0);
            if (payload == null) Log.i(TAG, "MESSAGE_FROM_VIEW type=unknown");
            else {
                JSONObject request = payload.optJSONObject("request");
                Log.i(TAG, "MESSAGE_FROM_VIEW type=" + payload.optString("type", "unknown")
                    + (request == null ? "" : " method=" + request.optString("method", "unknown")));
            }
            if (payload != null) {
                String type = payload.optString("type");
                if ("persisted-atom-sync-request".equals(type)) {
                    emitPersistedAtomState();
                } else if ("persisted-atom-update".equals(type)) {
                    updatePersistedAtomState(payload);
                } else if ("electron-add-new-workspace-root-option".equals(type)) {
                    addWorkspaceRoot(payload.optString("root", ""));
                } else if ("electron-set-active-workspace-root".equals(type)) {
                    setActiveWorkspaceRoot(payload.optString("root", ""));
                } else if ("electron-clear-active-workspace-root".equals(type)) {
                    saveStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, new JSONArray());
                    emitToView(new JSONObject().put("type", "active-workspace-roots-updated"));
                } else if ("electron-update-workspace-root-options".equals(type)) {
                    updateWorkspaceRootOptions(payload);
                } else if ("electron-remove-workspace-root-option".equals(type)) {
                    removeWorkspaceRoot(payload.optString("root", payload.optString("path", "")));
                } else if ("workspace-file-reveal-path".equals(type) || "reveal-canonical".equals(type) || "electron-open-path".equals(type)) {
                    String path = payload.optString("path", payload.optString("cwd", defaultProjectRoot()));
                    activity.runOnUiThread(() -> { if (activity instanceof CodexHomeActivity) ((CodexHomeActivity) activity).openNativeFileManager(path); });
                } else if ("mcp-request".equals(type) && payload.optJSONObject("request") != null) {
                    if (appServerBridge != null) appServerBridge.sendDesktopRequest(payload.getJSONObject("request"));
                } else if ("fetch".equals(type)) {
                    emitToView(new JSONObject()
                        .put("type", "fetch-response")
                        .put("responseType", "success")
                        .put("requestId", payload.optString("requestId"))
                        .put("status", 200)
                        .put("headers", new JSONObject().put("content-type", "application/json"))
                        .put("bodyJsonString", fetchBody(payload.optString("url"), payload.optString("body"))));
                }
            }
        }
        return JSONObject.NULL;
    }


    private String canonicalWorkspacePath(String root) throws Exception {
        if (root == null || root.trim().isEmpty()) return "";
        return new File(root.trim()).getCanonicalFile().getAbsolutePath();
    }

    private String normalizeWorkspaceRoot(String root) throws Exception {
        String canonical = canonicalWorkspacePath(root);
        return !canonical.isEmpty() && new File(canonical).isDirectory() ? canonical : "";
    }

    private JSONArray normalizeWorkspaceRoots(JSONArray values) throws Exception {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                Object value = values.opt(i);
                String root = value instanceof JSONObject
                    ? ((JSONObject) value).optString("root", ((JSONObject) value).optString("path", ""))
                    : values.optString(i, "");
                String canonical = normalizeWorkspaceRoot(root);
                if (!canonical.isEmpty()) normalized.add(canonical);
            }
        }
        JSONArray result = new JSONArray();
        for (String root : normalized) result.put(root);
        return result;
    }

    private JSONArray readStringArray(String key, String defaultValue) {
        String stored = preferences.getString(key, null);
        if (stored == null || stored.isEmpty()) {
            try {
                String canonical = normalizeWorkspaceRoot(defaultValue);
                return canonical.isEmpty() ? new JSONArray() : new JSONArray().put(canonical);
            } catch (Exception error) {
                Log.w(TAG, "Could not normalize default workspace root", error);
                return new JSONArray();
            }
        }
        try {
            return normalizeWorkspaceRoots(new JSONArray(stored));
        } catch (Exception error) {
            Log.w(TAG, "Discarding invalid array preference: " + key, error);
            return new JSONArray();
        }
    }

    private void saveStringArray(String key, JSONArray values) {
        try {
            preferences.edit().putString(key, normalizeWorkspaceRoots(values).toString()).apply();
        } catch (Exception error) {
            Log.w(TAG, "Could not normalize workspace preference: " + key, error);
            preferences.edit().putString(key, values == null ? "[]" : values.toString()).apply();
        }
    }

    private void addWorkspaceRoot(String root) throws Exception {
        String directory = normalizeWorkspaceRoot(root);
        if (directory.isEmpty()) throw new IllegalArgumentException("Directory not found: " + root);
        JSONArray existing = readStringArray(WORKSPACE_ROOTS_KEY, defaultProjectRoot());
        JSONArray updated = new JSONArray().put(directory);
        for (int i = 0; i < existing.length(); i++) {
            String value = existing.optString(i, "");
            if (!directory.equals(value)) updated.put(value);
        }
        saveStringArray(WORKSPACE_ROOTS_KEY, updated);
        setActiveWorkspaceRoot(directory);
        emitToView(new JSONObject().put("type", "workspace-root-options-updated"));
        emitToView(new JSONObject().put("type", "workspace-root-option-added").put("root", directory));
        emitToView(new JSONObject().put("type", "navigate-to-route").put("path", "/")
            .put("state", new JSONObject().put("focusComposerNonce", System.currentTimeMillis())));
    }

    private void setActiveWorkspaceRoot(String root) throws Exception {
        String directory = normalizeWorkspaceRoot(root);
        if (directory.isEmpty()) return;
        saveStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, new JSONArray().put(directory));
        emitToView(new JSONObject().put("type", "active-workspace-roots-updated"));
    }

    private void updateWorkspaceRootOptions(JSONObject payload) throws Exception {
        JSONArray incoming = payload.optJSONArray("roots");
        if (incoming == null) incoming = payload.optJSONArray("workspaceRoots");
        if (incoming == null) incoming = payload.optJSONArray("options");
        if (incoming == null) return;
        JSONArray roots = normalizeWorkspaceRoots(incoming);
        saveStringArray(WORKSPACE_ROOTS_KEY, roots);
        JSONArray active = readStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, defaultProjectRoot());
        String activeRoot = active.length() == 0 ? "" : active.optString(0, "");
        if (!activeRoot.isEmpty() && !contains(roots, activeRoot)) {
            String fallback = roots.length() == 0 ? normalizeWorkspaceRoot(defaultProjectRoot()) : roots.optString(0, "");
            saveStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, fallback.isEmpty() ? new JSONArray() : new JSONArray().put(fallback));
        }
        emitToView(new JSONObject().put("type", "workspace-root-options-updated"));
        emitToView(new JSONObject().put("type", "active-workspace-roots-updated"));
    }

    private void removeWorkspaceRoot(String root) throws Exception {
        String target = canonicalWorkspacePath(root);
        if (target.isEmpty()) return;
        JSONArray old = readStringArray(WORKSPACE_ROOTS_KEY, defaultProjectRoot());
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            String value = old.optString(i, "");
            if (!target.equals(value)) updated.put(value);
        }
        saveStringArray(WORKSPACE_ROOTS_KEY, updated);

        JSONArray active = readStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, defaultProjectRoot());
        String activeRoot = active.length() == 0 ? "" : active.optString(0, "");
        if (target.equals(activeRoot)) {
            String fallback = updated.length() > 0 ? updated.optString(0, "") : normalizeWorkspaceRoot(defaultProjectRoot());
            saveStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, fallback.isEmpty() ? new JSONArray() : new JSONArray().put(fallback));
        }
        emitToView(new JSONObject().put("type", "workspace-root-options-updated"));
        emitToView(new JSONObject().put("type", "active-workspace-roots-updated"));
    }

    private boolean contains(JSONArray values, String target) {
        for (int i = 0; i < values.length(); i++) if (target.equals(values.optString(i))) return true;
        return false;
    }

    private JSONObject getPersistedAtomState() {
        String stored = preferences.getString(PERSISTED_ATOM_STATE_KEY, null);
        if (stored == null || stored.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(stored);
        } catch (Exception error) {
            Log.w(TAG, "Discarding invalid persisted atom state", error);
            preferences.edit().remove(PERSISTED_ATOM_STATE_KEY).apply();
            return new JSONObject();
        }
    }

    private void emitPersistedAtomState() throws Exception {
        emitToView(new JSONObject()
            .put("type", "persisted-atom-sync")
            .put("state", getPersistedAtomState()));
    }

    private void updatePersistedAtomState(JSONObject payload) throws Exception {
        String key = payload.optString("key", "");
        if (key.isEmpty()) {
            Log.w(TAG, "Ignoring persisted atom update without a key");
            return;
        }

        JSONObject state = getPersistedAtomState();
        boolean deleted = payload.optBoolean("deleted", false);
        if (deleted || !payload.has("value")) state.remove(key);
        else state.put(key, payload.isNull("value") ? JSONObject.NULL : payload.get("value"));

        preferences.edit().putString(PERSISTED_ATOM_STATE_KEY, state.toString()).apply();
        emitToView(new JSONObject()
            .put("type", "persisted-atom-updated")
            .put("key", key)
            .put("value", deleted ? JSONObject.NULL : payload.opt("value"))
            .put("deleted", deleted));
    }

    private JSONObject readObjectPreference(String key) {
        String stored = preferences.getString(key, null);
        if (stored == null || stored.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(stored);
        } catch (Exception error) {
            Log.w(TAG, "Discarding invalid JSON preference: " + key, error);
            preferences.edit().remove(key).apply();
            return new JSONObject();
        }
    }

    private JSONObject requestObject(String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(requestBody);
        } catch (Exception error) {
            Log.w(TAG, "Ignoring invalid fetch request body", error);
            return new JSONObject();
        }
    }

    private String fetchBody(String url, String requestBody) throws Exception {
        JSONObject request = requestObject(requestBody);
        if (url.endsWith("/recommended-skills")) {
            return recommendedSkills().toString();
        }
        if (url.endsWith("/install-recommended-skill")) {
            return installRecommendedSkill(request).toString();
        }
        if (url.endsWith("/read-file-binary")) {
            String requestedPath = request.optString("path", "");
            File file = new File(requestedPath).getCanonicalFile();
            if (!file.isFile() || !file.canRead()) throw new IllegalArgumentException("File is not readable: " + requestedPath);
            final long maxBytes = 40L * 1024L * 1024L;
            if (file.length() > maxBytes) throw new IllegalArgumentException("File exceeds 40 MB: " + file.getName());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.max(0L, file.length()));
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) if (count > 0) bytes.write(buffer, 0, count);
            }
            String encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName());
            String mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension == null ? "" : extension.toLowerCase());
            if (mimeType == null) mimeType = "application/octet-stream";
            Log.i(TAG, "Read binary attachment " + file.getName() + " (" + file.length() + " bytes)");
            return new JSONObject().put("contentsBase64", encoded).put("mimeType", mimeType).toString();
        }
        if (url.contains("/wham/tasks/list")) return new JSONObject().put("tasks", new JSONArray()).put("items", new JSONArray()).put("data", new JSONArray()).put("total", 0).put("has_more", false).put("next_cursor", JSONObject.NULL).toString();
        if (url.endsWith("/get-global-state")) {
            JSONObject state = readObjectPreference(GLOBAL_STATE_KEY);
            String key = request.optString("key", "");
            return new JSONObject().put("value", key.isEmpty() ? JSONObject.NULL : state.opt(key)).toString();
        }
        if (url.endsWith("/set-global-state")) {
            JSONObject state = readObjectPreference(GLOBAL_STATE_KEY);
            String key = request.optString("key", "");
            if (!key.isEmpty()) {
                if (!request.has("value") || request.isNull("value")) state.remove(key);
                else state.put(key, request.get("value"));
                preferences.edit().putString(GLOBAL_STATE_KEY, state.toString()).apply();
            }
            return new JSONObject().put("success", true).toString();
        }
        if (url.endsWith("/get-setting")) {
            JSONObject settings = readObjectPreference(SETTINGS_STATE_KEY);
            String key = request.optString("key", "");
            return new JSONObject().put("value", key.isEmpty() ? JSONObject.NULL : settings.opt(key)).toString();
        }
        if (url.endsWith("/set-setting")) {
            JSONObject settings = readObjectPreference(SETTINGS_STATE_KEY);
            String key = request.optString("key", "");
            if (!key.isEmpty()) {
                if (!request.has("value") || request.isNull("value")) settings.remove(key);
                else settings.put(key, request.get("value"));
                preferences.edit().putString(SETTINGS_STATE_KEY, settings.toString()).apply();
            }
            return new JSONObject().put("success", true).toString();
        }
        if (url.endsWith("/get-settings")) {
            JSONObject settings = readObjectPreference(SETTINGS_STATE_KEY);
            return new JSONObject().put("values", settings).put("configuredValues", settings).toString();
        }
        if (url.endsWith("/home-directory")) {
            return new JSONObject().put("homeDirectory", TermuxConstants.TERMUX_HOME_DIR_PATH).toString();
        }
        if (url.endsWith("/codex-home")) {
            return new JSONObject().put("codexHome", TermuxConstants.TERMUX_HOME_DIR_PATH + "/.codex")
                .put("worktreesSegment", TermuxConstants.TERMUX_HOME_DIR_PATH + "/.codex/worktrees").toString();
        }
        if (url.endsWith("/os-info")) {
            return new JSONObject().put("platform", "linux").put("osVersion", "Android")
                .put("osRelease", "Android").put("isSystemBackdropSupported", false).put("hasWsl", false)
                .put("isVsCodeRunningInsideWsl", false).put("windowsAccountType", JSONObject.NULL).toString();
        }
        if (url.endsWith("/list-pinned-threads")) return new JSONObject().put("threadIds", new JSONArray()).toString();
        // The add-context menu always queries local custom agents. Returning a generic {} makes
        // the desktop selector produce { roles: undefined }, which crashes when it calls filter().
        if (url.endsWith("/local-custom-agents")) return new JSONObject().put("agents", new JSONArray()).toString();
        if (url.endsWith("/is-copilot-api-available")) return new JSONObject().put("available", false).toString();
        if (url.endsWith("/get-global-state")) return new JSONObject().put("value", JSONObject.NULL).toString();
        if (url.endsWith("/list-automations")) return new JSONObject().put("items", new JSONArray()).toString();
        if (url.endsWith("/inbox-items")) return new JSONObject().put("items", new JSONArray())
            .put("unreadRunCounts", new JSONObject()
                .put("unreadRuns", new JSONArray())
                .put("total", 0)).toString();
        if (url.endsWith("/active-workspace-roots")) return new JSONObject()
            .put("roots", readStringArray(ACTIVE_WORKSPACE_ROOTS_KEY, defaultProjectRoot())).toString();
        if (url.endsWith("/workspace-root-options")) {
            JSONArray roots = readStringArray(WORKSPACE_ROOTS_KEY, defaultProjectRoot());
            JSONObject labels = new JSONObject();
            for (int i = 0; i < roots.length(); i++) {
                String root = roots.optString(i, "");
                if (!root.isEmpty()) labels.put(root, new File(root).getName().isEmpty() ? "Home" : new File(root).getName());
            }
            return new JSONObject().put("roots", roots).put("labels", labels).toString();
        }
        if (url.endsWith("/projectless-workspace-root")) return new JSONObject()
            .put("workspaceRoot", defaultProjectRoot()).toString();
        if (url.endsWith("/projectless-thread-cwd")) return new JSONObject()
            .put("cwd", defaultProjectRoot())
            .put("workspaceRoot", defaultProjectRoot())
            .put("outputDirectory", defaultProjectRoot()).toString();
        if (url.endsWith("/locale-info")) {
            JSONObject settings = readObjectPreference(SETTINGS_STATE_KEY);
            String locale = settings.optString("localeOverride", "zh-CN");
            if (locale.isEmpty() || "auto".equals(locale)) locale = "zh-CN";
            return new JSONObject().put("ideLocale", locale).put("systemLocale", locale).toString();
        }
        if (url.endsWith("/git-origins")) return new JSONObject().put("origins", new JSONArray()).toString();
        if (url.endsWith("/codex-command-keymap-state")) return "null";
        return new JSONObject().toString();
    }

    private JSONObject recommendedSkills() throws Exception {
        String catalog = readAssetText("official-skills.json");
        JSONObject parsed = new JSONObject(catalog);
        File repoRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "vendor_imports/skills");
        repoRoot.mkdirs();
        return new JSONObject()
            .put("skills", parsed.optJSONArray("skills") == null ? new JSONArray() : parsed.getJSONArray("skills"))
            .put("repoRoot", repoRoot.getAbsolutePath())
            .put("source", "bundled")
            .put("error", JSONObject.NULL);
    }

    private JSONObject installRecommendedSkill(JSONObject request) {
        String skillId = request.optString("skillId", "").trim();
        try {
            if (!skillId.matches("[a-z0-9][a-z0-9._-]{0,80}")) {
                throw new IllegalArgumentException("Invalid skill id");
            }
            File skillsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "skills").getCanonicalFile();
            skillsRoot.mkdirs();
            File destination = new File(skillsRoot, skillId).getCanonicalFile();
            if (!destination.getParentFile().equals(skillsRoot)) throw new IllegalArgumentException("Invalid skill destination");
            File staging = new File(skillsRoot, ".installing-" + skillId + "-" + System.nanoTime()).getCanonicalFile();
            String prefix = "skills-main/skills/.curated/" + skillId + "/";
            boolean extracted = false;
            try (InputStream raw = activity.getAssets().open("official-skills.zip");
                 ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                byte[] buffer = new byte[32 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!name.startsWith(prefix) || name.equals(prefix)) continue;
                    String relative = name.substring(prefix.length());
                    File output = new File(staging, relative).getCanonicalFile();
                    String stagingPrefix = staging.getAbsolutePath() + File.separator;
                    if (!output.getAbsolutePath().startsWith(stagingPrefix)) throw new IllegalArgumentException("Unsafe skill archive entry");
                    if (entry.isDirectory()) {
                        output.mkdirs();
                    } else {
                        File parent = output.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (FileOutputStream stream = new FileOutputStream(output)) {
                            int count;
                            while ((count = zip.read(buffer)) >= 0) if (count > 0) stream.write(buffer, 0, count);
                        }
                        extracted = true;
                    }
                }
            }
            if (!extracted || !new File(staging, "SKILL.md").isFile()) throw new IllegalArgumentException("Skill not found in official catalog");
            deleteTree(destination, skillsRoot);
            if (!staging.renameTo(destination)) {
                copyTree(staging, destination);
                deleteTree(staging, skillsRoot);
            }
            Log.i(TAG, "Installed official skill " + skillId + " to " + destination);
            return new JSONObject().put("success", true).put("destination", destination.getAbsolutePath()).put("error", JSONObject.NULL);
        } catch (Exception error) {
            Log.e(TAG, "Failed to install official skill " + skillId, error);
            try { return new JSONObject().put("success", false).put("destination", JSONObject.NULL).put("error", error.getMessage()); }
            catch (Exception ignored) { return new JSONObject(); }
        }
    }

    private String readAssetText(String name) throws Exception {
        try (InputStream input = activity.getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copyTree(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IllegalStateException("Cannot create " + target);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(target, child.getName()));
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) if (count > 0) output.write(buffer, 0, count);
        }
    }

    private static void deleteTree(File target, File allowedRoot) throws Exception {
        if (target == null || !target.exists()) return;
        File canonical = target.getCanonicalFile();
        File root = allowedRoot.getCanonicalFile();
        if (canonical.equals(root) || !canonical.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator)) {
            throw new IllegalArgumentException("Refusing to delete outside skills directory");
        }
        File[] children = canonical.listFiles();
        if (children != null) for (File child : children) deleteTree(child, root);
        if (!canonical.delete() && canonical.exists()) throw new IllegalStateException("Cannot replace " + canonical.getName());
    }

    void onAppServerInitialized() {
        try {
            emitToView(new JSONObject().put("type", "codex-app-server-connection-changed")
                .put("hostId", "local").put("state", "connected"));
            emitToView(new JSONObject().put("type", "codex-app-server-initialized")
                .put("hostId", "local").put("appServerVersion", "0.144.4")
                .put("installedCodexVersion", "0.144.4"));
        } catch (Exception error) {
            Log.e(TAG, "Failed to announce app-server initialization", error);
        }
        if (activity instanceof CodexHomeActivity) {
            activity.runOnUiThread(((CodexHomeActivity) activity)::onCodexAppServerReady);
        }
    }

    void onAppServerMessage(JSONObject message) {
        try {
            if (message.has("id") && message.opt("id") instanceof Number) {
                return; // Numeric IDs are reserved for native bootstrap requests.
            }
            JSONObject payload;
            if (message.has("id") && message.has("method")) {
                payload = new JSONObject().put("type", "mcp-request").put("hostId", "local")
                    .put("request", message);
            } else if (message.has("id")) {
                payload = new JSONObject().put("type", "mcp-response").put("hostId", "local").put("message", message);
            } else if (message.has("method")) {
                payload = new JSONObject().put("type", "mcp-notification").put("hostId", "local")
                    .put("method", message.optString("method"))
                    .put("params", message.opt("params") == null ? JSONObject.NULL : message.opt("params"));
            } else return;
            emitToViewBatched(payload);
        } catch (Exception e) { Log.e(TAG, "Failed to forward app-server message", e); }
    }

    private void emitToViewBatched(JSONObject payload) throws Exception {
        JSONObject envelope = new JSONObject()
            .put("type", "ipc-main-event")
            .put("channel", "codex_desktop:message-for-view")
            .put("args", new JSONArray().put(payload));
        synchronized (pendingAppServerMessages) {
            pendingAppServerMessages.add(envelope.toString());
            if (appServerFlushScheduled) return;
            appServerFlushScheduled = true;
        }
        webView.postDelayed(this::flushAppServerMessages, 16L);
    }

    private void flushAppServerMessages() {
        final String batchJson;
        synchronized (pendingAppServerMessages) {
            JSONArray batch = new JSONArray();
            for (String value : pendingAppServerMessages) {
                try { batch.put(new JSONObject(value)); } catch (Exception ignored) {}
            }
            pendingAppServerMessages.clear();
            appServerFlushScheduled = false;
            batchJson = batch.toString();
        }
        if ("[]".equals(batchJson)) return;
        webView.evaluateJavascript("(function(batch){"
            + "if(typeof window.__codexDesktopReceiveBatch==='function'){window.__codexDesktopReceiveBatch(batch);return;}"
            + "if(typeof window.__codexDesktopReceive==='function'){for(var i=0;i<batch.length;i++){window.__codexDesktopReceive(batch[i]);}}"
            + "})(" + batchJson + ")", null);
    }

    void onAppServerError(Object id, String error) {
        try {
            JSONObject response = new JSONObject().put("id", id == null ? JSONObject.NULL : id)
                .put("error", new JSONObject().put("code", -32000).put("message", error));
            emitToView(new JSONObject().put("type", "mcp-response").put("hostId", "local").put("message", response));
        } catch (Exception e) { Log.e(TAG, "Failed to forward app-server error", e); }
    }

    private void emitToView(JSONObject payload) throws Exception {
        send(new JSONObject()
            .put("type", "ipc-main-event")
            .put("channel", "codex_desktop:message-for-view")
            .put("args", new JSONArray().put(payload)));
    }

    private void send(JSONObject message) {
        final String payload = JSONObject.quote(message.toString());
        activity.runOnUiThread(() -> webView.evaluateJavascript(
            "window.__codexDesktopReceive && window.__codexDesktopReceive(" + payload + ")", null));
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 4000) return value;
        return value.substring(0, 4000) + "...";
    }
}

