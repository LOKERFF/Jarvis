package com.jarvis.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonButton
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber
import com.jarvis.ui.vm.ModelsViewModel
import com.jarvis.ui.vm.SetupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// AI Models
// ---------------------------------------------------------------------------
@Composable
fun AiModelsScreen(onBack: () -> Unit, vm: ModelsViewModel = viewModel()) {
    val models by vm.models.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val updatedAt by vm.updatedAt.collectAsStateWithLifecycle()
    val perModelVoice by vm.perModelVoiceEnabled.collectAsStateWithLifecycle()
    val modelVoiceMap by vm.modelVoiceOverrides.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    var category by remember { mutableStateOf("ALL") }
    var customModelInput by remember { mutableStateOf("") }

    val filteredModels = remember(models, category) {
        when (category) {
            "2.5" -> models.filter { "2.5" in it.lowercase() }
            "PRO" -> models.filter { "pro" in it.lowercase() }
            "FLASH" -> models.filter { "flash" in it.lowercase() && "lite" !in it.lowercase() }
            "LITE" -> models.filter { "lite" in it.lowercase() }
            else -> models
        }
    }
    val activeMeta = remember(selected) { com.jarvis.data.prefs.AppPreferences.describeModel(selected) }

    SettingsPage(
        title = "AI Models",
        subtitle = "Alag-alag Gemini models yahan se badlo",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle("Active Gemini Model")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selected,
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Text(
                        text = activeMeta.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                StatusChip(text = activeMeta.tier, color = accent, filled = true)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsHint(
                    if (updatedAt == 0L) {
                        "Built-in Gemini presets active · Live API ke liye Refresh dabao"
                    } else {
                        "Live list updated: ${formatTime(updatedAt)}"
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                GhostButton(
                    text = if (vm.ui.loading) "Load ho raha…" else "Refresh Live",
                    onClick = { vm.refresh() },
                    enabled = !vm.ui.loading,
                )
            }
            vm.ui.error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = DangerRed, fontSize = 12.sp, lineHeight = 17.sp)
            }
            vm.ui.message?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = SuccessGreen, fontSize = 12.sp)
            }
        }

        GlassCard {
            CardTitle("Gemini Model Chuno (${filteredModels.size})")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint("Har kaam ke hisaab se Pro, Flash ya Lite Gemini model select karo.")
            Spacer(modifier = Modifier.height(10.dp))
            ChoiceChips(
                options = listOf(
                    "ALL" to "Sab",
                    "2.5" to "Gemini 2.5",
                    "PRO" to "Pro",
                    "FLASH" to "Flash",
                    "LITE" to "Lite",
                ),
                selected = category,
                onSelect = { category = it },
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (filteredModels.isEmpty()) {
                Text(
                    text = "Is filter me koi model nahi mila. 'Sab' chuno ya Refresh Live dabao.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            } else {
                filteredModels.forEach { model ->
                    val isSelected = model == selected
                    val meta = com.jarvis.data.prefs.AppPreferences.describeModel(model)
                    val tierColor = when {
                        "PRO" in meta.tier -> com.jarvis.ui.theme.NeonPurple
                        "LITE" in meta.tier -> SuccessGreen
                        else -> accent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.select(model) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = model,
                                    color = if (isSelected) accent else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                                )
                                StatusChip(text = meta.tier, color = tierColor)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val assignedVoice = modelVoiceMap[model]?.let {
                                com.jarvis.voice.VoicePreset.fromId(it).title
                            }
                            val subtitle = if (perModelVoice && assignedVoice != null) {
                                "${meta.description} · Awaaz: $assignedVoice"
                            } else {
                                meta.description
                            }
                            Text(
                                text = subtitle,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        if (isSelected) {
                            StatusChip(text = "IN USE", color = accent, filled = true)
                        } else {
                            GhostButton(text = "Use", onClick = { vm.select(model) }, accent = accent)
                        }
                    }
                }
            }
        }

        GlassCard {
            CardTitle("Custom Gemini Model ID")
            Spacer(modifier = Modifier.height(4.dp))
            SettingsHint("Agar koi naya ya experimental Gemini model ID use karna ho to yahan likho.")
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customModelInput,
                onValueChange = { customModelInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(text = "e.g. gemini-2.5-pro-preview-05-06", color = TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (customModelInput.isNotBlank()) {
                            vm.addAndSelectCustomModel(customModelInput)
                            customModelInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                    cursorColor = accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            NeonButton(
                text = "Custom Model Set Karo",
                onClick = {
                    vm.addAndSelectCustomModel(customModelInput)
                    customModelInput = ""
                },
                enabled = customModelInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

// ---------------------------------------------------------------------------
// API Keys
// ---------------------------------------------------------------------------
@Composable
fun ApiKeysScreen(onBack: () -> Unit, vm: SetupViewModel = viewModel()) {
    val accent = LocalAccent.current
    val hasKey = remember(vm.keyUi) { vm.hasKey }
    val masked = remember(vm.keyUi) { vm.maskedKey }

    var replacing by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SettingsPage(
        title = "API Keys",
        subtitle = "Sealed with the Android Keystore",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Gemini")
                Spacer(modifier = Modifier.weight(1f))
                if (hasKey) {
                    StatusChip(text = "STORED", color = SuccessGreen, filled = true)
                } else {
                    StatusChip(text = "MISSING", color = DangerRed, filled = true)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = masked ?: "No key stored yet.",
                color = TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint(
                "AES-256-GCM, key material inside Android Keystore, never in app storage."
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "Test stored key",
                    enabled = hasKey,
                    onClick = {
                        testResult = null
                        vm.testStoredKey { ok, message -> testResult = ok to message }
                    },
                    accent = accent,
                )
                GhostButton(
                    text = if (replacing) "Cancel" else "Replace",
                    onClick = {
                        replacing = !replacing
                        newKey = ""
                    },
                )
                GhostButton(
                    text = "Remove",
                    enabled = hasKey,
                    onClick = { confirmRemove = true },
                    accent = DangerRed,
                )
            }

            testResult?.let { (ok, message) ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = if (ok) SuccessGreen else DangerRed,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (replacing) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(text = "New key (AIza…)", color = TextMuted) },
                    visualTransformation = if (reveal) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newKey.isNotBlank() && !vm.keyUi.loading) {
                                vm.testAndSaveKey(newKey) {
                                    replacing = false
                                    newKey = ""
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        Text(
                            text = if (reveal) "hide" else "show",
                            color = accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { reveal = !reveal }
                                .padding(horizontal = 10.dp),
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                NeonButton(
                    text = if (vm.keyUi.loading) "Test ho raha hai…" else "Test & Save Karo",
                    onClick = {
                        vm.testAndSaveKey(newKey) {
                            replacing = false
                            newKey = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newKey.isNotBlank() && !vm.keyUi.loading,
                )
                vm.keyUi.error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = it, color = DangerRed, fontSize = 12.sp, lineHeight = 17.sp)
                }
                vm.keyUi.message?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = it, color = SuccessGreen, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        GlassCard {
            SectionLabel("Other providers")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "OpenAI, Anthropic, DeepSeek, Groq and OpenRouter compatible endpoints " +
                    "arrive with the custom AI connector in Phase 4. They will use the same " +
                    "encrypted vault.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatusChip(text = "PHASE 4", color = WarningAmber)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the API key?") },
            text = { Text("Jarvis cannot answer anything until you add a key again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeKey()
                    testResult = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}
