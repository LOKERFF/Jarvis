package com.jarvis.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonButton
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.vm.SetupViewModel

@Composable
fun ApiKeyScreen(
    onDone: () -> Unit,
    vm: SetupViewModel = viewModel(),
) {
    val accent = LocalAccent.current
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    val submitKey: () -> Unit = {
        if (key.isNotBlank() && !vm.keyUi.loading) {
            vm.testAndSaveKey(key) { onDone() }
        }
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SectionLabel("Step 4 of 4")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "AI Brain Connect Karo",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Jarvis aapki apni Gemini API key use karta hai. Ye Android Keystore me " +
                    "encrypted store hoti hai aur real test call pass hone ke baad hi save hoti hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard {
                Text(text = "Gemini API Key", color = TextPrimary, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(text = "AIza…", color = TextMuted) },
                    visualTransformation = if (reveal) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitKey() }),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                imageVector = if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (reveal) "Key chupao" else "Key dikhao",
                                tint = TextMuted,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))
                NeonButton(
                    text = if (vm.keyUi.loading) "Key Test Ho Rahi Hai…" else "Key Test & Save Karo",
                    onClick = submitKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_submit_button"),
                    enabled = key.isNotBlank() && !vm.keyUi.loading,
                )

                if (vm.keyUi.loading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                vm.keyUi.message?.let { message ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = message, color = SuccessGreen, fontSize = 12.sp, lineHeight = 17.sp)
                }
                vm.keyUi.error?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = error, color = DangerRed, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            GlassCard {
                SectionLabel("Key Kahan Se Milegi")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Kisi bhi browser me aistudio.google.com/apikey kholo.\n" +
                        "2. Apne Google account se ek API key create karo.\n" +
                        "3. Copy karke upar box me paste karo.\n\n" +
                        "Free-tier keys bhi smoothly chalti hain.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Key sirf Google ke HTTP header me jaati hai. Ye kabhi plain text me store ya log nahi hoti.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
