package com.termux.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import me.rerere.hugeicons.stroke.Shield02
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Cancel01

/**
 * Permission banner above the composer (pattern: claudecodeui PermissionRequestsBanner).
 * A pending tool approval surfaces as a non-blocking banner with the three decisions inline;
 * tapping the body opens the full [NativeApprovalDialog] for context.
 */
@Composable
internal fun FcodeApprovalBanner(
    raw: String,
    onDecision: (String) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val summary = remember(raw) { nativeApprovalSummary(raw, language) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.86f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
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
                Icon(HugeIcons.Shield02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        summary.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        summary.detail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            if (summary.command.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fcodePressClickable(
                            onClickLabel = nativeText(language, "查看审批详情", "View approval details"),
                        ) { onOpenDetails() }
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                ) {
                    Spacer(Modifier.width(27.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)) {
                        Text(
                            summary.command,
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                TextButton(
                    onClick = { onDecision("decline") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(HugeIcons.Cancel01, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(nativeText(language, "拒绝", "Deny"))
                }
                if (summary.allowForSession) {
                    TextButton(
                        onClick = { onDecision("acceptForSession") },
                        modifier = Modifier.weight(1.4f),
                    ) {
                        Icon(HugeIcons.Tick02, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(nativeText(language, "本会话允许", "Allow session"))
                    }
                }
                TextButton(
                    onClick = { onDecision("accept") },
                    modifier = Modifier.weight(1.4f),
                ) {
                    Text(nativeText(language, "允许一次", "Allow once"))
                }
            }
        }
    }
}
