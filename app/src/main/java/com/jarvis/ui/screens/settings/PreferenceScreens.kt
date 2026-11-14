package com.jarvis.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.core.ServiceLocator
import com.jarvis.domain.Prompts
import com.jarvis.domain.orb.OrbSpec
import com.jarvis.ui.components.OrbPreview
import com.jarvis.ui.components.ToggleRow
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonButton
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.vm.OrbViewModel

// ---------------------------------------------------------------------------
// General
// ---------------------------------------------------------------------------
@Composable
fun GeneralScreen(onBack: () -> Unit, onRestartSetup: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val continuous by prefs.continuousMode.collectAsStateWithLifecycle()
    val hotword by prefs.hotwordEnabled.collectAsStateWithLifecycle()
    val model by prefs.selectedModel.collectAsStateWithLifecycle()
    val email by prefs.accountEmail.collectAsStateWithLifecycle()
    val limited by prefs.limitedMode.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }

    SettingsPage(
        title = "General",
        subtitle = "Jarvis ka startup aur behaviour",
        onBack = onBack,
    ) {
        GlassCard {
            CardTitle("Conversation")
            ToggleRow(
                title = "Continuous conversation",
                description = "Jawab khatam hone ke baad mic apne aap phir khul jayega.",
                checked = continuous,
                onCheckedChange = { prefs.setContinuousMode(it) },
            )
            ToggleRow(
                title = "Wake word \"Jarvis\"",
                description = "Screen khuli hone par \"Jarvis\" bolkar seedha mic start karein.",
                checked = hotword,
                onCheckedChange = { prefs.setHotwordEnabled(it) },
            )
        }

        GlassCard {
            CardTitle("Account")
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email ?: "Guest Mode",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (limited) {
                    StatusChip(text = "GUEST MODE", color = com.jarvis.ui.theme.WarningAmber, filled = true)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint("Active model: $model")
        }

        GlassCard {
            CardTitle("Setup")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Running the setup again re-checks permissions, the integrity result " +
                    "and the API key. Your chat history is kept.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            GhostButton(
                text = "Run setup wizard again",
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassCard {
            CardTitle("Local data")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Chat messages and the audit log live in this app's private database. " +
                    "Only this device can open it.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            GhostButton(
                text = "Clear conversation",
                onClick = { confirmClearChat = true },
                modifier = Modifier.fillMaxWidth(),
                accent = DangerRed,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Run setup again?") },
            text = {
                Text(
                    "The app will go back to the sign-in step. Memory, orbit settings and the " +
                        "API key stay untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    ServiceLocator.prefs.resetOnboarding()
                    onRestartSetup()
                }) { Text("Run setup") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Clear conversation?") },
            text = { Text("All chat messages on this phone will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearChat = false
                    ServiceLocator.engine.clearConversation()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChat = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// AI Brain
// ---------------------------------------------------------------------------
@Composable
fun AiBrainScreen(onBack: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val storedPrompt by prefs.systemPrompt.collectAsStateWithLifecycle()
    val temperature by prefs.temperature.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    var prompt by remember(storedPrompt) { mutableStateOf(storedPrompt) }
    var saved by remember { mutableStateOf(false) }

    SettingsPage(
        title = "AI Brain",
        subtitle = "The system prompt and creativity",
        onBack = onBack,
    ) {
        GlassCard {
            CardTitle("System prompt")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint(
                "This paragraph is sent with every request. Jarvis appends its own tool " +
                    "instructions, language rule and current model name automatically."
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
                    saved = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                    cursorColor = accent,
                    focusedTextColor = TextSecondary,
                    unfocusedTextColor = TextSecondary,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(
                    text = if (saved) "Saved" else "Save prompt",
                    onClick = {
                        prefs.setSystemPrompt(prompt)
                        saved = true
                    },
                    enabled = prompt.isNotBlank(),
                )
                GhostButton(
                    text = "Reset",
                    onClick = {
                        prompt = Prompts.BASE_SYSTEM_PROMPT
                        prefs.setSystemPrompt(Prompts.BASE_SYSTEM_PROMPT)
                        saved = false
                    },
                )
            }
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Creativity")
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(text = String.format(java.util.Locale.US, "%.2f", temperature), color = accent)
            }
            SettingsHint("Low = predictable, high = more varied answers.")
            Slider(
                value = temperature,
                onValueChange = { prefs.setTemperature(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
        }

        GlassCard {
            CardTitle("What Jarvis already knows")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• It can reshape its orb with set_orb.\n" +
                    "• It can switch models with set_model, but only from the list you loaded.\n" +
                    "• It must ask before destructive or outward-facing actions.\n" +
                    "• It refuses passwords, OTPs and SMS content.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Voice
// ---------------------------------------------------------------------------
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val speak by prefs.speakReplies.collectAsStateWithLifecycle()
    val rate by prefs.ttsRate.collectAsStateWithLifecycle()
    val pitch by prefs.ttsPitch.collectAsStateWithLifecycle()
    val selectedPreset by prefs.ttsVoicePreset.collectAsStateWithLifecycle()
    val selectedVoiceName by prefs.ttsVoiceName.collectAsStateWithLifecycle()
    val perModelVoice by prefs.perModelVoiceEnabled.collectAsStateWithLifecycle()
    val modelVoiceMap by prefs.modelVoiceOverrides.collectAsStateWithLifecycle()
    val models by prefs.availableModels.collectAsStateWithLifecycle()
    val offline by prefs.hotwordOffline.collectAsStateWithLifecycle()
    val language by prefs.replyLanguage.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val tts = ServiceLocator.tts
    val deviceVoices by tts.availableVoices.collectAsStateWithLifecycle()
    var hindiVoice by remember { mutableStateOf<Boolean?>(null) }

    SettingsPage(
        title = "Voice & Awaaz",
        subtitle = "Alag-alag awaaz, pitch, speed aur model voices",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Awaaz Profile Chuno")
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(
                    text = com.jarvis.voice.VoicePreset.fromId(selectedPreset).title,
                    color = accent,
                    filled = true,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SettingsHint("Har profile Jarvis ki pitch, speed aur tone alag tarah set karta hai.")
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.jarvis.voice.VoicePreset.SELECTABLE.forEach { preset ->
                    val isSelected = selectedPreset == preset.id
                    LanguageOption(
                        id = preset.id,
                        title = preset.title,
                        description = "${preset.subtitle} (Pitch ${String.format(java.util.Locale.US, "%.2f", preset.pitch)}x · Speed ${String.format(java.util.Locale.US, "%.2f", preset.rate)}x)",
                        selected = selectedPreset,
                        onSelect = { id ->
                            prefs.applyVoicePreset(id)
                            hindiVoice = tts.hasHindiVoice()
                            tts.speak(
                                text = sample(language, preset.title),
                                rate = preset.rate,
                                pitch = preset.pitch,
                                voiceName = selectedVoiceName,
                                presetId = preset.id,
                            )
                        },
                    )
                }
            }
        }

        GlassCard {
            CardTitle("Fine Tuning & Test")
            ToggleRow(
                title = "Bolkar jawab do (Speak replies)",
                description = "Off karne par Jarvis sirf chat me text likhega.",
                checked = speak,
                onCheckedChange = { prefs.setSpeakReplies(it) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Speaking Speed", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(text = String.format(java.util.Locale.US, "%.2fx", rate), color = accent)
            }
            Slider(
                value = rate,
                onValueChange = { prefs.setTtsRate(it) },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Voice Pitch (Awaaz ki tone)", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(text = String.format(java.util.Locale.US, "%.2fx", pitch), color = accent)
            }
            Slider(
                value = pitch,
                onValueChange = { prefs.setTtsPitch(it) },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(
                    text = "Awaaz Test Karo",
                    onClick = {
                        hindiVoice = tts.hasHindiVoice()
                        tts.speak(
                            text = sample(language, com.jarvis.voice.VoicePreset.fromId(selectedPreset).title),
                            rate = rate,
                            pitch = pitch,
                            voiceName = selectedVoiceName,
                            presetId = selectedPreset,
                        )
                    },
                )
                GhostButton(
                    text = "Stop",
                    onClick = { tts.stop() },
                )
            }
            hindiVoice?.let { has ->
                Spacer(modifier = Modifier.height(8.dp))
                if (has) {
                    Text(
                        text = "Is phone par Hindi/Hinglish voice engine active hai.",
                        color = SuccessGreen,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = "Phone me Hindi TTS data missing hai. Neeche 'Android TTS Settings' se Hindi/India pack download kar sakte ho.",
                        color = com.jarvis.ui.theme.WarningAmber,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle("Device TTS Voices")
                    SettingsHint("Phone me installed real Google/System voices (${deviceVoices.size} mile)")
                }
                GhostButton(
                    text = "Refresh",
                    onClick = { tts.refreshVoices() },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val autoSelected = selectedVoiceName.isBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto (Hindi + English Smart Match)",
                        color = if (autoSelected) accent else com.jarvis.ui.theme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Selected preset aur bhasha ke hisaab se best voice chunta hai.",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }
                if (autoSelected) {
                    StatusChip(text = "ACTIVE", color = accent, filled = true)
                } else {
                    GhostButton(
                        text = "Use",
                        onClick = {
                            prefs.setTtsVoiceName("")
                            tts.speak(sample(language, "Auto"), rate, pitch, "", selectedPreset)
                        },
                        accent = TextMuted,
                    )
                }
            }

            deviceVoices.take(12).forEach { voice ->
                val isCurrent = selectedVoiceName == voice.name
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = voice.label,
                            color = if (isCurrent) accent else com.jarvis.ui.theme.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = "${voice.localeTag} · ${if (voice.isNetwork) "Neural/Online" else "Offline local"}",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (isCurrent) {
                        StatusChip(text = "ACTIVE", color = accent, filled = true)
                    } else {
                        GhostButton(
                            text = "Sunao",
                            onClick = {
                                prefs.setTtsVoiceName(voice.name)
                                tts.speak(sample(language, voice.label), rate, pitch, voice.name, selectedPreset)
                            },
                            accent = accent,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            GhostButton(
                text = "Android System TTS Settings Kholo",
                onClick = { tts.openSystemTtsSettings() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassCard {
            CardTitle("Alag Gemini Model = Alag Awaaz")
            ToggleRow(
                title = "Har model ke liye alag awaaz use karo",
                description = "On karne par Pro, Flash aur Lite models apni assigned awaaz me bolenge.",
                checked = perModelVoice,
                onCheckedChange = { prefs.setPerModelVoiceEnabled(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            val highlightedModels = (listOf(prefs.selectedModel.value) + models)
                .distinct()
                .take(5)
            val presetOptions = com.jarvis.voice.VoicePreset.SELECTABLE.map { it.id to it.title }
            highlightedModels.forEach { modelId ->
                val assignedPresetId = modelVoiceMap[modelId] ?: selectedPreset
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = modelId,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ChoiceChips(
                    options = presetOptions,
                    selected = assignedPresetId,
                    onSelect = { chosenPreset ->
                        prefs.setVoicePresetForModel(modelId, chosenPreset)
                        if (!perModelVoice) prefs.setPerModelVoiceEnabled(true)
                        val p = com.jarvis.voice.VoicePreset.fromId(chosenPreset)
                        tts.speak(
                            text = "$modelId ab ${p.title} awaaz me bolega.",
                            rate = p.rate,
                            pitch = p.pitch,
                            voiceName = selectedVoiceName,
                            presetId = p.id,
                        )
                    },
                )
            }
        }

        GlassCard {
            CardTitle("Listening")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint(
                "Wake-word bursts Android ke speech recogniser par chalte hain. Offline toggle " +
                    "on hone par device bina extra network bheje sunta hai."
            )
            ToggleRow(
                title = "Wake word ke liye on-device recognition",
                description = "Aapki bhasha ka offline speech pack chahiye.",
                checked = offline,
                onCheckedChange = { prefs.setHotwordOffline(it) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sunne ke waqt hamesha live mic indicator aur level bars dikhte hain.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        GlassCard {
            CardTitle("Recognition & Reply Language")
            Spacer(modifier = Modifier.height(8.dp))
            ChoiceChips(
                options = listOf(
                    Prompts.LANGUAGE_AUTO to "Auto",
                    Prompts.LANGUAGE_HINDI to "Hindi",
                    Prompts.LANGUAGE_ENGLISH to "English",
                    Prompts.LANGUAGE_HINGLISH to "Hinglish",
                ),
                selected = language,
                onSelect = { prefs.setReplyLanguage(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsHint("Ye reply aur voice dono ki bhasha control karta hai.")
        }
    }
}

private fun sample(language: String, voiceLabel: String = "Jarvis"): String = when (language) {
    Prompts.LANGUAGE_HINDI -> "नमस्ते, मैं जार्विस हूँ। अभी $voiceLabel आवाज़ सक्रिय है।"
    Prompts.LANGUAGE_HINGLISH -> "Hello, main Jarvis hoon. Abhi $voiceLabel awaaz active hai, bataiye kya kaam hai?"
    Prompts.LANGUAGE_ENGLISH -> "Hello. I am Jarvis speaking with the $voiceLabel voice profile."
    else -> "Hello, main Jarvis hoon. $voiceLabel voice taiyaar hai."
}

// ---------------------------------------------------------------------------
// Appearance (theme + orb editor)
// ---------------------------------------------------------------------------
@Composable
fun AppearanceScreen(onBack: () -> Unit, orbVm: OrbViewModel = viewModel()) {
    val prefs = ServiceLocator.prefs
    val theme by prefs.themeMode.collectAsStateWithLifecycle()
    val accentHex by prefs.accent.collectAsStateWithLifecycle()
    val spec by orbVm.spec.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    SettingsPage(
        title = "Appearance",
        subtitle = "Theme, accent and the orb",
        onBack = onBack,
    ) {
        GlassCard {
            CardTitle("Theme")
            Spacer(modifier = Modifier.height(10.dp))
            ChoiceChips(
                options = listOf(
                    "dark" to "Dark",
                    "light" to "Light",
                    "system" to "System",
                ),
                selected = theme,
                onSelect = { prefs.setThemeMode(it) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            CardTitle("Accent")
            Spacer(modifier = Modifier.height(10.dp))
            ChoiceChips(
                options = listOf(
                    "#00E5FF" to "Cyan",
                    "#A855F7" to "Purple",
                    "#FF3B30" to "Red",
                    "#34D399" to "Green",
                    "#FFB020" to "Amber",
                ),
                selected = accentHex,
                onSelect = { prefs.setAccent(it) },
            )
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Orb")
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(text = spec.shape.replace('_', ' '), color = accent)
            }
            Spacer(modifier = Modifier.height(4.dp))
            SettingsHint("Jarvis can change all of this by itself when you ask it to.")
            Spacer(modifier = Modifier.height(8.dp))
            OrbPreview(
                spec = spec,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChoiceChips(
                options = listOf(
                    OrbSpec.SHAPE_SPHERE to "Sphere",
                    OrbSpec.SHAPE_HEART to "Heart",
                    OrbSpec.SHAPE_TORUS to "Torus",
                ),
                selected = spec.shape,
                onSelect = { shape -> orbVm.updateSpec { it.copy(shape = shape, formula = null) } },
            )
            Spacer(modifier = Modifier.height(14.dp))

            SliderRow(
                label = "Size",
                value = spec.size,
                range = 0.3f..1.5f,
                onValue = { value -> orbVm.updateSpec { it.copy(size = value) } },
            )
            SliderRow(
                label = "Glow",
                value = spec.glow,
                range = 0f..1f,
                onValue = { value -> orbVm.updateSpec { it.copy(glow = value) } },
            )
            SliderRow(
                label = "Rotation speed",
                value = spec.rotationSpeed,
                range = 0f..2f,
                onValue = { value -> orbVm.updateSpec { it.copy(rotationSpeed = value) } },
            )
            SliderRow(
                label = "Particles",
                value = spec.particleCount.toFloat(),
                range = 500f..8000f,
                onValue = { value -> orbVm.updateSpec { it.copy(particleCount = value.toInt()) } },
                display = { it.toInt().toString() },
            )
            Spacer(modifier = Modifier.height(6.dp))
            ChoiceChips(
                options = listOf(
                    "#FF3B30" to "Red",
                    "#00E5FF" to "Cyan",
                    "#A855F7" to "Purple",
                    "#FFFFFF" to "White",
                    "#34D399" to "Green",
                ),
                selected = spec.color,
                onSelect = { color -> orbVm.updateSpec { it.copy(color = color) } },
            )
            Spacer(modifier = Modifier.height(14.dp))
            GhostButton(text = "Reset orb", onClick = { orbVm.reset() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    display: (Float) -> String = { String.format(java.util.Locale.US, "%.2f", it) },
) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        StatusChip(text = display(value), color = accent)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValue,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
        ),
    )
}

// ---------------------------------------------------------------------------
// Language
// ---------------------------------------------------------------------------
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val language by prefs.replyLanguage.collectAsStateWithLifecycle()

    SettingsPage(
        title = "Language",
        subtitle = "How Jarvis answers you",
        onBack = onBack,
    ) {
        GlassCard {
            CardTitle("Reply language")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint(
                "This changes both the spoken language and the instruction sent to the model."
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguageOption(
                    id = Prompts.LANGUAGE_AUTO,
                    title = "Auto",
                    description = "Match whatever language you used.",
                    selected = language,
                    onSelect = { prefs.setReplyLanguage(it) },
                )
                LanguageOption(
                    id = Prompts.LANGUAGE_HINDI,
                    title = "हिन्दी",
                    description = "Always answer in Devanagari Hindi.",
                    selected = language,
                    onSelect = { prefs.setReplyLanguage(it) },
                )
                LanguageOption(
                    id = Prompts.LANGUAGE_HINGLISH,
                    title = "Hinglish",
                    description = "Roman Hindi mixed with English.",
                    selected = language,
                    onSelect = { prefs.setReplyLanguage(it) },
                )
                LanguageOption(
                    id = Prompts.LANGUAGE_ENGLISH,
                    title = "English",
                    description = "Always answer in English.",
                    selected = language,
                    onSelect = { prefs.setReplyLanguage(it) },
                )
            }
        }
        GlassCard {
            CardTitle("App Interface")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "App ke main screens, onboarding, alerts aur voice responses Hinglish me " +
                    "set kiye gaye hain. Aap upar se AI reply aur voice language kabhi bhi badal sakte hain.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun LanguageOption(
    id: String,
    title: String,
    description: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val accent = LocalAccent.current
    val isSelected = id == selected
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (isSelected) accent else com.jarvis.ui.theme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                StatusChip(text = "SELECTED", color = accent, filled = true)
            } else {
                GhostButton(text = "Use", onClick = { onSelect(id) }, accent = TextMuted)
            }
        }
        Text(text = description, color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
    }
}
