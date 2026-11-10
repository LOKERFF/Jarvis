package com.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Text to speech with automatic Hindi / English selection.
 *
 * A Devanagari reply is spoken with the hi-IN voice, everything else with
 * en-IN. If the device has no Hindi voice installed we fall back to the
 * default voice and the app tells the user (Settings > Voice shows a warning).
 */
class TtsController(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var counter = 0

    private var pendingText: String? = null
    private var pendingRate = 1f
    private var pendingPitch = 1f

    /** Called when an utterance finishes (or fails) so the engine can loop. */
    var onFinished: (() -> Unit)? = null

    /** Called with a user-facing message when speech cannot start. */
    var onError: ((String) -> Unit)? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            onFinished?.invoke()
        }

        override fun onError(utteranceId: String?) {
            onFinished?.invoke()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            onError?.invoke("Text to speech error ($errorCode).")
            onFinished?.invoke()
        }
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setOnUtteranceProgressListener(progressListener)
                    val queued = pendingText
                    if (queued != null) {
                        pendingText = null
                        speakNow(queued, pendingRate, pendingPitch)
                    }
                } else {
                    onError?.invoke("Text to speech is not available on this device.")
                }
            }
        } catch (t: Throwable) {
            tts = null
            ready = false
        }
    }

    fun speak(text: String, rate: Float = 1f, pitch: Float = 1f) {
        val clean = forSpeech(text)
        if (clean.isBlank()) {
            onFinished?.invoke()
            return
        }
        val engine = tts
        if (engine == null || !ready) {
            pendingText = clean
            pendingRate = rate
            pendingPitch = pitch
            return
        }
        speakNow(clean, rate, pitch)
    }

    fun stop() {
        pendingText = null
        try {
            tts?.stop()
        } catch (t: Throwable) {
            // nothing useful to do
        }
    }

    fun shutdown() {
        pendingText = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            // nothing useful to do
        }
        tts = null
        ready = false
    }

    fun hasHindiVoice(): Boolean {
        val engine = tts ?: return false
        return try {
            engine.isLanguageAvailable(Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE
        } catch (t: Throwable) {
            false
        }
    }

    fun isReady(): Boolean = ready

    // ------------------------------------------------------------- internals

    private fun speakNow(text: String, rate: Float, pitch: Float) {
        val engine = tts ?: return
        try {
            engine.stop()
            engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
            engine.setPitch(pitch.coerceIn(0.5f, 2f))
            applyLanguage(engine, text)
            val id = "jarvis-${++counter}"
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (queued != TextToSpeech.SUCCESS) {
                onError?.invoke("Text to speech could not start.")
                onFinished?.invoke()
            }
        } catch (t: Throwable) {
            onError?.invoke("Text to speech failed: ${t.message ?: "unknown error"}")
            onFinished?.invoke()
        }
    }

    private fun applyLanguage(engine: TextToSpeech, text: String) {
        val wantsHindi = DEVANAGARI.containsMatchIn(text)
        val primary = if (wantsHindi) Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(primary)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
        }
    }

    /** Removes markdown and code so the voice does not read punctuation aloud. */
    private fun forSpeech(text: String): String {
        var value = text
        value = value.replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
        value = value.replace(Regex("`([^`]*)`"), "\$1")
        value = value.replace(Regex("\\*\\*([^*]*)\\*\\*"), "\$1")
        value = value.replace(Regex("\\*([^*]*)\\*"), "\$1")
        value = value.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        value = value.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "\$1")
        value = value.replace(Regex("https?://\\S+"), "link")
        value = value.replace(Regex("[ \\t]+"), " ")
        value = value.replace(Regex("\\n{2,}"), ". ")
        value = value.replace("\n", ". ")
        value = value.replace(Regex("\\.{2,}"), ".")
        return value.trim()
    }

    companion object {
        private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
    }
}
