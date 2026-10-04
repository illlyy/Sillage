package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The compaction divider can now state what the compaction saved. The figures come from the
 * backend as a late, separate report, so they have to survive the timeline encode/decode round
 * trip -- otherwise they would vanish on the next snapshot and never come back on replay.
 */
@RunWith(RobolectricTestRunner::class)
class NativeCompactionMetricsTest {

    private fun item(
        pre: Long = 0L,
        post: Long = 0L,
        dropped: Long = 0L,
        durationMs: Long = 0L,
    ) = NativeCompactionItem(
        id = "c1",
        threadId = "t1",
        source = NativeCompactionSource.MANUAL,
        status = NativeCompactionStatus.COMPLETED,
        preTokens = pre,
        postTokens = post,
        droppedTokens = dropped,
        durationMs = durationMs,
    )

    @Test
    fun metricsSurviveTheTimelineRoundTrip() {
        val encoded = NativeHistoryAdapter.encodeCompaction(
            item(pre = 30_832, post = 5_481, dropped = 25_351, durationMs = 14_668),
        )
        val decoded = NativeHistoryAdapter.decodeCompaction(encoded, "t1")
        assertEquals(30_832L, decoded?.preTokens)
        assertEquals(5_481L, decoded?.postTokens)
        assertEquals(25_351L, decoded?.droppedTokens)
        assertEquals(14_668L, decoded?.durationMs)
    }

    @Test
    fun olderRecordsWithoutFiguresStillDecode() {
        // Everything written before this feature existed has no such keys; it must read as "the
        // backend said nothing" rather than failing to decode and losing the whole divider.
        val legacy = NativeCompactionItem(
            id = "legacy",
            threadId = "t1",
            status = NativeCompactionStatus.COMPLETED,
        )
        val decoded = NativeHistoryAdapter.decodeCompaction(
            NativeHistoryAdapter.encodeCompaction(legacy),
            "t1",
        )
        assertEquals("legacy", decoded?.id)
        assertEquals(0L, decoded?.preTokens)
        assertEquals(0L, decoded?.postTokens)
    }

    @Test
    fun aLaterRecordWithFiguresWinsOverOneWithout() {
        // The lifecycle and the metrics arrive as two separate writes, so whichever half carries
        // the numbers must not be dropped when the halves are merged back together.
        val requestOnly = item(pre = 30_832)
        val serverHalf = item(pre = 0, post = 5_481, dropped = 25_351)
        val merged = NativeHistoryAdapter.mergeCompactionTimeline(listOf(requestOnly, serverHalf))
        assertEquals(1, merged.size)
        assertEquals(30_832L, merged.first().preTokens)
        assertEquals(5_481L, merged.first().postTokens)
        assertEquals(25_351L, merged.first().droppedTokens)
    }

    @Test
    fun preservedSegmentCompactionReportsOnlyThePreTotal() {
        // Claude omits `postTokens` when it kept a segment of the history verbatim, so the UI has
        // to be able to say "compressed from N" with nothing else. This must not read as a saving.
        val decoded = NativeHistoryAdapter.decodeCompaction(
            NativeHistoryAdapter.encodeCompaction(item(pre = 47_318, durationMs = 42_224)),
            "t1",
        )
        assertEquals(47_318L, decoded?.preTokens)
        assertEquals(0L, decoded?.postTokens)
        assertTrue((decoded?.droppedTokens ?: 0L) <= 0L)
    }

    @Test
    fun theJournalKeepsTheFiguresAcrossARestart() {
        // The journal is what a relaunched process reads, not the timeline. A compaction whose
        // saving only lived in the timeline would lose its numbers on the next cold start.
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("compaction-metrics-test", android.content.Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        val store = NativeCompactionJournalStore(preferences)
        store.record(item(pre = 30_832, post = 5_481, dropped = 25_351, durationMs = 14_668))

        val restored = NativeCompactionJournalStore(preferences).load("t1")
        assertEquals(1, restored.size)
        assertEquals(30_832L, restored.single().preTokens)
        assertEquals(5_481L, restored.single().postTokens)
        assertEquals(25_351L, restored.single().droppedTokens)
        assertEquals(14_668L, restored.single().durationMs)
    }
}
