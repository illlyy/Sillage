package com.termux.app

import android.content.Context

/**
 * Builds the persona + memory preamble injected into every conversation.
 *
 * Two hard rules keep this cheap as memories pile up:
 * - Core memories are injected verbatim but capped (they are the user profile).
 * - Everything else is injected as a one-line index only; the body is fetched on demand through
 *   the `fcode-memory` CLI, which both backends can call from their shell tool.
 */
internal object NativePersonaPrompt {
    /** Absolute ceiling for the whole preamble; roughly 3k tokens of Chinese text. */
    internal const val MAX_TOTAL = 6_000
    internal const val MAX_PERSONA = 2_200
    private const val MAX_CORE_BLOCK = 1_500
    private const val MAX_INDEX_BLOCK = 1_500
    private const val CORE_LIMIT = 8
    private const val INDEX_LIMIT = 20
    private const val MAX_CORE_LINE = 340
    private const val MAX_INDEX_LINE = 180

    fun build(context: Context, threadId: String? = null): String {
        NativeMemoryStore.ensureTool(context)
        val persona = NativePersonaStore.effective(context, threadId)
        val core = NativeMemoryStore.core(context, CORE_LIMIT)
        val index = NativeMemoryStore.recent(context, INDEX_LIMIT)
        return assemble(persona, core, index)
    }

    /**
     * Priority-preserving assembly:
     * persona -> memory-tool contract -> core memories -> title index.
     *
     * A long index can never push out the persona or the instructions that make retrieval work.
     */
    internal fun assemble(
        persona: NativePersona,
        core: List<NativeMemory>,
        index: List<NativeMemory>,
    ): String {
        val builder = StringBuilder(MAX_TOTAL)
        builder.append(buildPersonaBlock(persona).takeSafely(MAX_PERSONA))
        builder.append('\n').append(TOOL_HELP).append('\n')

        val remainingAfterTools = (MAX_TOTAL - builder.length).coerceAtLeast(0)
        val coreBudget = minOf(MAX_CORE_BLOCK, (remainingAfterTools - 180).coerceAtLeast(0))
        val coreBody = buildCoreBlock(core, coreBudget)
        if (coreBody.isNotEmpty()) {
            builder.append("\n[用户档案 · 核心记忆]\n").append(coreBody)
        }

        val remainingForIndex = (MAX_TOTAL - builder.length).coerceAtLeast(0)
        val indexBody = buildIndexBlock(index, minOf(MAX_INDEX_BLOCK, remainingForIndex - 180))
        if (indexBody.isNotEmpty()) {
            builder.append("\n[记忆索引 · 最近 ").append(INDEX_LIMIT).append(" 条]\n")
            builder.append("（这里只有标题。需要细节时用 fcode-memory recall 查询，不要凭标题猜测内容。）\n")
            builder.append(indexBody)
        }
        return builder.toString()
    }

    private fun buildPersonaBlock(persona: NativePersona): String = buildString {
        append("[人物设定]\n")
        append("你现在扮演「").append(persona.name).append("」")
        if (persona.emoji.isNotBlank()) append(" ").append(persona.emoji)
        append("。\n")
        if (persona.tone.isNotBlank()) {
            append("口吻：").append(persona.tone.trim()).append('\n')
        }
        if (persona.prompt.isNotBlank()) {
            append("补充设定：").append(persona.prompt.trim()).append('\n')
        }
        if (persona.greeting.isNotBlank()) {
            append("开场白参考（不要每轮重复，只在合适时自然使用）：")
                .append(persona.greeting.trim()).append('\n')
        }
    }

    private fun buildCoreBlock(memories: List<NativeMemory>, budget: Int): String {
        if (memories.isEmpty() || budget <= 0) return ""
        val builder = StringBuilder()
        for (memory in memories.take(CORE_LIMIT)) {
            val line = "- ${memory.dateLabel.take(10)} ${memory.title}：${memory.body.replace("\n", " ")}"
                .takeSafely(MAX_CORE_LINE) + "\n"
            if (builder.length + line.length > budget) break
            builder.append(line)
        }
        return builder.toString()
    }

    private fun buildIndexBlock(memories: List<NativeMemory>, budget: Int): String {
        if (memories.isEmpty() || budget <= 0) return ""
        val builder = StringBuilder()
        for (memory in memories.take(INDEX_LIMIT)) {
            val line = NativeMemoryStore.indexLine(memory).takeSafely(MAX_INDEX_LINE) + "\n"
            if (builder.length + line.length > budget) break
            builder.append(line)
        }
        return builder.toString()
    }

    /** Character-budget truncation that never splits a UTF-16 surrogate pair. */
    private fun String.takeSafely(limit: Int): String {
        if (limit <= 0 || length <= limit) return this
        var end = limit - 1
        if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
        return substring(0, end).trimEnd() + "…"
    }

    private const val TOOL_HELP = """[记忆工具]
你有一个本地命令 fcode-memory，用于长期记忆（数据保存在用户设备上）：
- 检索：fcode-memory recall "关键词" [条数]
- 最近：fcode-memory list [条数]
- 保存：fcode-memory save --title "标题" --tags a,b --body "内容"
  正文含多行时可以把正文通过标准输入传入：fcode-memory save --title "标题" <正文文件
- 核心记忆（每次对话都会自动带上，请保持简短）：在 save 时加 --core

使用规则：
1. 只有当前问题涉及过去的事件、用户的偏好、之前做过的决定或历史细节时，才检索记忆。
2. 不要为了"了解背景"一次性读取全部记忆；先看标题索引，再有针对性地 recall。
3. 用户提到值得长期记住的事实（称呼、习惯、技术栈偏好、重要决定）时，用 save 写入；琐碎内容不要存。
4. 需要纠正过时的记忆时，先 list 找到 id，再告知用户可在「人物与记忆」页删除，不要凭空假设。"""
}
