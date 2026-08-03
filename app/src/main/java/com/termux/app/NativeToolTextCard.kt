package com.termux.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Sparkles

/** Generic collapsible tool card. Large payloads stay loaded so the exit shrink stays matched. */
@Composable
internal fun ToolTextCard(title: String, detail: String, diff: Boolean, payloadRef: String = "") {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    var loadedDetail by remember(payloadRef) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded, payloadRef) {
        if (expanded && payloadRef.isNotBlank() && loadedDetail == null) {
            loadedDetail = withContext(Dispatchers.Default) { NativeLargePayloadStore.get(payloadRef) }
        }
    }
    val resolvedDetail = loadedDetail ?: detail
    val displayDetail = remember(resolvedDetail) { NativeUiRenderSafety.sanitizeToolDetail(resolvedDetail) }
    val failed = resolvedDetail.contains("failed", true) || resolvedDetail.contains("error", true) || Regex("exit [1-9]").containsMatchIn(resolvedDetail)
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "收起工具详情", "Collapse tool details")
                        else nativeText(language, "展开工具详情", "Expand tool details"),
                    ) {
                        pauseFollowForToggle()
                        expanded = !expanded
                    }
                    .padding(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(if (diff) HugeIcons.Folder01 else HugeIcons.Sparkles, null, modifier = Modifier.size(17.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(if (failed) "失败" else "完成", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 420.dp) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (diff) {
                            DiffText(displayDetail)
                        } else {
                            SelectionContainer {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    NativeUiRenderSafety.splitPlainText(displayDetail).forEach { chunk ->
                                        Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
