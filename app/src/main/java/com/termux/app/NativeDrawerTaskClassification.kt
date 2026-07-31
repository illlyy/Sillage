package com.termux.app

import java.util.Locale

/**
 * Drawer sections derived from persisted conversations and explicitly registered projects.
 *
 * A conversation's workspace path is only treated as a project assignment when it matches a
 * visible registered project. This keeps ordinary/projectless tasks discoverable and prevents an
 * arbitrary cwd from silently becoming a project in the drawer.
 */
internal data class NativeDrawerTaskClassification(
    val standaloneTasks: List<NativeConversation>,
    val visibleProjects: List<String>,
    val tasksByProject: Map<String, List<NativeConversation>>,
)

/**
 * Result of inspecting an old task whose project assignment has not been persisted yet.
 *
 * [resolvedPath] deliberately keeps the raw session cwd even when no project currently matches.
 * A null [registeredProjectPath] means the task must remain unknown so a later project
 * registration (or a restart followed by another history scan) can still migrate it.
 */
internal data class NativeConversationProjectMigration(
    val resolvedPath: String?,
    val registeredProjectPath: String?,
)

internal fun resolveNativeConversationProjectMigration(
    cachedResolvedPath: String?,
    freshlyResolvedPath: String?,
    registeredProjectPaths: Collection<String>,
): NativeConversationProjectMigration {
    val resolvedPath = cachedResolvedPath?.takeIf(String::isNotBlank)
        ?: freshlyResolvedPath?.takeIf(String::isNotBlank)
    val resolvedIdentity = resolvedPath?.let(::nativeDrawerPathIdentity)
    val registeredProjectPath = resolvedIdentity?.let { identity ->
        registeredProjectPaths.firstOrNull { nativeDrawerPathIdentity(it) == identity }
    }
    return NativeConversationProjectMigration(resolvedPath, registeredProjectPath)
}

internal fun classifyNativeDrawerTasks(
    conversations: List<NativeConversation>,
    registeredProjectPaths: Collection<String>,
    hiddenProjectPaths: Collection<String>,
): NativeDrawerTaskClassification {
    val hiddenKeys = hiddenProjectPaths.mapNotNullTo(linkedSetOf(), ::nativeDrawerPathIdentity)
    val projectPathByKey = linkedMapOf<String, String>()

    registeredProjectPaths.forEach { rawPath ->
        val path = rawPath.trim()
        val key = nativeDrawerPathIdentity(path) ?: return@forEach
        if (key !in hiddenKeys) projectPathByKey.putIfAbsent(key, path)
    }

    val tasksByProject = linkedMapOf<String, MutableList<NativeConversation>>()
    projectPathByKey.values.forEach { path -> tasksByProject[path] = mutableListOf() }
    val standaloneTasks = ArrayList<NativeConversation>()

    conversations.forEach { conversation ->
        val projectPath = nativeDrawerPathIdentity(conversation.projectPath)
            ?.let(projectPathByKey::get)
        if (projectPath == null) {
            standaloneTasks.add(conversation)
        } else {
            tasksByProject.getValue(projectPath).add(conversation)
        }
    }

    return NativeDrawerTaskClassification(
        standaloneTasks = standaloneTasks,
        visibleProjects = projectPathByKey.values.toList(),
        tasksByProject = tasksByProject.mapValuesTo(linkedMapOf()) { (_, tasks) -> tasks.toList() },
    )
}

/**
 * Pure lexical path identity used by drawer grouping.
 *
 * Filesystem-backed stores should still persist canonical paths when available. This normalizer is
 * intentionally free of filesystem access so classification stays deterministic in unit tests and
 * while removable Android storage is temporarily unavailable. Windows drive/UNC paths compare
 * case-insensitively; Android/Unix paths retain case sensitivity.
 */
internal fun nativeDrawerPathIdentity(rawPath: String): String? {
    var path = rawPath.trim().replace('\\', '/')
    if (path.isEmpty()) return null

    val drivePath = path.length >= 2 && path[0].isLetter() && path[1] == ':'
    val uncPath = path.startsWith("//")
    val unixAbsolute = !uncPath && path.startsWith('/')
    val caseInsensitive = drivePath || uncPath

    val prefix: String
    val absolute: Boolean
    when {
        drivePath -> {
            prefix = path.substring(0, 2)
            path = path.substring(2)
            absolute = path.startsWith('/')
            path = path.trimStart('/')
        }
        uncPath -> {
            prefix = "//"
            absolute = true
            path = path.trimStart('/')
        }
        unixAbsolute -> {
            prefix = "/"
            absolute = true
            path = path.trimStart('/')
        }
        else -> {
            prefix = ""
            absolute = false
        }
    }

    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> when {
                segments.isNotEmpty() && segments.last() != ".." -> segments.removeLast()
                !absolute -> segments.addLast(segment)
            }
            else -> segments.addLast(segment)
        }
    }

    val body = segments.joinToString("/")
    val normalized = when {
        drivePath && absolute -> if (body.isEmpty()) "$prefix/" else "$prefix/$body"
        drivePath -> prefix + body
        prefix == "//" -> if (body.isEmpty()) prefix else prefix + body
        prefix == "/" -> if (body.isEmpty()) prefix else prefix + body
        body.isEmpty() -> "."
        else -> body
    }
    return if (caseInsensitive) normalized.lowercase(Locale.ROOT) else normalized
}
