package com.termux.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What the Claude CLI actually is on this device, and whether the system sandbox has blocked it.
 *
 * The install page used to answer only "is it installed". That is not enough to act on: the same
 * app version ends up running a completely different CLI depending on whether the official Bun
 * binary could start, and a phone where it cannot shows no symptom other than a chat that never
 * becomes usable. Naming the distribution, its version, and a remembered sandbox kill turns that
 * into something the user can act on.
 */
internal data class ClaudeInstallInfo(
    val kind: ClaudeInstaller.InstallKind = ClaudeInstaller.InstallKind.NONE,
    val version: String = "",
    val sandboxBlocked: Boolean = false,
    val sandboxExitCode: Int = 0,
) {
    companion object {
        val Unknown = ClaudeInstallInfo()

        /** Reads the on-disk state; a few `stat` calls plus one small manifest read. */
        fun from(context: Context): ClaudeInstallInfo {
            val kind = ClaudeInstaller.installKind()
            if (kind == ClaudeInstaller.InstallKind.NONE) return Unknown
            return ClaudeInstallInfo(
                kind = kind,
                version = ClaudeInstaller.displayVersion(kind),
                sandboxBlocked = ClaudeRuntimeNote.seccompBlocked(context),
                sandboxExitCode = ClaudeRuntimeNote.blockedExitCode(context),
            )
        }
    }
}

@Composable
internal fun ClaudeInstallStatusCard(lang: String, info: ClaudeInstallInfo) {
    if (info.kind == ClaudeInstaller.InstallKind.NONE) return
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (info.sandboxBlocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        },
    ) {
        Column(Modifier.padding(FcodeSpace.lg.dp)) {
            Text(
                tr(
                    lang,
                    "当前 CLI：${distributionLabel(lang, info)}",
                    "Current CLI: ${distributionLabel(lang, info)}",
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                tr(lang, detailZh(lang, info), detailEn(lang, info)),
                Modifier.padding(top = FcodeSpace.xs.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (info.sandboxBlocked) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun distributionLabel(lang: String, info: ClaudeInstallInfo): String {
    val name = when (info.kind) {
        ClaudeInstaller.InstallKind.OFFICIAL_BINARY -> tr(lang, "官方二进制", "official binary")
        ClaudeInstaller.InstallKind.NPM_PACKAGE -> tr(lang, "npm 版", "npm build")
        ClaudeInstaller.InstallKind.NONE -> return tr(lang, "未安装", "not installed")
    }
    return if (info.version.isBlank()) name else "$name · ${info.version}"
}

private fun detailZh(lang: String, info: ClaudeInstallInfo): String = when {
    info.sandboxBlocked -> "上次启动被系统沙箱拦截（退出码 ${info.sandboxExitCode}）：这台设备无法运行官方二进制，" +
        "npm 版是当前的兼容方案，功能与官方版一致。"
    info.kind == ClaudeInstaller.InstallKind.NPM_PACKAGE ->
        "由 Node.js 运行。npm 上已没有能在 Android 上直接执行的版本，因此固定在 ${info.version}，" +
            "比官方二进制旧；官方二进制在部分 Android 上会被系统沙箱拦截。"
    else -> "由 Node.js 运行不受此限制的官方静态二进制。"
}

private fun detailEn(lang: String, info: ClaudeInstallInfo): String = when {
    info.sandboxBlocked -> "The last launch was blocked by the system sandbox (exit code ${info.sandboxExitCode}). " +
        "This device cannot run the official binary; the npm build is the compatible path."
    info.kind == ClaudeInstaller.InstallKind.NPM_PACKAGE ->
        "Runs on Node.js. npm no longer publishes a build Android can execute directly, so this is pinned to " +
            "${info.version}, which is older than the official binary."
    else -> "The official static binary, running on Node.js is not required."
}
