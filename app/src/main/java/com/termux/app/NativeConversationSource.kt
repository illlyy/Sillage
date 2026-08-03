package com.termux.app

/**
 * Per-backend conversation list source. Each backend exposes one [NativeConversationSource]; the
 * drawer asks the registry for the active backend's source and never sees another backend's
 * records. Adding a future backend = add a [NativeBackendType] value, implement this interface,
 * and register it in [NativeConversationSourceRegistry] — no new if/else branches elsewhere.
 */
internal interface NativeConversationSource {
    /**
     * Produces the backend's conversation list and publishes it via
     * [CodexChatActivity.applyConversationSnapshot]. Runs any slow I/O off the main thread.
     * The [generation] guard in the apply rejects a late result from a superseded refresh, and the
     * [backend] is re-checked at apply time so a refresh that read a stale pref can never publish.
     */
    fun refresh(activity: CodexChatActivity, backend: NativeBackendType, generation: Int)
}

internal object NativeConversationSourceRegistry {
    fun sourceFor(backend: NativeBackendType): NativeConversationSource = when (backend) {
        NativeBackendType.CLAUDE -> ClaudeConversationSource
        NativeBackendType.CODEX -> CodexConversationSource
    }
}

/** Claude conversations come from a recursive scan of the `.claude/projects` transcripts tree. */
internal object ClaudeConversationSource : NativeConversationSource {
    override fun refresh(activity: CodexChatActivity, backend: NativeBackendType, generation: Int) {
        Thread {
            val conversations = ClaudeHistoryAdapter.listConversations(activity)
            activity.applyConversationSnapshot(backend, generation, conversations)
        }.apply { name = "ClaudeConversationScan" }.start()
    }
}

/** Codex conversations come from the overlay task store (SharedPreferences registry). */
internal object CodexConversationSource : NativeConversationSource {
    override fun refresh(activity: CodexChatActivity, backend: NativeBackendType, generation: Int) {
        val favorites = activity.favoriteThreadIds()
        val snapshot = CodexTaskStore.current(activity)
        val registeredProjectPaths = NativeDrawerProjectStore.registered(activity).map(NativeDrawerProject::path)
        fun visibleProjectPath(path: String): String = resolveNativeConversationProjectMigration(
            cachedResolvedPath = path,
            freshlyResolvedPath = null,
            registeredProjectPaths = registeredProjectPaths,
        ).registeredProjectPath.orEmpty()

        // Apply the enriched list exactly once. The old immediate+enriched double-apply mutated
        // the same observable twice per refresh and caused a drawer flicker even within one backend.
        Thread {
            val missingProjectIds = snapshot.asSequence()
                .filter { !it.projectAssignmentKnown && !activity.conversationProjectCache.containsKey(it.threadId) }
                .map { it.threadId }
                .toList()
            val resolvedProjects = if (missingProjectIds.isEmpty()) emptyMap()
                else CodexAppServerBridge.resolveConversationProjects(missingProjectIds)
            snapshot.asSequence().filterNot { it.projectAssignmentKnown }.forEach { task ->
                val migration = resolveNativeConversationProjectMigration(
                    cachedResolvedPath = activity.conversationProjectCache[task.threadId],
                    freshlyResolvedPath = resolvedProjects[task.threadId],
                    registeredProjectPaths = registeredProjectPaths,
                )
                migration.resolvedPath?.let { activity.conversationProjectCache[task.threadId] = it }
                migration.registeredProjectPath?.let { project ->
                    CodexTaskStore.assignProject(activity, task.threadId, project)
                }
            }
            val enriched = snapshot.mapNotNull { task ->
                var title = activity.conversationTitleCache[task.threadId] ?: task.title
                val fallbackTitle = title.startsWith("Codex 任务")
                if (fallbackTitle) {
                    val resolvedTitle = CodexAppServerBridgeHistory.resolveConversationTitle(task.threadId)
                    if (resolvedTitle.isNotBlank()) {
                        title = resolvedTitle
                        activity.conversationTitleCache[task.threadId] = resolvedTitle
                    }
                }
                val project = if (task.projectAssignmentKnown) visibleProjectPath(task.projectPath)
                    else visibleProjectPath(activity.conversationProjectCache[task.threadId].orEmpty())
                if (fallbackTitle && title.startsWith("Codex 任务")) null
                else NativeConversation(
                    task.threadId, title, task.state, project, task.threadId in favorites,
                    activity.taskAttention(task.threadId, task.state),
                )
            }.sortedWith(compareByDescending<NativeConversation> { it.state == CodexTaskStore.RUNNING }
                .thenByDescending { activity.attentionPriority(it.attention) })
            activity.applyConversationSnapshot(backend, generation, enriched)
        }.apply { name = "CodexConversationMetadata" }.start()
    }
}
