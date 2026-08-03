package com.termux.app

/**
 * Groups consecutive same-type tool items so a dense turn (many reads, many MCP calls)
 * collapses into one expandable container instead of one timeline step per item.
 * Commands are never grouped: they carry independent status rows.
 */
internal sealed interface NativeToolListItem {
    val firstItem: NativeActivityItem

    data class Item(val item: NativeActivityItem) : NativeToolListItem {
        override val firstItem: NativeActivityItem get() = item
    }

    data class Group(val type: NativeActivityItemType, val items: List<NativeActivityItem>) : NativeToolListItem {
        override val firstItem: NativeActivityItem get() = items.first()
        val count: Int get() = items.size
    }
}

internal const val TOOL_GROUP_THRESHOLD = 2

/**
 * Merges consecutive non-command items of the same type into groups of at least
 * [TOOL_GROUP_THRESHOLD]. Runs shorter than the threshold pass through as single items.
 * Grouping is per contiguous run: `A B B A` produces two single `A`s and one `B` group.
 */
internal fun groupConsecutiveTools(items: List<NativeActivityItem>): List<NativeToolListItem> {
    val result = mutableListOf<NativeToolListItem>()
    var runType: NativeActivityItemType? = null
    var run = mutableListOf<NativeActivityItem>()
    fun flushRun() {
        val type = runType
        if (type == null) return
        if (run.size >= TOOL_GROUP_THRESHOLD) {
            result.add(NativeToolListItem.Group(type, run.toList()))
        } else {
            run.forEach { result.add(NativeToolListItem.Item(it)) }
        }
        run = mutableListOf()
        runType = null
    }
    for (item in items) {
        if (item.type == NativeActivityItemType.COMMAND) {
            flushRun()
            result.add(NativeToolListItem.Item(item))
            continue
        }
        if (runType != item.type) flushRun()
        runType = item.type
        run.add(item)
    }
    flushRun()
    return result
}

/** Group-level status color priority: failed > running > waiting > completed. */
internal fun NativeToolListItem.Group.aggregateStatus(): NativeActivityItemStatus {
    if (items.any { it.status == NativeActivityItemStatus.FAILED }) return NativeActivityItemStatus.FAILED
    if (items.any { it.status == NativeActivityItemStatus.RUNNING }) return NativeActivityItemStatus.RUNNING
    if (items.any { it.status == NativeActivityItemStatus.WAITING }) return NativeActivityItemStatus.WAITING
    return NativeActivityItemStatus.COMPLETED
}
