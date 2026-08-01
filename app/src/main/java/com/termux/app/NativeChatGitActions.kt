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


    internal fun CodexChatActivity.configuredProjectPath(): String {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val configured = prefs.getString("custom_project_root", "").orEmpty()
        return configured.takeIf { prefs.getBoolean("custom_project_root_enabled", true) && File(it).isDirectory } ?: ""
    }

    internal fun CodexChatActivity.performGitAction(action: String, value: String) {
        val project = chatState.projectPath
        val routeThreadId = currentThreadId
        if (project.isBlank()) {
            chatState.gitError = nativeText(nativeLanguage, "当前对话没有绑定项目目录", "This conversation has no project directory")
            return
        }
        if (action == "diff") {
            if (value.isBlank() || value in chatState.gitDiffLoading) return
            chatState.gitDiffLoading.add(value)
            Thread({
                val unstaged = runGit(project, listOf("diff", "--no-ext-diff", "--unified=3", "--", value))
                val staged = runGit(project, listOf("diff", "--cached", "--no-ext-diff", "--unified=3", "--", value))
                var unstagedText = unstaged.second.takeIf { unstaged.first == 0 }.orEmpty()
                if (unstagedText.isBlank() && staged.second.isBlank()) {
                    val untracked = runGit(project, listOf("diff", "--no-index", "--unified=3", "--", "/dev/null", value))
                    if (untracked.first in setOf(0, 1)) unstagedText = untracked.second
                }
                val snapshot = NativeGitWorkflow.diffSnapshot(
                    unstagedText,
                    staged.second.takeIf { staged.first == 0 }.orEmpty(),
                    listOfNotNull(
                        unstaged.second.takeIf { unstaged.first != 0 },
                        staged.second.takeIf { staged.first != 0 },
                    ).joinToString("\n").trim(),
                )
                runOnUiThread {
                    if (currentThreadId != routeThreadId || chatState.projectPath != project) return@runOnUiThread
                    chatState.gitDiffs[value] = snapshot
                    chatState.gitDiffLoading.remove(value)
                }
            }, "NativeGitDiff").start()
            return
        }
        if (chatState.gitBusy) return
        chatState.gitBusy = true
        chatState.gitError = ""
        chatState.gitNotice = ""
        Thread({
            val result = when (action) {
                "refresh" -> 0 to ""
                "fetch" -> runGit(project, listOf("fetch", "--prune", "--all"), mapOf("GIT_TERMINAL_PROMPT" to "0"))
                "push" -> pushCurrentBranch(project)
                "stage" -> runGit(project, listOf("add", "--", value))
                "unstage" -> {
                    val restore = runGit(project, listOf("restore", "--staged", "--", value))
                    if (restore.first == 0) restore else runGit(project, listOf("reset", "HEAD", "--", value))
                }
                "commit" -> if (value.trim().isBlank()) -1 to nativeText(nativeLanguage, "请输入提交说明", "Enter a commit message")
                    else runGit(project, listOf("commit", "-m", value.trim()))
                else -> -1 to nativeText(nativeLanguage, "未知 Git 操作", "Unknown Git action")
            }
            val status = loadGitSnapshot(project)
            runOnUiThread {
                if (currentThreadId != routeThreadId || chatState.projectPath != project) return@runOnUiThread
                chatState.gitSnapshot = status
                chatState.gitBusy = false
                chatState.gitDiffs.clear()
                chatState.gitDiffLoading.clear()
                if (result.first == 0) {
                    chatState.gitError = ""
                    chatState.gitNotice = when (action) {
                        "stage" -> nativeText(nativeLanguage, "已暂存 $value", "Staged $value")
                        "unstage" -> nativeText(nativeLanguage, "已取消暂存 $value", "Unstaged $value")
                        "commit" -> result.second.lineSequence().firstOrNull().orEmpty().ifBlank { nativeText(nativeLanguage, "提交成功", "Commit created") }
                        "fetch" -> nativeText(nativeLanguage, "远程引用已更新", "Remote references updated")
                        "push" -> result.second.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().ifBlank { nativeText(nativeLanguage, "分支已推送", "Branch pushed") }
                        else -> ""
                    }
                } else {
                    chatState.gitError = result.second.trim()
                    chatState.gitNotice = ""
                }
            }
        }, "NativeGitWorkflow").start()
    }

    internal fun CodexChatActivity.loadGitSnapshot(project: String): String {
        val git = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git")
        if (!git.isFile) return NativeGitWorkflow.unavailable(project, nativeText(nativeLanguage, "尚未安装 Git", "Git is not installed"))
        val result = runGit(project, listOf("-c", "core.quotepath=false", "status", "--porcelain=v1", "--branch", "--untracked-files=all"))
        if (result.first != 0) return NativeGitWorkflow.unavailable(project, result.second.trim().ifBlank { nativeText(nativeLanguage, "这里不是 Git 仓库", "This is not a Git repository") })
        val head = runGit(project, listOf("rev-parse", "--verify", "HEAD"))
        val remotes = runGit(project, listOf("remote", "-v"))
        val history = if (head.first == 0) runGit(project, listOf(
            "log", "-n", "30", "--date-order",
            "--pretty=format:%H%x1f%h%x1f%an%x1f%at%x1f%s%x1f%D%x1e",
        )) else 0 to ""
        return NativeGitRemoteProtocol.enrichSnapshot(
            NativeGitWorkflow.parse(project, result.second),
            NativeGitRemoteProtocol.parseRemotes(remotes.second.takeIf { remotes.first == 0 }.orEmpty()),
            NativeGitRemoteProtocol.parseHistory(history.second.takeIf { history.first == 0 }.orEmpty()),
            head.first == 0,
        )
    }

    internal fun CodexChatActivity.pushCurrentBranch(project: String): Pair<Int, String> {
        val branch = runGit(project, listOf("symbolic-ref", "--quiet", "--short", "HEAD"))
        val branchName = branch.second.trim()
        if (branch.first != 0 || branchName.isBlank()) return -1 to nativeText(nativeLanguage, "分离 HEAD 不能直接推送", "A detached HEAD cannot be pushed directly")
        val remotesResult = runGit(project, listOf("remote", "-v"))
        val remotes = NativeGitRemoteProtocol.parseRemotes(remotesResult.second.takeIf { remotesResult.first == 0 }.orEmpty())
        val upstream = runGit(project, listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"))
        val upstreamName = upstream.second.trim().takeIf { upstream.first == 0 }.orEmpty()
        val remoteName = upstreamName.substringBefore('/', "").ifBlank {
            remotes.firstOrNull { it.name == "origin" }?.name ?: remotes.firstOrNull()?.name.orEmpty()
        }
        if (remoteName.isBlank()) return -1 to nativeText(nativeLanguage, "仓库没有可推送的远程地址", "The repository has no remote to push to")
        val arguments = if (upstreamName.isBlank()) {
            listOf("push", "-u", remoteName, branchName)
        } else {
            val remoteBranch = upstreamName.substringAfter('/', branchName)
            listOf("push", remoteName, "HEAD:refs/heads/$remoteBranch")
        }
        return runGit(project, arguments, mapOf("GIT_TERMINAL_PROMPT" to "0"))
    }

    internal fun CodexChatActivity.runGit(project: String, arguments: List<String>): Pair<Int, String> = runCatching {
        runGit(project, arguments, emptyMap())
    }.getOrElse { -1 to (it.message ?: it.javaClass.simpleName) }

    internal fun CodexChatActivity.runGit(project: String, arguments: List<String>, environment: Map<String, String>): Pair<Int, String> = runCatching {
        val directory = File(project).canonicalFile
        require(directory.isDirectory) { "Project directory is unavailable" }
        val command = mutableListOf(File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git").absolutePath)
        command.addAll(arguments)
        val process = ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
            .also { builder ->
                builder.environment()["PATH"] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin:/system/xbin"
                builder.environment()["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
                builder.environment()["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
                builder.environment()["TMPDIR"] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
                builder.environment().putAll(environment)
            }
            .start()
        val collected = StringBuilder()
        val outputReader = Thread({
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    synchronized(collected) {
                        val remaining = 240_000 - collected.length
                        if (remaining > 0) collected.append(buffer, 0, minOf(count, remaining))
                    }
                }
            }
        }, "NativeGitOutput").apply { isDaemon = true; start() }
        val completed = waitForProcess(process, 90_000L)
        if (!completed) {
            stopProcess(process)
            outputReader.join(2_000L)
            return@runCatching -1 to nativeText(nativeLanguage, "Git 操作超时", "Git operation timed out")
        }
        outputReader.join(2_000L)
        val output = synchronized(collected) { collected.toString() }
        process.exitValue() to output
    }.getOrElse { -1 to (it.message ?: it.javaClass.simpleName) }

    internal fun CodexChatActivity.waitForProcess(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0L) * 1_000_000L
        while (true) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0L) return false
                try {
                    Thread.sleep(minOf(50L, (remainingNanos / 1_000_000L).coerceAtLeast(1L)))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
    }

    internal fun CodexChatActivity.stopProcess(process: Process) {
        runCatching { process.destroy() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { process.destroyForcibly() }
        }
    }

    internal fun CodexChatActivity.captureWorkspaceCommit(project: String): Pair<String, String> {
        val checkpointDir = File(cacheDir, "native-workspace-checkpoints").apply { mkdirs() }
        val indexFile = File.createTempFile("index-", ".tmp", checkpointDir).apply { delete() }
        val environment = mapOf(
            "GIT_INDEX_FILE" to indexFile.absolutePath,
            "GIT_AUTHOR_NAME" to "Sillage Checkpoint",
            "GIT_AUTHOR_EMAIL" to "checkpoint@sillage.local",
            "GIT_COMMITTER_NAME" to "Sillage Checkpoint",
            "GIT_COMMITTER_EMAIL" to "checkpoint@sillage.local",
        )
        return try {
            val head = runGit(project, listOf("rev-parse", "--verify", "HEAD"))
            val headCommit = head.second.trim().takeIf { head.first == 0 && it.matches(Regex("[0-9a-fA-F]{40,64}")) }.orEmpty()
            val readTree = if (headCommit.isBlank()) runGit(project, listOf("read-tree", "--empty"), environment)
                else runGit(project, listOf("read-tree", headCommit), environment)
            if (readTree.first != 0) return "" to readTree.second
            val add = runGit(project, listOf("-c", "core.quotepath=false", "add", "-A", "--", "."), environment)
            if (add.first != 0) return "" to add.second
            val tree = runGit(project, listOf("write-tree"), environment)
            if (tree.first != 0) return "" to tree.second
            val commitArgs = mutableListOf("commit-tree", tree.second.trim(), "-m", "Sillage workspace checkpoint")
            if (headCommit.isNotBlank()) commitArgs.addAll(listOf("-p", headCommit))
            val commit = runGit(project, commitArgs, environment)
            if (commit.first != 0) "" to commit.second else commit.second.trim() to ""
        } finally {
            indexFile.delete()
            File(indexFile.absolutePath + ".lock").delete()
        }
    }

    internal fun CodexChatActivity.createWorkspaceSnapshot(
        threadId: String,
        project: String,
        label: String,
        automatic: Boolean,
    ): Pair<NativeWorkspaceSnapshot?, String> {
        val captured = captureWorkspaceCommit(project)
        if (captured.first.isBlank()) return null to captured.second.ifBlank { nativeText(nativeLanguage, "\u65e0\u6cd5\u521b\u5efa\u5de5\u4f5c\u533a\u5feb\u7167", "Unable to create workspace snapshot") }
        val id = UUID.randomUUID().toString()
        val safeThread = threadId.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val ref = "refs/fcode/checkpoints/$safeThread/$id"
        val updateRef = runGit(project, listOf("update-ref", ref, captured.first))
        if (updateRef.first != 0) return null to updateRef.second
        val item = NativeWorkspaceSnapshot(id, threadId, project, captured.first, ref, label, System.currentTimeMillis(), automatic)
        val previous = NativeWorkspaceSnapshotStore.load(this, threadId)
        val updated = NativeWorkspaceSnapshotStore.add(this, item)
        previous.filter { old -> updated.none { it.id == old.id } && old.ref.isNotBlank() }
            .forEach { old -> runGit(project, listOf("update-ref", "-d", old.ref)) }
        return item to ""
    }

    internal fun CodexChatActivity.performSnapshotAction(action: String, rawValue: String) {
        val threadId = currentThreadId ?: return
        val project = chatState.projectPath
        if (project.isBlank()) {
            chatState.workspaceSnapshotError = nativeText(nativeLanguage, "\u5f53\u524d\u5bf9\u8bdd\u6ca1\u6709\u7ed1\u5b9a\u9879\u76ee\u76ee\u5f55", "This conversation has no project directory")
            return
        }
        if (action == "refresh") {
            chatState.workspaceSnapshots.clear()
            chatState.workspaceSnapshots.addAll(NativeWorkspaceSnapshotStore.load(this, threadId))
            return
        }
        if (action == "clearPreview") {
            chatState.workspaceSnapshotPreview = ""
            chatState.workspaceSnapshotDiffs.clear()
            chatState.workspaceSnapshotDiffLoading.clear()
            return
        }
        if (action == "diff") {
            val request = runCatching { JSONObject(rawValue) }.getOrNull() ?: return
            val snapshotId = request.optString("snapshotId")
            val path = request.optString("path")
            val preview = runCatching { JSONObject(chatState.workspaceSnapshotPreview) }.getOrNull() ?: return
            val snapshot = chatState.workspaceSnapshots.firstOrNull { it.id == snapshotId } ?: return
            val currentCommit = preview.optString("currentCommit")
            val key = "$snapshotId|$path"
            if (path.isBlank() || currentCommit.isBlank() || key in chatState.workspaceSnapshotDiffLoading) return
            chatState.workspaceSnapshotDiffLoading.add(key)
            Thread({
                val diff = runGit(project, listOf("diff", "--no-ext-diff", "--unified=3", snapshot.commit, currentCommit, "--", path))
                runOnUiThread {
                    if (currentThreadId != threadId || chatState.projectPath != project) return@runOnUiThread
                    chatState.workspaceSnapshotDiffs[key] = if (diff.first == 0) diff.second else diff.second.ifBlank { "Unable to load diff" }
                    chatState.workspaceSnapshotDiffLoading.remove(key)
                }
            }, "NativeSnapshotDiff").start()
            return
        }
        if (chatState.workspaceSnapshotBusy) return
        chatState.workspaceSnapshotBusy = true
        chatState.workspaceSnapshotError = ""
        chatState.workspaceSnapshotNotice = ""
        Thread({
            var error = ""
            var notice = ""
            var preview = chatState.workspaceSnapshotPreview
            val failure = runCatching { when (action) {
                "create" -> {
                    val label = rawValue.trim().ifBlank { nativeText(nativeLanguage, "\u624b\u52a8\u5feb\u7167", "Manual snapshot") }
                    val result = createWorkspaceSnapshot(threadId, project, label, false)
                    error = result.second
                    if (result.first != null) notice = nativeText(nativeLanguage, "\u5df2\u521b\u5efa\u975e\u7834\u574f\u6027\u5feb\u7167", "Non-destructive snapshot created")
                }
                "preview" -> {
                    val snapshot = NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == rawValue }
                    if (snapshot == null) error = nativeText(nativeLanguage, "\u5feb\u7167\u5df2\u4e0d\u5b58\u5728", "Snapshot no longer exists")
                    else if (!runCatching { File(snapshot.projectPath).canonicalPath == File(project).canonicalPath }.getOrDefault(false)) {
                        error = nativeText(nativeLanguage, "\u5feb\u7167\u5c5e\u4e8e\u53e6\u4e00\u4e2a\u9879\u76ee\u76ee\u5f55", "This snapshot belongs to another project directory")
                    }
                    else {
                        val current = captureWorkspaceCommit(project)
                        if (current.first.isBlank()) error = current.second
                        else {
                            val names = runGit(project, listOf("diff", "--name-status", "--find-renames", snapshot.commit, current.first, "--"))
                            val stats = runGit(project, listOf("diff", "--numstat", snapshot.commit, current.first, "--"))
                            if (names.first != 0) error = names.second
                            else preview = NativeWorkspaceSnapshotPreview.build(snapshot.id, current.first, names.second, stats.second)
                        }
                    }
                }
                "delete" -> {
                    val snapshot = NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == rawValue }
                    if (snapshot != null && snapshot.ref.isNotBlank()) {
                        val deleted = runGit(snapshot.projectPath.ifBlank { project }, listOf("update-ref", "-d", snapshot.ref))
                        if (deleted.first != 0) error = deleted.second
                    }
                    if (error.isBlank()) {
                        NativeWorkspaceSnapshotStore.remove(this, threadId, rawValue)
                        preview = ""
                        notice = nativeText(nativeLanguage, "\u5feb\u7167\u5df2\u5220\u9664", "Snapshot deleted")
                    }
                }
                "restore" -> {
                    val request = runCatching { JSONObject(rawValue) }.getOrNull()
                    val snapshot = request?.optString("snapshotId")?.let { id -> NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == id } }
                    val path = request?.optString("path").orEmpty()
                    if (snapshot == null || path.isBlank()) error = nativeText(nativeLanguage, "\u65e0\u6548\u7684\u6062\u590d\u8bf7\u6c42", "Invalid restore request")
                    else if (!runCatching { File(snapshot.projectPath).canonicalPath == File(project).canonicalPath }.getOrDefault(false)) {
                        error = nativeText(nativeLanguage, "\u4e0d\u80fd\u5c06\u5176\u4ed6\u9879\u76ee\u7684\u5feb\u7167\u6062\u590d\u5230\u5f53\u524d\u5de5\u4f5c\u533a", "A snapshot from another project cannot be restored here")
                    }
                    else {
                        val safety = createWorkspaceSnapshot(threadId, project, nativeText(nativeLanguage, "\u6062\u590d $path \u524d\u7684\u81ea\u52a8\u5907\u4efd", "Automatic backup before restoring $path"), true)
                        if (safety.first == null) error = safety.second
                        else {
                            val restored = runGit(project, listOf("restore", "--source=${snapshot.commit}", "--worktree", "--", path))
                            if (restored.first != 0) error = restored.second
                            else {
                                preview = ""
                                notice = nativeText(nativeLanguage, "\u5df2\u6062\u590d $path\uff0c\u5e76\u521b\u5efa\u4e86\u6062\u590d\u524d\u5907\u4efd", "Restored $path and created a pre-restore backup")
                            }
                        }
                    }
                }
                else -> error = nativeText(nativeLanguage, "\u672a\u77e5\u5feb\u7167\u64cd\u4f5c", "Unknown snapshot action")
            } }.exceptionOrNull()
            if (failure != null) error = failure.message ?: failure.javaClass.simpleName
            val snapshots = NativeWorkspaceSnapshotStore.load(this, threadId)
            runOnUiThread {
                if (currentThreadId != threadId || chatState.projectPath != project) return@runOnUiThread
                chatState.workspaceSnapshotBusy = false
                chatState.workspaceSnapshotError = error.trim()
                chatState.workspaceSnapshotNotice = notice
                chatState.workspaceSnapshotPreview = preview
                chatState.workspaceSnapshotDiffs.clear()
                chatState.workspaceSnapshotDiffLoading.clear()
                chatState.workspaceSnapshots.clear()
                chatState.workspaceSnapshots.addAll(snapshots)
                if (action == "restore" && error.isBlank()) performGitAction("refresh", "")
            }
        }, "NativeWorkspaceSnapshot").start()
    }

    internal fun CodexChatActivity.loadWorktrees(project: String): Pair<List<NativeWorktreeEntry>, String> {
        val listed = runGit(project, listOf("worktree", "list", "--porcelain"))
        if (listed.first != 0) return emptyList<NativeWorktreeEntry>() to listed.second
        val entries = NativeWorktreeProtocol.parse(listed.second).map { entry ->
            val status = runGit(entry.path, listOf("status", "--porcelain=v1", "--untracked-files=all"))
            entry.copy(dirty = status.first != 0 || status.second.isNotBlank())
        }
        return entries to ""
    }

    internal fun CodexChatActivity.samePath(left: String, right: String): Boolean = runCatching {
        File(left).canonicalPath == File(right).canonicalPath
    }.getOrDefault(false)

    internal fun CodexChatActivity.pathInside(path: String, directory: String): Boolean = runCatching {
        val child = File(path).canonicalFile
        val parent = File(directory).canonicalFile
        child == parent || child.path.startsWith(parent.path.trimEnd(File.separatorChar) + File.separator)
    }.getOrDefault(false)

    internal fun CodexChatActivity.performWorktreeAction(action: String, rawValue: String) {
        val project = chatState.projectPath
        val routeThread = currentThreadId
        val referencedProjects = chatState.conversations.map { it.projectPath }.filter { it.isNotBlank() }
        if (project.isBlank()) {
            chatState.worktreeError = nativeText(nativeLanguage, "\u5f53\u524d\u5bf9\u8bdd\u6ca1\u6709\u7ed1\u5b9a\u9879\u76ee\u76ee\u5f55", "This conversation has no project directory")
            return
        }
        if (action == "clearPreview") {
            chatState.worktreeMergePreview = ""
            return
        }
        if (action == "clearPrHandoff") {
            chatState.worktreePrHandoff = ""
            return
        }
        if (action == "open") {
            val target = rawValue.trim()
            if (target.isNotBlank() && File(target).isDirectory) newConversationAtProject(target)
            return
        }
        if (chatState.worktreeBusy) return
        chatState.worktreeBusy = true
        chatState.worktreeError = ""
        chatState.worktreeNotice = ""
        Thread({
            var error = ""
            var notice = ""
            var preview = chatState.worktreeMergePreview
            var prHandoff = chatState.worktreePrHandoff
            var openProject = ""
            val failure = runCatching {
                val initial = loadWorktrees(project)
                if (initial.second.isNotBlank()) {
                    error = initial.second
                    return@runCatching
                }
                val entries = initial.first
                val main = entries.firstOrNull()
                when (action) {
                    "refresh" -> Unit
                    "create" -> {
                        if (main == null) { error = nativeText(nativeLanguage, "\u65e0\u6cd5\u786e\u5b9a Git \u4e3b\u5de5\u4f5c\u533a", "Unable to resolve the main Git worktree"); return@runCatching }
                        val branch = NativeWorktreeProtocol.normalizeBranch(rawValue, System.currentTimeMillis())
                        val validBranch = runGit(main.path, listOf("check-ref-format", "--branch", branch))
                        if (validBranch.first != 0) { error = validBranch.second; return@runCatching }
                        val repoName = File(main.path).name.ifBlank { "repository" }
                        val base = File(TermuxConstants.TERMUX_HOME_DIR, ".fcode/worktrees/$repoName").apply { mkdirs() }
                        var target = File(base, NativeWorktreeProtocol.pathSlug(branch))
                        if (target.exists()) target = File(base, NativeWorktreeProtocol.pathSlug(branch) + "-" + System.currentTimeMillis())
                        val exists = runGit(main.path, listOf("show-ref", "--verify", "--quiet", "refs/heads/$branch")).first == 0
                        val command = if (exists) listOf("worktree", "add", target.absolutePath, branch)
                            else listOf("worktree", "add", "-b", branch, target.absolutePath, "HEAD")
                        val created = runGit(main.path, command)
                        if (created.first != 0) error = created.second
                        else {
                            openProject = target.absolutePath
                            notice = nativeText(nativeLanguage, "\u5df2\u521b\u5efa\u9694\u79bb\u5de5\u4f5c\u533a $branch", "Created isolated worktree $branch")
                        }
                    }
                    "previewMerge" -> {
                        val source = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || source == null || samePath(source.path, main.path) || source.branch.isBlank() || main.branch.isBlank()) {
                            error = nativeText(nativeLanguage, "\u65e0\u6cd5\u9884\u89c8\u8be5 worktree \u7684\u5408\u5e76", "Unable to preview merge for this worktree")
                        } else if (main.dirty || source.dirty) {
                            error = nativeText(nativeLanguage, "\u5408\u5e76\u524d\u4e3b\u5de5\u4f5c\u533a\u548c\u9694\u79bb\u5de5\u4f5c\u533a\u90fd\u5fc5\u987b\u5e72\u51c0", "Both the main and isolated worktrees must be clean before merging")
                        } else {
                            val commits = runGit(main.path, listOf("log", "--oneline", "${main.branch}..${source.branch}", "--"))
                            val stat = runGit(main.path, listOf("diff", "--stat", "${main.branch}...${source.branch}", "--"))
                            preview = JSONObject()
                                .put("sourcePath", source.path).put("sourceBranch", source.branch)
                                .put("targetPath", main.path).put("targetBranch", main.branch)
                                .put("commits", commits.second).put("stat", stat.second)
                                .put("canMerge", commits.first == 0 && commits.second.isNotBlank())
                                .toString()
                        }
                    }
                    "preparePr" -> {
                        val source = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || source == null || samePath(source.path, main.path) || source.branch.isBlank() || main.branch.isBlank()) {
                            error = nativeText(nativeLanguage, "无法为该 worktree 生成 PR 交接信息", "Unable to generate a PR handoff for this worktree")
                        } else {
                            val delta = runGit(main.path, listOf("rev-list", "--left-right", "--count", "${main.branch}...${source.branch}"))
                            if (delta.first != 0) {
                                error = delta.second
                            } else {
                                val counts = delta.second.trim().split(Regex("\\s+")).mapNotNull(String::toIntOrNull)
                                val behind = counts.getOrElse(0) { 0 }
                                val ahead = counts.getOrElse(1) { 0 }
                                val commits = runGit(main.path, listOf("log", "--reverse", "--pretty=format:- %h %s", "${main.branch}..${source.branch}", "--"))
                                val stat = runGit(main.path, listOf("diff", "--stat", "${main.branch}...${source.branch}", "--"))
                                val files = runGit(main.path, listOf("-c", "core.quotepath=false", "diff", "--name-status", "${main.branch}...${source.branch}", "--"))
                                val remotesResult = runGit(source.path, listOf("remote", "-v"))
                                val remotes = NativeGitRemoteProtocol.parseRemotes(remotesResult.second.takeIf { remotesResult.first == 0 }.orEmpty())
                                val upstream = runGit(source.path, listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"))
                                val upstreamRemote = upstream.second.trim().takeIf { upstream.first == 0 }.orEmpty().substringBefore('/', "")
                                val remote = remotes.firstOrNull { it.name == upstreamRemote }
                                    ?: remotes.firstOrNull { it.name == "origin" }
                                    ?: remotes.firstOrNull()
                                prHandoff = NativeGitRemoteProtocol.buildHandoff(
                                    source.path, source.branch, main.branch,
                                    remote?.name.orEmpty(), remote?.pushUrl.orEmpty(),
                                    ahead, behind,
                                    commits.second.takeIf { commits.first == 0 }.orEmpty(),
                                    stat.second.takeIf { stat.first == 0 }.orEmpty(),
                                    files.second.takeIf { files.first == 0 }.orEmpty(),
                                    source.dirty,
                                )
                                preview = ""
                                notice = nativeText(nativeLanguage, "已生成 ${source.branch} 的 PR 交接信息", "PR handoff generated for ${source.branch}")
                            }
                        }
                    }
                    "merge" -> {
                        val request = runCatching { JSONObject(rawValue) }.getOrNull()
                        val sourcePath = request?.optString("sourcePath").orEmpty()
                        val targetPath = request?.optString("targetPath").orEmpty()
                        val source = entries.firstOrNull { samePath(it.path, sourcePath) }
                        val target = entries.firstOrNull { samePath(it.path, targetPath) }
                        if (source == null || target == null || source.branch.isBlank() || source.dirty || target.dirty) {
                            error = nativeText(nativeLanguage, "worktree \u72b6\u6001\u5df2\u53d8\u5316\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5", "Worktree state changed; refresh and try again")
                        } else {
                            val threadId = routeThread.orEmpty().ifBlank { "worktree-merge" }
                            val safety = createWorkspaceSnapshot(threadId, target.path, nativeText(nativeLanguage, "\u5408\u5e76 ${source.branch} \u524d\u7684\u81ea\u52a8\u5907\u4efd", "Automatic backup before merging ${source.branch}"), true)
                            if (safety.first == null) error = safety.second
                            else {
                                val merged = runGit(target.path, listOf("merge", "--no-ff", "--no-edit", source.branch))
                                if (merged.first != 0) {
                                    runGit(target.path, listOf("merge", "--abort"))
                                    error = merged.second + "\n" + nativeText(nativeLanguage, "\u5df2\u81ea\u52a8\u53d6\u6d88\u51b2\u7a81\u5408\u5e76\uff0c\u4e3b\u5de5\u4f5c\u533a\u5df2\u6062\u590d\u3002", "The conflicted merge was aborted and the main worktree was restored.")
                                } else {
                                    preview = ""
                                    notice = nativeText(nativeLanguage, "\u5df2\u5c06 ${source.branch} \u5408\u5e76\u5230 ${target.branch}", "Merged ${source.branch} into ${target.branch}")
                                }
                            }
                        }
                    }
                    "remove" -> {
                        val target = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || target == null || samePath(target.path, main.path) || pathInside(project, target.path)) {
                            error = nativeText(nativeLanguage, "\u4e0d\u80fd\u79fb\u9664\u4e3b\u5de5\u4f5c\u533a\u6216\u5f53\u524d\u5bf9\u8bdd\u6b63\u5728\u4f7f\u7528\u7684 worktree", "The main or currently active worktree cannot be removed")
                        } else if (referencedProjects.any { pathInside(it, target.path) }) {
                            error = nativeText(nativeLanguage, "\u8fd8\u6709\u5bf9\u8bdd\u7ed1\u5b9a\u5230\u8be5 worktree\uff0c\u8bf7\u5148\u5220\u9664\u6216\u8fc1\u79fb\u8fd9\u4e9b\u5bf9\u8bdd", "Conversations still reference this worktree; remove or migrate them first")
                        } else if (target.dirty || target.locked) {
                            error = nativeText(nativeLanguage, "\u53ea\u80fd\u79fb\u9664\u5e72\u51c0\u4e14\u672a\u9501\u5b9a\u7684 worktree", "Only clean, unlocked worktrees can be removed")
                        } else {
                            val removed = runGit(main.path, listOf("worktree", "remove", target.path))
                            if (removed.first != 0) error = removed.second
                            else notice = nativeText(nativeLanguage, "worktree \u5df2\u79fb\u9664\uff0c\u5206\u652f ${target.branch} \u4ecd\u4fdd\u7559", "Worktree removed; branch ${target.branch} was kept")
                        }
                    }
                    else -> error = nativeText(nativeLanguage, "\u672a\u77e5 worktree \u64cd\u4f5c", "Unknown worktree action")
                }
            }.exceptionOrNull()
            if (failure != null) error = failure.message ?: failure.javaClass.simpleName
            val refreshed = loadWorktrees(project)
            runOnUiThread {
                if (currentThreadId != routeThread || chatState.projectPath != project) return@runOnUiThread
                chatState.worktreeBusy = false
                chatState.worktreeError = error.trim()
                chatState.worktreeNotice = notice
                chatState.worktreeMergePreview = preview
                chatState.worktreePrHandoff = prHandoff
                chatState.worktrees.clear()
                chatState.worktrees.addAll(refreshed.first)
                if (openProject.isNotBlank() && error.isBlank()) newConversationAtProject(openProject)
                if (action == "merge" && error.isBlank()) performGitAction("refresh", "")
            }
        }, "NativeWorktreeWorkflow").start()
    }

