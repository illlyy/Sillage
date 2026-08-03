@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02
import org.json.JSONObject

/** Inline preview of one or more model-produced images; multiple images in a turn are grouped. */
@Composable
internal fun ImageGroupCard(items: List<JSONObject>) {
    val language = LocalNativeLanguage.current
    val imagesKey = items.map { it.toString() }
    val images = remember(imagesKey) {
        items.mapNotNull { item ->
            listOf("path", "imagePath", "url", "file")
                .firstNotNullOfOrNull { key -> item.optString(key).takeIf(String::isNotBlank) }
        }.distinct()
    }
    if (images.isEmpty()) return
    var previewPath by remember { mutableStateOf<String?>(null) }
    val single = images.size == 1
    Surface(
        modifier = Modifier.widthIn(max = if (single) 320.dp else 300.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                images.forEach { path ->
                    AttachmentThumbnail(
                        path,
                        (if (single) Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp) else Modifier.size(88.dp))
                            .clip(RoundedCornerShape(11.dp))
                            .fcodePressClickable(
                                onClickLabel = nativeText(language, "预览图片", "Preview image"),
                            ) { previewPath = path },
                        maxEdge = if (single) 960 else 320,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Image02, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (single) java.io.File(images.first()).name.ifBlank { nativeText(language, "图片", "Image") }
                    else nativeText(language, "${images.size} 张图片", "${images.size} images"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    previewPath?.let { path ->
        AttachmentPreviewDialog(
            attachment = NativeAttachment(java.io.File(path).name.ifBlank { "image" }, path, true),
            onDismiss = { previewPath = null },
        )
    }
}
