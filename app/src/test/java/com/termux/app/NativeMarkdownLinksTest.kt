package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMarkdownLinksTest {

    @Test
    fun externalSchemesRouteToBrowser() {
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("https://example.com"))
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("http://example.com/a?b=1"))
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("mailto:a@b.com"))
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("tel:+8612345678"))
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("data:text/plain,hi"))
        assertEquals(NativeMarkdownLinkAction.External, resolveMarkdownLink("//cdn.example.com/x.js"))
    }

    @Test
    fun bareHashIsIgnored() {
        assertEquals(NativeMarkdownLinkAction.None, resolveMarkdownLink("#"))
        assertEquals(NativeMarkdownLinkAction.None, resolveMarkdownLink(""))
    }

    @Test
    fun filePathsRouteToViewer() {
        assertEquals(
            NativeMarkdownLinkAction.FileRef("src/foo.ts", null),
            resolveMarkdownLink("src/foo.ts"),
        )
        assertEquals(
            NativeMarkdownLinkAction.FileRef("src/foo.ts", 130),
            resolveMarkdownLink("src/foo.ts:130"),
        )
        assertEquals(
            NativeMarkdownLinkAction.FileRef("src/foo.ts", 130),
            resolveMarkdownLink("src/foo.ts:130:5"),
        )
        assertEquals(
            NativeMarkdownLinkAction.FileRef("README.md", null),
            resolveMarkdownLink("README.md"),
        )
        assertEquals(
            NativeMarkdownLinkAction.FileRef("path with spaces/file.txt", null),
            resolveMarkdownLink("path with spaces/file.txt"),
        )
    }

    @Test
    fun plainWordsAreNotLinks() {
        assertEquals(NativeMarkdownLinkAction.None, resolveMarkdownLink("filename"))
        assertEquals(NativeMarkdownLinkAction.None, resolveMarkdownLink("just text"))
        assertEquals(NativeMarkdownLinkAction.None, resolveMarkdownLink("129"))
    }

    @Test
    fun looksLikeFilePathHeuristics() {
        assertTrue(looksLikeFilePath("src/main.kt"))
        assertTrue(looksLikeFilePath("a/b"))
        assertTrue(looksLikeFilePath(".gitignore"))
        assertFalse(looksLikeFilePath("word"))
        assertFalse(looksLikeFilePath(""))
        assertFalse(looksLikeFilePath("  "))
    }

    @Test
    fun looksLikeJsonResponseDetectsObjectsAndArrays() {
        assertTrue(looksLikeJsonResponse("""{"a": 1}"""))
        assertTrue(looksLikeJsonResponse("""[1, 2, 3]"""))
        assertTrue(looksLikeJsonResponse("""{"nested": {"deep": [true, null]}}"""))
        assertFalse(looksLikeJsonResponse("""not json {"a": 1}"""))
        assertFalse(looksLikeJsonResponse("""{"a": 1} trailing"""))
        assertFalse(looksLikeJsonResponse("""{"broken": }"""))
        assertFalse(looksLikeJsonResponse("plain text"))
        assertFalse(looksLikeJsonResponse("x"))
    }

    @Test
    fun jsonDetectionIsBoundedByMaxChars() {
        val big = "{" + (1..300).joinToString(",") { "\"k$it\": $it" } + "}"
        assertTrue(looksLikeJsonResponse(big, maxChars = Int.MAX_VALUE))
        assertFalse(looksLikeJsonResponse(big, maxChars = 100))
    }
}
