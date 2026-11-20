package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativePersonaPromptTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        NativePersonaStore.prefs(context).edit().clear().commit()
    }

    @Test
    fun promptCarriesTheActivePersona() {
        val prompt = NativePersonaPrompt.build(context, null)

        assertTrue(prompt.startsWith("[人物设定]"))
        assertTrue(prompt.contains("Sillage"))
    }

    @Test
    fun switchingPersonaChangesThePrompt() {
        NativePersonaStore.setActive(context, "dafeiyu")

        val prompt = NativePersonaPrompt.build(context, null)

        assertTrue(prompt.contains("大肥鱼"))
        assertTrue(prompt.contains("QQ 群友"))
        assertTrue(prompt.contains("白米饭"))
        assertFalse(prompt.contains("Sillage"))
    }

    @Test
    fun promptAlwaysExplainsTheMemoryTool() {
        val prompt = NativePersonaPrompt.build(context, null)

        assertTrue(prompt.contains("fcode-memory recall"))
        assertTrue(prompt.contains("fcode-memory save"))
        assertTrue(prompt.contains("不要为了"))
    }

    @Test
    fun unknownThreadKeepsTheDefaultOwnerInsteadOfFollowingGlobalChanges() {
        NativePersonaStore.setActive(context, "dafeiyu")

        val prompt = NativePersonaPrompt.build(context, "thread-with-no-override")

        assertTrue(prompt.contains("Sillage"))
        assertFalse(prompt.contains("大肥鱼"))
    }

    @Test
    fun longMemoriesNeverTruncatePersonaOrMemoryToolInstructions() {
        val persona = NativePersona(
            id = "stress",
            name = "压力测试人物",
            emoji = "🧪",
            greeting = "你好",
            tone = "简洁",
            prompt = "设定".repeat(2_000),
        )
        val core = (0 until 8).map { index ->
            memory("core-$index", if (index == 0) NativeMemoryTier.CORE else NativeMemoryTier.NORMAL, "核心$index", "内容".repeat(800))
        }
        val index = (0 until 20).map { value ->
            memory("index-$value", NativeMemoryTier.NORMAL, "索引$value-${"标题".repeat(80)}", "正文")
        }

        val prompt = NativePersonaPrompt.assemble(persona, core, index)

        assertTrue(prompt.length <= NativePersonaPrompt.MAX_TOTAL)
        assertTrue(prompt.contains("压力测试人物"))
        assertTrue(prompt.contains("fcode-memory recall"))
        assertTrue(prompt.contains("fcode-memory save"))
    }

    private fun memory(id: String, tier: NativeMemoryTier, title: String, body: String) = NativeMemory(
        id = id,
        tier = tier,
        createdAt = 1L,
        dateLabel = "2026-11-19 12:00",
        tags = emptyList(),
        title = title,
        body = body,
    )
}
