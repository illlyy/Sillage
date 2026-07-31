package com.termux.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FcodeToolNavigationTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun externalWebUiIntentPreservesNativeBackStack() {
        val intent = FcodeToolNavigation.webUiIntent(context)

        assertEquals(CodexHomeActivity.ACTION_OPEN_WEBUI, intent.action)
        assertEquals(CodexHomeActivity::class.java.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(CodexHomeActivity.EXTRA_RETURN_TO_NATIVE, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertFalse(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertFalse(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun externalTermuxIntentUsesSameReturnContract() {
        val intent = FcodeToolNavigation.termuxIntent(context)

        assertEquals(CodexHomeActivity.ACTION_OPEN_TERMUX, intent.action)
        assertTrue(intent.getBooleanExtra(CodexHomeActivity.EXTRA_RETURN_TO_NATIVE, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertFalse(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun launcherShortcutStartsAnOrdinaryStandaloneToolEntry() {
        val webUi = FcodeToolNavigation.shortcutIntent(context, terminal = false)
        val termux = FcodeToolNavigation.shortcutIntent(context, terminal = true)

        assertEquals(CodexHomeActivity.ACTION_OPEN_WEBUI, webUi.action)
        assertEquals(CodexHomeActivity.ACTION_OPEN_TERMUX, termux.action)
        assertFalse(webUi.hasExtra(CodexHomeActivity.EXTRA_RETURN_TO_NATIVE))
        assertFalse(termux.hasExtra(CodexHomeActivity.EXTRA_RETURN_TO_NATIVE))
        assertTrue(webUi.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertTrue(termux.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0)
        assertFalse(webUi.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertFalse(termux.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun legacyHostIsSingleTopSoReorderingDoesNotClearNativeActivitiesAboveIt() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, CodexHomeActivity::class.java),
            0,
        )

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }

    @Test
    fun launcherShortcutHelperBuildsDistinctLocalizedEntries() {
        val webUi = FcodeLauncherShortcuts.shortcutInfo(context, terminal = false, language = "zh")
        val termux = FcodeLauncherShortcuts.shortcutInfo(context, terminal = true, language = "en")

        assertEquals("codex_webui", webUi.id)
        assertEquals("Sillage WebUI", webUi.shortLabel.toString())
        assertEquals("打开 Codex WebUI", webUi.longLabel.toString())
        assertEquals(CodexHomeActivity.ACTION_OPEN_WEBUI, webUi.intent?.action)
        assertEquals("codex_termux", termux.id)
        assertEquals("Sillage Termux", termux.shortLabel.toString())
        assertEquals("Open the built-in Codex terminal", termux.longLabel.toString())
        assertEquals(CodexHomeActivity.ACTION_OPEN_TERMUX, termux.intent?.action)
    }
}
