package com.jarvis.ui.vm

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.auth.GoogleAccount
import com.jarvis.core.ServiceLocator
import com.jarvis.core.security.SecureStore
import com.jarvis.data.db.AuditLogEntity
import com.jarvis.data.db.CommandLogEntity
import com.jarvis.data.db.SavedSetupEntity
import com.jarvis.data.db.SetupStepEntity
import com.jarvis.core.terminal.CommandGuard
import com.jarvis.domain.JarvisUiState
import com.jarvis.domain.learning.LearningRepository
import com.jarvis.domain.setup.SetupUiState
import com.jarvis.domain.orb.OrbSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Thin MVVM layer. The single source of truth for conversation + voice is the
 * app-scoped JarvisEngine; these view models only expose it to Compose and run
 * one-shot operations (sign-in, key test, model refresh).
 */

data class AsyncUi(
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val idle: Boolean get() = !loading
}

// ---------------------------------------------------------------------------
// Chat / home
// ---------------------------------------------------------------------------
class ChatViewModel : ViewModel() {

    private val engine = ServiceLocator.engine
    private val prefs = ServiceLocator.prefs

    val state: StateFlow<JarvisUiState> = engine.state
    val micLevel: StateFlow<Float> = engine.micLevel
    val orbSpec: StateFlow<OrbSpec> = engine.orbSpec
    val continuousMode: StateFlow<Boolean> = prefs.continuousMode
    val selectedModel: StateFlow<String> = prefs.selectedModel
    val setupState: StateFlow<SetupUiState> = engine.setupState

    /** Called when the home screen appears: re-arms the wake word. */
    fun onScreenVisible() = engine.onForeground()

    fun onOrbTap() {
        if (state.value.listening) engine.stopListening() else engine.startListening()
    }

    fun stopListening() = engine.stopListening()

    fun send(text: String) = engine.sendText(text)

    fun stopEverything() = engine.emergencyStop()

    fun stopEverythingByVoice() = engine.emergencyStop("Stopped by voice command.")

    fun resume() = engine.resume()

    fun dismissError() = engine.dismissError()

    fun toggleContinuous(enabled: Boolean) = prefs.setContinuousMode(enabled)

    // ------------------------------------------------------ module actions

    fun attach(uri: Uri) = engine.attachFile(uri)

    fun clearAttachment() = engine.clearAttachment()

    fun confirmSetup(approved: Boolean) = engine.confirmSetup(approved)

    fun cancelSetup() = engine.cancelSetup()

    fun dismissSetupReport() = engine.dismissSetupReport()

    fun reportError(message: String) = engine.reportError(message)

    override fun onCleared() {
        // The engine is app-scoped on purpose: closing the screen must not stop
        // the wake word listener or cut off a reply.
        super.onCleared()
    }
}

// ---------------------------------------------------------------------------
// Onboarding: sign-in, integrity, API key
// ---------------------------------------------------------------------------
class SetupViewModel : ViewModel() {

    private val prefs = ServiceLocator.prefs
    private val secure = ServiceLocator.secure
    private val auth = ServiceLocator.auth
    private val integrity = ServiceLocator.integrity
    private val gemini = ServiceLocator.gemini
    private val audit = ServiceLocator.audit

    var signIn by mutableStateOf(AsyncUi())
        private set

    var integrityUi by mutableStateOf(AsyncUi())
        private set

    var keyUi by mutableStateOf(AsyncUi())
        private set

    val hasKey: Boolean get() = secure.has(SecureStore.KEY_GEMINI)

    val maskedKey: String? get() = secure.maskedPreview(SecureStore.KEY_GEMINI)

    fun signInWithGoogle(activityContext: Context, onSuccess: () -> Unit) {
        if (signIn.loading) return
        signIn = AsyncUi(loading = true)
        viewModelScope.launch {
            try {
                val account: GoogleAccount = auth.signIn(activityContext)
                prefs.setAccount(account.email, account.displayName ?: account.email, false)
                audit.info("AUTH", "Google sign-in ok for ${account.email}")
                signIn = AsyncUi(message = "Signed in as ${account.displayName ?: account.email}")
                onSuccess()
            } catch (t: Throwable) {
                val message = t.message ?: "Google sign-in failed."
                audit.warn("AUTH", message)
                signIn = AsyncUi(error = message)
            }
        }
    }

