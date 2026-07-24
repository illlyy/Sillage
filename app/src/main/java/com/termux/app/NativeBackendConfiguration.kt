package com.termux.app

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runtime-relevant provider state captured together so the UI, catalog writer and native attach
 * cannot accidentally use values from different settings revisions.
 */
internal data class NativeBackendConfiguration(
    val profile: CodexProviderStore.Profile?,
    val routeThroughMihomo: Boolean,
    val fingerprint: String,
    val modelOptions: List<NativeModelOption>,
) {
    val profileId: String get() = profile?.id.orEmpty()
    val defaultModel: String get() = profile?.model.orEmpty()
    val hasRuntimeCredentials: Boolean
        get() = profile?.let { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() } == true
}

/** Pure configuration rules shared by initial startup and settings-return reload checks. */
internal object NativeBackendConfigurationResolver {
    fun resolve(
        profile: CodexProviderStore.Profile?,
        routeApiPreference: Boolean,
        mcpRevision: Long,
        mcpFileFingerprint: String,
    ): NativeBackendConfiguration {
        val routeThroughMihomo = routeApiPreference ||
            (profile?.let { it.proxyEnabled && it.proxyWebUi } == true)
        val fingerprint = NativeProviderSync.configurationFingerprint(profile, routeThroughMihomo) +
            "|mcp=$mcpRevision:$mcpFileFingerprint"
        return NativeBackendConfiguration(
            profile = profile,
            routeThroughMihomo = routeThroughMihomo,
            fingerprint = fingerprint,
            modelOptions = profile?.let(NativeProviderSync::modelOptions).orEmpty(),
        )
    }
}

/**
 * Serializes catalog preparation off the main thread. CodexModelCatalog also synchronizes across
 * Activity instances; this per-owner mutex preserves request order within one chat Activity.
 */
internal class NativeBackendCatalogPreparer(
    private val catalogFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun prepare(models: List<CodexProviderStore.ModelConfig>) {
        withContext(ioDispatcher) {
            mutex.withLock { CodexModelCatalog.writeAtomic(catalogFile, models) }
        }
    }
}
