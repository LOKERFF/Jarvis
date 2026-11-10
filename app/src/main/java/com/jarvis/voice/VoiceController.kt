package com.jarvis.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.domain.Prompts
import java.util.Locale

/**
 * One [SpeechRecognizer] shared by two jobs:
 *
 *  - HOTWORD: listens for "Jarvis" in short bursts and restarts itself. This is
 *    the Phase 1 wake word. Phase 2 replaces the listening part with an offline
 *    Vosk model inside the foreground service; the callbacks stay identical.
 *  - COMMAND: a single dictation pass whose transcript is sent to the engine.
 *
 * Everything runs on the main thread because SpeechRecognizer requires it.
 * Errors never crash the app: they are retried or reported through [onError].
 */
class VoiceController(
    private val context: Context,
    private val prefs: AppPreferences,
) {

    enum class Mode { OFF, HOTWORD, COMMAND }

    var onWake: (() -> Unit)? = null
    var onTranscript: ((String) -> Unit)? = null
    var onListeningChanged: ((Boolean) -> Unit)? = null
    var onRms: ((Float) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var mode = Mode.OFF
    private var wantHotword = false
    private var destroyed = false
    private var failuresInARow = 0

    private val restartRunnable = Runnable {
        if (destroyed) return@Runnable
        if (wantHotword && mode == Mode.OFF) startHotwordInternal()
    }

    // ---------------------------------------------------------------- public

    fun startHotword() {
        post {
            wantHotword = true
            if (mode == Mode.OFF) startHotwordInternal()
        }
    }

    fun stopHotword() {
        post {
            wantHotword = false
            if (mode == Mode.HOTWORD) {
                mode = Mode.OFF
                cancelRecognizer()
            }
        }
    }

    /** Returns true if the recogniser accepted the request. */
    fun startCommand(): Boolean {
        var started = false
        postNow {
            if (destroyed) return@postNow
            if (!hasMicPermission()) {
                onError?.invoke("Microphone permission is required for voice.")
                return@postNow
            }
            val engine = recognizerOrNull() ?: return@postNow
            main.removeCallbacks(restartRunnable)
            cancelQuietly(engine)
            mode = Mode.COMMAND
            failuresInARow = 0
            started = listen(engine)
        }
        return started
    }

    fun stop() {
        post {
            mode = Mode.OFF
            cancelRecognizer()
        }
    }

    fun destroy() {
        destroyed = true
        wantHotword = false
        mode = Mode.OFF
        main.removeCallbacks(restartRunnable)
        try {
            recognizer?.destroy()
        } catch (t: Throwable) {
            // ignore
        }
        recognizer = null
    }

    fun isListening(): Boolean = mode != Mode.OFF

    // -------------------------------------------------------------- internal

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Runs immediately (used where the caller needs the return value). */
    private fun postNow(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startHotwordInternal() {
        if (destroyed) return
        if (!hasMicPermission()) {
            wantHotword = false
            onError?.invoke("Microphone permission is required for the wake word.")
            return
        }
        val engine = recognizerOrNull() ?: return
        main.removeCallbacks(restartRunnable)
        cancelQuietly(engine)
        mode = Mode.HOTWORD
        listen(engine)
    }

    private fun recognizerOrNull(): SpeechRecognizer? {
        if (destroyed) return null
        val available = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (t: Throwable) {
            false
        }
        if (!available) {
            onError?.invoke("No speech recognition service found. Install/enable Google speech services.")
            return null
        }
        recognizer?.let { return it }
        return try {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        } catch (t: Throwable) {
            onError?.invoke("Could not start speech recognition: ${t.message ?: "unknown error"}")
            null
        }
    }

    private fun listen(engine: SpeechRecognizer): Boolean {
        val hotword = mode == Mode.HOTWORD
        return try {
            engine.startListening(buildIntent(hotword))
            true
        } catch (t: Throwable) {
            // Busy recogniser is the usual cause; try once more shortly.
            cancelQuietly(engine)
            main.postDelayed({
                if (!destroyed && (mode == Mode.HOTWORD || mode == Mode.COMMAND)) {
                    try {
                        engine.startListening(buildIntent(mode == Mode.HOTWORD))
                    } catch (ignored: Throwable) {
                        onListeningChanged?.invoke(false)
                    }
                }
            }, 350)
            true
        }
    }

    private fun cancelQuietly(engine: SpeechRecognizer) {
        try {
            engine.cancel()
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun cancelRecognizer() {
        main.removeCallbacks(restartRunnable)
        recognizer?.let { cancelQuietly(it) }
        onListeningChanged?.invoke(false)
    }

    private fun buildIntent(hotword: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                if (hotword) 1500L else 1200L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                if (hotword) 800L else 500L
            )
            // Wake word bursts stay on-device when the phone has an offline model,
            // which keeps hours of ambient audio away from any server.
            if (hotword && prefs.hotwordOffline.value) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            languageTag()?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        }
    }

    /**
     * Command dictation language. Hinglish is best recognised by en-IN, and
     * "auto" follows the phone language.
     */
    private fun languageTag(): String? = when (prefs.replyLanguage.value) {
        Prompts.LANGUAGE_HINDI -> "hi-IN"
        Prompts.LANGUAGE_ENGLISH, Prompts.LANGUAGE_HINGLISH -> "en-IN"
        else -> Locale.getDefault().toLanguageTag()
    }

    private fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    private fun containsWakeWord(text: String): Boolean {
        val value = text.lowercase()
        return value.contains("jarvis") ||
            value.contains("jarvus") ||
            value.contains("javis") ||
            value.contains("जार्विस") ||
            value.contains("जारविस") ||
            value.contains("जर्विस")
    }

    private fun handleResults(results: Bundle?) {
        onListeningChanged?.invoke(false)
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?: arrayListOf()
        when (mode) {
            Mode.HOTWORD -> {
                if (texts.any { containsWakeWord(it) }) {
                    failuresInARow = 0
                    mode = Mode.OFF
                    onWake?.invoke()
                } else {
                    scheduleRestart(350)
                }
            }

            Mode.COMMAND -> {
                failuresInARow = 0
                mode = Mode.OFF
                val best = texts.firstOrNull { it.isNotBlank() }
                if (best != null) onTranscript?.invoke(best) else resumeHotword()
            }

            Mode.OFF -> Unit
        }
    }

    private fun handleError(error: Int) {
        onListeningChanged?.invoke(false)
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                mode = Mode.OFF
                wantHotword = false
                onError?.invoke("Microphone permission is required.")
            }

            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (mode == Mode.COMMAND) {
                    mode = Mode.OFF
                    onError?.invoke("I did not catch that. Tap the orb and try again.")
                    resumeHotword()
                } else {
                    scheduleRestart(500)
                }
            }

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> {
                if (mode == Mode.COMMAND) {
                    mode = Mode.OFF
                    onError?.invoke("Speech recognition needs a network connection right now.")
                    resumeHotword()
                } else {
                    // Slow down retries so a dead network does not burn battery.
                    failuresInARow++
                    scheduleRestart(if (failuresInARow > 3) 8000 else 2500)
                }
            }

            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                scheduleRestart(900)
            }

            else -> {
                if (mode == Mode.COMMAND) {
                    mode = Mode.OFF
                    onError?.invoke("Voice input stopped (error $error).")
                }
                scheduleRestart(1200)
            }
        }
    }

    private fun resumeHotword() {
        if (wantHotword) scheduleRestart(700)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            failuresInARow = 0
            onListeningChanged?.invoke(true)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB is roughly -2..10
            onRms?.invoke(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = handleError(error)

        override fun onResults(results: Bundle?) = handleResults(results)

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
