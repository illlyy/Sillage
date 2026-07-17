package com.termux.app

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class NativeChatRole {
    USER,
    ASSISTANT,
    ACTIVITY,
    ERROR,
}

internal data class NativeAttachment(val name: String, val path: String, val image: Boolean)

internal data class NativeModelOption(val id: String, val name: String, val efforts: List<String>, val defaultEffort: String)

internal data class NativeConversation(val threadId: String, val title: String, val state: String, val projectPath: String, val favorite: Boolean) { val projectName: String get() = projectPath.trimEnd('/').substringAfterLast('/').ifBlank { "无项目" } }

internal data class NativeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: NativeChatRole,
    val content: String,
    val streaming: Boolean = false,
    val revealStartedAt: Long = 0L,
    val finalOnlyReveal: Boolean = false,
)

internal class NativeChatState {
    val messages = mutableStateListOf<NativeChatMessage>()
    val conversations = mutableStateListOf<NativeConversation>()
    val modelOptions = mutableStateListOf<NativeModelOption>()
    val attachments = mutableStateListOf<NativeAttachment>()
    val toolDetails = mutableStateListOf<String>()
    var input by mutableStateOf("")
    var connectionLabel by mutableStateOf("正在启动 Codex…")
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var modelLabel by mutableStateOf("")
    var conversationTitle by mutableStateOf("新对话")
    var conversationAnimationKey by mutableStateOf("new-${UUID.randomUUID()}")
    var selectedModel by mutableStateOf("")
    var selectedEffort by mutableStateOf("high")
    var revision by mutableIntStateOf(0)
    var processingLabel by mutableStateOf("")
    var reasoningText by mutableStateOf("")
    var reasoningComplete by mutableStateOf(false)
    var commandText by mutableStateOf("")
    var turnStartedAt by mutableStateOf(0L)
    var turnMessageStartIndex by mutableIntStateOf(0)

    fun resetConversation() {
        messages.clear()
        busy = false
        revision++
    }

    fun addUser(text: String) {
        messages.add(NativeChatMessage(role = NativeChatRole.USER, content = text))
        turnMessageStartIndex = messages.size
        busy = true
        processingLabel = "处理中"
        reasoningText = ""
        reasoningComplete = false
        commandText = ""
        toolDetails.clear()
        turnStartedAt = System.currentTimeMillis()
        revision++
    }

    fun appendAssistant(delta: String) {
        if (delta.isEmpty()) return
        val last = messages.lastOrNull()
        if (last != null && last.role == NativeChatRole.ASSISTANT && last.streaming) {
            messages[messages.lastIndex] = last.copy(content = last.content + delta)
        } else {
            messages.add(
                NativeChatMessage(
                    role = NativeChatRole.ASSISTANT,
                    content = delta,
                    streaming = true,
                    revealStartedAt = System.currentTimeMillis(),
                ),
            )
        }
        revision++
    }

