package com.termux.app

import android.app.ActivityOptions
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import android.view.Choreographer
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.termux.R
import com.termux.app.update.AppUpdateManager
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


/** Immutable hand-off between background catalog preparation and main-thread runtime attach. */
internal data class NativeBackendStartRequest(
    val generation: Int,
    val profile: CodexProviderStore.Profile,
    val providerFingerprint: String,
    val routeThroughMihomo: Boolean,
)


    internal fun CodexChatActivity.isCodexCliInstalled(): Boolean =
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex").canExecute()

    internal fun CodexChatActivity.isBackendCliInstalled(): Boolean {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        return if (NativeBackendType.current(prefs) == NativeBackendType.CLAUDE) {
            File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "claude").canExecute()
        } else {
            isCodexCliInstalled()
        }
    }

    internal fun CodexChatActivity.backendRuntimeExists(): Boolean {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        return if (NativeBackendType.current(prefs) == NativeBackendType.CLAUDE) {
            ClaudeNativeRuntime.exists()
        } else {
            CodexNativeRuntime.exists()
        }
    }

    internal fun CodexChatActivity.maybeOfferCodexInstallOnFirstLaunch() {
        if (isCodexCliInstalled() || isFinishing || isDestroyed) return
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val alreadyViewed = prefs.getBoolean(CODEX_INSTALL_PROMPT_VIEWED_PREFERENCE, false) ||
            prefs.getBoolean("setup_skipped", false)
        if (alreadyViewed) return
        prefs.edit().putBoolean(CODEX_INSTALL_PROMPT_VIEWED_PREFERENCE, true).apply()
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_codex_logo)
            .setTitle(nativeText(nativeLanguage, "下载 Codex CLI？", "Download Codex CLI?"))
            .setMessage(
                nativeText(
                    nativeLanguage,
                    "原生聊天、WebUI 和 Termux 需要 Codex CLI 才能运行。\n\n安装包来自 OpenAI 官方 Release，只会写入应用私有目录，不会覆盖你的对话、项目和配置。",
                    "Native chat, WebUI, and Termux require Codex CLI.\n\nThe package comes from the official OpenAI release and is stored only in the app's private directory. Your chats, projects, and settings are preserved.",
                ),
            )
            .setNegativeButton(nativeText(nativeLanguage, "稍后", "Later"), null)
            .setPositiveButton(nativeText(nativeLanguage, "下载 Codex", "Download Codex")) { _, _ ->
                window.decorView.post {
                    CodexInstaller.setupBootstrapIfNeeded(this) {
                        if (!isFinishing && !isDestroyed) {
                            startBackend(preferConfiguredDefault = true)
                        }
                    }
                }
            }
            .show()
    }

    internal fun CodexChatActivity.currentBackendConfiguration(
        prefs: android.content.SharedPreferences,
    ): NativeBackendConfiguration = NativeBackendConfigurationResolver.resolve(
        profile = providerStore.active(),
        routeApiPreference = prefs.getBoolean("mihomo_route_api", false),
        mcpRevision = prefs.getLong(NativeMcpConfigStore.REVISION_KEY, 0L),
        mcpFileFingerprint = NativeMcpConfigStore.fileFingerprint(),
    )

    internal fun CodexChatActivity.startBackend(
        preferConfiguredDefault: Boolean = false,
        preparedConfiguration: NativeBackendConfiguration? = null,
    ) {
        // Every request invalidates an older background catalog write/attach continuation.
        // Catalog writes themselves are serialized, so the newest valid request always wins.
        val generation = ++backendStartGeneration
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        if (NativeBackendType.current(prefs) == NativeBackendType.CLAUDE) {
            startClaudeBackend(generation, preferConfiguredDefault)
            return
        }
        val configuration = preparedConfiguration ?: currentBackendConfiguration(prefs)
        val profile = configuration.profile

        backendConfigurationLoaded = true
        pendingBackendConfigurationReload = false
        appliedProviderFingerprint = configuration.fingerprint
        appliedProviderDefaultModel = configuration.defaultModel
        activeProfileId = configuration.profileId

        chatState.modelOptions.clear()
        chatState.modelOptions.addAll(configuration.modelOptions)
        if (profile != null) {
            restoreNativeSelection(prefs, profile.id, profile.model, preferConfiguredDefault)
        } else {
            chatState.selectedModel = ""
            chatState.modelLabel = "默认模型"
            chatState.selectedEffort = "high"
        }

        if (!configuration.hasRuntimeCredentials) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("没有可用的 API 配置，请先返回首页创建并启用配置。")
            return
        }
        checkNotNull(profile)

        val binary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        if (!binary.canExecute()) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("Codex CLI 尚未安装，请先返回首页完成运行环境安装。")
            return
        }

        chatState.connectionLabel = "正在连接 ${chatState.modelLabel}…"
        chatState.ready = false

        // Native chat owns a dedicated reasoning panel, so request summaries from
        // capable models even if the shared WebUI profile defaults them to off.
        profile.models.forEach { model ->
            if (model.reasoningSummaries && model.defaultReasoningSummary.equals("none", ignoreCase = true)) {
                model.defaultReasoningSummary = "detailed"
            }
        }
        val request = NativeBackendStartRequest(
            generation = generation,
            profile = profile,
            providerFingerprint = configuration.fingerprint,
            routeThroughMihomo = configuration.routeThroughMihomo,
        )
        backendScope.launch {
            // JSON construction, writing and FileDescriptor.sync() stay off the UI thread, but
            // complete before app-server startup reads the catalog.
            backendCatalogPreparer.prepare(request.profile.models)
            if (request.generation != backendStartGeneration || isFinishing || isDestroyed) return@launch
            attachPreparedBackend(request)
        }
    }

    internal fun CodexChatActivity.attachPreparedBackend(request: NativeBackendStartRequest) {
        val profile = request.profile
        val retainedRuntime = CodexNativeRuntime.exists()
        val retainedThread = notificationTargetThreadId.takeIf { it.isNotBlank() }
            ?: CodexNativeRuntime.currentThreadId()?.takeIf { it.isNotBlank() }
            ?: currentThreadId?.takeIf { it.isNotBlank() }
        bridge = CodexNativeRuntime.attach(
            this,
            this,
            request.providerFingerprint,
            profile.baseUrl,
            profile.apiKey,
            profile.model,
            profile.apiFormat.takeUnless { it.isBlank() || it == "auto" } ?: "openai_responses",
            request.routeThroughMihomo,
            profile.forwardReasoningContext,
            profile.ultraSubagentLimit,
            profile.normalSubagentLimit,
            profile.ultraTransportEfforts(),
            profile.hasV2Models(),
            profile.customSubagentStability && profile.hasCustomV2Models(),
        )
        if (!retainedThread.isNullOrBlank()) {
            val bridgeWasRecreated = CodexNativeRuntime.lastAttachRecreatedBridge()
            resumeConversation(
                retainedThread,
                retainedRuntime = retainedRuntime && !bridgeWasRecreated,
            )
        }
    }

    /** Claude backend: attach via ClaudeNativeRuntime; the Claude bridge shares the same events. */
    internal fun CodexChatActivity.startClaudeBackend(
        generation: Int,
        preferConfiguredDefault: Boolean = false,
    ) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        backendConfigurationLoaded = true
        pendingBackendConfigurationReload = false
        activeProfileId = ""
        val store = ClaudeProviderStore(prefs)
        val profile = store.active()
        val claudeBin = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "claude")
        if (profile == null) {
            if (ClaudeNativeRuntime.exists()) ClaudeNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("没有可用的 Claude API 配置，请先返回首页在设置中创建。")
            return
        }
        if (!claudeBin.canExecute()) {
            if (ClaudeNativeRuntime.exists()) ClaudeNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("Claude CLI 尚未安装，请先返回首页在设置中下载。")
            return
        }
        val model = profile.model.ifBlank { ClaudeProfile.DEFAULT_MODEL }
        chatState.modelOptions.clear()
        chatState.modelOptions.add(NativeModelOption(id = model, name = model, efforts = listOf("none"), defaultEffort = "none"))
        chatState.selectedModel = model
        chatState.modelLabel = model
        chatState.selectedEffort = "none"
        chatState.connectionLabel = "正在连接 Claude…"
        chatState.ready = false

        val permissionMode = NativePermissionMode.normalize(
            prefs.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS),
        )
        val claudePermissionMode = when (permissionMode) {
            NativePermissionMode.READ_ONLY -> "plan"
            NativePermissionMode.WORKSPACE -> "acceptEdits"
            else -> "bypassPermissions"
        }
        val fingerprint = listOf(profile.apiKey, profile.baseUrl, model, claudePermissionMode).joinToString("\n")
        val configDir = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".claude").absolutePath
        val routeThroughMihomo = prefs.getBoolean("mihomo_route_api", false)
        val retainedRuntime = ClaudeNativeRuntime.exists()
        val retainedThread = notificationTargetThreadId.takeIf { it.isNotBlank() }
            ?: ClaudeNativeRuntime.currentThreadId()?.takeIf { it.isNotBlank() }
            ?: currentThreadId?.takeIf { it.isNotBlank() }
        val bridgeWasRecreated = !ClaudeNativeRuntime.exists()
        bridge = ClaudeNativeRuntime.attach(
            this,
            this,
            fingerprint,
            claudeBin.absolutePath,
            configDir,
            profile.apiKey,
            profile.baseUrl,
            model,
            claudePermissionMode,
            allowedClaudeTools(permissionMode),
            retainedThread.orEmpty(),
            routeThroughMihomo,
        )
        if (!retainedThread.isNullOrBlank() && !bridgeWasRecreated) {
            resumeConversation(retainedThread, retainedRuntime = retainedRuntime)
        }
    }

    /** Claude tool allow-list for non-bypass permission modes; read-only gets a minimal set. */
    internal fun CodexChatActivity.allowedClaudeTools(permissionMode: String): String = when (permissionMode) {
        NativePermissionMode.READ_ONLY -> "Read,Grep,Glob,Bash(ls:*),Bash(cat:*),Bash(find:*),Bash(pwd:*),Bash(git status:*),Bash(git log:*),Bash(git diff:*),Bash(git show:*),WebFetch,WebSearch"
        NativePermissionMode.WORKSPACE -> "Read,Grep,Glob,WebFetch,WebSearch,Bash(ls:*),Bash(pwd:*),Bash(mkdir:*),Bash(cp:*),Bash(mv:*),Bash(rm:*),Bash(echo:*),Bash(git status:*),Bash(git log:*),Bash(git diff:*),Bash(git add:*),Bash(git commit:*),Bash(git push:*),Bash(git pull:*),Bash(git branch:*),Bash(git checkout:*),Bash(git stash:*)"
        else -> ""
    }

    internal fun CodexChatActivity.reloadProviderConfigurationIfChanged() {
        if (!backendConfigurationLoaded) return
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val configuration = currentBackendConfiguration(prefs)
        if (configuration.fingerprint == appliedProviderFingerprint) {
            pendingBackendConfigurationReload = false
            return
        }
        if (chatState.busy) {
            pendingBackendConfigurationReload = true
            return
        }
        val profile = configuration.profile
        val preferConfiguredDefault = NativeProviderSync.shouldPreferConfiguredDefault(
            activeProfileId,
            appliedProviderDefaultModel,
            configuration.profileId,
            configuration.defaultModel,
        )
        NativeChatDiagnostics.record(this, "provider_configuration_reload", JSONObject()
            .put("previousProfileId", activeProfileId)
            .put("nextProfileId", configuration.profileId)
            .put("preferConfiguredDefault", preferConfiguredDefault)
            .put("models", profile?.models?.size ?: 0))
        startBackend(preferConfiguredDefault, configuration)
    }

    internal fun CodexChatActivity.applyPendingProviderConfiguration() {
        if (!pendingBackendConfigurationReload) return
        streamHandler.post { reloadProviderConfigurationIfChanged() }
    }

    internal fun CodexChatActivity.preferencePart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    internal fun CodexChatActivity.modelPreferenceKey(profileId: String): String =
        "native_chat_model_v2_${preferencePart(profileId)}"

    internal fun CodexChatActivity.effortPreferenceKey(profileId: String, modelId: String): String =
        "native_chat_effort_v2_${preferencePart(profileId)}_${preferencePart(modelId.lowercase())}"

    internal fun CodexChatActivity.restoreNativeSelection(
        prefs: android.content.SharedPreferences,
        profileId: String,
        profileDefaultModel: String,
        preferConfiguredDefault: Boolean = false,
    ) {
        val storedModelId = prefs.getString(modelPreferenceKey(profileId), null).orEmpty()
        val selectedModelId = NativeProviderSync.resolveModelId(
            chatState.modelOptions.map { it.id },
            storedModelId,
            profileDefaultModel,
            preferConfiguredDefault,
        )
        val selectedOption = chatState.modelOptions.firstOrNull {
            it.id.equals(selectedModelId, ignoreCase = true)
        }

        if (selectedOption == null) {
            chatState.selectedModel = profileDefaultModel
            chatState.modelLabel = profileDefaultModel.ifBlank { "默认模型" }
            chatState.selectedEffort = "high"
            return
        }

        chatState.selectedModel = selectedOption.id
        chatState.modelLabel = selectedOption.name.ifBlank { selectedOption.id }
        val storedEffort = prefs.getString(effortPreferenceKey(profileId, selectedOption.id), null)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        chatState.selectedEffort = storedEffort.takeIf { it in selectedOption.efforts }
            ?: selectedOption.defaultEffort

        prefs.edit()
            .putString(modelPreferenceKey(profileId), selectedOption.id)
            .putString(effortPreferenceKey(profileId, selectedOption.id), chatState.selectedEffort)
            .apply()
        NativeChatDiagnostics.record(this, "native_selection_restored", JSONObject()
            .put("profileId", profileId)
            .put("model", chatState.selectedModel)
            .put("effort", chatState.selectedEffort)
            .put("hadStoredModel", storedModelId.isNotBlank())
            .put("hadValidStoredEffort", storedEffort in selectedOption.efforts))
    }

    internal fun CodexChatActivity.selectNativeModel(modelId: String) {
        val option = chatState.modelOptions.firstOrNull { it.id.equals(modelId, ignoreCase = true) } ?: return
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profileId = activeProfileId
        val storedEffort = if (profileId.isBlank()) "" else {
            prefs.getString(effortPreferenceKey(profileId, option.id), null)
                ?.trim()
                ?.lowercase()
                .orEmpty()
        }
        chatState.selectedModel = option.id
        chatState.modelLabel = option.name.ifBlank { option.id }
        chatState.selectedEffort = storedEffort.takeIf { it in option.efforts } ?: option.defaultEffort
        if (profileId.isNotBlank()) {
            prefs.edit()
                .putString(modelPreferenceKey(profileId), option.id)
                .putString(effortPreferenceKey(profileId, option.id), chatState.selectedEffort)
                .apply()
        }
        NativeChatDiagnostics.record(this, "native_model_selected", JSONObject()
            .put("profileId", profileId)
            .put("model", chatState.selectedModel)
            .put("effort", chatState.selectedEffort))
    }

    internal fun CodexChatActivity.selectNativeEffort(effort: String) {
        val option = chatState.modelOptions.firstOrNull {
            it.id.equals(chatState.selectedModel, ignoreCase = true)
        } ?: return
        val normalized = effort.trim().lowercase()
        if (normalized !in option.efforts) {
            NativeChatDiagnostics.record(this, "native_effort_rejected", JSONObject()
                .put("model", option.id)
                .put("effort", normalized))
            return
        }
        chatState.selectedEffort = normalized
        if (activeProfileId.isNotBlank()) {
            getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
                .putString(modelPreferenceKey(activeProfileId), option.id)
                .putString(effortPreferenceKey(activeProfileId, option.id), normalized)
                .apply()
        }
        NativeChatDiagnostics.record(this, "native_effort_selected", JSONObject()
            .put("profileId", activeProfileId)
            .put("model", option.id)
            .put("effort", normalized))
    }

    internal fun CodexChatActivity.cacheAttachment(uri: Uri, image: Boolean) {
        try {
            var name = "attachment-${System.currentTimeMillis()}"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val directory = File(cacheDir, "native-chat-attachments").apply { mkdirs() }
            val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}-$safeName")
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("无法读取附件")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            if (!target.isFile) throw IllegalStateException("附件缓存失败")
            chatState.attachments.add(NativeAttachment(name, target.absolutePath, image))
        } catch (error: Exception) {
            Toast.makeText(this, "附件读取失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    internal fun CodexChatActivity.setUiMotionActive(active: Boolean) {
        if (uiMotionActive == active) return
        uiMotionActive = active
        val generation = ++uiMotionCatchUpGeneration
        NativeChatDiagnostics.record(this, "ui_motion_priority", JSONObject()
            .put("active", active)
            .put("pendingReasoning", pendingReasoning.length)
            .put("pendingAnswer", pendingAnswer.length))
        if (active) return

        // Wait until the first frame after the gesture/navigation animation. Publishing in the
        // same frame as drawer settlement can still turn the final animation frame into a hitch.
        Choreographer.getInstance().postFrameCallback {
            if (uiMotionActive || generation != uiMotionCatchUpGeneration) return@postFrameCallback
            streamHandler.post {
                if (uiMotionActive || generation != uiMotionCatchUpGeneration) return@post
                flushReasoningDeltas()
                flushAnswerDeltas()
            }
        }
    }

    internal fun CodexChatActivity.openHomeSettings() {
        // Queue the back-preview capture before launching, but keep all bitmap allocation and
        // copying off the shared UI thread so it cannot stall the settings enter transition.
        NativeSettingsBackPreview.captureAsync(window)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14's system transition supplies the live previous-Activity preview for
            // predictive back. A custom ActivityOptions animation replaces that preview.
            startActivity(Intent(this, NativeSettingsActivity::class.java))
        } else {
            val transition = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.codex_settings_enter,
                R.anim.codex_chat_hold,
            )
            startActivity(Intent(this, NativeSettingsActivity::class.java), transition.toBundle())
        }
    }

    internal fun CodexChatActivity.openLegacyWebUi() {
        startActivity(FcodeToolNavigation.webUiIntent(this))
    }

    /**
     * A drawer can stay open long enough to accumulate thousands of characters. Once direct
     * manipulation ends, replay that backlog in bounded pieces instead of attaching one very
     * large document update to the first free frame. Terminal/phase-boundary flushes remain exact.
     */

