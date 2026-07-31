package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSettingsNavigatorTest {
    @Test
    fun childRoutesReturnThroughTheirDeclaredParents() {
        val navigator = NativeSettingsNavigator()
        navigator.navigate(SettingsPage.APPEARANCE)
        navigator.navigate(SettingsPage.THEME)
        navigator.navigate(SettingsPage.CHAT_BACKGROUND)

        assertTrue(navigator.navigateBack())
        assertEquals(SettingsPage.THEME, navigator.page)
        assertTrue(navigator.navigateBack())
        assertEquals(SettingsPage.APPEARANCE, navigator.page)
        assertTrue(navigator.navigateBack())
        assertEquals(SettingsPage.ROOT, navigator.page)
        assertFalse(navigator.navigateBack())
    }

    @Test
    fun editorRouteOwnsItsEditTarget() {
        val navigator = NativeSettingsNavigator()
        navigator.openProfileEditor("profile-id")
        assertEquals(SettingsPage.MODEL_EDITOR, navigator.page)
        assertEquals("profile-id", navigator.editingProfileId)
        navigator.finishProfileEditor()
        assertEquals(SettingsPage.MODEL_CONFIGS, navigator.page)

        navigator.openMcpEditor(null)
        assertEquals(SettingsPage.MCP_EDITOR, navigator.page)
        assertNull(navigator.editingMcpKey)
    }

    @Test
    fun aboutPageIsAFirstLevelSettingsDestination() {
        val navigator = NativeSettingsNavigator()

        navigator.navigate(SettingsPage.ABOUT)

        assertEquals(1, SettingsPage.ABOUT.navigationDepth)
        assertEquals(SettingsPage.ROOT, SettingsPage.ABOUT.previousPage)
        assertTrue(navigator.navigateBack())
        assertEquals(SettingsPage.ROOT, navigator.page)
    }

    @Test
    fun typographyPageReturnsDirectlyToSettingsRoot() {
        val navigator = NativeSettingsNavigator()

        navigator.navigate(SettingsPage.TYPOGRAPHY)

        assertEquals(1, SettingsPage.TYPOGRAPHY.navigationDepth)
        assertEquals(SettingsPage.ROOT, SettingsPage.TYPOGRAPHY.previousPage)
        assertTrue(navigator.navigateBack())
        assertEquals(SettingsPage.ROOT, navigator.page)
    }
}
