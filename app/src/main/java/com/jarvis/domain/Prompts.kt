package com.jarvis.domain

import com.jarvis.domain.orb.OrbSpec

/**
 * The system prompt is sent with every Gemini call (Phase 1 requirement) and
 * the user can edit the first paragraph in Settings > AI Brain.
 */
object Prompts {

    const val BASE_SYSTEM_PROMPT =
        "You are JARVIS, a personal AI assistant on the user's Android phone. " +
            "Reply in the user's language (Hindi, Hinglish, or English). Be concise. " +
            "Never invent results. When you use a tool, wait for its result before claiming " +
            "success. You may change your own orb with set_orb and switch models with " +
            "set_model. You must refuse to request or handle passwords, OTPs, or SMS content."

    const val LANGUAGE_AUTO = "auto"
    const val LANGUAGE_HINDI = "hindi"
    const val LANGUAGE_ENGLISH = "english"
    const val LANGUAGE_HINGLISH = "hinglish"

    fun languageRule(language: String): String = when (language) {
        LANGUAGE_HINDI -> "Answer in Hindi (Devanagari script), even if the user writes in English."
        LANGUAGE_ENGLISH -> "Answer in English, even if the user writes in Hindi."
        LANGUAGE_HINGLISH -> "Answer in Hinglish (Roman-script Hindi mixed with English)."
        else -> "Answer in the same language the user used."
    }

    fun systemPrompt(
        userPrompt: String,
        language: String,
        currentModel: String,
        availableModels: List<String>,
    ): String {
        val base = userPrompt.ifBlank { BASE_SYSTEM_PROMPT }
        val models = if (availableModels.isEmpty()) {
            "unknown (the user has not loaded the list yet)"
        } else {
            availableModels.take(40).joinToString(", ")
        }
        return buildString {
            appendLine(base)
            appendLine()
            appendLine("## Language")
            appendLine(languageRule(language))
            appendLine()
            appendLine("## Tools")
            appendLine("- set_orb changes your orb. Arguments: shape (sphere|heart|torus|custom_formula),")
            appendLine("  size (0.3-1.5), color (#RRGGBB), particleCount (500-8000), glow (0-1),")
            appendLine("  rotationSpeed (0-2), formula (only for custom_formula).")
            appendLine("- A custom_formula needs three expressions separated by ';' meaning x;y;z,")
            appendLine("  where u and v go from 0 to 1. Example: ${OrbSpec.EXAMPLE_FORMULA}")
            appendLine("- set_model switches the Gemini model. Only use a name from this list:")
            appendLine("  $models")
            appendLine("- After a tool returns, describe the real result in one short sentence.")
            appendLine("- If a tool returns status=error, say what failed. Never pretend it worked.")
            appendLine()
            appendLine("## Hard rules")
            appendLine("- Never ask for, repeat or store passwords, PINs, OTPs, card numbers, SMS content,")
            appendLine("  bank details or Aadhaar numbers. Offer to stop instead.")
            appendLine("- You cannot read SMS, OTPs or call logs; the app has no permission for that.")
            appendLine("- Before a destructive or outward-facing action, ask the user to confirm.")
            appendLine("- If you are not sure something happened, say you are not sure.")
            appendLine()
            append("Current model: ").append(currentModel)
        }
    }
}
