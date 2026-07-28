package com.termux.app.update

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateBrowserIntentTest {
    @Test
    fun updateDownloadOpensTheDirectApkUrlAsBrowsableContent() {
        val manifest = AppUpdateManifest(
            versionCode = 1005,
            versionName = "0.2.1",
            minSdk = 24,
            force = false,
            apkUrl = "https://github.com/illlyy/Fcode/releases/download/v0.2.1/Fcode-v0.2.1.apk",
            sha256 = "a".repeat(64),
            changelog = "Browser download",
        )

        val intent = AppUpdateManager.browserDownloadIntent(manifest)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(manifest.apkUrl, intent.dataString)
        assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
    }
}
