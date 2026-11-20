package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativePersonaStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        NativePersonaStore.prefs(context).edit().clear().commit()
    }

    @Test
    fun firstLoadSeedsBuiltins() {
        val personas = NativePersonaStore.load(context)

        assertEquals(2, personas.size)
        assertTrue(personas.all { it.builtin })
        assertTrue(personas.any { it.id == NativePersonaStore.DEFAULT_ID })
        assertTrue(personas.any { it.name == "大肥鱼" })
        // A second load must not duplicate the seed.
        assertEquals(2, NativePersonaStore.load(context).size)
    }

    @Test
    fun olderSeedIsReplacedButCustomPersonasSurvive() {
        val preferences = NativePersonaStore.prefs(context)
        val legacy = org.json.JSONArray()
            .put(NativePersona("sillage", "Sillage", "✦", "", "", "", true).toJson())
            .put(NativePersona("xiaoxi", "小汐", "🌊", "", "", "", true).toJson())
            .put(NativePersona("mine", "我的人物", "🧪", "", "", "", false).toJson())
        preferences.edit().putString("native_personas_v1", legacy.toString()).apply()

        val personas = NativePersonaStore.load(context)

        assertTrue(personas.none { it.id == "xiaoxi" })
        assertTrue(personas.any { it.id == "dafeiyu" })
        assertTrue(personas.any { it.id == "mine" })
    }

    @Test
    fun activeDefaultsToTheFirstBuiltin() {
        assertEquals(NativePersonaStore.DEFAULT_ID, NativePersonaStore.active(context).id)
    }

    @Test
    fun switchingActivePersonaSticks() {
        val target = NativePersonaStore.load(context).first { it.id != NativePersonaStore.DEFAULT_ID }

        NativePersonaStore.setActive(context, target.id)

        assertEquals(target.id, NativePersonaStore.active(context).id)
    }

    @Test
    fun customPersonaIsStoredAndSelectable() {
        val created = NativePersonaStore.create(context, "测试人物", "🧪")

        assertFalse(created.builtin)
        assertTrue(NativePersonaStore.load(context).any { it.id == created.id })

        NativePersonaStore.setActive(context, created.id)
        assertEquals("测试人物", NativePersonaStore.active(context).name)
    }

    @Test
    fun deletingTheActivePersonaFallsBackToAnother() {
        val created = NativePersonaStore.create(context, "临时", "✦")
        NativePersonaStore.setActive(context, created.id)

        NativePersonaStore.delete(context, created.id)

        assertNull(NativePersonaStore.find(context, created.id))
        assertNotEquals(created.id, NativePersonaStore.active(context).id)
    }

    @Test
    fun threadOverrideWinsOverTheGlobalPersona() {
        val personas = NativePersonaStore.load(context)
        val global = personas.first { it.id == NativePersonaStore.DEFAULT_ID }
        val override = personas.first { it.id != global.id }
        NativePersonaStore.setActive(context, global.id)

        NativePersonaStore.setThreadPersona(context, "thread-1", override.id)

        assertEquals(override.id, NativePersonaStore.effective(context, "thread-1").id)
        assertEquals(global.id, NativePersonaStore.effective(context, "thread-2").id)
        assertEquals(global.id, NativePersonaStore.effective(context, null).id)
    }

    @Test
    fun clearingTheThreadOverrideFallsBackToDefault() {
        val personas = NativePersonaStore.load(context)
        val other = personas.first { it.id != NativePersonaStore.DEFAULT_ID }
        NativePersonaStore.setThreadPersona(context, "thread-3", other.id)

        NativePersonaStore.setThreadPersona(context, "thread-3", "")

        assertEquals(NativePersonaStore.DEFAULT_ID, NativePersonaStore.effective(context, "thread-3").id)
    }

    @Test
    fun legacyThreadDoesNotFollowLaterGlobalChanges() {
        NativePersonaStore.setActive(context, NativePersonaStore.DEFAULT_ID)

        NativePersonaStore.setActive(context, "dafeiyu")

        assertEquals(NativePersonaStore.DEFAULT_ID, NativePersonaStore.effective(context, "legacy-thread").id)
    }

    @Test
    fun deletingPersonaClearsThreadBindingsAndFallsBackToDefault() {
        val created = NativePersonaStore.create(context, "临时人物", "🧪")
        NativePersonaStore.setThreadPersona(context, "thread-delete", created.id)

        NativePersonaStore.delete(context, created.id)

        assertEquals(NativePersonaStore.DEFAULT_ID, NativePersonaStore.effective(context, "thread-delete").id)
        assertTrue(
            NativePersonaStore.prefs(context).getString("native_persona_threads_v1", "{}")!!
                .contains("thread-delete").not(),
        )
    }
}
