package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSubagentCardTest {

    @Test
    fun statusLabelsAreBilingual() {
        assertEquals("执行中…", subagentCardStatusLabel(NativeActivityItemStatus.RUNNING, "zh"))
        assertEquals("Working…", subagentCardStatusLabel(NativeActivityItemStatus.RUNNING, "en"))
        assertEquals("等待中", subagentCardStatusLabel(NativeActivityItemStatus.WAITING, "zh"))
        assertEquals("Waiting", subagentCardStatusLabel(NativeActivityItemStatus.WAITING, "en"))
        assertEquals("失败", subagentCardStatusLabel(NativeActivityItemStatus.FAILED, "zh"))
        assertEquals("Failed", subagentCardStatusLabel(NativeActivityItemStatus.FAILED, "en"))
        assertEquals("完成", subagentCardStatusLabel(NativeActivityItemStatus.COMPLETED, "zh"))
        assertEquals("Done", subagentCardStatusLabel(NativeActivityItemStatus.COMPLETED, "en"))
    }
}
