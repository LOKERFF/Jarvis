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
    val accent = LocalAccent.current

    SettingsPage(
        title = "AI Models",
        subtitle = "Fetched live from your Gemini key",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle("Model list")
                    SettingsHint(
                        if (updatedAt == 0L) {
                            "Never loaded. Tap Refresh."
                        } else {
                            "Updated ${formatTime(updatedAt)}"
                        }
                    )
                }
                GhostButton(
                    text = if (vm.ui.loading) "Loading…" else "Refresh",
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
            CardTitle("Choose a model")
            Spacer(modifier = Modifier.height(4.dp))
            SettingsHint(
                "Jarvis may switch models itself with set_model, but only to a name in this list."
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (models.isEmpty()) {
                Text(
                    text = "No models loaded yet. Add your key in API Keys, then tap Refresh.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            } else {
                models.forEach { model ->
                    val isSelected = model == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.select(model) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = model,
                            color = if (isSelected) accent else TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            StatusChip(text = "IN USE", color = accent, filled = true)
                        }
                    }
                }
            }
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
                    text = if (vm.keyUi.loading) "Testing…" else "Test & save",
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
