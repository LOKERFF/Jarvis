package com.jarvis.domain.setup

import com.jarvis.data.remote.gemini.Content
import com.jarvis.data.remote.gemini.GeminiApi
import com.jarvis.data.remote.gemini.GeminiException
import com.jarvis.data.remote.gemini.Part

/**
 * Turns "setup kar lo" + an uploaded file into a machine-readable plan.
 *
 * Only the model's *plan* is borrowed from the AI; the execution, the
 * verification and the failure handling are all local and deterministic.
 */
class SetupPlanner(private val api: GeminiApi) {

    suspend fun plan(
        apiKey: String,
        model: String,
        fileName: String,
        kind: String,
        fileDetails: String,
        userCommand: String,
        learnedRules: List<String>,
    ): SetupPlan {
        val rulesBlock = if (learnedRules.isEmpty()) {
            "(no learned corrections yet)"
        } else {
            learnedRules.joinToString("\n") { "- $it" }
        }

        val userPrompt = buildString {
            appendLine("User command: $userCommand")
            appendLine("File: $fileName (type: $kind)")
            appendLine()
            appendLine("What I know about the file:")
            appendLine(fileDetails.take(12_000))
            appendLine()
            appendLine("Learned corrections from the user that you MUST follow:")
            appendLine(rulesBlock)
        }

        val response = api.generate(
            apiKey = apiKey,
            model = model,
            systemPrompt = SYSTEM_PROMPT,
            contents = listOf(Content(role = "user", parts = listOf(Part(text = userPrompt)))),
            tools = emptyList(),
            temperature = 0.15,
        )

        val text = response.candidates.firstOrNull()
            ?.content?.parts
            ?.filter { it.thought != true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            throw GeminiException("Setup plan nahi ban paya. Dobara try karein.")
        }

        val parsed = SetupPlan.parse(text)
            ?: throw GeminiException(
                "AI ka plan samajh nahi aaya (JSON galat tha). Dobara \"setup kar lo\" likhein."
            )

        val validation = SetupPlanValidator.validate(parsed)
        if (!validation.ok) {
            throw GeminiException(
                "Plan safe nahi tha, isliye kuch bhi run nahi kiya:\n" +
                    validation.errors.take(4).joinToString("\n") { "- $it" }
            )
        }
        return validation.plan
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You are the setup planner of JARVIS, an Android assistant.
            You receive one uploaded file (code, config, zip listing, or document)
            and you must decide the setup steps yourself. The phone will execute
            your plan in a private sandbox with /system/bin/sh (Android toybox).

            Return ONLY valid JSON, no prose, no markdown fences:
            {
              "title": "short title",
              "summary": "2-3 sentences in simple Hindi about what will be set up",
              "steps": [
                {"title":"short Hindi title","kind":"mkdir","path":"config"},
                {"title":"...","kind":"write_file","path":"config/app.conf","content":"..."},
                {"title":"...","kind":"shell","command":"...","expect":"optional text"}
              ]
            }

            Hard rules:
            1. Allowed kinds: mkdir, write_file, shell. Nothing else.
            2. Paths must be RELATIVE and inside the workspace. Never use / or ...
            3. Maximum 25 steps. Prefer write_file/mkdir over shell.
            4. Never use: sudo, su, rm, mv, chmod, pm, am, pkg install, pip install,
               npm install, curl, wget, git clone, dd, mkfs. Those are blocked.
            5. Android has no python3/node/gradle by default. Before relying on a
               tool, add a shell step that checks it, e.g.
               command: "command -v python3 >/dev/null 2>&1 && echo HAVE_PYTHON || echo NO_PYTHON"
               expect: "HAVE_PYTHON"
               If it is missing, do not try to install it; instead write a note file
               telling the user which app/tool to install.
            6. Prepare API key / secret placeholders by writing config files that
               contain obvious placeholders like YOUR_API_KEY_HERE. Never invent keys.
            7. Always end with a shell step that lists the result, e.g.
               "ls -R . | head -40" and a write_file step "SETUP_NOTES.md"
               summarising what was created and what the user still must fill in.
            8. Write step titles and summary in simple Hindi (Hinglish is fine).
            9. Follow the learned corrections below exactly; they override your habits.
        """.trimIndent()
    }
}
