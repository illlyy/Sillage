package com.termux.app

internal data class NativePlanContentPart(
    val role: String,
    val text: String,
)

/** Snapshot produced after every plan/assistant delta. */
internal data class NativePlanStreamResult(
    val assistantText: String = "",
    val planText: String = "",
    val hasPlan: Boolean = planText.isNotBlank(),
    val planOnly: Boolean = planText.isNotBlank() && assistantText.isBlank(),
    val dedicatedPlan: Boolean = false,
    val completed: Boolean = false,
    val itemId: String? = null,
)

/**
 * Stateful parser for Codex plan markup. It reparses the accumulated item on each bounded UI
 * flush, which makes tags split at any character boundary safe without exposing a partial tag
 * to the assistant bubble. Dedicated plan items are authoritative and deduplicated against the
 * tag fallback used by older providers.
 */
internal class NativePlanStreamParser {
    private val fallbackSource = StringBuilder()
    private val dedicatedSource = StringBuilder()
    private var currentItemId: String? = null
    private var finished = false
    private var dedicatedSeen = false

    fun reset(itemId: String? = null) {
        fallbackSource.setLength(0)
        dedicatedSource.setLength(0)
        currentItemId = itemId
        finished = false
        dedicatedSeen = false
    }

    fun start(itemId: String? = null, dedicated: Boolean = false): NativePlanStreamResult {
        // A lifecycle start is an explicit item boundary.  Do not wait for the previous item to
        // emit a completion event: several app-server versions start the next plan/assistant item
        // before delivering the prior completion notification.
        if (isDifferentItem(itemId) || finished || (dedicated && dedicatedSeen && itemId.isNullOrBlank())) reset(itemId)
        if (!itemId.isNullOrBlank()) currentItemId = itemId
        if (dedicated) dedicatedSeen = true
        finished = false
        return snapshot()
    }

    fun append(delta: String, itemId: String? = null, dedicated: Boolean = false): NativePlanStreamResult {
        if (isDifferentItem(itemId)) reset(itemId)
        if (!itemId.isNullOrBlank()) currentItemId = itemId
        if (delta.isNotEmpty()) {
            if (dedicated) {
                dedicatedSeen = true
                dedicatedSource.append(delta)
            } else {
                fallbackSource.append(delta)
            }
        }
        return snapshot()
    }

    fun feed(delta: String, itemId: String? = null, dedicated: Boolean = false): NativePlanStreamResult =
        append(delta, itemId, dedicated)

    fun complete(
        text: String = "",
        itemId: String? = null,
        dedicated: Boolean = false,
    ): NativePlanStreamResult {
        if (isDifferentItem(itemId)) reset(itemId)
        if (!itemId.isNullOrBlank()) currentItemId = itemId
        if (dedicated) {
            dedicatedSeen = true
            replaceWithAuthoritative(dedicatedSource, text)
        } else if (text.isNotEmpty()) {
            replaceWithAuthoritative(fallbackSource, text)
        }
        finished = true
        return snapshot()
    }

    fun finish(text: String = "", itemId: String? = null, dedicated: Boolean = false): NativePlanStreamResult =
        complete(text, itemId, dedicated)

    fun snapshot(): NativePlanStreamResult {
        val fallback = scanFallback(fallbackSource.toString(), finished)
        val dedicated = cleanDedicated(dedicatedSource.toString(), finished)
        val plan = mergePlan(fallback.plan, dedicated)
        return NativePlanStreamResult(
            assistantText = fallback.assistant,
            planText = plan,
            hasPlan = plan.isNotBlank(),
            planOnly = plan.isNotBlank() && fallback.assistant.isBlank(),
            dedicatedPlan = dedicatedSeen && dedicated.isNotBlank(),
            completed = finished,
            itemId = currentItemId,
        )
    }

    val assistantText: String get() = snapshot().assistantText
    val planText: String get() = snapshot().planText

    /** Ordered parts used by history replay. This is the same scanner used by the live parser,
     * rather than a second regex implementation that can disagree at malformed tag boundaries. */
    fun completeParts(value: String): List<NativePlanContentPart> =
        scanFallbackParts(value, completed = true)

    private fun isDifferentItem(itemId: String?): Boolean =
        !itemId.isNullOrBlank() && !currentItemId.isNullOrBlank() && itemId != currentItemId

    private fun replaceWithAuthoritative(target: StringBuilder, authoritative: String) {
        if (authoritative.isEmpty()) return
        val current = target.toString()
        val merged = when {
            current.isBlank() -> authoritative
            authoritative.startsWith(current) -> authoritative
            current.startsWith(authoritative) -> current
            normalizedForDedupe(current) == normalizedForDedupe(authoritative) -> current
            else -> authoritative
        }
        target.setLength(0)
        target.append(merged)
    }

