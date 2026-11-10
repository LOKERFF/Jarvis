package com.jarvis.domain

import android.net.Uri
import com.jarvis.core.AuditLogger
import com.jarvis.core.EmergencyStop
import com.jarvis.core.security.Redactor
import com.jarvis.core.security.SecureStore
import com.jarvis.core.terminal.ShellRunner
import com.jarvis.data.db.ChatDao
import com.jarvis.data.db.ChatMessageEntity
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.data.remote.gemini.Content
import com.jarvis.data.remote.gemini.FunctionResponse
import com.jarvis.data.remote.gemini.GeminiApi
import com.jarvis.data.remote.gemini.GeminiException
import com.jarvis.data.remote.gemini.Part
import com.jarvis.domain.artifacts.ArtifactRepository
import com.jarvis.domain.learning.LearningRepository
import com.jarvis.domain.model.ChatMessage
import com.jarvis.domain.model.OrbState
import com.jarvis.domain.orb.OrbSpec
import com.jarvis.domain.setup.FileIntake
import com.jarvis.domain.setup.SelfSetupEngine
import com.jarvis.domain.setup.SetupUiState
import com.jarvis.domain.tools.JarvisTools
import com.jarvis.domain.tools.ToolHost
import com.jarvis.voice.TtsController
import com.jarvis.voice.VoiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class AttachmentInfo(
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val path: String,
)

data class JarvisUiState(
    val orb: OrbState = OrbState.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val attachment: AttachmentInfo? = null,
)

private data class PendingMistake(
    val whatWentWrong: String,
)

/**
 * The brain.
 *
 * Phase 1 turn:
 *   text -> Redactor -> Gemini (tools) -> tool calls -> answer -> audit -> TTS
 *
 * Add-on routing (before any AI call):
 *   "jarvis stop"            -> emergency stop
 *   pending learning answer  -> save the correction as an encrypted note
 *   "ye galat hai"           -> ask one short question, then save a note
 *   "setup kar lo" + file    -> SelfSetupEngine (plan, execute, verify, persist)
 *   everything else          -> normal Gemini turn with the learned rules injected
 */
