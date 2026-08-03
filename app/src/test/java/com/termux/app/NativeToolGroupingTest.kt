package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolGroupingTest {

    private fun item(
        id: String,
        type: NativeActivityItemType,
        status: NativeActivityItemStatus = NativeActivityItemStatus.COMPLETED,
    ) = NativeActivityItem(
        id = id,
        type = type,
        title = id,
        status = status,
    )

    @Test
    fun singleItemNeverGroups() {
        val result = groupConsecutiveTools(listOf(item("a", NativeActivityItemType.WEB_SEARCH)))
        assertEquals(1, result.size)
        assertEquals("a", (result[0] as NativeToolListItem.Item).item.id)
    }

    @Test
    fun twoConsecutiveSameTypeFormGroup() {
        val result = groupConsecutiveTools(
            listOf(
                item("a", NativeActivityItemType.WEB_SEARCH),
                item("b", NativeActivityItemType.WEB_SEARCH),
            ),
        )
        assertEquals(1, result.size)
        val group = result[0] as NativeToolListItem.Group
        assertEquals(NativeActivityItemType.WEB_SEARCH, group.type)
        assertEquals(listOf("a", "b"), group.items.map { it.id })
    }

    @Test
    fun commandsNeverGroupAndBreakRuns() {
        val result = groupConsecutiveTools(
            listOf(
                item("a", NativeActivityItemType.FILE_CHANGE),
                item("c1", NativeActivityItemType.COMMAND),
                item("b", NativeActivityItemType.FILE_CHANGE),
            ),
        )
        assertEquals(3, result.size)
        assertTrue(result[0] is NativeToolListItem.Item)
        assertTrue(result[1] is NativeToolListItem.Item)
        assertEquals("c1", (result[1] as NativeToolListItem.Item).item.id)
        assertTrue(result[2] is NativeToolListItem.Item)
    }

    @Test
    fun interleavedTypesProduceSeparateRuns() {
        val result = groupConsecutiveTools(
            listOf(
                item("a", NativeActivityItemType.WEB_SEARCH),
                item("b", NativeActivityItemType.TOOL),
                item("c", NativeActivityItemType.TOOL),
                item("d", NativeActivityItemType.WEB_SEARCH),
            ),
        )
        // a (single), b+c (group), d (single)
        assertEquals(3, result.size)
        assertTrue(result[0] is NativeToolListItem.Item)
        assertEquals(NativeActivityItemType.TOOL, (result[1] as NativeToolListItem.Group).type)
        assertTrue(result[2] is NativeToolListItem.Item)
    }

    @Test
    fun mixedSingleAndGroupedRuns() {
        val result = groupConsecutiveTools(
            listOf(
                item("a", NativeActivityItemType.WEB_SEARCH),
                item("b", NativeActivityItemType.FILE_CHANGE),
                item("c", NativeActivityItemType.FILE_CHANGE),
                item("d", NativeActivityItemType.FILE_CHANGE),
            ),
        )
        assertEquals(2, result.size)
        assertTrue(result[0] is NativeToolListItem.Item)
        assertEquals(3, (result[1] as NativeToolListItem.Group).count)
    }

    @Test
    fun groupAggregatesStatusByPriorityFailedRunningWaitingCompleted() {
        fun group(vararg statuses: NativeActivityItemStatus): NativeToolListItem.Group {
            val items = statuses.mapIndexed { index, s -> item("i$index", NativeActivityItemType.TOOL, s) }
            return NativeToolListItem.Group(NativeActivityItemType.TOOL, items)
        }
        assertEquals(
            NativeActivityItemStatus.FAILED,
            group(NativeActivityItemStatus.RUNNING, NativeActivityItemStatus.FAILED).aggregateStatus(),
        )
        assertEquals(
            NativeActivityItemStatus.RUNNING,
            group(NativeActivityItemStatus.WAITING, NativeActivityItemStatus.RUNNING).aggregateStatus(),
        )
        assertEquals(
            NativeActivityItemStatus.WAITING,
            group(NativeActivityItemStatus.WAITING, NativeActivityItemStatus.COMPLETED).aggregateStatus(),
        )
        assertEquals(
            NativeActivityItemStatus.COMPLETED,
            group(NativeActivityItemStatus.COMPLETED, NativeActivityItemStatus.COMPLETED).aggregateStatus(),
        )
    }

    @Test
    fun emptyInputYieldsEmptyOutput() {
        assertEquals(emptyList<NativeToolListItem>(), groupConsecutiveTools(emptyList()))
    }
}