    private fun mergePlan(fallback: String, dedicated: String): String {
        if (dedicated.isBlank()) return fallback
        if (fallback.isBlank()) return dedicated
        val fallbackNormalized = normalizedForDedupe(fallback)
        val dedicatedNormalized = normalizedForDedupe(dedicated)
        return when {
            fallbackNormalized == dedicatedNormalized -> dedicated
            dedicatedNormalized.contains(fallbackNormalized) -> dedicated
            fallbackNormalized.contains(dedicatedNormalized) -> dedicated // dedicated item wins
            else -> dedicated
        }
    }

    private data class ScanResult(val assistant: String, val plan: String)

    private fun scanFallback(source: String, completed: Boolean): ScanResult {
        val parts = scanFallbackParts(source, completed)
        return ScanResult(
            assistant = parts.filter { it.role == "assistant" }.joinToString("") { it.text },
            plan = parts.filter { it.role == "plan" }.joinToString("") { it.text },
        )
    }

    private fun scanFallbackParts(source: String, completed: Boolean): List<NativePlanContentPart> {
        val parts = ArrayList<NativePlanContentPart>()
        val chunk = StringBuilder()
        var chunkRole = "assistant"
        fun flush() {
            if (chunk.isNotEmpty()) {
                parts += NativePlanContentPart(chunkRole, chunk.toString())
                chunk.setLength(0)
            }
        }
        fun route(value: String, inPlan: Boolean) {
            if (value.isEmpty()) return
            val role = if (inPlan) "plan" else "assistant"
            if (role != chunkRole && chunk.isNotEmpty()) flush()
            chunkRole = role
            chunk.append(value)
        }
        var inPlan = false
        var index = 0
        while (index < source.length) {
            val open = source.indexOf('<', index)
            if (open < 0) {
                route(source.substring(index), inPlan)
                break
            }
            if (open > index) route(source.substring(index, open), inPlan)
            val close = source.indexOf('>', open + 1)
            if (close < 0) {
                val tail = source.substring(open)
                if (completed) {
                    if (!looksLikeProtocolPrefix(tail)) route(tail, inPlan)
                } else if (!looksLikeProtocolPrefix(tail)) {
                    route(tail, inPlan)
                }
                break
            }
            val tag = source.substring(open, close + 1)
            when {
                OPEN_PLAN_TAG.matches(tag) -> {
                    flush()
                    inPlan = true
                    chunkRole = "plan"
                }
                CLOSE_PLAN_TAG.matches(tag) -> {
                    flush()
                    inPlan = false
                    chunkRole = "assistant"
                }
                FINAL_TAG.matches(tag) -> Unit
                else -> route(tag, inPlan)
            }
            index = close + 1
        }
        flush()
        return parts
    }

    private fun cleanDedicated(source: String, completed: Boolean): String {
        if (source.isBlank()) return ""
        val result = StringBuilder()
        var index = 0
        while (index < source.length) {
            val open = source.indexOf('<', index)
            if (open < 0) {
                result.append(source.substring(index))
                break
            }
            if (open > index) result.append(source.substring(index, open))
            val close = source.indexOf('>', open + 1)
            if (close < 0) {
                val tail = source.substring(open)
                if (completed && !looksLikeProtocolPrefix(tail)) result.append(tail)
                else if (!looksLikeProtocolPrefix(tail)) result.append(tail)
                break
            }
            val tag = source.substring(open, close + 1)
            if (!OPEN_PLAN_TAG.matches(tag) && !CLOSE_PLAN_TAG.matches(tag) && !FINAL_TAG.matches(tag)) {
                result.append(tag)
            }
            index = close + 1
        }
        return result.toString()
    }

    private fun looksLikeProtocolPrefix(value: String): Boolean {
        val normalized = value.lowercase().replace(Regex("\\s+"), "")
        val candidates = listOf(
            "<plan>", "</plan>",
            "<propose_plan>", "</propose_plan>",
            "<proposed_plan>", "</proposed_plan>",
            "<final>", "</final>",
        )
        return candidates.any { candidate -> candidate.startsWith(normalized) || normalized.startsWith(candidate.dropLast(1)) }
    }

    private fun normalizedForDedupe(value: String): String = value
        .replace(OPEN_PLAN_TAG, "")
        .replace(CLOSE_PLAN_TAG, "")
        .replace(FINAL_TAG, "")
        .trim()
        .replace(Regex("\\s+"), " ")

    companion object {
        val OPEN_PLAN_TAG = Regex(
            """<\s*(?:plan|propose_plan|proposed_plan)(?:\s+[^>]*)?\s*>""",
            RegexOption.IGNORE_CASE,
        )
        val CLOSE_PLAN_TAG = Regex(
            """<\s*/\s*(?:plan|propose_plan|proposed_plan)\s*>""",
            RegexOption.IGNORE_CASE,
        )
        val FINAL_TAG = Regex("""<\s*/?\s*final(?:\s+[^>]*)?\s*>""", RegexOption.IGNORE_CASE)
        /** Java/history bridge entry point for a complete assistant item. */
        @JvmStatic
        fun splitComplete(value: String?): List<NativePlanContentPart> =
            NativePlanStreamParser().completeParts(value.orEmpty())
    }
}

internal typealias NativePlanParseResult = NativePlanStreamResult