class JarvisEngine(
    private val api: GeminiApi,
    private val prefs: AppPreferences,
    private val secure: SecureStore,
    private val audit: AuditLogger,
    private val chatDao: ChatDao,
    private val voice: VoiceController,
    private val tts: TtsController,
    private val intake: FileIntake,
    private val selfSetup: SelfSetupEngine,
    private val learning: LearningRepository,
    private val artifacts: ArtifactRepository,
    private val shell: ShellRunner,
    private val scope: CoroutineScope,
) : ToolHost {

    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    val orbSpec: StateFlow<OrbSpec> = prefs.orbSpec

    val setupState: StateFlow<SetupUiState> get() = selfSetup.state

    private val tools = JarvisTools(this)
    private val notes = mutableListOf<String>()

    private var turnJob: Job? = null
    private var speechWatchdog: Job? = null
    private var foreground = false
    private var hotwordArmed = false
    private var pendingAttachment: FileIntake.Attachment? = null
    private var pendingMistake: PendingMistake? = null

    init {
        scope.launch {
            chatDao.recent(80).collect { rows ->
                _state.update { current ->
                    current.copy(messages = rows.map { ChatMessage.fromEntity(it) })
                }
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

    // -------------------------------------------------------- file attachment

    fun attachFile(uri: Uri) {
        if (_state.value.busy) {
            say("Abhi ek kaam chal raha hai. Pura hone ke baad file attach karo.", speakIt = false)
            return
        }
        scope.launch {
            _state.update { it.copy(status = "File padh raha hoon…") }
            val attachment = withContext(Dispatchers.IO) {
                runCatching { intake.intake(uri) }.getOrNull()
            }
            if (attachment == null) {
                _state.update {
                    it.copy(
                        status = "",
                        error = "File padhi nahi ja saki. Doosri file try karo ya storage permission check karo.",
                    )
                }
                return@launch
            }
            pendingAttachment = attachment
            _state.update {
                it.copy(
                    status = "File attach ho gayi",
                    error = null,
                    attachment = AttachmentInfo(
                        name = attachment.displayName,
                        kind = attachment.kind.name,
                        sizeBytes = attachment.sizeBytes,
                        path = attachment.file.absolutePath,
                    ),
                )
            }
            audit.info("FILE", "attached ${attachment.displayName} (${attachment.kind}, ${attachment.sizeBytes}B)")
        }
    }

    fun clearAttachment() {
        pendingAttachment = null
        _state.update { it.copy(attachment = null) }
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

        // 1. an answer to the learning question we just asked
        val awaiting = pendingMistake
        if (awaiting != null) {
            pendingMistake = null
            saveCorrection(awaiting.whatWentWrong, text)
            return
        }

        // 2. the user says something was wrong
        if (isMistakeCommand(text)) {
            handleMistake(text)
            return
        }

        // 3. setup / save command for the attached file
        if (isSetupCommand(text)) {
            startSetup(text)
            return
        }

        // 4. normal AI turn
        val apiKey = secure.get(SecureStore.KEY_GEMINI)
        if (apiKey.isNullOrBlank()) {
            _state.update {
                it.copy(orb = OrbState.ERROR, error = "Add your Gemini API key in Settings › API Keys.")
            }
            return
        }
        runAiTurn(apiKey, text)
    }

    fun clearConversation() {
        scope.launch {
            chatDao.clearAll()
            audit.info("CHAT", "conversation cleared by user")
        }
    }

    // ----------------------------------------------------------- setup module

    private fun startSetup(command: String) {
        val attachment = pendingAttachment
        if (attachment == null) {
            say(
                "Pehle 📎 button se file attach karo, phir \"setup kar lo\" likho. " +
                    "Bina file ke main setup shuru nahi karunga.",
                speakIt = true,
            )
            return
        }
        pendingAttachment = null
        _state.update { it.copy(attachment = null) }
        audit.info("SETUP", "user command: $command for ${attachment.displayName}")
        selfSetup.start(attachment, command)
    }

    fun confirmSetup(approved: Boolean) = selfSetup.confirm(approved)

    fun cancelSetup() = selfSetup.cancel("Aapne setup rok diya.")

    fun dismissSetupReport() = selfSetup.dismiss()

    // ------------------------------------------------------------- learning

    private fun handleMistake(text: String) {
        val lastAssistant = _state.value.messages
            .lastOrNull { it.role == ChatMessage.Role.ASSISTANT }
            ?.text
            ?.take(400)
            ?: "pichla jawab"

        if (looksLikeFullCorrection(text)) {
            saveCorrection(lastAssistant, text)
        } else {
            pendingMistake = PendingMistake(lastAssistant)
            say(
                "Theek hai, note kar lunga. Chhota sa batao — exactly kya galat tha aur aage kya karna chahiye?",
                speakIt = true,
            )
        }
    }

    private fun saveCorrection(mistake: String, correction: String) {
        scope.launch {
            val result = learning.remember(mistake, correction)
            result.fold(
                onSuccess = { id ->
                    say("Yaad rakh liya, aage se ye nahi karunga. (Note #$id)", speakIt = true)
                    _state.update { it.copy(status = "Learning note saved") }
                    audit.info("LEARN", "correction saved from chat (#$id)")
                },
                onFailure = { error ->
                    say(
                        "Note save nahi ho paya. Wajah: ${error.message ?: "secure storage error"}.",
                        speakIt = false,
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------- AI turn

    private fun runAiTurn(apiKey: String, text: String) {
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
                    status = "Soch raha hoon…",
                )
            }

            // Global Safety Rule 3: redact before anything leaves the phone
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

            val attachmentContext = buildAttachmentContext()
            try {
                val answer = runTurn(apiKey, redaction.text, attachmentContext)
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
                _state.update {
                    it.copy(busy = false, orb = OrbState.IDLE, speaking = false, status = message)
                }
                throw cancellation
            } catch (t: Throwable) {
                if (EmergencyStop.active.value) {
                    _state.update { it.copy(busy = false, orb = OrbState.IDLE, speaking = false) }
                    return@launch
                }
                val message = t.message?.takeIf { it.isNotBlank() } ?: "Kuch galat ho gaya."
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
            } finally {
                if (pendingAttachment != null) {
                    // the attachment was already used for this turn
                    pendingAttachment = null
                    _state.update { it.copy(attachment = null) }
                }
            }
        }
    }

    private suspend fun buildAttachmentContext(): String? {
        val attachment = pendingAttachment ?: return null
        val preview = withContext(Dispatchers.IO) {
            when (attachment.kind) {
                FileIntake.Kind.TEXT -> intake.textPreview(attachment.file).orEmpty()
                FileIntake.Kind.ZIP -> "(zip) " + intake.treeOf(attachment.file)
                else -> "(binary file, content not readable)"
            }
        }
        return "Attached file: ${attachment.displayName} (${attachment.kind}, " +
            "${attachment.sizeBytes} bytes)\n$preview"
    }

    private suspend fun runTurn(apiKey: String, userText: String, attachmentContext: String?): String {
        val userParts = mutableListOf<Part>()
        if (attachmentContext != null) {
            userParts += Part(text = attachmentContext)
        }
        userParts += Part(text = userText)

        val contents = mutableListOf(Content(role = "user", parts = userParts))
        var answer = ""

        for (step in 0 until MAX_TOOL_STEPS) {
            if (EmergencyStop.active.value) throw GeminiException("Emergency stop.")

            val model = prefs.selectedModel.value
            val learnedRules = learning.activeRules()
            val systemPrompt = Prompts.systemPrompt(
                userPrompt = prefs.systemPrompt.value,
                language = prefs.replyLanguage.value,
                currentModel = model,
                availableModels = prefs.availableModels.value,
                learnedRules = learnedRules,
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

            val responses = mutableListOf<Part>()
            for (call in calls) {
                val args = call.args ?: JsonObject(emptyMap())
                val result = tools.execute(call.name, args)
                audit.info("TOOL", "${call.name} → $result")
                responses += Part(functionResponse = FunctionResponse(call.name, result))
            }
            contents += Content(role = "user", parts = responses)
        }

        if (answer.isBlank()) {
            answer = "Is baar jawab nahi bana. Dobara try karo."
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

    private fun say(text: String, speakIt: Boolean) {
        scope.launch {
            runCatching {
                chatDao.insert(
                    ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = ChatMessage.ROLE_ASSISTANT,
                        text = text,
                    )
                )
            }
            if (speakIt) speak(text)
        }
    }

    private fun speak(text: String) {
        if (!prefs.speakReplies.value) {
            _state.update { it.copy(orb = OrbState.IDLE, speaking = false) }
            afterSpeaking()
            return
        }
        _state.update { it.copy(orb = OrbState.SPEAKING, speaking = true) }
        tts.speak(text, prefs.ttsRate.value, prefs.ttsPitch.value)

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
        // module rule: emergency stop cancels a running setup and kills commands
        if (selfSetup.state.value.running) {
            selfSetup.cancel("Emergency stop ne setup rok diya.")
        }
        shell.cancelAll()
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

    override suspend fun generateFile(name: String, content: String): Pair<Boolean, String> {
        return artifacts.createTextFile(name, content).fold(
            onSuccess = { created ->
                chatDao.insert(
                    ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = ChatMessage.ROLE_ASSISTANT,
                        text = "Ye aapki file hai: ${created.artifact.name}",
                        artifactName = created.artifact.name,
                        artifactPath = created.artifact.path,
                        artifactMime = created.artifact.mime,
                    )
                )
                true to "File ban gayi aur chat me bhej di: ${created.artifact.name}"
            },
            onFailure = { error ->
                false to "File nahi ban payi: ${error.message ?: "unknown error"}"
            },
        )
    }

    override suspend fun generateVideo(
        title: String,
        lines: List<String>,
        seconds: Int,
    ): Pair<Boolean, String> {
        _state.update { it.copy(status = "Video bana raha hoon…") }
        val result = artifacts.createVideo(title, lines, seconds)
        _state.update { it.copy(status = "") }
        return result.fold(
            onSuccess = { created ->
                chatDao.insert(
                    ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = ChatMessage.ROLE_ASSISTANT,
                        text = "Video taiyaar hai: ${created.artifact.name} (${seconds}s)",
                        artifactName = created.artifact.name,
                        artifactPath = created.artifact.path,
                        artifactMime = created.artifact.mime,
                    )
                )
                true to "Video ban gaya aur chat me player ke saath bhej diya."
            },
            onFailure = { error ->
                false to (error.message ?: "Video nahi ban paya.")
            },
        )
    }

    // -------------------------------------------------------------- helpers

    private fun isStopCommand(text: String): Boolean {
        val normalised = normalise(text)
        return normalised in setOf(
            "stop", "stop jarvis", "jarvis stop", "jarvis stop please", "emergency stop",
            "band karo", "jarvis band karo", "ruko", "jarvis ruko", "sab band karo",
        )
    }

    private fun isSetupCommand(text: String): Boolean {
        val normalised = normalise(text)
        return SETUP_PHRASES.any { normalised.contains(it) }
    }

    private fun isMistakeCommand(text: String): Boolean {
        val normalised = normalise(text)
        return MISTAKE_PHRASES.any { normalised.contains(it) }
    }

    /** "ye galat hai" alone is not enough detail; anything longer probably is. */
    private fun looksLikeFullCorrection(text: String): Boolean {
        val normalised = normalise(text)
        if (normalised.length > 30) return true
        return listOf("karo", "karna", "likho", "bolo", "hona chahiye", "chahiye").any {
            normalised.contains(it)
        } && normalised.length > 18
    }

    private fun normalise(text: String): String = text.lowercase()
        .replace(Regex("[^a-z\\u0900-\\u097F ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val MAX_TOOL_STEPS = 4

        val SETUP_PHRASES = listOf(
            "setup kar lo", "setup karo", "setup kar do", "set up kar lo", "set up karo",
            "set up kar do", "save kar lo", "save karo", "save kar do",
            "isko setup", "ise setup", "file ko setup", "install kar lo", "install kar do",
            "configure kar do", "config kar do",
        )

        val MISTAKE_PHRASES = listOf(
            "ye galat hai", "yah galat hai", "yeh galat hai", "galat hai", "ye glt hai",
            "ise aise sudharo", "ise sudharo", "ye mat karo", "aisa mat karo", "aise mat karo",
            "galat kiya", "galti hai", "mistake hai", "ye wrong hai",
        )
    }
}