    fun appendAssistantFinal(text: String) {
        if (text.isBlank()) return
        val existingIndex = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT }
        if (existingIndex != null) {
            val existing = messages[existingIndex]
            // A few app-server/provider combinations deliver both deltas and the final
            // item. Keep the stable message id and merge the authoritative final text;
            // adding a second assistant item makes a whole paragraph flash on screen.
            val merged = when {
                text.startsWith(existing.content) -> text
                existing.content.startsWith(text) -> existing.content
                else -> text
            }
            messages[existingIndex] = existing.copy(
                content = merged,
                streaming = false,
                finalOnlyReveal = false,
            )
        } else {
            messages.add(
                NativeChatMessage(
                    role = NativeChatRole.ASSISTANT,
                    content = text,
                    streaming = false,
                    revealStartedAt = System.currentTimeMillis(),
                    finalOnlyReveal = true,
                ),
            )
        }
        revision++
    }

    fun replaceHistory(value: String) {
        val parsed = ArrayList<NativeChatMessage>()
        val items = JSONArray(value)
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val role = when (item.optString("role")) {
                "user" -> NativeChatRole.USER
                "assistant" -> NativeChatRole.ASSISTANT
                "activity" -> NativeChatRole.ACTIVITY
                else -> continue
            }
            val content = item.optString("content").trim()
            if (content.isNotEmpty()) parsed.add(NativeChatMessage(role = role, content = content))
        }
        // One snapshot mutation avoids recomposing the chat once for every historical item.
        messages.clear()
        messages.addAll(parsed)
        revision++
    }

    fun addActivity(type: String) {
        processingLabel = when (type) {
            "reasoning" -> "正在思考"
            "commandExecution" -> "正在运行命令"
            "fileChange" -> "正在修改文件"
            "mcpToolCall" -> "正在调用工具"
            "webSearch" -> "正在搜索"
            else -> "处理中"
        }
        revision++
    }

    fun completeTurn() {
        val duration = ((System.currentTimeMillis() - turnStartedAt).coerceAtLeast(0L) / 1000L)
        if (reasoningText.isNotBlank() || commandText.isNotBlank() || toolDetails.isNotEmpty()) {
            val payload = JSONObject()
                .put("duration", duration)
                .put("reasoning", reasoningText.trim())
                .put("command", commandText.trim())
                .put("tools", JSONArray(toolDetails))
                .toString()
            val process = "PROCESS2|" + Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val assistantIndex = (turnMessageStartIndex until messages.size).firstOrNull { messages[it].role == NativeChatRole.ASSISTANT } ?: messages.size
            messages.add(assistantIndex, NativeChatMessage(role = NativeChatRole.ACTIVITY, content = process, revealStartedAt = System.currentTimeMillis()))
        }
        val lastAssistant = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT }
        if (lastAssistant >= 0) {
            messages[lastAssistant] = messages[lastAssistant].copy(streaming = false)
        }
        busy = false
        connectionLabel = "已连接"
        revision++
    }

    fun addError(message: String) {
        messages.add(
            NativeChatMessage(
                role = NativeChatRole.ERROR,
                content = message.ifBlank { "Codex 后端发生未知错误" },
            ),
        )
        busy = false
        connectionLabel = "连接异常"
        revision++
    }
}

class CodexChatActivity : ComponentActivity(), CodexAppServerBridge.EventListener {
    private val chatState = NativeChatState()
    private var bridge: CodexAppServerBridge? = null
    private var pendingConversationAnimationKey: String? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { cacheAttachment(it, true) }
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { cacheAttachment(it, false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatState.input = getSharedPreferences("codex_mobile", MODE_PRIVATE).getString("native_chat_draft_v1", "").orEmpty()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        setContent {
            FcodeChatTheme {
                NativeChatScreen(
                    state = chatState,
                    onSend = ::sendMessage,
                    onInputChange = ::updateDraft,
                    onRetry = ::retryMessage,
                    onEditMessage = ::editMessage,
                    onStop = ::stopCurrentTurn,
                    onNewConversation = ::newConversation,
                    onResumeConversation = ::resumeConversation,
                    onPickImages = { imagePicker.launch("image/*") },
                    onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                    onRemoveAttachment = { chatState.attachments.remove(it) },
                    onRenameConversation = ::renameConversation,
                    onDeleteConversation = ::deleteConversation,
                    onToggleFavorite = ::toggleFavorite,
                    onBackHome = ::finish,
                    onOpenLegacyWebUi = ::openLegacyWebUi,
                )
            }
        }

        refreshConversations()
        startBackend()
    }

    private fun startBackend() {
        val binary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        if (!binary.canExecute()) {
            chatState.addError("Codex CLI 尚未安装，请先返回首页完成运行环境安装。")
            return
        }

        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profile = CodexProviderStore(prefs).active()
        if (profile == null || profile.baseUrl.isBlank() || profile.apiKey.isBlank()) {
            chatState.addError("没有可用的 API 配置，请先返回首页创建并启用配置。")
            return
        }

        chatState.modelOptions.clear()
        profile.models.forEach { model ->
            val efforts = model.supportedReasoningEfforts.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            val defaultEffort = model.defaultReasoningEffort.trim().lowercase()
                .takeIf { it in efforts }
                ?: efforts.firstOrNull { it == "high" }
                ?: efforts.firstOrNull()
                ?: "high"
            chatState.modelOptions.add(
                NativeModelOption(
                    id = model.id,
                    name = model.name.ifBlank { model.id },
                    efforts = efforts.ifEmpty { listOf(defaultEffort) },
                    defaultEffort = defaultEffort,
                ),
            )
        }
        chatState.selectedModel = profile.model
        val selectedModelOption = chatState.modelOptions.firstOrNull { it.id.equals(profile.model, ignoreCase = true) }
        chatState.selectedEffort = selectedModelOption?.defaultEffort ?: "high"

        chatState.modelLabel = profile.model.ifBlank { "默认模型" }
        chatState.connectionLabel = "正在连接 ${chatState.modelLabel}…"

        val routeThroughMihomo = prefs.getBoolean("mihomo_route_api", false) ||
            (profile.proxyEnabled && profile.proxyWebUi)
        bridge = CodexAppServerBridge(this, this).also {
            it.start(
                profile.baseUrl,
                profile.apiKey,
                profile.model,
                profile.apiFormat,
                routeThroughMihomo,
                profile.forwardReasoningContext,
                profile.ultraSubagentLimit,
                profile.normalSubagentLimit,
                profile.ultraTransportEfforts(),
                profile.customSubagentStability && profile.hasCustomV2Models(),
            )
        }
    }

    private fun editMessage(messageId: String, text: String) {
        val value = text.trim()
        if (value.isEmpty() || chatState.busy || !chatState.ready) return
        val userIndex = chatState.messages.indexOfFirst { it.id == messageId && it.role == NativeChatRole.USER }
        if (userIndex < 0) return
        val rollbackTurns = chatState.messages.drop(userIndex).count { it.role == NativeChatRole.USER }.coerceAtLeast(1)
        chatState.messages[userIndex] = chatState.messages[userIndex].copy(content = value)
        while (chatState.messages.size > userIndex + 1) chatState.messages.removeAt(chatState.messages.lastIndex)
        chatState.messages.add(NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "NOTICE|已从此处重新生成"))
        chatState.busy = true
        chatState.processingLabel = "处理中"
        chatState.reasoningText = ""
        chatState.commandText = ""
        chatState.toolDetails.clear()
        chatState.turnStartedAt = System.currentTimeMillis()
        chatState.turnMessageStartIndex = chatState.messages.size
        bridge?.editTurn(value, chatState.selectedModel, chatState.selectedEffort, rollbackTurns)
    }

