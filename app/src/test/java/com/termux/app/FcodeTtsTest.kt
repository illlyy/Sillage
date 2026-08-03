package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FcodeTtsTest {

    @Test
    fun detectsChineseText() {
        assertEquals("zh", nativeDetectSpeechLanguage("你好，请帮我修复这个 bug"))
    }

    @Test
    fun detectsEnglishText() {
        assertEquals("en", nativeDetectSpeechLanguage("Please fix this bug"))
    }

    @Test
    fun mixedTextWithChinesePrefersChinese() {
        assertEquals("zh", nativeDetectSpeechLanguage("Mixed 你好 with english"))
    }

    @Test
    fun emptyTextDefaultsToEnglish() {
        assertEquals("en", nativeDetectSpeechLanguage(""))
        assertEquals("en", nativeDetectSpeechLanguage("12345"))
    }
}
