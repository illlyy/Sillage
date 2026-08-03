package com.termux.app

import android.util.Base64

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh03
import org.json.JSONObject

/**
 * Durable/historical rendering of one ACTIVITY-role message. Live groups render through
 * [NativeActivityGroupRenderer]; when a turn completes, the snapshot is written into a
 * `PROCESS2|` payload and this dispatcher re-derives the same visual from JSON.
 */
@Composable
internal fun RikkaActivityMessage(message: NativeChatMessage, state: NativeChatState, onLoadSubagentHistory: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val openSubagentDrawer = LocalOpenSubagentDrawer.current
    val text = message.content
    if (isNativePlanActivity(text)) {
        NativePlanActivityMessage(
            message = message,
            planJson = state.planJson,
            planExplanation = state.planExplanation,
        ) { planText, streaming ->
            if (streaming) {
                StreamingResponseText(message.id, planText, true, message.revealStartedAt, false, projectPath = state.projectPath)
            } else {
                DeferredHistoricalRichText(planText)
            }
        }
        return
    }
    if (text.startsWith(NATIVE_COMPACTION_PREFIX)) {
        NativeCompactionDivider(
            item = NativeHistoryAdapter.decodeCompaction(text, state.currentThreadId),
            messageId = message.id,
        )
        return
    }
    if (text.startsWith("NOTICE|")) {
        val legacyCompaction = NativeHistoryAdapter.decodeLegacyNotice(text, state.currentThreadId)
        if (legacyCompaction != null) {
            NativeCompactionDivider(legacyCompaction, message.id)
            return
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(text.substringAfter('|'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        return
    }
    val payload = remember(text) {
        if (text.startsWith("PROCESS2|") || text.startsWith("PROCESS|")) runCatching {
            JSONObject(String(Base64.decode(text.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull() else null
    }
    if (payload != null) {
        // Both current PROCESS2 and legacy PROCESS snapshots use the same domain group. This
        // keeps history in lockstep with the live renderer instead of regressing to one capsule
        // per command after a restart.
        val group = remember(message.id, state.currentThreadId, text) {
            NativeHistoryAdapter.processGroup(message.id, state.currentThreadId, payload, message.revealStartedAt)
        }
        val messageSnapshot = state.messages.toList()
        val liveSubagentSnapshot = state.liveSubagents.toList()
        val subagentCandidates = remember(messageSnapshot, liveSubagentSnapshot) {
            collectAllSubagentItems(messageSnapshot, liveSubagentSnapshot)
        }
        NativeActivityGroupRenderer(
            group = group,
            enterExpanded = message.enterExpanded,
            onAutoCollapsed = { state.markActivityAutoCollapsed(message.id) },
            onSubagentClick = { visual ->
                val anchor = subagentDrawerAnchor(visual, subagentCandidates)
                openSubagentDrawer(anchor)
                subagentThreadId(anchor).takeIf { it.isNotBlank() }?.let(onLoadSubagentHistory)
            },
            onSubagentOverflowClick = { visuals ->
                val first = visuals.firstOrNull()
                val anchor = first?.let { subagentDrawerAnchor(it, subagentCandidates) }
                    ?: JSONObject().put("type", "subAgentActivity")
                anchor.put("_openOverview", true)
                openSubagentDrawer(anchor)
            },
            onLoadSubagentHistory = onLoadSubagentHistory,
        )
        return
    }
    val duration = payload?.optLong("duration")?.toString() ?: text.substringAfter("PROCESS|", "0").substringBefore('|')
    val reasoning = payload?.optString("reasoning").orEmpty()
    val command = payload?.optString("command").orEmpty()
    val reasoningUnavailable = payload?.optBoolean("reasoningUnavailable", false) == true
    val tools = payload?.optJSONArray("tools")
    // Freshly-sealed rows enter expanded and fold themselves back with the exit animation (the old
    // code popped in already-collapsed, matching nothing the user had just watched). Historical rows
    // loaded from disk stay collapsed by default.
    var expanded by remember(message.id) { mutableStateOf(message.enterExpanded) }
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    LaunchedEffect(message.id, message.enterExpanded) {
        if (message.enterExpanded) {
            delay(1_200)
            if (expanded) {
                expanded = false
                pauseFollowForToggle()
                state.markActivityAutoCollapsed(message.id)
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        FcodeReasoningBlock(
            content = reasoning,
            isStreaming = false,
            durationSeconds = duration.toIntOrNull(),
            open = expanded,
            onToggle = { expanded = it },
            headerOnly = true,
        )
        // One accordion for the whole card (reasoning + tools) so a toggle drives a single smooth
        // height animation instead of two nested springs compounding into a fast layout shove.
        QElasticExpand(expanded, durationMs = 360) {
            Column {
                if (reasoning.isNotBlank()) {
                    Column(Modifier.padding(top = 10.dp)) { DeferredHistoricalRichText(reasoning) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                SafeExpandableViewport(maxHeight = 520.dp) {
                    Column {
                        if (reasoning.isBlank() && reasoningUnavailable) Text(
            nativeText(language, "模型未返回可见的思考摘要", "The model did not return a visible reasoning summary"),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (command.isNotBlank()) ToolTextCard(nativeText(language, "命令执行", "Command execution"), command, false)
        val hasThreadBackedAgents = tools != null && (0 until tools.length()).any { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.let(::subagentThreadId).orEmpty().isNotBlank()
        }
        val historicalFileChangeItems = if (tools == null) emptyList() else (0 until tools.length()).mapNotNull { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.takeIf { it.optString("type") == "fileChange" }
        }
        if (historicalFileChangeItems.isNotEmpty()) FileDiffCard(historicalFileChangeItems, state.projectPath)
        val historicalImageItems = if (tools == null) emptyList() else (0 until tools.length()).mapNotNull { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.takeIf { isImageToolItem(it.optString("type")) }
        }
        if (historicalImageItems.isNotEmpty()) ImageGroupCard(historicalImageItems)
        if (tools != null) for (index in 0 until tools.length()) {
            val item = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
            val type = item.optString("type")
            if (type == "fileChange") continue
            if (isImageToolItem(type)) continue
            if (type in setOf("collabAgentToolCall", "subAgentActivity") && hasThreadBackedAgents && subagentThreadId(item).isBlank()) continue
            val title = NativeToolConfigs.ofJson(type).label(language)
            val payloadRef = item.optString(NativeLargePayloadStore.PAYLOAD_REF)
            val detail = if (payloadRef.isNotBlank()) item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW) else when (type) {
                "commandExecution" -> buildString {
                    val commandValue = item.optString("command", "")
                    val outputValue = item.optString("aggregatedOutput", item.optString("output", ""))
                    if (commandValue.isNotBlank()) append("$ ").append(commandValue).append('\n')
                    if (outputValue.isNotBlank()) append(outputValue.trimEnd()).append('\n')
                    val code = item.opt("exitCode")
                    if (code != null && code != JSONObject.NULL) append("exit ").append(code)
                }
                "fileChange" -> item.optString("changes", item.toString(2))
                "mcpToolCall" -> buildString {
                    append(item.optString("tool", item.optString("name", "MCP")))
                    val arguments = item.opt("arguments")
                    if (arguments != null && arguments != JSONObject.NULL) append("\n\n参数\n").append(arguments.toString())
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n结果\n").append(output)
                }
                "webSearch" -> buildString {
                    append(item.optString("query", item.toString(2)))
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n").append(output)
                }
                "collabAgentToolCall", "subAgentActivity" -> item.optString("detail", item.toString(2))
                else -> item.toString(2)
            }
            if (type == "commandExecution") CommandExecutionCard(item)
            else if (type == "collabAgentToolCall" || type == "subAgentActivity") {
                val thread = subagentThreadId(item)
                CollabAgentCapsule(
                    item = item,
                    state = state,
                    onLoadHistory = onLoadSubagentHistory,
                )
            }
            else ToolTextCard(title, detail, type == "fileChange", payloadRef)
            }
                }
            }
            }
        }
    }
}