    fun continueInLimitedMode() {
        prefs.setAccount(null, "Limited mode", true)
        audit.warn("AUTH", "limited mode: signed in without Google verification")
        signIn = AsyncUi(message = "Limited mode enabled.")
    }

    fun runIntegrityCheck(onDone: (String) -> Unit = {}) {
        if (integrityUi.loading) return
        integrityUi = AsyncUi(loading = true)
        viewModelScope.launch {
            val outcome = integrity.check()
            prefs.setIntegrity(outcome.status, outcome.detail)
            audit.info("INTEGRITY", "${outcome.status}: ${outcome.detail}")
            integrityUi = AsyncUi(message = outcome.detail)
            onDone(outcome.status)
        }
    }

    /**
     * Global rule: the key is only stored after a real call to Gemini succeeds.
     * The models list is stored at the same time so "set_model" has something
     * to validate against.
     */
    fun testAndSaveKey(rawKey: String, onSuccess: () -> Unit) {
        val key = rawKey.trim()
        if (key.isEmpty()) {
            keyUi = AsyncUi(error = "Paste your Gemini API key first.")
            return
        }
        if (keyUi.loading) return
        keyUi = AsyncUi(loading = true)

        viewModelScope.launch {
            try {
                val models = gemini.listModels(key)
                if (models.isEmpty()) {
                    keyUi = AsyncUi(
                        error = "The key works but returned no text models. Check the key's " +
                            "Generative Language API access."
                    )
                    return@launch
                }
                val ids = models.map { it.id }
                prefs.setAvailableModels(ids)

                val chosen = pickDefaultModel(ids, prefs.selectedModel.value)
                prefs.setSelectedModel(chosen)

                val stored = secure.put(SecureStore.KEY_GEMINI, key)
                if (!stored) {
                    keyUi = AsyncUi(
                        error = "The key works, but this device's secure storage refused to save it."
                    )
                    return@launch
                }

                audit.info("AI", "API key verified and stored; ${ids.size} models found")
                keyUi = AsyncUi(message = "Key verified. ${ids.size} models available, using $chosen.")
                onSuccess()
            } catch (t: Throwable) {
                val message = t.message ?: "Could not verify the key."
                audit.warn("AI", "API key test failed: $message")
                keyUi = AsyncUi(error = message)
            }
        }
    }

    fun testStoredKey(onResult: (Boolean, String) -> Unit) {
        val key = secure.get(SecureStore.KEY_GEMINI)
        if (key.isNullOrBlank()) {
            onResult(false, "No key stored.")
            return
        }
        viewModelScope.launch {
            try {
                val models = gemini.listModels(key)
                prefs.setAvailableModels(models.map { it.id })
                onResult(true, "Key is valid. ${models.size} models available.")
            } catch (t: Throwable) {
                onResult(false, t.message ?: "Key test failed.")
            }
        }
    }

    fun removeKey() {
        secure.remove(SecureStore.KEY_GEMINI)
        prefs.setAvailableModels(emptyList())
        audit.warn("AI", "API key removed by user")
        keyUi = AsyncUi(message = "Key removed.")
    }

    private fun pickDefaultModel(ids: List<String>, current: String): String {
        if (ids.contains(current)) return current
        val preferred = ids.firstOrNull { it == "gemini-2.0-flash" }
        val anyFlash = ids.firstOrNull {
            it.contains("flash") &&
                !it.contains("thinking") &&
                !it.contains("image") &&
                !it.contains("tts") &&
                !it.contains("live")
        }
        return preferred ?: anyFlash ?: ids.first()
    }
}

