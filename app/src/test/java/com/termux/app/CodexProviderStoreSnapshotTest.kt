package com.termux.app

import android.content.Context
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CodexProviderStoreSnapshotTest {
    private fun freshPreferencesWithoutClearing() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("provider-snapshot-test", Context.MODE_PRIVATE)

    private fun freshPreferences() = freshPreferencesWithoutClearing()
        .also { it.edit().clear().commit() }

    @Test
    fun unchangedPreferencesReuseParsedSnapshot() {
        val store = CodexProviderStore(freshPreferences())
        val profile = CodexProviderStore.Profile("one", "One", "", "https://one.example/v1", "key", "model")
        store.save(profile)

        val first = store.snapshot()
        val second = store.snapshot()
        val secondStore = CodexProviderStore(freshPreferencesWithoutClearing())

        assertSame(first, second)
        assertSame(first, secondStore.snapshot())
        assertEquals(first.profiles.map { it.id }, store.all().map { it.id })
        assertNotSame(first.profiles.first(), store.find("one"))
        assertEquals("one", store.find("one")?.id)
    }

    @Test
    fun writesAndExternalPreferenceChangesRefreshSnapshot() {
        val preferences = freshPreferences()
        val store = CodexProviderStore(preferences)
        val firstProfile = CodexProviderStore.Profile("one", "One", "", "https://one.example/v1", "key", "model-a")
        store.activate(firstProfile)
        val first = store.snapshot()
        assertEquals("one", first.active?.id)

        val secondProfile = CodexProviderStore.Profile("two", "Two", "", "https://two.example/v1", "key", "model-b")
        preferences.edit()
            .putString(CodexProviderStore.KEY_PROFILES, JSONArray().put(secondProfile.json()).toString())
            .putString(CodexProviderStore.KEY_ACTIVE, "two")
            .commit()

        val refreshed = store.snapshot()
        assertNotSame(first, refreshed)
        assertEquals(listOf("two"), refreshed.profiles.map { it.id })
        assertEquals("two", refreshed.active?.id)
        assertTrue(store.isActive(secondProfile))
    }
}
