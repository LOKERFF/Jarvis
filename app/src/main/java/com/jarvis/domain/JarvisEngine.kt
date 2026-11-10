package com.jarvis.domain

import com.jarvis.core.AuditLogger
import com.jarvis.core.EmergencyStop
import com.jarvis.core.security.Redactor
import com.jarvis.core.security.SecureStore
import com.jarvis.data.db.ChatDao
import com.jarvis.data.db.ChatMessageEntity
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.data.remote.gemini.Content
import com.jarvis.data.remote.gemini.FunctionResponse
import com.jarvis.data.remote.gemini.GeminiApi
import com.jarvis.data.remote.gemini.GeminiException
import com.jarvis.data.remote.gemini.Part
import com.jarvis.domain.model.ChatMessage
import com.jarvis.domain.model.OrbState
import com.jarvis.domain.orb.OrbSpec
import com.jarvis.domain.tools.JarvisTools
import com.jarvis.domain.tools.ToolHost
import com.jarvis.voice.TtsController
import com.jarvis.voice.VoiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class JarvisUiState(
    val orb: OrbState = OrbState.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val status: String = "",
    val error: String? = null,
)

/**
 * The brain.
 *
 * One turn looks like this:
 *   text -> Redactor -> Gemini (tools) -> optional tool calls -> answer
 *        -> audit log -> text-to-speech -> back to wake word.
 *
 * Safety behaviour baked in:
 *  - the emergency stop flag is checked before every network step;
 *  - tool results are validated before they touch the UI;
 *  - every failure produces an honest message, never a fake success.
 */
