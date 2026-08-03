package com.termux.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Rough speech-language detection for assistant answers (zh vs en). */
internal fun nativeDetectSpeechLanguage(text: String): String =
    if (text.any { it.code in 0x4E00..0x9FFF }) "zh" else "en"

/**
 * Process-wide text-to-speech for assistant answers (pattern: claudecodeui MessageSpeakControl).
 * Lazily initializes the engine, keeps one instance for the process and exposes a Compose
 * observable speaking flag so the button can morph into a stop control.
 */
internal object FcodeTtsController {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var speaking by mutableStateOf(false)
        private set

    private fun ensure(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                pendingText?.let { text -> pendingText = null; speakInternal(text) }
            }
        }
    }

    fun speak(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ensure(context)
        if (!ready) {
            pendingText = trimmed
            return
        }
        speakInternal(trimmed)
    }

    private fun speakInternal(text: String) {
        val engine = tts ?: return
        engine.setLanguage(if (nativeDetectSpeechLanguage(text) == "zh") Locale.CHINESE else Locale.US)
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { speaking = true }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { speaking = false }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { speaking = false }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { speaking = false }
            }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fcode-tts")
    }

    fun stop() {
        tts?.stop()
        mainHandler.post { speaking = false }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        speaking = false
    }
}