    private fun retryMessage(text: String) {
        val value = text.trim()
        if (value.isEmpty() || chatState.busy || !chatState.ready) return
        while (chatState.messages.isNotEmpty() && chatState.messages.last().role != NativeChatRole.USER) {
            chatState.messages.removeAt(chatState.messages.lastIndex)
        }
        chatState.busy = true
        chatState.processingLabel = "处理中"
        chatState.reasoningText = ""
        chatState.commandText = ""
        chatState.toolDetails.clear()
        chatState.turnStartedAt = System.currentTimeMillis()
        chatState.turnMessageStartIndex = chatState.messages.size
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, "[]")
    }

    private fun updateDraft(value: String) {
        chatState.input = value
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_chat_draft_v1", value).apply()
    }

    private fun sendMessage(text: String) {
        val value = text.trim()
        if ((value.isEmpty() && chatState.attachments.isEmpty()) || chatState.busy || !chatState.ready) return
        updateDraft("")
        if (chatState.conversationTitle == "新对话" && value.isNotBlank()) {
            chatState.conversationTitle = value.lineSequence().firstOrNull().orEmpty().trim().let { if (it.length > 28) it.take(27) + "…" else it }.ifBlank { "新对话" }
        }
        chatState.addUser(value)
        val attachments = JSONArray().also { array ->
            chatState.attachments.forEach { attachment ->
                array.put(JSONObject().put("name", attachment.name).put("path", attachment.path).put("image", attachment.image))
            }
        }
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, attachments.toString())
        chatState.attachments.clear()
    }

    private fun cacheAttachment(uri: Uri, image: Boolean) {
        try {
            var name = "attachment-${System.currentTimeMillis()}"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val directory = File(cacheDir, "native-chat-attachments").apply { mkdirs() }
            val target = File(directory, "${System.currentTimeMillis()}-$safeName")
            contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            chatState.attachments.add(NativeAttachment(name, target.absolutePath, image))
        } catch (error: Exception) {
            Toast.makeText(this, "附件读取失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCurrentTurn() {
        if (!chatState.busy) return
        chatState.connectionLabel = "正在停止…"
        bridge?.interruptCurrentTurn()
    }

    private fun refreshConversations() {
        Thread {
            val favorites = favoriteThreadIds()
            val tasks = CodexTaskStore.current(this).mapNotNull { task ->
                var title = task.title
                val resolved = CodexAppServerBridge.resolveConversationTitle(task.threadId)
                if (resolved.isNotBlank() && title.startsWith("Codex 任务")) {
                    title = resolved
                    CodexTaskStore.updateTitle(this, task.threadId, resolved)
                }
                if (resolved.isBlank() && title.startsWith("Codex 任务")) null
                else NativeConversation(task.threadId, title, task.state, CodexAppServerBridge.resolveConversationProject(task.threadId), task.threadId in favorites)
            }
            runOnUiThread {
                chatState.conversations.clear()
                chatState.conversations.addAll(tasks)
            }
        }.start()
    }

    private fun favoriteThreadIds(): Set<String> {
        val raw = getSharedPreferences("codex_mobile", MODE_PRIVATE).getString("native_favorite_threads_v1", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildSet { for (index in 0 until array.length()) add(array.optString(index)) }
    }

    private fun toggleFavorite(conversation: NativeConversation) {
        val favorites = favoriteThreadIds().toMutableSet()
        if (!favorites.add(conversation.threadId)) favorites.remove(conversation.threadId)
        val array = JSONArray().also { value -> favorites.forEach { value.put(it) } }
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_favorite_threads_v1", array.toString()).apply()
        refreshConversations()
    }

    private fun renameConversation(conversation: NativeConversation, title: String) {
        CodexTaskStore.updateTitle(this, conversation.threadId, title)
        refreshConversations()
    }

    private fun deleteConversation(conversation: NativeConversation) {
        CodexTaskStore.delete(this, conversation.threadId)
        refreshConversations()
    }

    private fun resumeConversation(threadId: String) {
        pendingConversationAnimationKey = threadId
        chatState.busy = false
        chatState.conversationTitle = chatState.conversations.firstOrNull { it.threadId == threadId }?.title ?: "对话"
        chatState.ready = false
        chatState.connectionLabel = "正在恢复对话…"
        bridge?.resumeConversation(threadId)
    }

    private fun newConversation() {
        pendingConversationAnimationKey = null
        chatState.conversationAnimationKey = "new-${UUID.randomUUID()}"
        chatState.resetConversation()
        chatState.conversationTitle = "新对话"
        chatState.ready = false
        chatState.connectionLabel = "正在创建新对话…"
        bridge?.newConversation()
    }

    private fun openLegacyWebUi() {
        startActivity(
            Intent(this, CodexHomeActivity::class.java)
                .setAction(CodexHomeActivity.ACTION_OPEN_WEBUI),
        )
        finish()
    }

    override fun onEvent(function: String, value: String) {
        when (function) {
            "onReady" -> {
                chatState.ready = true
                chatState.connectionLabel = "已连接"
            }
            "onHistory" -> {
                chatState.replaceHistory(value)
                pendingConversationAnimationKey?.let { key -> chatState.conversationAnimationKey = key }
                pendingConversationAnimationKey = null
            }
            "onDelta" -> chatState.appendAssistant(value)
            "onFinalAnswer" -> chatState.appendAssistantFinal(value)
            "onReasoningDelta" -> { chatState.reasoningText += value; chatState.revision++ }
            "onReasoningComplete" -> {
                if (value.length > chatState.reasoningText.length) chatState.reasoningText = value
                chatState.reasoningComplete = true
                chatState.revision++
            }
            "onCommandDelta" -> { chatState.commandText += value; chatState.revision++ }
            "onCommandComplete" -> {
                chatState.toolDetails.add(value)
                chatState.commandText = ""
                chatState.revision++
            }
            "onToolComplete" -> { chatState.toolDetails.add(value); chatState.revision++ }
            "onItem" -> chatState.addActivity(value)
            "onTurnComplete" -> {
                chatState.completeTurn()
                refreshConversations()
            }
            "onNativeError" -> chatState.addError(value)
            "onLog" -> Unit
        }
    }

    override fun onDestroy() {
        bridge?.stop()
        bridge = null
        super.onDestroy()
    }
}
