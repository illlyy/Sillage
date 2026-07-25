package com.termux.app

internal enum class NativeSubagentStatus { WORKING, WAITING, DONE, FAILED }

/** Stable, renderer-neutral visual state for one subagent chip. */
internal data class NativeSubagentVisual(
    val seed: String,
    val agentThreadId: String = "",
    val callId: String = "",
    val aliases: Set<String> = emptySet(),
    val name: String = "Subagent",
    val status: NativeSubagentStatus = NativeSubagentStatus.WAITING,
    val colorIndex: Int = 0,
    val icon: String = "subagent",
    val lightColorArgb: Int = NativeSubagentVisualPalette.light(0),
    val darkColorArgb: Int = NativeSubagentVisualPalette.dark(0),
) {
    val key: String get() = seed
}

internal data class NativeSubagentInlineModel(
    val visible: List<NativeSubagentVisual>,
    val overflowCount: Int,
)

internal object NativeSubagentVisualPalette {
    private val light = intArrayOf(
        0xFFDDEAFE.toInt(), // blue
        0xFFE7E1FE.toInt(), // violet
        0xFFD9F1E6.toInt(), // mint
        0xFFFFE4D6.toInt(), // orange
        0xFFFFE0EA.toInt(), // rose
        0xFFE2EEF1.toInt(), // cyan
        0xFFF1E6CF.toInt(), // amber
        0xFFE5E8F5.toInt(), // indigo
    )
    private val dark = intArrayOf(
        0xFF274467.toInt(),
        0xFF463A6B.toInt(),
        0xFF285243.toInt(),
        0xFF68432F.toInt(),
        0xFF673547.toInt(),
        0xFF2E5159.toInt(),
        0xFF5D4D2E.toInt(),
        0xFF3A4162.toInt(),
    )

    fun light(index: Int): Int = light[index.floorMod(light.size)]
    fun dark(index: Int): Int = dark[index.floorMod(dark.size)]

    private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size
}

internal object NativeSubagentVisualFactory {
    fun create(
        agentThreadId: String = "",
        callId: String = "",
        name: String = "",
        status: String = "waiting",
        aliases: Iterable<String> = emptyList(),
    ): NativeSubagentVisual {
        val normalizedAliases = buildSet {
            agentThreadId.cleanAlias()?.let(::add)
            callId.cleanAlias()?.let(::add)
            aliases.forEach { it.cleanAlias()?.let(::add) }
        }
        val seed = agentThreadId.cleanAlias()
            ?: callId.cleanAlias()
            ?: normalizedAliases.firstOrNull()
            ?: "subagent:${stableHash(name)}"
        val colorIndex = (stableHash(seed).toLong() and 0x7fff_ffffL).rem(8L).toInt()
        return NativeSubagentVisual(
            seed = seed,
            agentThreadId = agentThreadId.cleanAlias().orEmpty(),
            callId = callId.cleanAlias().orEmpty(),
            aliases = normalizedAliases,
            name = name.trim().ifBlank { defaultName(seed) },
            status = normalizeStatus(status),
            colorIndex = colorIndex,
            lightColorArgb = NativeSubagentVisualPalette.light(colorIndex),
            darkColorArgb = NativeSubagentVisualPalette.dark(colorIndex),
        )
    }

    fun merge(existing: NativeSubagentVisual, incoming: NativeSubagentVisual): NativeSubagentVisual {
        val aliases = existing.aliases + incoming.aliases + setOf(existing.seed, incoming.seed)
        // Keep the first real identity as the visual seed. A call id is already stable, so a
        // later agentThreadId discovery must not recolor or reorder a chip that is on screen.
        // Synthetic name-derived seeds may be replaced once a real id arrives.
        val seed = existing.seed.takeUnless { it.startsWith("subagent:") }
            ?: existing.agentThreadId.ifBlank {
                incoming.agentThreadId.ifBlank { existing.callId.ifBlank { incoming.seed } }
            }
        val base = create(
            agentThreadId = existing.agentThreadId.ifBlank { incoming.agentThreadId },
            callId = existing.callId.ifBlank { incoming.callId },
            name = incoming.name.takeUnless { it.startsWith("Subagent ") || it == "Subagent" }
                ?: existing.name,
            status = incoming.status.name,
            aliases = aliases,
        )
        val stableSeed = seed.ifBlank { base.seed }
        val colorIndex = (stableHash(stableSeed).toLong() and 0x7fff_ffffL).rem(8L).toInt()
        return base.copy(
            seed = stableSeed,
            colorIndex = colorIndex,
            lightColorArgb = NativeSubagentVisualPalette.light(colorIndex),
            darkColorArgb = NativeSubagentVisualPalette.dark(colorIndex),
        )
    }

    fun fromActivityItem(item: NativeActivityItem): NativeSubagentVisual = create(
        agentThreadId = item.agentThreadId,
        callId = item.callId,
        name = item.title,
        status = item.status.name.lowercase(),
        aliases = listOfNotNull(item.itemId),
    )

    fun mergeAll(items: Iterable<NativeSubagentVisual>): List<NativeSubagentVisual> {
        val result = ArrayList<NativeSubagentVisual>()
        items.forEach { incoming ->
            val index = result.indexOfFirst { existing ->
                existing.seed == incoming.seed || existing.aliases.any(incoming.aliases::contains)
            }
            if (index >= 0) result[index] = merge(result[index], incoming) else result.add(incoming)
        }
        return result
    }

    fun inline(items: Iterable<NativeSubagentVisual>, maxVisible: Int = 8): NativeSubagentInlineModel {
        val merged = mergeAll(items)
        val limit = maxVisible.coerceAtLeast(0)
        return NativeSubagentInlineModel(
            visible = merged.take(limit),
            overflowCount = (merged.size - limit).coerceAtLeast(0),
        )
    }

    fun normalizeStatus(value: String): NativeSubagentStatus = when (value.trim().lowercase()) {
        "working", "running", "started", "inprogress", "in_progress" -> NativeSubagentStatus.WORKING
        "done", "complete", "completed", "success" -> NativeSubagentStatus.DONE
        "failed", "error", "cancelled", "canceled", "stopped", "interrupted" -> NativeSubagentStatus.FAILED
        else -> NativeSubagentStatus.WAITING
    }

    /** FNV-1a has stable output across Android/JVM versions, unlike depending on collection order. */
    fun stableHash(value: String): Int {
        var hash = 0x811C9DC5u
        value.forEach { char ->
            hash = hash xor char.code.toUInt()
            hash *= 0x01000193u
        }
        return hash.toInt()
    }

    private fun defaultName(seed: String): String = "Subagent ${seed.takeLast(6)}"
    private fun String.cleanAlias(): String? = trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
}
