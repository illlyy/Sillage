package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentToolCatalogTest {
    @Test
    fun idsAndPackageNamesAreSafeAndUnique() {
        val tools = DevelopmentToolCatalog.tools
        assertEquals(tools.size, tools.map { it.id }.distinct().size)
        assertTrue(tools.all { it.id.matches(Regex("[a-z0-9-]+")) })
        assertTrue(tools.flatMap { it.aptPackages }.all { it.matches(Regex("[a-z0-9+.-]+")) })
        assertTrue(tools.all { it.probeExecutables.isNotEmpty() })
    }

    @Test
    fun presetsOnlyReferenceCatalogTools() {
        assertTrue(DevelopmentToolCatalog.recommendedIds.isNotEmpty())
        assertTrue(DevelopmentToolCatalog.recommendedIds.all { it in DevelopmentToolCatalog.allIds })
        assertEquals(DevelopmentToolCatalog.tools.size, DevelopmentToolCatalog.allIds.size)
    }

    @Test
    fun codexIsSpecialAndNotAnAptPackage() {
        val codex = DevelopmentToolCatalog.byId("codex")!!
        assertTrue(codex.codexCli)
        assertTrue(codex.aptPackages.isEmpty())
        assertEquals(listOf("codex"), codex.probeExecutables)
    }
}