// ---------------------------------------------------------------------------
// AI models
// ---------------------------------------------------------------------------
class ModelsViewModel : ViewModel() {

    private val prefs = ServiceLocator.prefs
    private val secure = ServiceLocator.secure
    private val gemini = ServiceLocator.gemini
    private val audit = ServiceLocator.audit

    var ui by mutableStateOf(AsyncUi())
        private set

    val models: StateFlow<List<String>> = prefs.availableModels
    val selected: StateFlow<String> = prefs.selectedModel
    val updatedAt: StateFlow<Long> = prefs.modelsUpdatedAt

    fun refresh() {
        val key = secure.get(SecureStore.KEY_GEMINI)
        if (key.isNullOrBlank()) {
            ui = AsyncUi(error = "Add a Gemini API key first (API Keys).")
            return
        }
        if (ui.loading) return
        ui = AsyncUi(loading = true)
        viewModelScope.launch {
            try {
                val list = gemini.listModels(key)
                prefs.setAvailableModels(list.map { it.id })
                audit.info("AI", "model list refreshed (${list.size} models)")
                ui = AsyncUi(message = "${list.size} models found.")
            } catch (t: Throwable) {
                ui = AsyncUi(error = t.message ?: "Could not load models.")
            }
        }
    }

    fun select(model: String) {
        prefs.setSelectedModel(model)
        audit.info("MODEL", "user selected $model")
        ui = AsyncUi(message = "Now using $model.")
    }
}

// ---------------------------------------------------------------------------
// Logs
// ---------------------------------------------------------------------------
class LogsViewModel : ViewModel() {

    private val dao = ServiceLocator.database.auditLogDao()

    private val filter = MutableStateFlow("ALL")

    val activeFilter: StateFlow<String> = filter

    private val allLogs: StateFlow<List<AuditLogEntity>> = dao.recent(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<AuditLogEntity>> = combine(allLogs, filter) { list, selected ->
        if (selected == "ALL") list else list.filter { it.category == selected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: String) {
        filter.value = value
    }

    fun clear() {
        viewModelScope.launch { dao.clearAll() }
    }
}

// ---------------------------------------------------------------------------
// Orb editor (used by Settings > Appearance)
// ---------------------------------------------------------------------------
class OrbViewModel : ViewModel() {

    private val prefs = ServiceLocator.prefs
    private val audit = ServiceLocator.audit

    val spec: StateFlow<OrbSpec> = prefs.orbSpec

    fun updateSpec(update: (OrbSpec) -> OrbSpec) {
        val next = update(prefs.orbSpec.value).sanitized()
        prefs.setOrbSpec(next)
        audit.info("ORB", "manual orb update → ${next.describe()}")
    }

    fun reset() {
        prefs.setOrbSpec(OrbSpec.DEFAULT)
        audit.info("ORB", "orb reset to default")
    }
}

// ---------------------------------------------------------------------------
// Module: Saved Setups
// ---------------------------------------------------------------------------
data class SetupDetail(
    val setup: SavedSetupEntity,
    val steps: List<SetupStepEntity>,
)

class SavedSetupsViewModel : ViewModel() {

    private val setupDao = ServiceLocator.database.setupDao()
    private val stepDao = ServiceLocator.database.setupStepDao()

    val setups: StateFlow<List<SavedSetupEntity>> = setupDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var detail by mutableStateOf<SetupDetail?>(null)
        private set

    fun open(id: Long) {
        viewModelScope.launch {
            val setup = setupDao.byId(id) ?: return@launch
            val steps = stepDao.forSetupOnce(id)
            detail = SetupDetail(setup, steps)
        }
    }

    fun closeDetail() {
        detail = null
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            stepDao.deleteForSetup(id)
            setupDao.delete(id)
            if (detail?.setup?.id == id) detail = null
        }
    }
}

// ---------------------------------------------------------------------------
// Module: Memory (learned corrections)
// ---------------------------------------------------------------------------
class MemoryViewModel : ViewModel() {

    private val repo: LearningRepository = ServiceLocator.learning

