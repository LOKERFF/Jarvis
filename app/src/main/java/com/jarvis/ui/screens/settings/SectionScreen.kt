package com.jarvis.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ui.nav.SettingsSection
import com.jarvis.ui.screens.module.MemoryScreen
import com.jarvis.ui.screens.module.ModuleOverviewScreen
import com.jarvis.ui.screens.module.SavedSetupsScreen
import com.jarvis.ui.screens.module.TerminalScreen
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber

/**
 * Single entry point for every settings section. Phase 1 sections are real
 * screens; the rest say exactly which phase they arrive in and do nothing.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onBack: () -> Unit,
    onRestartSetup: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit = {},
) {
    when (section) {
        SettingsSection.GENERAL -> GeneralScreen(onBack, onRestartSetup)
        SettingsSection.AI_BRAIN -> AiBrainScreen(onBack)
        SettingsSection.AI_MODELS -> AiModelsScreen(onBack)
        SettingsSection.API_KEYS -> ApiKeysScreen(onBack)
        SettingsSection.VOICE -> VoiceScreen(onBack)
        SettingsSection.PRIVACY -> PrivacyScreen(onBack)
        SettingsSection.SECURITY -> SecurityScreen(onBack)
        SettingsSection.APPEARANCE -> AppearanceScreen(onBack)
        SettingsSection.LANGUAGE -> LanguageScreen(onBack)
        SettingsSection.LOGS -> LogsScreen(onBack)
        SettingsSection.DEVELOPER -> DeveloperScreen(onBack, onRestartSetup)
        SettingsSection.ABOUT -> AboutScreen(onBack)
        SettingsSection.MODULE -> ModuleOverviewScreen(onBack = onBack, onOpenSection = onOpenSection)
        SettingsSection.SAVED_SETUPS -> SavedSetupsScreen(onBack = onBack)
        SettingsSection.MEMORY -> MemoryScreen(onBack = onBack)
        SettingsSection.TERMINAL -> TerminalScreen(onBack = onBack)
        else -> ComingSoonScreen(section, onBack)
    }
}

@Composable
fun ComingSoonScreen(section: SettingsSection, onBack: () -> Unit) {
    SettingsPage(
        title = section.title,
        subtitle = "Coming in ${section.phaseLabel}",
        onBack = onBack,
    ) {
        GlassCard {
            SectionLabel("Status")
            Spacer(modifier = Modifier.height(10.dp))
            StatusChip(text = "COMING IN ${section.phaseLabel.uppercase()}", color = WarningAmber, filled = true)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = section.subtitle,
                color = TextSecondary,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = plannedWork(section),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        GlassCard {
            SectionLabel("Honest note")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This screen has no fake switches or placeholder data. It will appear " +
                    "with working controls when Phase ${section.phase} is built. Nothing here " +
                    "is half-connected in the background.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

private fun plannedWork(section: SettingsSection): String = when (section) {
    SettingsSection.WAKE_WORD ->
        "Offline \"Jarvis\" detection with a Vosk model, sensitivity slider, battery impact " +
            "estimate and a live test that shows what the engine hears."
    SettingsSection.MOBILE_CONTROL ->
        "Per-app skills and an action runner with a verification loop: click, type, scroll, " +
            "open app, and re-read the screen to prove the action worked."
    SettingsSection.ACCESSIBILITY ->
        "The JarvisControl accessibility service, its password-field filter, the banking / OTP " +
            "/ SMS skip list, and a live tree inspector."
    SettingsSection.WHATSAPP ->
        "Notification reading with the user's permission, reply drafting, send confirmation, " +
            "and a hard limit of five automated sends per hour."
    SettingsSection.CALLING ->
        "Contact lookup by name, confirmation before dialling, in-call controls where Android " +
            "allows them, and masked numbers everywhere."
    SettingsSection.MEMORY ->
        "Room tables for long-term facts, session context and saved instructions with search, " +
            "edit, delete and incognito mode."
    SettingsSection.AUTOMATION ->
        "Time, voice, notification and battery triggers; multi-step actions; WorkManager " +
            "scheduling; run history."
    SettingsSection.AGENTS ->
        "The main Jarvis router plus coding, research, web, automation and file agents, each " +
            "with its own prompt, provider and tool list."
    SettingsSection.CODING ->
        "A project folder in app storage, builds run through the terminal, error reading and a " +
            "fix loop capped at five iterations."
    SettingsSection.TERMINAL ->
        "A real /system/bin/sh session in the app's private folder, streaming output, history, " +
            "and a confirmation gate for destructive commands."
    SettingsSection.NOTIFICATIONS ->
        "The persistent ON/OFF, pause, resume and Emergency Stop notification, plus summary " +
            "rules for other apps."
    SettingsSection.BATTERY ->
        "Wake-lock behaviour, doze guidance that opens the right Android screen, and a measured " +
            "battery cost estimate for always-on listening."
    SettingsSection.NETWORK ->
        "Live connectivity state, API latency history and request counters per provider."
    SettingsSection.INSTAGRAM ->
        "Reels control, comment drafting with confirmation, and rate-limit aware navigation " +
            "without bulk actions."
    SettingsSection.BACKUP ->
        "Encrypted export and restore of memory and settings, with API keys excluded unless you " +
            "explicitly include them."
    else -> "This section is planned for Phase ${section.phase}."
}
