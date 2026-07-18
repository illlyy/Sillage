package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProviderSyncTest {
    @Test
    fun modelOptionsReflectLatestCatalogAndReasoningDefaults() {
        val first = CodexProviderStore.ModelConfig("Model A", "model-a", 100_000L).apply {
            supportedReasoningEfforts = "low, high,low"
            defaultReasoningEffort = "high"
        }
        val second = CodexProviderStore.ModelConfig("", "model-b", 50_000L).apply {
            supportedReasoningEfforts = ""
            defaultReasoningEffort = "medium"
        }
        val profile = profile("profile-a", "model-a", listOf(first, second))

        val options = NativeProviderSync.modelOptions(profile)

        assertEquals(listOf("model-a", "model-b"), options.map { it.id })
        assertEquals(listOf("low", "high"), options[0].efforts)
        assertEquals("high", options[0].defaultEffort)
        assertEquals("model-b", options[1].name)
        assertEquals(listOf("none", "minimal", "low", "medium", "high", "xhigh"), options[1].efforts)
    }

    @Test
    fun selectionKeepsValidChatChoiceDuringCatalogOnlyEdit() {
        assertEquals(
            "model-b",
            NativeProviderSync.resolveModelId(
                listOf("model-a", "model-b", "model-c"),
                "MODEL-B",
                "model-a",
                false,
            ),
        )
    }

    @Test
    fun selectionUsesNewConfiguredDefaultWhenSettingsChangedIt() {
        assertTrue(
            NativeProviderSync.shouldPreferConfiguredDefault(
                "profile-a", "model-a", "profile-a", "model-b",
            ),
        )
        assertEquals(
            "model-b",
            NativeProviderSync.resolveModelId(
                listOf("model-a", "model-b"),
                "model-a",
                "model-b",
                true,
            ),
        )
    }

    @Test
    fun selectionFallsBackWhenStoredModelWasDeleted() {
        assertEquals(
            "model-b",
            NativeProviderSync.resolveModelId(
                listOf("model-b", "model-c"),
                "deleted-model",
                "model-b",
                false,
            ),
        )
        assertEquals(
            "model-c",
            NativeProviderSync.resolveModelId(
                listOf("model-c"),
                "deleted-model",
                "also-deleted",
                false,
            ),
        )
    }

    @Test
    fun activeProfileSwitchDoesNotInheritPreviousProfilesDefaultOverride() {
        assertFalse(
            NativeProviderSync.shouldPreferConfiguredDefault(
                "profile-a", "model-a", "profile-b", "model-b",
            ),
        )
    }

    @Test
    fun fingerprintChangesForModelCatalogAndRoutingEdits() {
        val profile = profile(
            "profile-a",
            "model-a",
            listOf(CodexProviderStore.ModelConfig("Model A", "model-a", 100_000L)),
        )
        val original = NativeProviderSync.configurationFingerprint(profile, false)
        assertEquals(64, original.length)
        assertEquals(original, NativeProviderSync.configurationFingerprint(profile, false))

        profile.models.add(CodexProviderStore.ModelConfig("Model B", "model-b", 50_000L))
        val catalogChanged = NativeProviderSync.configurationFingerprint(profile, false)
        assertNotEquals(original, catalogChanged)
        assertNotEquals(catalogChanged, NativeProviderSync.configurationFingerprint(profile, true))
    }

    private fun profile(
        id: String,
        defaultModel: String,
        models: List<CodexProviderStore.ModelConfig>,
    ) = CodexProviderStore.Profile(
        id,
        "Provider",
        "",
        "https://example.com/v1",
        "secret",
        defaultModel,
        "openai_responses",
        models,
    )
}