    private val query = MutableStateFlow("")

    /** Unfiltered list, used for the honest total count in the header. */
    val allNotes: StateFlow<List<LearningRepository.Note>> = repo.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery: StateFlow<String> = query

    /** Search across the mistake AND the correct behaviour (decrypted in memory). */
    val notes: StateFlow<List<LearningRepository.Note>> = combine(allNotes, query) { list, text ->
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) {
            list
        } else {
            list.filter { note ->
                note.mistake.lowercase().contains(needle) ||
                    note.correct.lowercase().contains(needle) ||
                    note.id.toString() == needle
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    var ui by mutableStateOf(AsyncUi())
        private set

    fun save(id: Long, mistake: String, correct: String, onDone: () -> Unit = {}) {
        if (correct.isBlank()) {
            ui = AsyncUi(error = "Sahi behaviour likhna zaroori hai.")
            return
        }
        viewModelScope.launch {
            val ok = repo.update(id, mistake, correct)
            ui = if (ok) {
                AsyncUi(message = "Note update ho gaya.")
            } else {
                AsyncUi(error = "Note update nahi ho paya.")
            }
            if (ok) onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            ui = AsyncUi(message = "Note delete ho gaya.")
        }
    }

    fun dismissUi() {
        ui = AsyncUi()
    }
}

// ---------------------------------------------------------------------------
// Module: in-app terminal
// ---------------------------------------------------------------------------
data class PendingCommand(
    val command: String,
    val reasons: List<String>,
)

class TerminalViewModel : ViewModel() {

    private val shell = ServiceLocator.shell
    private val logDao = ServiceLocator.database.commandLogDao()
    private val audit = ServiceLocator.audit

    val workDir: File = File(ServiceLocator.appContext.filesDir, "workspace/terminal")
        .apply { mkdirs() }

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    var running by mutableStateOf(false)
        private set

    var pending by mutableStateOf<PendingCommand?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    val log: StateFlow<List<CommandLogEntity>> = logDao.recent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun submit(raw: String) {
        val command = raw.trim()
        if (command.isEmpty() || running) return

        CommandGuard.isBlocked(command)?.let { reason ->
            error = reason
            return
        }
        val verdict = CommandGuard.inspect(command)
        if (verdict.dangerous) {
            pending = PendingCommand(command, verdict.reasons)
            return
        }
        execute(command)
    }

    fun confirm(approved: Boolean) {
        val command = pending?.command
        pending = null
        if (approved && command != null) {
            execute(command)
        } else if (command != null) {
            appendLine("\n\$ $command  [cancelled]")
        }
    }

    fun cancelPending() {
        pending = null
    }

    fun cancelRunning() {
        shell.cancelAll()
        appendLine("\n[stopped by user]")
        running = false
    }

    fun clearOutput() {
        _output.value = ""
    }

    fun clearLog() {
        viewModelScope.launch { logDao.clearAll() }
    }

    fun dismissError() {
        error = null
    }

    private fun execute(command: String) {
        running = true
        error = null
        appendLine("\n\$ $command")
        viewModelScope.launch {
            val outcome = shell.run(
                command = command,
                workDir = workDir,
                onOutput = { chunk -> append(chunk) },
            )
            val stamp = if (outcome.ok) "ok" else "exit ${outcome.exitCode}"
            appendLine("\n[${stamp} · ${outcome.durationMs} ms]")
            running = false
            logDao.insert(
                CommandLogEntity(
                    timestamp = System.currentTimeMillis(),
                    setupId = 0L,
                    command = command,
                    outcome = if (outcome.ok) "ok" else if (outcome.timedOut) "timeout" else "failed",
                    exitCode = outcome.exitCode,
                )
            )
            audit.info("TERMINAL", "$command → ${outcome.exitCode}")
        }
    }

    private fun append(chunk: String) {
        _output.update { current -> (current + chunk).takeLast(40_000) }
    }

    private fun appendLine(line: String) {
        append("$line\n")
    }
}
