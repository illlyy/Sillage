package com.termux.app

import java.util.LinkedHashMap

/** Categories that can be rendered inside one WebUI-style exploration group. */
internal enum class NativeActivityItemType {
    REASONING,
    COMMAND,
    FILE_CHANGE,
    WEB_SEARCH,
    TOOL,
    SUBAGENT,
}

internal enum class NativeActivityItemStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    WAITING,
}

/** A compact activity DTO. Large output is represented by a reference and a bounded preview. */
internal data class NativeActivityItem(
    val id: String,
    val type: NativeActivityItemType,
    val title: String = "",
    val text: String = "",
    val outputRef: String = "",
    val outputPreview: String = "",
    val status: NativeActivityItemStatus = NativeActivityItemStatus.RUNNING,
    val sequence: Long = 0L,
    val startedAtMs: Long = 0L,
    val completedAtMs: Long = 0L,
    val turnId: String? = null,
    val itemId: String? = null,
    val agentThreadId: String = "",
    val callId: String = "",
)

/**
 * One stable row in the conversation. Reasoning and commands before the first assistant
 * message intentionally share this row; a later assistant message creates a new exploration
 * segment, which prevents a long task from producing one capsule per command.
 */
internal data class NativeActivityGroup(
    val key: String,
    val threadId: String,
    val turnId: String?,
    val segment: Int,
    val reasoning: String = "",
    val items: List<NativeActivityItem> = emptyList(),
    val running: Boolean = true,
    val expandedByDefault: Boolean = true,
    val assistantBoundaryBefore: Boolean = false,
    val assistantBoundaryAfter: Boolean = false,
    /** True when a plan/compaction/error/assistant lifecycle closed this exploration segment. */
    val activityBoundaryAfter: Boolean = false,
    val createdAtMs: Long = 0L,
    val completedAtMs: Long = 0L,
) {
    val commands: List<NativeActivityItem>
        get() = items.filter { it.type == NativeActivityItemType.COMMAND }
    val runningCommandCount: Int
        get() = commands.count { it.status == NativeActivityItemStatus.RUNNING }
    val failedCount: Int
        get() = items.count { it.status == NativeActivityItemStatus.FAILED }
    val commandCount: Int get() = commands.size
}

internal data class NativeActivityReducerState(
    val groups: List<NativeActivityGroup> = emptyList(),
    val currentGroupKey: String? = null,
    val assistantSeenByTurn: Set<String> = emptySet(),
    val completedTurns: Set<String> = emptySet(),
    val lastSequence: Long = Long.MIN_VALUE,
)

/**
 * Pure event reducer for live and replayed activity. It is deliberately tolerant of missing
 * start events and duplicate/out-of-order completion events because older app-server versions
 * do not always emit a complete item lifecycle.
 */
