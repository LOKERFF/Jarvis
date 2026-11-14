package com.jarvis.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.core.ServiceLocator
import com.jarvis.ui.components.LetterBadge
import com.jarvis.ui.nav.SettingsSection
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.JarvisTopBar
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.WarningAmber

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
) {
    val prefs = ServiceLocator.prefs
    val model by prefs.selectedModel.collectAsStateWithLifecycle()
    val voicePresetId by prefs.ttsVoicePreset.collectAsStateWithLifecycle()
    val perModelVoice by prefs.perModelVoiceEnabled.collectAsStateWithLifecycle()
    val modelVoiceMap by prefs.modelVoiceOverrides.collectAsStateWithLifecycle()
    val email by prefs.accountEmail.collectAsStateWithLifecycle()
    val limited by prefs.limitedMode.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val activeVoiceTitle = com.jarvis.voice.VoicePreset.fromId(
        if (perModelVoice) modelVoiceMap[model] ?: voicePresetId else voicePresetId
    ).title

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            JarvisTopBar(
                title = "Settings",
                subtitle = "${SettingsSection.entries.size} sections · Self-Setup + 3D Orb live",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = email ?: "Guest Mode (Local)",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Model: $model · Awaaz: $activeVoiceTitle",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            StatusChip(
                                text = if (limited) "GUEST" else "GOOGLE",
                                color = if (limited) WarningAmber else accent,
                                filled = true,
                            )
                        }
                    }
                }

                items(SettingsSection.entries) { section ->
                    SectionRow(
                        section = section,
                        accent = accent,
                        onClick = { onOpenSection(section) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: SettingsSection,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(
                letters = section.badge,
                color = if (section.available) accent else TextMuted,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = section.subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (section.available) {
                Text(text = "›", color = accent, fontSize = 20.sp)
            } else {
                StatusChip(text = section.phaseLabel, color = WarningAmber)
            }
        }
    }
}
