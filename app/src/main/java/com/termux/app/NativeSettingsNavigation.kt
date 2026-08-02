package com.termux.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings navigation lives outside the Activity so route ownership and edit targets cannot drift
 * into contradictory combinations while AnimatedContent or predictive back composes two pages.
 */
internal enum class SettingsPage {
    ROOT, APPEARANCE, THEME, CHAT_APPEARANCE, CHAT_BACKGROUND, TYPOGRAPHY,
    MCP, MCP_EDITOR, SKILLS,
    OVERLAY, DEVELOPMENT_TOOLS, MODEL_CONFIGS, MODEL_EDITOR, CLAUDE_EDITOR, WEB_UI, PROXY,
    DEVELOPER, ABOUT,
}

internal val SettingsPage.navigationDepth: Int
    get() = when (this) {
        SettingsPage.ROOT -> 0
        SettingsPage.APPEARANCE, SettingsPage.MCP, SettingsPage.SKILLS,
        SettingsPage.OVERLAY, SettingsPage.DEVELOPMENT_TOOLS, SettingsPage.MODEL_CONFIGS,
        SettingsPage.WEB_UI, SettingsPage.PROXY, SettingsPage.DEVELOPER, SettingsPage.TYPOGRAPHY,
        SettingsPage.ABOUT -> 1
        SettingsPage.THEME, SettingsPage.CHAT_APPEARANCE,
        SettingsPage.MODEL_EDITOR, SettingsPage.MCP_EDITOR, SettingsPage.CLAUDE_EDITOR -> 2
        SettingsPage.CHAT_BACKGROUND -> 3
    }

internal val SettingsPage.previousPage: SettingsPage?
    get() = when (this) {
        SettingsPage.ROOT -> null
        SettingsPage.MODEL_EDITOR, SettingsPage.CLAUDE_EDITOR -> SettingsPage.MODEL_CONFIGS
        SettingsPage.MCP_EDITOR -> SettingsPage.MCP
        SettingsPage.CHAT_BACKGROUND -> SettingsPage.THEME
        SettingsPage.THEME, SettingsPage.CHAT_APPEARANCE -> SettingsPage.APPEARANCE
        SettingsPage.APPEARANCE, SettingsPage.MCP, SettingsPage.SKILLS,
        SettingsPage.OVERLAY, SettingsPage.DEVELOPMENT_TOOLS, SettingsPage.MODEL_CONFIGS,
        SettingsPage.WEB_UI, SettingsPage.PROXY, SettingsPage.DEVELOPER, SettingsPage.TYPOGRAPHY,
        SettingsPage.ABOUT -> SettingsPage.ROOT
    }

@Stable
internal class NativeSettingsNavigator {
    var page by mutableStateOf(SettingsPage.ROOT)
        private set

    var editingProfileId by mutableStateOf<String?>(null)
        private set

    var editingClaudeProfileId by mutableStateOf<String?>(null)
        private set

    var editingMcpKey by mutableStateOf<String?>(null)
        private set

    fun navigate(destination: SettingsPage) {
        page = destination
    }

    /** Returns false when the Activity itself is the next back destination. */
    fun navigateBack(): Boolean {
        val destination = page.previousPage ?: return false
        page = destination
        return true
    }

    fun openProfileEditor(profileId: String?) {
        editingProfileId = profileId
        page = SettingsPage.MODEL_EDITOR
    }

    fun finishProfileEditor() {
        page = SettingsPage.MODEL_CONFIGS
    }

    fun openClaudeEditor(profileId: String?) {
        editingClaudeProfileId = profileId
        page = SettingsPage.CLAUDE_EDITOR
    }

    fun finishClaudeEditor() {
        page = SettingsPage.MODEL_CONFIGS
    }

    fun openMcpEditor(serverKey: String?) {
        editingMcpKey = serverKey
        page = SettingsPage.MCP_EDITOR
    }

    fun finishMcpEditor() {
        page = SettingsPage.MCP
    }
}
