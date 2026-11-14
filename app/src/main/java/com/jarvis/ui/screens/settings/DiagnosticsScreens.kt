package com.jarvis.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.BuildConfig
import com.jarvis.core.ServiceLocator
import com.jarvis.core.security.SecureStore
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.ui.components.BulletLine
import com.jarvis.ui.components.ToggleRow
import com.jarvis.ui.components.InfoRow
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.GlassDivider
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.JarvisTopBar
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber
import com.jarvis.ui.vm.LogsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Logs & Diagnostics
// ---------------------------------------------------------------------------
private val LOG_FILTERS = listOf(
    "ALL", "AI", "TOOL", "MODEL", "ORB", "VOICE", "TTS", "SAFETY",
    "AUTH", "INTEGRITY", "SETUP", "CHAT", "APP", "ERROR",
)

@Composable
fun LogsScreen(onBack: () -> Unit, vm: LogsViewModel = viewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val filter by vm.activeFilter.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val clipboard = LocalClipboardManager.current
    var confirmClear by remember { mutableStateOf(false) }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            JarvisTopBar(
                title = "Logs & Diagnostics",
                subtitle = "${logs.size} entries · always redacted",
                onBack = onBack,
            )

            // copy + clear row (kept separate so the top bar stays simple)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton(
                    text = "Copy all",
                    onClick = {
                        val text = logs.joinToString("\n") { entry ->
                            "${formatLogTime(entry.timestamp)} [${entry.category}] ${entry.message}"
                        }
                        clipboard.setText(AnnotatedString(text))
                    },
                )
                GhostButton(
                    text = "Clear",
                    onClick = { confirmClear = true },
                    accent = DangerRed,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Compact filter list; wraps by scrolling horizontally in two rows.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LOG_FILTERS.take(7).forEach { item ->
                            FilterChip(item, filter, accent) { vm.setFilter(it) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LOG_FILTERS.drop(7).forEach { item ->
                            FilterChip(item, filter, accent) { vm.setFilter(it) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing logged for this filter yet.",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(logs, key = { it.id }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.category,
                                        color = when (entry.level) {
                                            "ERROR" -> DangerRed
                                            "WARN" -> WarningAmber
                                            else -> accent
                                        },
                                        fontSize = 11.sp,
                                        letterSpacing = 0.8.sp,
                                    )
                                    Text(
                                        text = formatLogTime(entry.timestamp),
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                                StatusChip(
                                    text = entry.level,
                                    color = TextMuted,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = entry.message,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the audit log?") },
            text = { Text("Every recorded action and AI call on this phone will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: String,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    val isSelected = label == selected
    Text(
        text = label,
        color = if (isSelected) Color(0xFF04070C) else TextSecondary,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) accent else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .clickable { onSelect(label) },
    )
}

private fun formatLogTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

// ---------------------------------------------------------------------------
// Developer Options
// ---------------------------------------------------------------------------
@Composable
fun DeveloperScreen(onBack: () -> Unit, onRestartSetup: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val rawIo by prefs.developerRawIo.collectAsStateWithLifecycle()
    val model by prefs.selectedModel.collectAsStateWithLifecycle()
    val integrity by prefs.integrityStatus.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current

    var logCount by remember { mutableStateOf<Int?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        logCount = runCatching {
            ServiceLocator.database.auditLogDao().count()
        }.getOrNull()
    }

    SettingsPage(
        title = "Developer Options",
        subtitle = "Diagnostics and dangerous buttons",
        onBack = onBack,
    ) {
        GlassCard {
            CardTitle("Diagnostics")
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Package", BuildConfig.APPLICATION_ID)
            InfoRow("Model", model)
            InfoRow("Integrity", integrity)
            InfoRow(
                "Key vault",
                if (ServiceLocator.secure.keystoreReady()) "Keystore ready" else "Unavailable",
            )
            InfoRow(
                "API key stored",
                if (ServiceLocator.secure.has(SecureStore.KEY_GEMINI)) "yes" else "no",
            )
            InfoRow("Audit entries", logCount?.toString() ?: "…")
            InfoRow("Database", "jarvis.db (app private storage)")
            Spacer(modifier = Modifier.height(12.dp))
            GhostButton(
                text = "Copy diagnostics",
                onClick = {
                    val report = buildString {
                        appendLine("Jarvis ${BuildConfig.VERSION_NAME}")
                        appendLine("package=${BuildConfig.APPLICATION_ID}")
                        appendLine("model=$model")
                        appendLine("integrity=$integrity")
                        appendLine("keyStored=${ServiceLocator.secure.has(SecureStore.KEY_GEMINI)}")
                        appendLine("auditEntries=${logCount ?: -1}")
                        appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine("android=${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    }
                    clipboard.setText(AnnotatedString(report))
                },
                accent = accent,
            )
        }

        GlassCard {
            CardTitle("Logging")
            ToggleRow(
                title = "Verbose audit logging",
                description = "Adds more detail to the redacted log. Secrets are still removed.",
                checked = rawIo,
                onCheckedChange = { prefs.setDeveloperRawIo(it) },
            )
            SettingsHint(
                "There is no \"send raw prompts\" switch on purpose: redaction is not optional."
            )
        }

        GlassCard {
            CardTitle("Danger zone")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Clearing local data removes settings, the audit log, chat history and " +
                    "the API key from this phone. There is no undo and no cloud copy.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            GhostButton(
                text = "Clear all local data",
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth(),
                accent = DangerRed,
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Clear everything?") },
            text = { Text("Settings, logs, chat and the API key will be erased from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ServiceLocator.database.clearAllTables()
                        }
                        ServiceLocator.secure.remove(SecureStore.KEY_GEMINI)
                        prefs.clearEverything()
                        ServiceLocator.audit.warn("APP", "all local data cleared by user")
                        onRestartSetup()
                    }
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Privacy
// ---------------------------------------------------------------------------
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SettingsPage(
        title = "Privacy",
        subtitle = "What leaves the phone and what never does",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Redaction")
                Spacer(modifier = Modifier.weight(1f))
                StatusChip(text = "ALWAYS ON", color = SuccessGreen, filled = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "There is no switch to disable this. Before any text is sent to an AI " +
                    "provider it passes through a redactor.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlassDivider()
            Spacer(modifier = Modifier.height(10.dp))
            BulletLine("Phone numbers (10-15 digits, with or without country code)")
            BulletLine("Email addresses")
            BulletLine("Card numbers (13-19 digits)")
            BulletLine("OTP / PIN / verification codes (4-8 digits next to the keyword)")
            BulletLine("API keys and bearer tokens")
        }

        GlassCard {
            CardTitle("Never requested")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Jarvis does not ask for READ_SMS, RECEIVE_SMS or READ_CALL_LOG, and it " +
                    "cannot read OTPs, SMS or call history. Password fields are excluded from " +
                    "any screen reading (Phase 3 accessibility), and banking / OTP / SMS apps " +
                    "are skipped entirely.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        GlassCard {
            CardTitle("Where your data lives")
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Chat history", "Local Room database")
            InfoRow("Audit log", "Local Room database, redacted")
            InfoRow("API key", "Android Keystore + AES-GCM")
            InfoRow("Account", "Local preference only")
            InfoRow("Backups", "Disabled (android:allowBackup=false)")
            Spacer(modifier = Modifier.height(8.dp))
            SettingsHint(
                "Your messages do reach the AI provider you configured (Google by default). " +
                    "That is the only outbound network call in Phase 1."
            )
        }

        GlassCard {
            CardTitle("Microphone")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The listening indicator is always visible: a red mic chip plus live " +
                    "level bars. The wake word only runs while Jarvis is on screen in Phase 1.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Security
// ---------------------------------------------------------------------------
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val prefs = ServiceLocator.prefs
    val integrity by prefs.integrityStatus.collectAsStateWithLifecycle()
    val detail by prefs.integrityDetail.collectAsStateWithLifecycle()
    val email by prefs.accountEmail.collectAsStateWithLifecycle()
    val limited by prefs.limitedMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    SettingsPage(
        title = "Security",
        subtitle = "Integrity, vault and account",
        onBack = onBack,
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Play Integrity")
                Spacer(modifier = Modifier.weight(1f))
                val (label, color) = when (integrity) {
                    AppPreferences.STATUS_VERIFIED -> "VERIFIED" to SuccessGreen
                    AppPreferences.STATUS_LIMITED -> "LIMITED" to WarningAmber
                    AppPreferences.STATUS_ERROR -> "ERROR" to DangerRed
                    else -> "UNKNOWN" to TextMuted
                }
                StatusChip(text = label, color = color, filled = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = detail.ifBlank { "No check has run yet." },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GhostButton(
                text = if (checking) "Checking…" else "Run check again",
                enabled = !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val outcome = ServiceLocator.integrity.check()
                        prefs.setIntegrity(outcome.status, outcome.detail)
                        ServiceLocator.audit.info("INTEGRITY", "${outcome.status}: ${outcome.detail}")
                        checking = false
                    }
                },
            )
        }

        GlassCard {
            CardTitle("Key vault")
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(
                "Keystore",
                if (ServiceLocator.secure.keystoreReady()) "ready" else "unavailable",
                if (ServiceLocator.secure.keystoreReady()) SuccessGreen else DangerRed,
            )
            InfoRow(
                "Gemini key",
                if (ServiceLocator.secure.has(SecureStore.KEY_GEMINI)) "stored" else "not stored",
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsHint("AES-256-GCM, fresh IV per write, hardware-backed where available.")
        }

        GlassCard {
            CardTitle("Account")
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Signed in", email ?: "no")
            InfoRow("Mode", if (limited) "Limited (unverified)" else "Google verified")
            Spacer(modifier = Modifier.height(12.dp))
            GhostButton(
                text = "Sign out of this device",
                onClick = { confirmSignOut = true },
                accent = DangerRed,
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This clears the local account. The app returns to the sign-in step on the " +
                        "next launch. Your API key and chat stay on this phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch {
                        ServiceLocator.auth.signOut()
                        prefs.setAccount(null, null, false)
                        ServiceLocator.audit.warn("AUTH", "signed out on this device")
                    }
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsPage(
        title = "About JARVIS",
        subtitle = "v${BuildConfig.VERSION_NAME} · 3D Reactive Core",
        onBack = onBack,
    ) {
        GlassCard {
            Text(
                text = "JARVIS",
                color = LocalAccent.current,
                fontSize = 26.sp,
                letterSpacing = 6.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (v${BuildConfig.VERSION_CODE})")
            InfoRow("Build", if (BuildConfig.DEBUG) "debug" else "release")
            InfoRow("Core", "Reactive 3D Orb + Multi-Voice + Multi-Model Gemini")
            InfoRow("Storage", "On-device Encrypted Room + Keystore")
        }

        GlassCard {
            CardTitle("Safety rules baked in")
            Spacer(modifier = Modifier.height(8.dp))
            BulletLine("Password fields are never read; banking / OTP / SMS apps are skipped.")
            BulletLine("No SMS, OTP or call-log permissions exist in the manifest.")
            BulletLine("Every outbound text is redacted first.")
            BulletLine("Destructive actions need explicit confirmation.")
            BulletLine("Success is never claimed without verification.")
            BulletLine("Emergency stop halts every agent at once.")
            BulletLine("Every AI call and tool call is logged (redacted).")
            BulletLine("The microphone indicator is always visible.")
        }

        GlassCard {
            CardTitle("Honest Notes & Limits")
            Spacer(modifier = Modifier.height(8.dp))
            BulletLine("Wake word Android speech recogniser use karta hai jab app khuli ho.")
            BulletLine("Play Integrity token bina backend server ke on-device verify nahi ho sakta.")
            BulletLine("Google Sign-In optional hai — bina uske Guest Mode me sab chalta hai.")
            BulletLine("In-app Terminal app ke private sandbox folder me chalta hai.")
            BulletLine("App UI aur multi-voice TTS Hinglish + Hindi + English me tuned hain.")
        }

        GlassCard {
            CardTitle("Roadmap")
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("v2 Active", "3D Reactive Orb, Multi-Awaaz, Multi-Model Gemini, Self-Setup & Learning")
            InfoRow("Next", "Always-on foreground wake word, accessibility control, browser & agents")
        }
    }
}
