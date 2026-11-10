package com.jarvis.domain.setup

import android.content.Context
import com.jarvis.core.AuditLogger
import com.jarvis.core.EmergencyStop
import com.jarvis.core.security.SecureStore
import com.jarvis.core.terminal.CommandGuard
import com.jarvis.core.terminal.ShellRunner
import com.jarvis.data.db.ChatDao
import com.jarvis.data.db.ChatMessageEntity
import com.jarvis.data.db.CommandLogEntity
import com.jarvis.data.db.CommandLogDao
import com.jarvis.data.db.SavedSetupEntity
import com.jarvis.data.db.SetupDao
import com.jarvis.data.db.SetupStepDao
import com.jarvis.data.db.SetupStepEntity
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.domain.learning.LearningRepository
import com.jarvis.domain.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class SetupStepUi(
    val index: Int,
    val title: String,
    val kind: String,
    val command: String,
    val status: String,
    val output: String,
)

data class PendingConfirmation(
    val stepIndex: Int,
    val title: String,
    val command: String,
    val reasons: List<String>,
)

data class SetupUiState(
    val running: Boolean = false,
    val setupId: Long = 0L,
    val title: String = "",
    val steps: List<SetupStepUi> = emptyList(),
    val activeIndex: Int = -1,
    val liveOutput: String = "",
    val pending: PendingConfirmation? = null,
    val finished: Boolean = false,
    val success: Boolean = false,
    val summary: String = "",
    val error: String? = null,
)

/**
 * The self-setup brain.
 *
 * Flow: read file -> ask the AI only for a *plan* -> validate it locally ->
 * execute every step in the sandbox -> verify the real result -> retry once ->
 * explain the exact error in Hindi -> persist everything in Room.
 *
 * Nothing is reported as done unless the verification actually passed.
 */
