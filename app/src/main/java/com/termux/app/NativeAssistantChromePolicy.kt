package com.termux.app

/** Chooses the single assistant segment that owns identity/actions for each user turn. */
object NativeAssistantChromePolicy {
    @JvmStatic
    fun terminalAssistantText(messages: List<NativeChatMessage>, currentTurnActive: Boolean): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var lastAssistantId: String? = null
        val turnText = ArrayList<String>()
        fun commit() {
            val id = lastAssistantId ?: return
            result[id] = turnText.filter { it.isNotBlank() }.joinToString("\n\n")
            lastAssistantId = null
            turnText.clear()
        }
        messages.forEach { message ->
            when (message.role) {
                NativeChatRole.USER -> commit()
                NativeChatRole.ASSISTANT -> {
                    lastAssistantId = message.id
                    if (message.content.isNotBlank()) turnText.add(message.content)
                }
                else -> Unit
            }
        }
        if (!currentTurnActive) commit()
        return result
    }

    @JvmStatic
    fun terminalAssistantIds(messages: List<NativeChatMessage>, currentTurnActive: Boolean): Set<String> =
        terminalAssistantText(messages, currentTurnActive).keys
}
