package com.termux.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject

/**
 * Live timeline cards for the two Claude tool results that need more than a text row.
 *
 * Claude's `Edit`/`Write` and its image reads produce `fileChange` / `image` items, but the live
 * renderer used to send every non-subagent item to a plain label+detail row — so an edit showed no
 * diff and a read image showed nothing at all. Both cards reuse the renderers the history path
 * already uses ([ToolTextCard] with `diff = true`, and [ImageGroupCard]) so a tool looks identical
 * before and after the turn is sealed.
 *
 * Each falls back to the plain row when the item carries nothing to render (which is how Codex's
 * live `fileChange` items arrive — they keep the payload in the store until history replay).
 */

/** Unified diff for an edit, as a collapsible card. */
@Composable
internal fun NativeToolFileChangeCard(item: NativeActivityItem) {
    if (item.text.isBlank()) {
        NativeToolTimelineContent(item)
        return
    }
    ToolTextCard(title = item.title, detail = item.text, diff = true)
}

/** Thumbnail for an image the model looked at, tappable to open the full-size viewer. */
@Composable
internal fun NativeToolImageCard(item: NativeActivityItem, projectPath: String) {
    // A tool argument is usually relative to the CLI's working directory, which is the project.
    val resolved = remember(item.text, projectPath) { resolveFilePath(item.text, projectPath) }
    if (resolved.isNullOrBlank()) {
        NativeToolTimelineContent(item)
        return
    }
    ImageGroupCard(listOf(JSONObject().put("path", resolved)))
}
