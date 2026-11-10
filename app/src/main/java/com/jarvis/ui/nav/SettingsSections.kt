package com.jarvis.ui.nav

/**
 * The 27 settings sections, in the exact order given in the build prompt.
 *
 * `available` is honest: true only where Phase 1 really implements the screen.
 * Everything else shows "Coming in Phase X" and does nothing.
 */
enum class SettingsSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val phase: Int,
    val available: Boolean,
    val badge: String,
) {
    GENERAL(
        "general", "General", "Startup, conversation flow, setup wizard", 1, true, "GE"
    ),
    AI_BRAIN(
        "ai_brain", "AI Brain", "System prompt and creativity", 1, true, "BR"
    ),
    AI_MODELS(
        "ai_models", "AI Models", "Fetch and choose a Gemini model", 1, true, "MD"
    ),
    API_KEYS(
        "api_keys", "API Keys", "Gemini key, stored in the Keystore", 1, true, "KY"
    ),
    VOICE(
        "voice", "Voice", "Speech output, rate, wake word toggles", 1, true, "VO"
    ),
    WAKE_WORD(
        "wake_word", "Wake Word", "Offline Vosk engine and sensitivity", 2, false, "WW"
    ),
    MOBILE_CONTROL(
        "mobile_control", "Mobile Control", "Accessibility automation", 3, false, "MO"
    ),
    ACCESSIBILITY(
        "accessibility", "Accessibility", "Screen reading service and filters", 3, false, "AC"
    ),
    WHATSAPP(
        "whatsapp", "WhatsApp", "Notification reading and drafted replies", 3, false, "WA"
    ),
    INSTAGRAM(
        "instagram", "Instagram", "Reels, comments, profiles", 5, false, "IG"
    ),
    CALLING(
        "calling", "Calling", "Contacts, calls, speaker control", 3, false, "CA"
    ),
    PRIVACY(
        "privacy", "Privacy", "What is redacted and what is never touched", 1, true, "PR"
    ),
    SECURITY(
        "security", "Security", "Integrity result and key vault status", 1, true, "SE"
    ),
    MEMORY(
        "memory", "Memory", "Long-term facts and incognito mode", 5, false, "ME"
    ),
    AUTOMATION(
        "automation", "Automation", "Triggers, actions, schedules", 5, false, "AU"
    ),
    AGENTS(
        "agents", "AI Agents", "Coding, research, web and file agents", 4, false, "AG"
    ),
    CODING(
        "coding", "Coding", "Build-fix loops in app storage", 4, false, "CO"
    ),
    TERMINAL(
        "terminal", "Terminal", "Local shell sessions and history", 2, false, "TM"
    ),
    NOTIFICATIONS(
        "notifications", "Notifications", "Persistent controls and summaries", 2, false, "NO"
    ),
    BATTERY(
        "battery", "Battery", "Optimisation guidance and keep-alive", 2, false, "BA"
    ),
    NETWORK(
        "network", "Network", "Connection status and API latency", 2, false, "NW"
    ),
    APPEARANCE(
        "appearance", "Appearance", "Theme, accent and the orb editor", 1, true, "AP"
    ),
    LANGUAGE(
        "language", "Language", "Reply language for voice and text", 1, true, "LA"
    ),
    BACKUP(
        "backup", "Backup & Restore", "Encrypted export of memory and settings", 5, false, "BK"
    ),
    LOGS(
        "logs", "Logs & Diagnostics", "Every action and AI call, redacted", 1, true, "LG"
    ),
    DEVELOPER(
        "developer", "Developer Options", "Diagnostics, raw logging, reset", 1, true, "DV"
    ),
    ABOUT(
        "about", "About JARVIS", "Version, safety rules, limitations", 1, true, "AB"
    );

    val phaseLabel: String get() = "Phase $phase"

    companion object {
        fun fromId(id: String?): SettingsSection? =
            entries.firstOrNull { it.id == id }
    }
}
