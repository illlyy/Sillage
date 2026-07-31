package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NativeCommandOutputStoreTest {
    @Before
    public void setUp() {
        NativeCommandOutputStore.clear();
    }

    @After
    public void tearDown() {
        NativeCommandOutputStore.clear();
    }

    @Test
    public void compactPayloadKeepsLargeOutputOutOfUiJson() throws Exception {
        String output = repeated("line-0123456789\n", 1_000); // 16 KB
        JSONObject source = new JSONObject()
            .put("id", "command-1")
            .put("command", "rg TODO app")
            .put("cwd", "/workspace")
            .put("aggregatedOutput", output)
            .put("exitCode", 0)
            .put("status", "completed");

        JSONObject compact = new JSONObject(NativeCommandOutputStore.compactCommandItem(source));
        String reference = compact.getString(NativeCommandOutputStore.OUTPUT_REF);

        assertFalse(compact.has("aggregatedOutput"));
        assertFalse(compact.has("stdout"));
        assertEquals(output.length(), compact.getInt(NativeCommandOutputStore.OUTPUT_CHARS));
        assertTrue(compact.toString().length() < 2_000);
        assertEquals(output, NativeCommandOutputStore.get(reference));
    }

    @Test
    public void separatesStdoutAndStderrAndPreservesMetadata() throws Exception {
        String stdout = repeated("ok\n", 25_000);
        String stderr = repeated("warning\n", 12_500);
        JSONObject source = new JSONObject()
            .put("command", "./gradlew test")
            .put("stdout", stdout)
            .put("stderr", stderr)
            .put("durationMs", 2500);

        JSONObject compact = new JSONObject(NativeCommandOutputStore.compactCommandItem(source));

        assertEquals(stdout, NativeCommandOutputStore.get(compact.getString(NativeCommandOutputStore.OUTPUT_REF)));
        assertEquals(stderr, NativeCommandOutputStore.get(compact.getString(NativeCommandOutputStore.STDERR_REF)));
        assertEquals(2500, compact.getInt("durationMs"));
        assertTrue(compact.getString(NativeCommandOutputStore.OUTPUT_PREVIEW).length() < 850);
        assertTrue(compact.getString(NativeCommandOutputStore.STDERR_PREVIEW).length() < 850);
    }

    @Test
    public void supportsOneMegabyteOutputWithoutEmbeddingItInMetadata() throws Exception {
        String output = repeated("x", 1024 * 1024);
        JSONObject compact = new JSONObject(NativeCommandOutputStore.compactCommandItem(
            new JSONObject().put("command", "large-output").put("output", output)));

        assertTrue(compact.toString().length() < 2_000);
        assertEquals(output, NativeCommandOutputStore.get(compact.getString(NativeCommandOutputStore.OUTPUT_REF)));
        assertEquals(output.length(), NativeCommandOutputStore.cachedCharacterCount());
    }

    @Test
    public void repeatedCompactionReusesExistingReference() throws Exception {
        JSONObject first = new JSONObject(NativeCommandOutputStore.compactCommandItem(
            new JSONObject().put("command", "pwd").put("aggregatedOutput", "workspace")));
        String reference = first.getString(NativeCommandOutputStore.OUTPUT_REF);
        JSONObject second = new JSONObject(NativeCommandOutputStore.compactCommandItem(first, "duplicate fallback"));

        assertEquals(reference, second.getString(NativeCommandOutputStore.OUTPUT_REF));
        assertEquals(1, NativeCommandOutputStore.cachedEntryCount());
        assertEquals("workspace", NativeCommandOutputStore.get(reference));
    }

    @Test
    public void resolvesFailedStatusFromAuthoritativeCommandEvidence() throws Exception {
        assertEquals("failed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject().put("status", "failed").put("exitCode", 0), "ok"));
        assertEquals("failed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject().put("status", "completed").put("exit_code", 126), ""));
        assertEquals("failed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject().put("status", "completed"),
            "exec_command failed: Permission denied"));
        assertEquals("failed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject(), "Exit code: 2\nWall time: 0.1 seconds\nOutput:\nmissing"));
    }

    @Test
    public void ordinaryErrorTextDoesNotBecomeAFailedCommand() throws Exception {
        assertEquals("completed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject(), "printed error: expected test fixture"));
        assertEquals("completed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject(), "documentation example: exec_command failed: Permission denied"));
        assertEquals("completed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject().put("exitCode", 0),
            "exec_command failed appears in the command's own successful output"));
        assertEquals("completed", NativeCommandOutputStore.resolvedCommandStatus(
            new JSONObject(),
            "Exit code: 0\nWall time: 0.1 seconds\nOutput:\nwarning: Permission denied"));
    }

    @Test
    public void compactionRepairsLegacySyntheticCompletedStatus() throws Exception {
        JSONObject compact = new JSONObject(NativeCommandOutputStore.compactCommandItem(
            new JSONObject()
                .put("status", "completed")
                .put("command", "restricted-command")
                .put("aggregatedOutput", "exec_command failed: Permission denied")));

        assertEquals("failed", compact.getString("status"));
    }

    @Test
    public void cacheEvictsOldStreamsWithinMemoryBudget() throws Exception {
        String output = repeated("z", 1024 * 1024);
        String firstReference = "";
        for (int index = 0; index < 10; index++) {
            JSONObject compact = new JSONObject(NativeCommandOutputStore.compactCommandItem(
                new JSONObject().put("id", "command-" + index).put("output", output + index)));
            if (index == 0) firstReference = compact.getString(NativeCommandOutputStore.OUTPUT_REF);
        }

        assertTrue(NativeCommandOutputStore.cachedCharacterCount() <= 8 * 1024 * 1024);
        assertTrue(NativeCommandOutputStore.cachedEntryCount() <= 8);
        assertEquals(null, NativeCommandOutputStore.get(firstReference));
    }

    @Test
    public void livePreviewIsBoundedAndKeepsNewestText() {
        String value = repeated("a", 20_000) + "THE-END";
        String preview = NativeCommandOutputStore.livePreview(value);

        assertTrue(preview.length() < 2_000);
        assertTrue(preview.endsWith("THE-END"));
        assertNotEquals(value, preview);
    }

    private static String repeated(String value, int count) {
        return value.repeat(count);
    }
}
