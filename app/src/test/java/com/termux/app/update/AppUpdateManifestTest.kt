package com.termux.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateManifestTest {
    @Test
    fun parsesValidManifest() {
        val manifest = AppUpdateManifest.parse(
            """
            {
              "versionCode": 1003,
              "versionName": "0.118.4",
              "minSdk": 24,
              "force": false,
              "apkUrl": "https://github.com/illlyy/Fcode/releases/download/v0.118.4/Fcode-v0.118.4.apk",
              "sha256": "${"ab".repeat(32)}",
              "changelog": "Fixes and improvements"
            }
            """.trimIndent(),
        )

        assertEquals(1003L, manifest.versionCode)
        assertEquals("0.118.4", manifest.versionName)
        assertEquals("Fcode-0.118.4.apk", manifest.apkFileName())
        assertFalse(manifest.force)
    }

    @Test
    fun persistsQueuedManifestWithoutLosingFields() {
        val original = AppUpdateManifest(
            versionCode = 1004,
            versionName = "0.118.5-beta.1",
            minSdk = 24,
            force = true,
            apkUrl = "https://github.com/illlyy/Fcode/releases/download/v0.118.5-beta.1/Fcode.apk",
            sha256 = "01".repeat(32),
            changelog = "Beta",
        )

        assertEquals(original, AppUpdateManifest.parse(original.toJson()))
    }

    @Test
    fun rejectsInsecureDownloadUrl() {
        val json = """
            {
              "versionCode": 1003,
              "versionName": "0.118.4",
              "apkUrl": "http://example.com/Fcode.apk",
              "sha256": "${"ab".repeat(32)}"
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateManifest.parse(json)
        }
    }

    @Test
    fun rejectsMalformedChecksum() {
        val json = """
            {
              "versionCode": 1003,
              "versionName": "0.118.4",
              "apkUrl": "https://github.com/illlyy/Fcode/releases/download/v0.118.4/Fcode.apk",
              "sha256": "not-a-checksum"
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateManifest.parse(json)
        }
    }
}
