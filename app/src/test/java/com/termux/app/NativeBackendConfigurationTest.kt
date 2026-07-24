package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackendConfigurationTest {
    @Test
    fun missingProfileProducesStableNonRunnableConfiguration() {
        val configuration = NativeBackendConfigurationResolver.resolve(
            profile = null,
            routeApiPreference = false,
            mcpRevision = 7L,
            mcpFileFingerprint = "missing",
        )

        assertEquals("missing-provider|mcp=7:missing", configuration.fingerprint)
        assertEquals("", configuration.profileId)
        assertEquals("", configuration.defaultModel)
        assertTrue(configuration.modelOptions.isEmpty())
        assertFalse(configuration.routeThroughMihomo)
        assertFalse(configuration.hasRuntimeCredentials)
    }

    @Test
    fun providerProxyAndModelCatalogAreResolvedInOneSnapshot() {
        val model = CodexProviderStore.ModelConfig("Model A", "model-a", 100_000L).apply {
            supportedReasoningEfforts = "medium,high"
            defaultReasoningEffort = "high"
        }
        val profile = profile(models = listOf(model)).apply {
            proxyEnabled = true
            proxyWebUi = true
        }

        val configuration = NativeBackendConfigurationResolver.resolve(
            profile = profile,
            routeApiPreference = false,
            mcpRevision = 2L,
            mcpFileFingerprint = "10:20",
        )

        assertEquals("profile", configuration.profileId)
        assertEquals("model-a", configuration.defaultModel)
        assertTrue(configuration.routeThroughMihomo)
        assertTrue(configuration.hasRuntimeCredentials)
        assertEquals(listOf("model-a"), configuration.modelOptions.map { it.id })
        assertEquals(listOf("medium", "high"), configuration.modelOptions.single().efforts)
        assertTrue(configuration.fingerprint.endsWith("|mcp=2:10:20"))
        assertFalse(configuration.fingerprint.contains("secret"))
    }

    @Test
    fun explicitRouteAndEveryMcpRevisionParticipateInFingerprint() {
        val profile = profile(models = emptyList())
        val base = NativeBackendConfigurationResolver.resolve(profile, false, 1L, "a")
        val routed = NativeBackendConfigurationResolver.resolve(profile, true, 1L, "a")
        val revised = NativeBackendConfigurationResolver.resolve(profile, false, 2L, "a")
        val fileChanged = NativeBackendConfigurationResolver.resolve(profile, false, 1L, "b")

        assertFalse(base.routeThroughMihomo)
        assertTrue(routed.routeThroughMihomo)
        assertNotEquals(base.fingerprint, routed.fingerprint)
        assertNotEquals(base.fingerprint, revised.fingerprint)
        assertNotEquals(base.fingerprint, fileChanged.fingerprint)
    }

    private fun profile(
        models: List<CodexProviderStore.ModelConfig>,
    ) = CodexProviderStore.Profile(
        "profile",
        "Provider",
        "",
        "https://example.com/v1",
        "secret",
        "model-a",
        "openai_responses",
        models,
    )
}
