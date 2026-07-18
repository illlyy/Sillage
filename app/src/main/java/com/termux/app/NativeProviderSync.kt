package com.termux.app

import java.security.MessageDigest

/** Pure provider/model synchronization rules shared by the settings return path and tests. */
internal object NativeProviderSync {
    private const val MISSING_CONFIGURATION = "missing-provider"

    @JvmStatic
    fun modelOptions(profile: CodexProviderStore.Profile): List<NativeModelOption> =
        profile.models.map { model ->
            val efforts = model.supportedReasoningEfforts.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            val defaultEffort = model.defaultReasoningEffort.trim().lowercase()
                .takeIf { it in efforts }
                ?: efforts.firstOrNull { it == "high" }
                ?: efforts.firstOrNull()
                ?: "high"
            NativeModelOption(
                id = model.id,
                name = model.name.ifBlank { model.id },
                efforts = efforts.ifEmpty { listOf(defaultEffort) },
                defaultEffort = defaultEffort,
            )
        }

    @JvmStatic
    fun resolveModelId(
        availableModelIds: List<String>,
        storedModelId: String?,
        configuredDefaultModelId: String?,
        preferConfiguredDefault: Boolean,
    ): String? {
        fun find(candidate: String?): String? {
            val value = candidate.orEmpty()
            if (value.isBlank()) return null
            return availableModelIds.firstOrNull { it.equals(value, ignoreCase = true) }
        }
        return if (preferConfiguredDefault) {
            find(configuredDefaultModelId) ?: find(storedModelId) ?: availableModelIds.firstOrNull()
        } else {
            find(storedModelId) ?: find(configuredDefaultModelId) ?: availableModelIds.firstOrNull()
        }
    }

    @JvmStatic
    fun shouldPreferConfiguredDefault(
        previousProfileId: String?,
        previousDefaultModelId: String?,
        nextProfileId: String?,
        nextDefaultModelId: String?,
    ): Boolean =
        !previousProfileId.isNullOrBlank() &&
            previousProfileId == nextProfileId &&
            !nextDefaultModelId.isNullOrBlank() &&
            !previousDefaultModelId.orEmpty().equals(nextDefaultModelId, ignoreCase = true)

    /**
     * Includes the active profile id and complete model catalog. The digest avoids retaining an
     * API key in Activity/Runtime fingerprint fields while still detecting every runtime-relevant
     * provider edit.
     */
    @JvmStatic
    fun configurationFingerprint(
        profile: CodexProviderStore.Profile?,
        routeThroughMihomo: Boolean,
    ): String {
        if (profile == null) return MISSING_CONFIGURATION
        val serialized = runCatching { profile.json().toString() }
            .getOrElse {
                listOf(profile.id, profile.baseUrl, profile.model, profile.models.joinToString("|") { it.id })
                    .joinToString("\n")
            }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((routeThroughMihomo.toString() + "\n" + serialized).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
