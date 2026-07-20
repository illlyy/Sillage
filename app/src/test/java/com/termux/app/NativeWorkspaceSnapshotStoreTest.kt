package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWorkspaceSnapshotStoreTest {
    @Test
    fun snapshotMetadataRoundTrips() {
        val source = listOf(NativeWorkspaceSnapshot(
            id = "snapshot-1",
            threadId = "thread-1",
            projectPath = "/repo",
            commit = "abc123",
            ref = "refs/fcode/checkpoints/thread-1/snapshot-1",
            label = "Before restore",
            createdAt = 1234L,
            automatic = true,
        ))
        val decoded = NativeWorkspaceSnapshotCodec.decode(NativeWorkspaceSnapshotCodec.encode(source))
        assertEquals(source, decoded)
        assertTrue(decoded.single().automatic)
    }

    @Test
    fun buildsSelectiveRestorePreview() {
        val preview = org.json.JSONObject(NativeWorkspaceSnapshotPreview.build(
            "s1",
            "current",
            "M\tapp/A.kt\nA\tapp/New.kt\nR100\told.txt\tnew.txt",
            "3\t1\tapp/A.kt\n5\t0\tapp/New.kt",
        ))
        val entries = preview.getJSONArray("entries")
        assertEquals(3, entries.length())
        assertTrue(entries.getJSONObject(0).getBoolean("restorable"))
        assertEquals(false, entries.getJSONObject(1).getBoolean("restorable"))
        assertEquals(false, entries.getJSONObject(2).getBoolean("restorable"))
    }
}