internal class NativeActivityReducer(
    private val maxPreviewChars: Int = 4_096,
) {
    private val groups = LinkedHashMap<String, MutableGroup>()
    private val itemToGroup = HashMap<String, String>()
    private val assistantSeen = HashSet<String>()
    private val completedTurns = HashSet<String>()
    /** Stateful fallback parser prevents a tag split across deltas from becoming a body boundary. */
    private val assistantPlanParsers = HashMap<String, NativePlanStreamParser>()
    /** Sequences are scoped to a thread. A child/background thread must not make a later
     * primary-thread event look stale (the bridge uses one process-wide sequence counter). */
    private val lastSequenceByThread = HashMap<String, Long>()
    /** Legacy callbacks may omit turnId; remember the latest accepted id per thread so they
     * continue updating the same exploration instead of creating a synthetic `:turn` group. */
    private val lastTurnIdByThread = HashMap<String, String>()
    private var segmentByTurn = HashMap<String, Int>()
    /** The active exploration is scoped by turn. A late completion from an older turn must not
     * move the global cursor and make the next event in the current turn allocate a duplicate
     * segment. */
    private val currentKeyByTurn = HashMap<String, String>()
    /** Sequence of the first visible assistant body in each turn. Used to reject a delayed,
     * uncorrelated reasoning completion without suppressing a genuinely new post-answer
     * exploration whose sequence is newer. */
    private val assistantBoundarySequenceByTurn = HashMap<String, Long>()
    /** Correlates legacy command callbacks that do not carry an item id. */
    private val openCommandIdsByTurn = HashMap<String, java.util.ArrayDeque<String>>()
    private val openCommandIdsByText = HashMap<String, java.util.ArrayDeque<String>>()
    private var commandCounter = 0L
    private var currentKey: String? = null

    fun reset() {
        groups.clear()
        itemToGroup.clear()
        assistantSeen.clear()
        completedTurns.clear()
        assistantPlanParsers.clear()
        segmentByTurn.clear()
        currentKeyByTurn.clear()
        assistantBoundarySequenceByTurn.clear()
        openCommandIdsByTurn.clear()
        openCommandIdsByText.clear()
        commandCounter = 0L
        currentKey = null
        lastSequenceByThread.clear()
        lastTurnIdByThread.clear()
    }

    fun accept(event: NativeProtocolEvent): NativeActivityReducerState {
        // Sequence zero is used by callers that do not have bridge metadata. Those events must
        // still be accepted in arrival order; only positive, older sequences are stale.
        val sequenceThread = event.threadId.ifBlank { "thread" }
        val previousSequence = lastSequenceByThread[sequenceThread] ?: Long.MIN_VALUE
        if (event.sequence > 0L && event.sequence <= previousSequence) return snapshot()
        if (event.sequence > previousSequence) lastSequenceByThread[sequenceThread] = event.sequence
        event.turnId?.takeIf { it.isNotBlank() }?.let { lastTurnIdByThread[sequenceThread] = it }
        // A completion barrier can race a final command/output notification on older servers.
        // Permit that notification to update its already-correlated row, but never allocate a
        // fresh exploration group after the turn has been closed.
        if (completedTurns.contains(eventTurnKey(event)) && !isKnownItemEvent(event)) return snapshot()
        when (event) {
            is NativeProtocolEvent.ReasoningDelta -> appendReasoning(event)
            is NativeProtocolEvent.ReasoningCompleted -> completeReasoning(event)
            is NativeProtocolEvent.CommandStarted -> startCommand(event)
            is NativeProtocolEvent.CommandOutput -> appendCommandOutput(event)
            is NativeProtocolEvent.CommandCompleted -> completeCommand(event)
            is NativeProtocolEvent.ToolCompleted -> completeTool(event)
            is NativeProtocolEvent.SubagentUpdated -> updateSubagent(event)
            is NativeProtocolEvent.AssistantDelta -> {
                // item/started is represented as an empty AssistantDelta by older app-server
                // versions. It is only a lifecycle hint; treating it as visible body would split
                // reasoning before a response has actually been emitted.
                if (assistantHasVisibleBody(event)) assistantBoundary(event)
            }
            is NativeProtocolEvent.AssistantCompleted -> {
                if (assistantHasVisibleBody(event) || event.finalAnswer) assistantBoundary(event)
            }
            is NativeProtocolEvent.PlanStarted,
            is NativeProtocolEvent.PlanDelta,
            is NativeProtocolEvent.PlanCompleted,
            is NativeProtocolEvent.CompactionStarted,
            is NativeProtocolEvent.CompactionCompleted,
            is NativeProtocolEvent.CompactionFailed,
            is NativeProtocolEvent.Error,
            is NativeProtocolEvent.Boundary,
            is NativeProtocolEvent.TurnCompleted,
            is NativeProtocolRawEvent -> handleBoundary(event)
            // Usage is metadata, not a visual boundary.  Token notifications are commonly
            // interleaved with reasoning/command deltas; treating them as boundaries recreates
            // the very one-command-per-capsule fragmentation this reducer is meant to remove.
            is NativeProtocolEvent.TokenUsageUpdated -> Unit
        }
        return snapshot()
    }

    fun onEvent(event: NativeProtocolEvent): NativeActivityReducerState = accept(event)
    fun reduce(event: NativeProtocolEvent): NativeActivityReducerState = accept(event)

    fun snapshot(): NativeActivityReducerState = NativeActivityReducerState(
        groups = groups.values.map { it.freeze() },
        currentGroupKey = currentKey,
        assistantSeenByTurn = assistantSeen.toSet(),
        completedTurns = completedTurns.toSet(),
        lastSequence = lastSequenceByThread.values.maxOrNull() ?: Long.MIN_VALUE,
    )

    fun groups(): List<NativeActivityGroup> = snapshot().groups

    private fun turnKey(threadId: String, turnId: String?): String =
        threadId.ifBlank { "thread" } + ":" + (turnId.orEmpty().ifBlank { "turn" })

    private fun effectiveTurnId(event: NativeProtocolEvent): String? =
        event.turnId?.takeIf { it.isNotBlank() }
            ?: lastTurnIdByThread[event.threadId.ifBlank { "thread" }]

    private fun eventTurnKey(event: NativeProtocolEvent): String =
        turnKey(event.threadId, effectiveTurnId(event))

    private fun itemKey(event: NativeProtocolEvent, fallback: String): String =
        event.itemId?.takeIf { it.isNotBlank() } ?: fallback

    private fun itemCorrelationKey(event: NativeProtocolEvent, id: String): String =
        eventTurnKey(event) + "|" + id

    private fun isKnownItemEvent(event: NativeProtocolEvent): Boolean {
        val id = event.itemId?.takeIf { it.isNotBlank() } ?: return false
        val prefix = eventTurnKey(event) + "|"
        return itemToGroup[prefix + id] != null ||
            (event.turnId.isNullOrBlank() && itemToGroup.keys.any { it.endsWith("|$id") })
    }

    private fun ensureGroup(event: NativeProtocolEvent, forceNew: Boolean = false): MutableGroup {
        val turn = eventTurnKey(event)
        val effectiveTurn = effectiveTurnId(event).orEmpty().ifBlank { "turn" }
        val stableThread = event.threadId.ifBlank { "thread" }
        val old = currentKeyByTurn[turn]?.let(groups::get)
            ?: groups.values.lastOrNull { it.turnKey == turn }?.also { currentKeyByTurn[turn] = it.key }
        val boundaryAfterAssistant = assistantSeen.contains(turn)
        // An assistant body is one kind of boundary, but plan/compaction/error lifecycle items
        // are boundaries too.  They must not be allowed to glue the next exploration onto the
        // activity that visually precedes a divider.  Item completions still resolve through
        // itemToGroup before reaching this method, so a late completion can safely update the
        // closed group without opening a duplicate row.
        val activityBoundary = old?.activityBoundaryAfter == true
        val needNew = forceNew || old == null || activityBoundary || completedTurns.contains(turn)
        if (!needNew) {
            currentKey = old.key
            return old
        }

        val segment = segmentByTurn[turn] ?: -1
        var nextSegment = segment + 1
        // A late event from an older turn can arrive after another turn became current. Never
        // overwrite that turn's existing stable group key; allocate a new segment only when the
        // key is already occupied.
        while (groups.containsKey("exploration:$stableThread:$effectiveTurn:$nextSegment")) {
            nextSegment++
        }
        segmentByTurn[turn] = nextSegment
        val key = "exploration:$stableThread:$effectiveTurn:$nextSegment"
        val group = MutableGroup(
            key = key,
            threadId = event.threadId,
            turnKey = turn,
            turnId = effectiveTurnId(event),
            segment = nextSegment,
            createdAtMs = event.timestampMs,
            assistantBoundaryBefore = boundaryAfterAssistant,
        )
        groups[key] = group
        currentKeyByTurn[turn] = key
        currentKey = key
        return group
    }

    private fun groupForItem(event: NativeProtocolEvent, fallback: String): MutableGroup {
        val id = itemKey(event, fallback)
        return groupForResolvedItem(event, id)
    }

    private fun groupForResolvedItem(event: NativeProtocolEvent, id: String): MutableGroup {
        val correlationKey = itemCorrelationKey(event, id)
        val existingKey = itemToGroup[correlationKey]
            ?: if (event.turnId.isNullOrBlank()) {
                // Older callbacks often omit turnId on completion/output while preserving itemId.
                // Resolve the scoped identity by thread as a compatibility fallback.
                itemToGroup.entries.firstOrNull { (key, groupKey) ->
                    key.endsWith("|$id") && groups[groupKey]?.threadId == event.threadId
                }?.value
            } else null
        if (existingKey != null) {
            currentKey = existingKey
            return groups[existingKey] ?: ensureGroup(event)
        }
        val group = ensureGroup(event)
        itemToGroup[correlationKey] = group.key
        return group
    }

    private fun appendReasoning(event: NativeProtocolEvent.ReasoningDelta) {
        if (event.delta.isEmpty()) return
        val group = if (event.itemId.isNullOrBlank()) {
            if (shouldIgnoreLateUncorrelatedReasoning(event, event.delta)) return
            ensureGroup(event)
        } else {
            // Reasoning items normally carry an id. Resolving through the item index lets a
            // completion/delta that arrives after the assistant boundary update its original
            // group instead of manufacturing a ghost post-answer group.
            groupForResolvedItem(event, event.itemId)
        }
        if (!event.itemId.isNullOrBlank()) {
            itemToGroup[itemCorrelationKey(event, event.itemId)] = group.key
        }
        group.reasoning.append(event.delta)
    }

    private fun completeReasoning(event: NativeProtocolEvent.ReasoningCompleted) {
        if (event.text.isBlank()) return
        val group = if (event.itemId.isNullOrBlank()) {
            if (shouldIgnoreLateUncorrelatedReasoning(event, event.text)) return
            ensureGroup(event)
        } else {
            groupForResolvedItem(event, event.itemId)
        }
        if (!event.itemId.isNullOrBlank()) {
            itemToGroup[itemCorrelationKey(event, event.itemId)] = group.key
        }
        // Some providers send the complete summary after deltas. Prefer the authoritative text
        // when it extends the streamed value, otherwise keep the longer one.
        val current = group.reasoning.toString()
        group.reasoning.setLength(0)
        group.reasoning.append(
            when {
                current.isBlank() -> event.text
                event.text.startsWith(current) -> event.text
                current.startsWith(event.text) -> current
                event.text.length >= current.length -> event.text
                else -> current
            },
        )
    }

    private fun startCommand(event: NativeProtocolEvent.CommandStarted) {
        val explicitId = event.itemId?.takeIf { it.isNotBlank() }
        val id = explicitId ?: "command:${eventTurnKey(event)}:${++commandCounter}"
        val group = groupForResolvedItem(event, id)
        val existing = group.items[id]
        if (existing == null) {
            group.items[id] = NativeActivityItem(
                id = id,
                type = NativeActivityItemType.COMMAND,
                title = event.command,
                text = event.command,
                status = NativeActivityItemStatus.RUNNING,
                sequence = event.sequence,
                startedAtMs = event.timestampMs,
                turnId = event.turnId,
                itemId = event.itemId,
            )
        } else if (existing.status != NativeActivityItemStatus.RUNNING) {
            group.items[id] = existing.copy(status = NativeActivityItemStatus.RUNNING)
        }
        registerOpenCommand(event, id)
    }

    private fun appendCommandOutput(event: NativeProtocolEvent.CommandOutput) {
        val id = event.itemId?.takeIf { it.isNotBlank() }
            ?: resolveOpenCommandId(event.threadId, event.turnId)
            ?: "command:${eventTurnKey(event)}:${++commandCounter}"
        val group = groupForResolvedItem(event, id)
        val existingItem = group.items[id]
        val existing = existingItem ?: NativeActivityItem(
            id = id,
            type = NativeActivityItemType.COMMAND,
            status = NativeActivityItemStatus.RUNNING,
            sequence = event.sequence,
            startedAtMs = event.timestampMs,
            turnId = event.turnId,
            itemId = event.itemId,
        )
        val preview = (existing.outputPreview + event.delta).takeLast(maxPreviewChars)
        group.items[id] = existing.copy(
            outputRef = event.outputRef.ifBlank { existing.outputRef },
            outputPreview = preview,
            text = if (existing.text.isBlank()) preview else existing.text,
        )
        if (existingItem == null) registerOpenCommand(event, id)
    }

    private fun completeCommand(event: NativeProtocolEvent.CommandCompleted) {
        val id = event.itemId?.takeIf { it.isNotBlank() }
            ?: resolveOpenCommandId(event.threadId, event.turnId, event.command)
            ?: "command:${eventTurnKey(event)}:${++commandCounter}"
        val group = groupForResolvedItem(event, id)
        val existing = group.items[id]
        val failed = event.status.lowercase() in setOf("failed", "error", "cancelled", "canceled", "interrupted") || event.exitCode?.let { it != 0 } == true
        val status = if (failed) NativeActivityItemStatus.FAILED else NativeActivityItemStatus.COMPLETED
        group.items[id] = (existing ?: NativeActivityItem(
            id = id,
            type = NativeActivityItemType.COMMAND,
            title = event.command,
            text = event.command,
            sequence = event.sequence,
            startedAtMs = event.timestampMs,
            turnId = event.turnId,
            itemId = event.itemId,
        )).copy(
            title = event.command.ifBlank { existing?.title.orEmpty() },
            text = event.command.ifBlank { existing?.text.orEmpty() },
            outputRef = event.outputRef.ifBlank { existing?.outputRef.orEmpty() },
            status = status,
            completedAtMs = event.timestampMs,
        )
        unregisterOpenCommand(event, id)
    }

    private fun commandTextKey(threadId: String, turnId: String?, command: String): String =
        turnKey(threadId, turnId ?: lastTurnIdByThread[threadId.ifBlank { "thread" }]) + "|" + command.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun registerOpenCommand(event: NativeProtocolEvent, id: String) {
        val turn = eventTurnKey(event)
        val byTurn = openCommandIdsByTurn.getOrPut(turn) { java.util.ArrayDeque() }
        if (!byTurn.contains(id)) byTurn.addLast(id)
        val command = when (event) {
            is NativeProtocolEvent.CommandStarted -> event.command
            else -> ""
        }
        if (command.isNotBlank()) {
            val byText = openCommandIdsByText.getOrPut(commandTextKey(event.threadId, event.turnId, command)) {
                java.util.ArrayDeque()
            }
            if (!byText.contains(id)) byText.addLast(id)
        }
    }

    /** Resolve an id for a legacy output/completion event which omitted itemId. */
    private fun resolveOpenCommandId(threadId: String, turnId: String?, command: String = ""): String? {
        val turn = turnKey(threadId, turnId ?: lastTurnIdByThread[threadId.ifBlank { "thread" }])
        if (command.isNotBlank()) {
            openCommandIdsByText[commandTextKey(threadId, turnId, command)]
                ?.firstOrNull { openCommandIdsByTurn[turn]?.contains(it) == true }
                ?.let { return it }
        }
        return openCommandIdsByTurn[turn]?.peekLast()
    }

    private fun unregisterOpenCommand(event: NativeProtocolEvent, id: String) {
        val turn = eventTurnKey(event)
        openCommandIdsByTurn[turn]?.remove(id)
        if (openCommandIdsByTurn[turn].isNullOrEmpty()) openCommandIdsByTurn.remove(turn)
        val command = when (event) {
            is NativeProtocolEvent.CommandStarted -> event.command
            is NativeProtocolEvent.CommandCompleted -> event.command
            else -> ""
        }
        if (command.isNotBlank()) {
            val key = commandTextKey(event.threadId, event.turnId, command)
            openCommandIdsByText[key]?.remove(id)
            if (openCommandIdsByText[key].isNullOrEmpty()) openCommandIdsByText.remove(key)
        } else {
            // An output/completion may have no command text. Remove the id from any text index
            // belonging to this turn so a later completion cannot resurrect the finished row.
            openCommandIdsByText.entries.removeIf { (key, ids) ->
                key.startsWith(turn + "|") && ids.remove(id) && ids.isEmpty()
            }
        }
    }

    private fun completeTool(event: NativeProtocolEvent.ToolCompleted) {
        val type = when (event.type.lowercase()) {
            "filechange", "file_change", "patch", "apply_patch" -> NativeActivityItemType.FILE_CHANGE
            "websearch", "web_search", "search" -> NativeActivityItemType.WEB_SEARCH
            "subagent", "subagentactivity", "collabagenttoolcall" -> NativeActivityItemType.SUBAGENT
            else -> NativeActivityItemType.TOOL
        }
        val id = itemKey(event, "tool:${event.sequence}:${event.type}")
        val group = groupForItem(event, id)
        val failed = event.status.lowercase() in setOf("failed", "error", "cancelled", "canceled")
        group.items[id] = (group.items[id] ?: NativeActivityItem(
            id = id,
            type = type,
            title = event.title.ifBlank { event.type },
            sequence = event.sequence,
            startedAtMs = event.timestampMs,
            turnId = event.turnId,
            itemId = event.itemId,
        )).copy(
            type = type,
            title = event.title.ifBlank { event.type },
            outputRef = event.payloadRef,
            status = if (failed) NativeActivityItemStatus.FAILED else NativeActivityItemStatus.COMPLETED,
            completedAtMs = event.timestampMs,
        )
    }

    private fun updateSubagent(event: NativeProtocolEvent.SubagentUpdated) {
        val id = event.itemId?.takeIf { it.isNotBlank() }
            ?: event.callId.takeIf { it.isNotBlank() }
            ?: event.agentThreadId.takeIf { it.isNotBlank() }
            ?: "subagent:${event.sequence}"
        val group = groupForResolvedItem(event, id)
        val status = when (event.status.trim().lowercase()) {
            "working", "running", "started", "inprogress", "in_progress" -> NativeActivityItemStatus.RUNNING
            "waiting", "pending", "queued" -> NativeActivityItemStatus.WAITING
            "failed", "error", "cancelled", "canceled", "interrupted" -> NativeActivityItemStatus.FAILED
            else -> NativeActivityItemStatus.COMPLETED
        }
        val existing = group.items[id]
        group.items[id] = (existing ?: NativeActivityItem(
            id = id,
            type = NativeActivityItemType.SUBAGENT,
            sequence = event.sequence,
            startedAtMs = event.timestampMs,
            turnId = effectiveTurnId(event),
            itemId = event.itemId,
        )).copy(
            title = event.name.ifBlank { existing?.title.orEmpty() },
            status = status,
            completedAtMs = if (status in setOf(NativeActivityItemStatus.COMPLETED, NativeActivityItemStatus.FAILED)) event.timestampMs else 0L,
            agentThreadId = event.agentThreadId.ifBlank { existing?.agentThreadId.orEmpty() },
            callId = event.callId.ifBlank { existing?.callId.orEmpty() },
        )
    }

    private fun assistantBoundary(event: NativeProtocolEvent) {
        val turn = eventTurnKey(event)
        assistantSeen.add(turn)
        if (event.sequence > 0L) {
            assistantBoundarySequenceByTurn[turn] = maxOf(
                assistantBoundarySequenceByTurn[turn] ?: Long.MIN_VALUE,
                event.sequence,
            )
        }
        val group = currentKeyByTurn[turn]?.let(groups::get)
            ?: currentKey?.let(groups::get)?.takeIf { it.turnKey == turn }
        if (group != null) {
            group.assistantBoundaryAfter = true
            group.activityBoundaryAfter = true
            // The assistant body is rendered as its own conversation item. Once that body
            // starts, the exploration that led to it is no longer a live spinner/expanded
            // group; keeping it running until turn/completed made the old reasoning capsule
            // appear active beside the answer and could keep following scroll after the model
            // had already moved on.
            group.running = false
            group.completedAtMs = event.timestampMs
        }
        // Assistant content itself is rendered separately. Keep the next exploration group
        // separate even if the next protocol event is another reasoning delta.
        if (event is NativeProtocolEvent.AssistantCompleted || event is NativeProtocolEvent.AssistantDelta) {
            currentKey = group?.key ?: currentKey
        }
    }

    private fun shouldIgnoreLateUncorrelatedReasoning(
        event: NativeProtocolEvent,
        text: String,
    ): Boolean {
        val turn = eventTurnKey(event)
        val boundarySequence = assistantBoundarySequenceByTurn[turn] ?: 0L
        if (boundarySequence > 0L && event.sequence > 0L && event.sequence <= boundarySequence) return true
        // Legacy callbacks may have no sequence or item id. If their completion is merely the
        // already-rendered reasoning summary, do not create a second visual group after the body.
        val previous = currentKeyByTurn[turn]?.let(groups::get)
        return previous?.activityBoundaryAfter == true && text.isNotBlank() &&
            previous.reasoning.toString().trim() == text.trim()
    }

    private fun assistantHasVisibleBody(event: NativeProtocolEvent): Boolean {
        val turn = eventTurnKey(event)
        val item = event.itemId.orEmpty().ifBlank { "assistant" }
        val key = "$turn|$item"
        val parser = assistantPlanParsers.getOrPut(key) { NativePlanStreamParser() }
        val result = when (event) {
            is NativeProtocolEvent.AssistantDelta -> parser.append(
                event.delta,
                event.itemId?.takeIf { it.isNotBlank() },
            )
            is NativeProtocolEvent.AssistantCompleted -> parser.complete(
                event.text,
                event.itemId?.takeIf { it.isNotBlank() },
            )
            else -> return false
        }
        if (event is NativeProtocolEvent.AssistantCompleted) assistantPlanParsers.remove(key)
        return result.assistantText.isNotBlank()
    }

    private fun handleBoundary(event: NativeProtocolEvent) {
        if (event is NativeProtocolEvent.TurnCompleted) {
            val turn = eventTurnKey(event)
            completedTurns.add(turn)
            groups.values.filter { it.turnKey == turn }.forEach { group ->
                group.running = false
                group.completedAtMs = event.timestampMs
                group.items.replaceAll { _, item ->
                    if (item.status == NativeActivityItemStatus.RUNNING) item.copy(
                        status = if (event.failed) NativeActivityItemStatus.FAILED else NativeActivityItemStatus.COMPLETED,
                        completedAtMs = event.timestampMs,
                    ) else item
                }
            }
            openCommandIdsByTurn.remove(turn)
            openCommandIdsByText.entries.removeIf { (key, _) -> key.startsWith(turn + "|") }
            assistantPlanParsers.entries.removeIf { (key, _) -> key.startsWith(turn + "|") }
            assistantBoundarySequenceByTurn.remove(turn)
            if (currentKeyByTurn[turn] == currentKey) currentKey = null
            currentKeyByTurn.remove(turn)
            return
        }
        // A standalone boundary should not force a new empty group. It only marks the current
        // exploration as closed; the next groupable event will allocate a stable next segment.
        val turn = eventTurnKey(event)
        val group = currentKeyByTurn[turn]?.let(groups::get)
            ?: currentKey?.let(groups::get)?.takeIf {
                (event.threadId.isBlank() || it.threadId == event.threadId) &&
                    (event.turnId.isNullOrBlank() || it.turnId == event.turnId)
            }
            ?: return
        if (event.threadId.isBlank() || group.threadId == event.threadId) {
            group.activityBoundaryAfter = true
            // Plan/compaction/error boundaries close the current exploration at this exact
            // protocol position. The next groupable event will allocate a new stable segment.
            group.running = false
            group.completedAtMs = event.timestampMs
            currentKey = group.key
            currentKeyByTurn[group.turnKey] = group.key
        }
    }

    private class MutableGroup(
        val key: String,
        val threadId: String,
        val turnKey: String,
        val turnId: String?,
        val segment: Int,
        val reasoning: StringBuilder = StringBuilder(),
        val items: LinkedHashMap<String, NativeActivityItem> = LinkedHashMap(),
        var running: Boolean = true,
        var assistantBoundaryBefore: Boolean = false,
        var assistantBoundaryAfter: Boolean = false,
        var activityBoundaryAfter: Boolean = false,
        val createdAtMs: Long = 0L,
        var completedAtMs: Long = 0L,
    ) {
        fun freeze(): NativeActivityGroup = NativeActivityGroup(
            key = key,
            threadId = threadId,
            turnId = turnId,
            segment = segment,
            reasoning = reasoning.toString(),
            // LinkedHashMap insertion order is the arrival order for legacy (sequence-less)
            // callbacks. Sorting those rows by their synthetic id would turn command:10 before
            // command:2 and make a replay visibly reorder the long task. Sequenced events already
            // arrive through NativeOrderedProtocolEventQueue, so preserve the map order here.
            items = items.values.toList(),
            running = running,
            expandedByDefault = running,
            assistantBoundaryBefore = assistantBoundaryBefore,
            assistantBoundaryAfter = assistantBoundaryAfter,
            activityBoundaryAfter = activityBoundaryAfter,
            createdAtMs = createdAtMs,
            completedAtMs = completedAtMs,
        )
    }
}

/** A protocol-neutral conversation snapshot consumed by both live and history renderers. */
internal data class NativeConversationRenderModel(
    val threadId: String = "",
    val activities: List<NativeActivityGroup> = emptyList(),
    val compactions: List<NativeCompactionItem> = emptyList(),
    val plans: List<NativePlanRenderItem> = emptyList(),
    val subagents: List<NativeSubagentVisual> = emptyList(),
    val assistantMessages: List<NativeConversationTextMessage> = emptyList(),
    val userMessages: List<NativeConversationTextMessage> = emptyList(),
    val errors: List<String> = emptyList(),
    val lastSequence: Long = Long.MIN_VALUE,
)

internal data class NativeConversationTextMessage(
    val id: String,
    val text: String,
    val streaming: Boolean = false,
    val turnId: String? = null,
)

internal data class NativePlanRenderItem(
    val id: String,
    val text: String,
    val streaming: Boolean = false,
    val dedicated: Boolean = false,
)