class JarvisEngine(
    private val api: GeminiApi,
    private val prefs: AppPreferences,
    private val secure: SecureStore,
    private val audit: AuditLogger,
    private val chatDao: ChatDao,
    private val voice: VoiceController,
    private val tts: TtsController,
    private val scope: CoroutineScope,
) : ToolHost {

    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    val orbSpec: StateFlow<OrbSpec> = prefs.orbSpec

    private val tools = JarvisTools(this)
    private val notes = mutableListOf<String>()

    private var turnJob: Job? = null
    private var speechWatchdog: Job? = null
    private var foreground = false
    private var hotwordArmed = false

    init {
        scope.launch {
            chatDao.recent(80).collect { rows ->
                _state.update { current -> current.copy(messages = rows.map { ChatMessage.fromEntity(it) }) }
            }
        }
        wireVoice()
        scope.launch {
            prefs.hotwordEnabled.collect { enabled ->
                if (!enabled) {
                    hotwordArmed = false
                    voice.stopHotword()
                } else {
                    armHotword()
                }
            }
        }
    }

    // ------------------------------------------------------------- lifecycle

    fun onForeground() {
        foreground = true
        armHotword()
    }

    fun onBackground() {
        foreground = false
        hotwordArmed = false
        voice.stopHotword()
        voice.stop()
        _micLevel.value = 0f
        _state.update { it.copy(listening = false) }
    }

    // ---------------------------------------------------------------- voice

    private fun wireVoice() {
        voice.onWake = {
            audit.info("VOICE", "wake word detected")
            startListening()
        }
        voice.onTranscript = { text -> sendText(text) }
        voice.onListeningChanged = { listening ->
            _micLevel.value = if (listening) _micLevel.value else 0f
            _state.update { current ->
                current.copy(
                    listening = listening,
                    orb = when {
                        listening -> OrbState.LISTENING
                        current.speaking -> OrbState.SPEAKING
                        current.busy -> OrbState.THINKING
                        current.orb == OrbState.ERROR -> OrbState.ERROR
                        else -> OrbState.IDLE
                    },
                    status = if (listening) "Listening…" else current.status,
                )
            }
        }
        voice.onRms = { level -> _micLevel.value = level.coerceIn(0f, 1f) }
        voice.onError = { message ->
            audit.warn("VOICE", message)
            _state.update { it.copy(listening = false, status = message) }
        }
        tts.onFinished = { afterSpeaking() }
        tts.onError = { message -> audit.warn("TTS", message) }
    }

    fun startListening() {
        if (EmergencyStop.active.value) {
            _state.update { it.copy(status = "Emergency stop is active. Tap Resume.") }
            return
        }
        if (!secure.has(SecureStore.KEY_GEMINI)) {
            _state.update { it.copy(error = "Add your Gemini API key in Settings › API Keys.") }
            return
        }
        hotwordArmed = false
        voice.stopHotword()
        val started = voice.startCommand()
        if (!started) {
            _state.update { it.copy(status = "Could not open the microphone.") }
        }
    }

    fun stopListening() {
        voice.stop()
        _micLevel.value = 0f
        _state.update { it.copy(listening = false) }
    }

    // ----------------------------------------------------------------- chat

    fun sendText(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        if (isStopCommand(text)) {
            emergencyStop("Stopped by voice command.")
            return
        }
        if (EmergencyStop.active.value) {
            _state.update { it.copy(status = "Emergency stop is active. Tap Resume to continue.") }
            return
        }

        val apiKey = secure.get(SecureStore.KEY_GEMINI)
        if (apiKey.isNullOrBlank()) {
            _state.update {
                it.copy(orb = OrbState.ERROR, error = "Add your Gemini API key in Settings › API Keys.")
            }
            return
        }

        turnJob?.cancel()
        turnJob = scope.launch {
            notes.clear()
            hotwordArmed = false
            voice.stop()
            _state.update {
                it.copy(
                    orb = OrbState.THINKING,
                    busy = true,
                    listening = false,
                    error = null,
                    status = "Thinking…",
                )
            }

            // ---- Global Safety Rule 3: redact before anything leaves the phone
            val redaction = Redactor.redact(text)
            audit.info(
                "AI",
                "→ gemini (${prefs.selectedModel.value}) redactions=${redaction.hits} " +
                    redaction.kinds.joinToString(",") + " | " + redaction.text
            )
            chatDao.insert(
                ChatMessageEntity(
                    timestamp = System.currentTimeMillis(),
                    role = ChatMessage.ROLE_USER,
                    text = redaction.text,
                )
            )

            try {
                val answer = runTurn(apiKey, redaction.text)
                val finalText = withNotes(answer)
                chatDao.insert(
                    ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = ChatMessage.ROLE_ASSISTANT,
                        text = finalText,
                    )
                )
                audit.info("AI", "← gemini | ${finalText.take(400)}")
                _state.update { it.copy(busy = false, status = "") }
                speak(finalText)
            } catch (cancellation: CancellationException) {
                val message = if (EmergencyStop.active.value) {
                    EmergencyStop.note.value.ifBlank { "Stopped." }
                } else {
                    "Stopped."
                }
                _state.update { it.copy(busy = false, orb = OrbState.IDLE, speaking = false, status = message) }
                throw cancellation
            } catch (t: Throwable) {
                if (EmergencyStop.active.value) {
                    _state.update { it.copy(busy = false, orb = OrbState.IDLE, speaking = false) }
                    return@launch
                }
                val message = t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
                audit.error("AI", message)
                _state.update {
                    it.copy(
                        busy = false,
                        orb = OrbState.ERROR,
                        speaking = false,
                        error = message,
                        status = "",
                    )
                }
                voice.stop()
                rearmHotwordSoon()
            }
        }
    }

    fun clearConversation() {
        scope.launch {
            chatDao.clearAll()
            audit.info("CHAT", "conversation cleared by user")
        }
    }

    // ---------------------------------------------------------------- turn

    private suspend fun runTurn(apiKey: String, userText: String): String {
        val contents = mutableListOf(
            Content(role = "user", parts = listOf(Part(text = userText)))
        )
        var answer = ""

        for (step in 0 until MAX_TOOL_STEPS) {
            if (EmergencyStop.active.value) throw GeminiException("Emergency stop.")

            // Read model + prompt on every step so a set_model call takes
            // effect immediately, in the same turn.
            val model = prefs.selectedModel.value
            val systemPrompt = Prompts.systemPrompt(
                userPrompt = prefs.systemPrompt.value,
                language = prefs.replyLanguage.value,
                currentModel = model,
                availableModels = prefs.availableModels.value,
            )

            val response = api.generate(
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                contents = contents,
                tools = tools.declarations(),
                temperature = prefs.temperature.value.toDouble(),
            )

            response.promptFeedback?.blockReason?.let { reason ->
                throw GeminiException("Gemini blocked this request ($reason).")
            }

            val candidate = response.candidates.firstOrNull()
                ?: throw GeminiException("Gemini returned no answer. Try rephrasing.")

            val parts = candidate.content?.parts.orEmpty()
            val visibleText = parts
                .filter { it.thought != true }
                .mapNotNull { it.text }
                .joinToString("")
                .trim()
            if (visibleText.isNotEmpty()) {
                answer = if (answer.isEmpty()) visibleText else answer + "\n\n" + visibleText
            }

            val calls = parts.mapNotNull { it.functionCall }
            if (calls.isEmpty()) break

            contents += Content(role = "model", parts = parts)

            val responses = calls.map { call ->
                val args = call.args ?: JsonObject(emptyMap())
                val result = tools.execute(call.name, args)
                audit.info("TOOL", "${call.name} → $result")
                Part(functionResponse = FunctionResponse(call.name, result))
            }
            contents += Content(role = "user", parts = responses)
        }

        if (answer.isBlank()) {
            answer = "I could not produce an answer for that. Please try again."
        }
        return answer
    }

    private fun withNotes(answer: String): String {
        if (notes.isEmpty()) return answer
        val suffix = notes.joinToString("\n") { "• $it" }
        notes.clear()
        return "$answer\n\n$suffix"
    }

    // -------------------------------------------------------------- speaking

    private fun speak(text: String) {
        if (!prefs.speakReplies.value) {
            _state.update { it.copy(orb = OrbState.IDLE, speaking = false) }
            afterSpeaking()
            return
        }
        _state.update { it.copy(orb = OrbState.SPEAKING, speaking = true) }
        tts.speak(text, prefs.ttsRate.value, prefs.ttsPitch.value)

        // Watchdog: if the TTS engine never reports back (some OEM builds),
        // do not leave the orb stuck in "speaking".
        speechWatchdog?.cancel()
        speechWatchdog = scope.launch {
            delay(2500L + text.length * 110L)
            if (_state.value.speaking) {
                audit.warn("TTS", "no completion callback; clearing the speaking flag")
                _state.update { it.copy(speaking = false, orb = OrbState.IDLE) }
                rearmHotwordSoon()
            }
        }
    }

    private fun afterSpeaking() {
        speechWatchdog?.cancel()
        _state.update {
            it.copy(
                speaking = false,
                orb = if (it.busy) OrbState.THINKING else OrbState.IDLE,
            )
        }
        if (!foreground) return
        if (EmergencyStop.active.value) return
        if (_state.value.busy) return
        if (prefs.continuousMode.value && prefs.speakReplies.value) {
            startListening()
        } else {
            rearmHotwordSoon()
        }
    }

    private fun armHotword() {
        if (!foreground) return
        if (EmergencyStop.active.value) return
        if (!prefs.hotwordEnabled.value) return
        if (_state.value.busy || _state.value.speaking) return
        if (!secure.has(SecureStore.KEY_GEMINI)) return
        if (hotwordArmed) return
        hotwordArmed = true
        voice.startHotword()
    }

    private fun rearmHotwordSoon() {
        hotwordArmed = false
        scope.launch {
            delay(700)
            armHotword()
        }
    }

    // ------------------------------------------------------------ emergency

    fun emergencyStop(reason: String = "Everything stopped by user.") {
        EmergencyStop.engage(reason)
        turnJob?.cancel()
        speechWatchdog?.cancel()
        hotwordArmed = false
        voice.stopHotword()
        voice.stop()
        tts.stop()
        _micLevel.value = 0f
        _state.update {
            it.copy(
                orb = OrbState.IDLE,
                busy = false,
                listening = false,
                speaking = false,
                status = reason,
            )
        }
        audit.warn("SAFETY", "EMERGENCY STOP engaged: $reason")
    }

    fun resume() {
        EmergencyStop.release()
        _state.update { it.copy(status = "Ready.", error = null) }
        audit.info("SAFETY", "emergency stop released")
        rearmHotwordSoon()
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null, orb = OrbState.IDLE) }
    }

    // ------------------------------------------------------------ ToolHost

    override fun currentOrb(): OrbSpec = prefs.orbSpec.value

    override fun applyOrb(spec: OrbSpec): Pair<Boolean, String> {
        val safe = spec.sanitized()
        return try {
            prefs.setOrbSpec(safe)
            audit.info("ORB", "set_orb → ${safe.describe()}")
            val note = "orb → ${safe.shape}"
            notes += note
            true to note
        } catch (t: Throwable) {
            false to "Could not save the orb settings."
        }
    }

    override fun modelList(): List<String> = prefs.availableModels.value

    override fun currentModel(): String = prefs.selectedModel.value

    override fun applyModel(requested: String): Pair<Boolean, String> {
        val clean = requested.removePrefix("models/").trim()
        if (clean.isEmpty()) return false to "No model name was given."
        val available = prefs.availableModels.value
        if (available.isEmpty()) {
            return false to "The model list is empty. Open Settings › AI Models and tap Refresh."
        }
        val match = available.firstOrNull { it.equals(clean, ignoreCase = true) }
            ?: available.firstOrNull { it.endsWith(clean, ignoreCase = true) }
            ?: return false to "Unknown model \"$clean\". Allowed models: ${available.joinToString(", ")}"

        prefs.setSelectedModel(match)
        audit.info("MODEL", "set_model → $match")
        val note = "model → $match"
        notes += note
        return true to note
    }

    // -------------------------------------------------------------- helpers

    private fun isStopCommand(text: String): Boolean {
        val normalised = text.lowercase()
            .replace(Regex("[^a-z\\u0900-\\u097F ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalised in setOf(
            "stop",
            "stop jarvis",
            "jarvis stop",
            "jarvis stop please",
            "emergency stop",
            "band karo",
            "jarvis band karo",
            "ruko",
            "jarvis ruko",
            "sab band karo",
        )
    }

    companion object {
        /** Tool-call rounds allowed per user message. */
        const val MAX_TOOL_STEPS = 4
    }
}