class SelfSetupEngine(
    private val context: Context,
    private val setupDao: SetupDao,
    private val stepDao: SetupStepDao,
    private val commandLogDao: CommandLogDao,
    private val chatDao: ChatDao,
    private val planner: SetupPlanner,
    private val shell: ShellRunner,
    private val intake: FileIntake,
    private val secure: SecureStore,
    private val prefs: AppPreferences,
    private val learning: LearningRepository,
    private val audit: AuditLogger,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var confirmDeferred: CompletableDeferred<Boolean>? = null
    private val executedCommands = mutableSetOf<String>()

    // ------------------------------------------------------------------ public

    fun start(attachment: FileIntake.Attachment, userCommand: String) {
        if (_state.value.running) {
            postAssistant("Ek setup pehle se chal raha hai. Pehle use pura hone do ya rok do.")
            return
        }
        if (EmergencyStop.active.value) {
            postAssistant("Emergency stop active hai. Pehle Resume dabao, phir setup karunga.")
            return
        }
        val apiKey = secure.get(SecureStore.KEY_GEMINI)
        if (apiKey.isNullOrBlank()) {
            postAssistant("Gemini API key nahi mili. Settings › API Keys me key daalo, phir setup karunga.")
            return
        }

        val model = prefs.selectedModel.value
        job = scope.launch {
            executedCommands.clear()
            _state.value = SetupUiState(running = true, title = "File padh raha hoon…", activeIndex = -1)

            val workspace = File(
                context.filesDir,
                "workspace/setup_${System.currentTimeMillis()}",
            ).apply { mkdirs() }

            try {
                // ---- 1. materialise the upload inside the sandbox
                val details: String
                val sourceKind: String
                withContext(Dispatchers.IO) {
                    runCatching { attachment.file.copyTo(File(workspace, attachment.displayName), overwrite = true) }
                }
                when (attachment.kind) {
                    FileIntake.Kind.ZIP -> {
                        sourceKind = "zip archive"
                        _state.update { it.copy(title = "Zip khol raha hoon…") }
                        val count = withContext(Dispatchers.IO) { intake.extractZip(attachment, workspace) }
                        val tree = withContext(Dispatchers.IO) { intake.treeOf(workspace) }
                        details = "A zip with $count files was extracted into the workspace.\n\n$tree"
                        audit.info("SETUP", "zip extracted: $count files")
                    }

                    FileIntake.Kind.TEXT -> {
                        sourceKind = "text / code / config"
                        val preview = withContext(Dispatchers.IO) {
                            intake.textPreview(attachment.file)
                        } ?: ""
                        details = "File content (already redacted):\n$preview"
                    }

                    FileIntake.Kind.IMAGE, FileIntake.Kind.PDF, FileIntake.Kind.BINARY -> {
                        sourceKind = attachment.kind.name.lowercase()
                        details = "Binary document of ${attachment.sizeBytes} bytes. You cannot see its " +
                            "content. Put it in the workspace, and plan only safe generic steps plus a " +
                            "SETUP_NOTES.md that explains what the user must do with this document."
                    }
                }

                // ---- 2. ask for a plan (planning only, no execution)
                _state.update { it.copy(title = "Setup plan bana raha hoon…") }
                val rules = learning.activeRules()
                val plan = planner.plan(
                    apiKey = apiKey,
                    model = model,
                    fileName = attachment.displayName,
                    kind = sourceKind,
                    fileDetails = details,
                    userCommand = userCommand,
                    learnedRules = rules,
                )

                // ---- 3. persist the job before running anything
                val now = System.currentTimeMillis()
                val setupId = setupDao.insert(
                    SavedSetupEntity(
                        name = plan.title,
                        sourceName = attachment.displayName,
                        sourceKind = sourceKind,
                        workspacePath = workspace.absolutePath,
                        status = STATUS_RUNNING,
                        summary = plan.summary,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                stepDao.insertAll(
                    plan.steps.mapIndexed { index, step ->
                        SetupStepEntity(
                            setupId = setupId,
                            stepIndex = index,
                            title = step.title,
                            kind = step.kind,
                            command = step.command,
                            path = step.path,
                            status = "pending",
                            output = "",
                            exitCode = 0,
                            updatedAt = now,
                        )
                    }
                )

                _state.value = SetupUiState(
                    running = true,
                    setupId = setupId,
                    title = plan.title,
                    steps = plan.steps.mapIndexed { index, step ->
                        SetupStepUi(
                            index = index,
                            title = step.title,
                            kind = step.kind,
                            command = step.command,
                            status = "pending",
                            output = "",
                        )
                    },
                    summary = plan.summary,
                )

                postAssistant(
                    "Setup plan taiyaar hai: ${plan.title}\n" +
                        "${plan.summary}\n\n" +
                        "${plan.steps.size} steps hain. Har step ka asli result check karunga."
                )
                audit.info("SETUP", "#$setupId plan: ${plan.steps.size} steps")

                // ---- 4. execute + verify
                executePlan(setupId, plan, workspace)
            } catch (cancelled: CancellationException) {
                shell.cancelAll()
                finishAborted(workspace)
                throw cancelled
            } catch (t: Throwable) {
                val message = t.message ?: "Setup start nahi ho paya."
                audit.error("SETUP", message)
                _state.update {
                    it.copy(running = false, finished = true, success = false, error = message)
                }
                postAssistant("Setup shuru nahi ho paya.\nWajah: $message")
            }
        }
    }

    fun confirm(approved: Boolean) {
        confirmDeferred?.complete(approved)
    }

    /** Emergency stop / user cancel: kill the shell and mark the job cancelled. */
    fun cancel(reason: String = "Setup rok diya gaya.") {
        val current = _state.value
        shell.cancelAll()
        confirmDeferred?.complete(false)
        job?.cancel()
        if (current.setupId > 0L) {
            scope.launch {
                setupDao.updateStatus(
                    current.setupId,
                    STATUS_CANCELLED,
                    "$reason Aage ke steps nahi chalaye.",
                    System.currentTimeMillis(),
                )
            }
        }
        _state.update {
            it.copy(
                running = false,
                finished = true,
                success = false,
                pending = null,
                error = reason,
                summary = reason,
            )
        }
        postAssistant("$reason Maine chal rahi commands band kar di hain.")
        audit.warn("SETUP", "cancelled: $reason")
    }

    fun dismiss() {
        if (_state.value.running) return
        _state.value = SetupUiState()
    }

    // --------------------------------------------------------------- execution

    private suspend fun executePlan(setupId: Long, plan: SetupPlan, workspace: File) {
        val rows = stepDao.forSetupOnce(setupId).associateBy { it.stepIndex }
        val deadline = System.currentTimeMillis() + MAX_TOTAL_MS
        var failedIndex = -1
        var failedOutcome: StepRun? = null
        var failedStep: SetupStep? = null

        for ((index, step) in plan.steps.withIndex()) {
            if (EmergencyStop.active.value) {
                throw CancellationException("emergency stop")
            }
            if (System.currentTimeMillis() > deadline) {
                failedIndex = index
                failedOutcome = StepRun(false, "Task timeout: setup 6 minute se zyada chal gaya.", -2, false)
                failedStep = step
                break
            }

            val rowId = rows[index]?.id ?: 0L

            // ---- duplicate command prevention
            if (step.kind == SetupStep.KIND_SHELL) {
                val normalised = CommandGuard.normalise(step.command)
                if (!executedCommands.add(normalised)) {
                    updateStep(rowId, SKIPPED, "Duplicate command, dobara nahi chalaya.", 0)
                    _state.update { it.markStep(index, SKIPPED, "Duplicate command, skip kiya.") }
                    continue
                }
            }

            // ---- confirmation for dangerous steps
            if (step.kind == SetupStep.KIND_SHELL) {
                val verdict = CommandGuard.inspect(step.command)
                if (verdict.dangerous) {
                    _state.update {
                        it.copy(
                            pending = PendingConfirmation(
                                stepIndex = index,
                                title = step.title,
                                command = step.command,
                                reasons = verdict.reasons,
                            ),
                            steps = it.markStep(index, WAITING, "").steps,
                        )
                    }
                    updateStep(rowId, WAITING, "", 0)
                    val approved = awaitConfirmation()
                    _state.update { it.copy(pending = null) }
                    if (!approved) {
                        updateStep(rowId, SKIPPED, "User ne mana kar diya.", -1)
                        _state.update { it.markStep(index, SKIPPED, "Aapne mana kar diya, skip kiya.") }
                        continue
                    }
                }
            }

            // ---- run once, retry once
            _state.update { it.copy(activeIndex = index, liveOutput = "") }
            updateStep(rowId, RUNNING, "", 0)
            _state.update { it.markStep(index, RUNNING, "") }

            var outcome = runStep(step, workspace) { chunk -> appendLive(chunk) }
            if (!outcome.ok && !outcome.timedOut) {
                appendLive("\n[retry] Ek baar phir try kar raha hoon…\n")
                outcome = runStep(step, workspace) { chunk -> appendLive(chunk) }
            }

            val status = if (outcome.ok) DONE else FAILED
            updateStep(rowId, status, outcome.output.take(6000), outcome.exitCode)
            _state.update { it.markStep(index, status, outcome.output.takeLast(1500)) }

            if (step.kind == SetupStep.KIND_SHELL) {
                commandLogDao.insert(
                    CommandLogEntity(
                        timestamp = System.currentTimeMillis(),
                        setupId = setupId,
                        command = step.command,
                        outcome = when {
                            outcome.ok -> "ok"
                            outcome.timedOut -> "timeout"
                            else -> "error"
                        },
                        exitCode = outcome.exitCode,
                    )
                )
            }

            if (!outcome.ok) {
                failedIndex = index
                failedOutcome = outcome
                failedStep = step
                break
            }
        }

        finishRun(setupId, plan, failedIndex, failedStep, failedOutcome, workspace)
    }

    private data class StepRun(
        val ok: Boolean,
        val output: String,
        val exitCode: Int,
        val timedOut: Boolean,
    )

    private suspend fun runStep(
        step: SetupStep,
        workspace: File,
        onChunk: (String) -> Unit,
    ): StepRun = withContext(Dispatchers.IO) {
        when (step.kind) {
            SetupStep.KIND_MKDIR -> {
                val dir = File(workspace, step.path)
                val created = dir.mkdirs() || dir.isDirectory
                val verified = created && dir.isDirectory
                StepRun(
                    ok = verified,
                    output = if (verified) {
                        "Folder ready: ${dir.absolutePath}"
                    } else {
                        "Folder nahi ban paya: ${dir.absolutePath}"
                    },
                    exitCode = if (verified) 0 else 1,
                    timedOut = false,
                )
            }

            SetupStep.KIND_WRITE -> {
                val file = File(workspace, step.path)
                val bytes = step.content.toByteArray(Charsets.UTF_8)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                val verified = file.isFile && file.length() == bytes.size.toLong()
                StepRun(
                    ok = verified,
                    output = if (verified) {
                        "File likhi gayi aur check ho gayi: ${file.absolutePath} (${bytes.size} bytes)"
                    } else {
                        "File likhne ke baad size match nahi hua: ${file.absolutePath}"
                    },
                    exitCode = if (verified) 0 else 1,
                    timedOut = false,
                )
            }

            else -> {
                val run = shell.run(
                    command = step.command,
                    workDir = workspace,
                    timeoutMs = COMMAND_TIMEOUT_MS,
                    onOutput = onChunk,
                )
                val expectOk = step.expect.isBlank() ||
                    run.output.contains(step.expect, ignoreCase = true)
                StepRun(
                    ok = run.ok && expectOk,
                    output = buildString {
                        appendLine(run.output.ifBlank { "(no output)" })
                        if (!expectOk) {
                            appendLine(
                                "Expected text \"${step.expect}\" output me nahi mila, " +
                                    "isliye step fail mana gaya."
                            )
                        }
                        appendLine("exit code: ${run.exitCode}")
                    },
                    exitCode = run.exitCode,
                    timedOut = run.timedOut,
                )
            }
        }
    }

    private suspend fun awaitConfirmation(timeoutMs: Long = MAX_CONFIRM_WAIT_MS): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        confirmDeferred = null
        return result
    }

    // ------------------------------------------------------------------ finish

    private suspend fun finishRun(
        setupId: Long,
        plan: SetupPlan,
        failedIndex: Int,
        failedStep: SetupStep?,
        failedOutcome: StepRun?,
        workspace: File,
    ) {
        val success = failedIndex < 0
        val now = System.currentTimeMillis()

        val message = if (success) {
            buildString {
                appendLine("Setup pura ho gaya — maine har step ka result khud verify kiya.")
                appendLine("Setup: ${plan.title}")
                appendLine("Workspace: ${workspace.absolutePath}")
                appendLine("Saved Setups me ye setup permanent save ho gaya hai.")
                appendLine("Agar koi API key placeholder hai to Settings me bhar dena.")
            }
        } else {
            val index = failedIndex.coerceAtLeast(0)
            val step = failedStep
            val outcome = failedOutcome
            buildString {
                appendLine("Setup step ${index + 1} par ruk gaya — jhoot nahi bolunga, aage kuch nahi chala.")
                if (step != null) {
                    appendLine("Step: ${step.title}")
                    if (step.command.isNotBlank()) appendLine("Command: ${step.command.take(300)}")
                }
                if (outcome != null) {
                    appendLine("Wajah: ${hindiReason(outcome)}")
                    val realError = outcome.output.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .takeLast(3)
                        .joinToString("\n")
                    if (realError.isNotBlank()) appendLine("Asli error:\n$realError")
                }
                appendLine("Maine ek baar retry kiya tha, phir bhi fail hua.")
                appendLine("Aap Saved Setups me poora log dekh sakte ho.")
            }
        }

        setupDao.updateStatus(
            setupId,
            if (success) STATUS_SUCCESS else STATUS_FAILED,
            message.take(1500),
            now,
        )
        _state.update {
            it.copy(
                running = false,
                finished = true,
                success = success,
                activeIndex = -1,
                pending = null,
                summary = message,
                error = if (success) null else message,
            )
        }
        postAssistant(message)
        audit.log(
            "SETUP",
            "finished #$setupId success=$success steps=${plan.steps.size}",
            if (success) AuditLogger.Level.INFO else AuditLogger.Level.WARN,
        )
    }

    private suspend fun finishAborted(workspace: File) {
        val current = _state.value
        if (current.setupId > 0L) {
            setupDao.updateStatus(
                current.setupId,
                STATUS_CANCELLED,
                "Emergency stop ne setup rok diya. Workspace: ${workspace.absolutePath}",
                System.currentTimeMillis(),
            )
        }
        _state.update {
            it.copy(
                running = false,
                finished = true,
                success = false,
                pending = null,
                error = "Emergency stop",
            )
        }
    }

    private fun hindiReason(outcome: StepRun): String = when {
        outcome.timedOut ->
            "command ka time limit ($COMMAND_TIMEOUT_MS ms) khatam ho gaya, isliye maine use band kar diya."
        outcome.output.contains("Permission denied", true) ->
            "file permission ki problem hai (Permission denied)."
        outcome.output.contains("not found", true) ->
            "jo tool ya file chahiye thi wo phone me nahi hai (not found)."
        outcome.output.contains("No space left", true) ->
            "phone ka storage bhar gaya hai (No space left)."
        outcome.output.contains("Read-only file system", true) ->
            "ye folder read-only hai, wahan likh nahi sakte."
        else -> "command ne error diya (exit code ${outcome.exitCode})."
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun updateStep(
        rowId: Long,
        status: String,
        output: String,
        exitCode: Int,
    ) {
        if (rowId == 0L) return
        runCatching {
            stepDao.updateResult(rowId, status, output, exitCode, System.currentTimeMillis())
        }
    }

    private fun appendLive(chunk: String) {
        _state.update { current ->
            val merged = (current.liveOutput + chunk).takeLast(MAX_LIVE_CHARS)
            current.copy(liveOutput = merged)
        }
    }

    private fun SetupUiState.markStep(index: Int, status: String, output: String): SetupUiState =
        copy(
            steps = steps.map { step ->
                if (step.index == index) {
                    step.copy(
                        status = status,
                        output = if (output.isBlank()) step.output else output,
                    )
                } else {
                    step
                }
            }
        )

    private fun postAssistant(text: String) {
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
        }
    }

    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"

        const val PENDING = "pending"
        const val RUNNING = "running"
        const val DONE = "done"
        const val FAILED = "failed"
        const val SKIPPED = "skipped"
        const val WAITING = "waiting_confirm"

        const val COMMAND_TIMEOUT_MS = 60_000L
        const val MAX_TOTAL_MS = 6L * 60 * 1000
        const val MAX_CONFIRM_WAIT_MS = 5L * 60 * 1000
        const val MAX_LIVE_CHARS = 4_000
    }
}
