package com.jarvis.domain.setup

import com.jarvis.core.terminal.CommandGuard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The plan Jarvis writes for itself after reading the uploaded file.
 * Strictly validated before a single command runs.
 */
@Serializable
data class SetupStep(
    val title: String = "",
    val kind: String = KIND_SHELL,
    /** relative path inside the setup workspace (mkdir / write_file) */
    val path: String = "",
    /** file body (write_file only) */
    val content: String = "",
    /** POSIX sh command (shell only) */
    val command: String = "",
    /** optional text that must appear in the output for the step to count as done */
    val expect: String = "",
) {
    companion object {
        const val KIND_MKDIR = "mkdir"
        const val KIND_WRITE = "write_file"
        const val KIND_SHELL = "shell"
        val KINDS = setOf(KIND_MKDIR, KIND_WRITE, KIND_SHELL)
    }
}

@Serializable
data class SetupPlan(
    val title: String = "",
    val summary: String = "",
    val steps: List<SetupStep> = emptyList(),
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** Parses the model's answer, tolerating ```json fences and prose around it. */
        fun parse(raw: String): SetupPlan? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val body = raw.substring(start, end + 1)
            return try {
                json.decodeFromString<SetupPlan>(body)
            } catch (t: Throwable) {
                null
            }
        }
    }
}

object SetupPlanValidator {

    data class Validation(
        val ok: Boolean,
        val errors: List<String>,
        val plan: SetupPlan,
    )

    fun validate(raw: SetupPlan): Validation {
        val errors = mutableListOf<String>()

        if (raw.steps.isEmpty()) errors += "Plan me koi step nahi mila."
        if (raw.steps.size > MAX_STEPS) {
            errors += "Plan me ${raw.steps.size} steps hain, limit $MAX_STEPS hai."
        }

        val cleanSteps = raw.steps.take(MAX_STEPS).mapIndexedNotNull { index, step ->
            val title = step.title.ifBlank { "Step ${index + 1}" }.take(120)
            when (step.kind) {
                SetupStep.KIND_MKDIR -> {
                    val path = safePath(step.path)
                    if (path == null) {
                        errors += "Step ${index + 1}: folder path galat hai (${step.path})."
                        null
                    } else {
                        SetupStep(title = title, kind = SetupStep.KIND_MKDIR, path = path)
                    }
                }

                SetupStep.KIND_WRITE -> {
                    val path = safePath(step.path)
                    when {
                        path == null -> {
                            errors += "Step ${index + 1}: file path galat hai (${step.path})."
                            null
                        }
                        step.content.length > MAX_FILE_CHARS -> {
                            errors += "Step ${index + 1}: file content bahut bada hai."
                            null
                        }
                        else -> SetupStep(
                            title = title,
                            kind = SetupStep.KIND_WRITE,
                            path = path,
                            content = step.content,
                        )
                    }
                }

                SetupStep.KIND_SHELL -> {
                    val command = step.command.trim()
                    val blocked = CommandGuard.isBlocked(command)
                    when {
                        command.isEmpty() -> {
                            errors += "Step ${index + 1}: command khali hai."
                            null
                        }
                        command.length > MAX_COMMAND_CHARS -> {
                            errors += "Step ${index + 1}: command bahut lambi hai."
                            null
                        }
                        blocked != null -> {
                            errors += "Step ${index + 1}: $blocked"
                            null
                        }
                        else -> SetupStep(
                            title = title,
                            kind = SetupStep.KIND_SHELL,
                            command = command,
                            expect = step.expect.take(200),
                        )
                    }
                }

                else -> {
                    errors += "Step ${index + 1}: unknown step type \"${step.kind}\"."
                    null
                }
            }
        }

        if (errors.isEmpty() && cleanSteps.isEmpty()) errors += "Plan khali hai."

        val cleaned = SetupPlan(
            title = raw.title.ifBlank { "Setup" }.take(80),
            summary = raw.summary.take(600),
            steps = cleanSteps,
        )
        return Validation(errors.isEmpty(), errors, cleaned)
    }

    /** Rejects absolute paths, "..", drive letters and control characters. */
    private fun safePath(path: String): String? {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("/")) return null
        if (trimmed.contains("..")) return null
        if (trimmed.contains(":")) return null
        if (trimmed.any { it.code < 32 }) return null
        if (trimmed.length > 200) return null
        return trimmed.removePrefix("./")
    }

    const val MAX_STEPS = 40
    const val MAX_FILE_CHARS = 200_000
    const val MAX_COMMAND_CHARS = 2_000
}
