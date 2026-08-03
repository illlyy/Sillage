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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.TransactionHistory

/** Pretty-prints JSON model output as a copyable card (pattern: claudecodeui JSON detection). */
@Composable
internal fun FcodeJsonCard(text: String) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val pretty = remember(text) {
        runCatching {
            val trimmed = text.trim()
            when (trimmed.first()) {
                '{' -> org.json.JSONObject(trimmed).toString(2)
                else -> org.json.JSONArray(trimmed).toString(2)
            }
        }.getOrDefault(text)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    HugeIcons.TransactionHistory,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    nativeText(language, "JSON", "JSON"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(pretty)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        HugeIcons.Copy01,
                        nativeText(language, "复制 JSON", "Copy JSON"),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SelectionContainer {
                Text(
                    pretty,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
