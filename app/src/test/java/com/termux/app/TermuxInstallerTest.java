package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class TermuxInstallerTest {
    @Test
    public void patchesBootstrapPrefixAndHomeWithoutChangingEntrySize() {
        String source = "#!/data/data/com.termux/files/usr/bin/bash\n" +
            "HOME=/data/data/com.termux/files/home\n";
        byte[] input = source.getBytes(StandardCharsets.US_ASCII);
        byte[] output = TermuxInstaller.patchBootstrapPaths(input);
        String patched = new String(output, StandardCharsets.US_ASCII);

        assertEquals(input.length, output.length);
        assertTrue(patched.contains("/data/data/com.ilyop.codex/xusr/bin/bash"));
        assertTrue(patched.contains("HOME=/data/data/com.ilyop.codex/xhome"));
        assertFalse(patched.contains("/data/data/com.termux/files"));
    }

    @Test
    public void patchesAbsoluteSymlinkTargets() {
        assertEquals(
            "/data/data/com.ilyop.codex/xusr/lib/libc.so",
            TermuxInstaller.patchBootstrapPath("/data/data/com.termux/files/usr/lib/libc.so")
        );
    }
}
