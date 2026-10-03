package com.termux.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Shield02
import me.rerere.hugeicons.stroke.Tick02
import org.json.JSONObject

/**
 * Tool approval rendered as a card **inside the message flow**, replacing the floating banner.
 *
 * The banner was a sibling of the composer in the bottom column, but it painted underneath the top
 * app bar: its Deny button sat in the occluded strip, so it could not be tapped, and once the
 * request wedged, the whole chat stopped responding until the app was killed. A message-flow card
 * cannot be occluded by chrome and cannot swallow input for the rest of the screen, and it keeps
 * the approval in the conversation where it can be scrolled back to.
 *
 * Stored as `APPROVAL|<base64 {raw, decision}>` on an ACTIVITY message so the resolved state
 * survives a restart alongside the rest of the transcript.
 */

internal const val NATIVE_APPROVAL_PREFIX = "APPROVAL|"

internal fun isNativeApprovalActivity(content: String): Boolean =
    content.startsWith(NATIVE_APPROVAL_PREFIX)

internal fun encodeNativeApproval(raw: String, decision: String): String =
    NATIVE_APPROVAL_PREFIX + NativeBase64.encode(
        JSONObject().put("raw", raw).put("decision", decision).toString().toByteArray(Charsets.UTF_8),
    )

/** `raw to decision`; decision is blank while the request is still pending. */
internal fun decodeNativeApproval(content: String): Pair<String, String>? = runCatching {
    val payload = JSONObject(String(NativeBase64.decode(content.substringAfter('|')), Charsets.UTF_8))
    payload.optString("raw") to payload.optString("decision")
}.getOrNull()

/**
 * Stable identity of an approval request. Codex puts it at the top level, Claude nests it under
 * `params` as `requestId`/`toolUseId`; the tool-use id is preferred because CLI version drift
 * cannot change it. Used both to answer the request and to key its card.
 */
internal fun nativeApprovalRequestId(raw: String): String = runCatching {
    val root = JSONObject(raw)
    root.requestIdOrNull()
        ?: root.optJSONObject("params")?.requestIdOrNull()
        ?: ""
}.getOrDefault("")

private fun JSONObject.requestIdOrNull(): String? =
    listOf("requestId", "toolUseId", "tool_use_id")
        .firstNotNullOfOrNull { key -> opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }

/** Inline approval card. [pending] is false once the request left `pendingApprovalRequest`. */
@Composable
internal fun FcodeApprovalCard(
    raw: String,
    decision: String,
    pending: Boolean,
    onDecision: (String) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val summary = remember(raw, language) { nativeApprovalSummary(raw, language) }
    val accent = if (pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    val container = if (pending) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = if (pending) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp).padding(top = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = onContainer,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = nativeText(language, "查看审批详情", "View approval details"),
                    ) { onOpenDetails() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Shield02, null, Modifier.size(18.dp), tint = onContainer)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        summary.detail,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.8f),
                    )
                }
                resolvedChip(language, decision)
            }
            if (summary.command.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Text(
                        summary.command,
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (pending) {
                HorizontalDivider(color = onContainer.copy(alpha = 0.14f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    TextButton(onClick = { onDecision("decline") }, modifier = Modifier.weight(1f)) {
                        Icon(HugeIcons.Cancel01, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(nativeText(language, "拒绝", "Deny"))
                    }
                    if (summary.allowForSession) {
                        TextButton(onClick = { onDecision("acceptForSession") }, modifier = Modifier.weight(1.4f)) {
                            Icon(HugeIcons.Tick02, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(nativeText(language, "本会话允许", "Allow session"))
                        }
                    }
                    TextButton(onClick = { onDecision("accept") }, modifier = Modifier.weight(1.4f)) {
                        Text(nativeText(language, "允许一次", "Allow once"))
                    }
                }
            } else {
                Spacer(Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun resolvedChip(language: String, decision: String) {
    if (decision.isBlank()) return
    val accepted = decision == "accept" || decision == "acceptForSession"
    val tint = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), contentColor = tint) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (accepted) HugeIcons.Tick02 else HugeIcons.Cancel01, null, Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    decision == "acceptForSession" -> nativeText(language, "本会话允许", "Allowed for session")
                    accepted -> nativeText(language, "已允许", "Allowed")
                    else -> nativeText(language, "已拒绝", "Denied")
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
