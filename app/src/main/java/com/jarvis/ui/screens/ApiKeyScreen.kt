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
                text = "Connect the AI brain",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Jarvis uses your own Gemini key. It is stored encrypted with the " +
                    "Android Keystore and only saved after a real test call succeeds.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard {
                Text(text = "Gemini API key", color = TextPrimary, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
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
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                imageVector = if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (reveal) "Hide key" else "Show key",
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
                    text = if (vm.keyUi.loading) "Testing key…" else "Test key & save",
                    onClick = { vm.testAndSaveKey(key) { onDone() } },
                    modifier = Modifier.fillMaxWidth(),
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
                SectionLabel("Where to get a key")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Open aistudio.google.com/apikey in any browser.\n" +
                        "2. Create an API key for the same Google account.\n" +
                        "3. Copy it and paste it above.\n\n" +
                        "Free-tier keys work. If you hit a quota error, Jarvis will say so " +
                        "instead of pretending the answer failed.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The key travels in an HTTP header to Google only. It is never written " +
                    "in plain text, never logged, and masked in every screen.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
