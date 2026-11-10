package com.jarvis.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.core.ServiceLocator
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonButton
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber

/**
 * One screen, every permission, plain-English reason, live status.
 * Continue unlocks only when the mandatory ones are actually granted.
 */
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    var showAccessibilityHelp by remember { mutableStateOf(false) }

    val micGranted = remember(refresh) {
        isGranted(context, Manifest.permission.RECORD_AUDIO)
    }
    val notificationsGranted = remember(refresh) {
        if (Build.VERSION.SDK_INT >= 33) isGranted(context, Manifest.permission.POST_NOTIFICATIONS) else true
    }
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }
    val accessibilityOn = remember(refresh) { isAccessibilityEnabled(context) }
    val mandatoryOk = micGranted && notificationsGranted

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SectionLabel("Step 3 of 4")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Nothing is requested silently. Each line says what it is for and " +
                    "shows its real state.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))

            // ---- mandatory
            GlassCard {
                SectionLabel("Required")
                PermissionRow(
                    title = "Microphone",
                    reason = "So you can talk to Jarvis and use the \"Jarvis\" wake word. " +
                        "Audio is handled by the system speech service.",
                    granted = micGranted,
                    mandatory = true,
                    onRequest = {
                        requestPermissions.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        )
                    },
                )
                PermissionRow(
                    title = "Notifications",
                    reason = "For the persistent ON/OFF + Emergency Stop control and for " +
                        "error alerts. Nothing promotional is ever posted.",
                    granted = notificationsGranted,
                    mandatory = true,
                    onRequest = {
                        requestPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    },
                )
                PermissionRow(
                    title = "Internet",
                    reason = "Only to reach the AI provider you configured. Uploads are " +
                        "redacted first.",
                    granted = true,
                    mandatory = false,
                    statusOverride = "Granted automatically",
                    onRequest = {},
                )
                PermissionRow(
                    title = "Foreground service",
                    reason = "Lets the wake word keep running with a visible notification " +
                        "(Phase 2). Android grants this automatically at install.",
                    granted = true,
                    mandatory = false,
                    statusOverride = "Granted automatically",
                    onRequest = {},
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- optional
            GlassCard {
                SectionLabel("Optional")
                PermissionRow(
                    title = "Display over other apps",
                    reason = "The draggable floating Jarvis button (Phase 2). Not needed " +
                        "for anything in Phase 1.",
                    granted = overlayGranted,
                    mandatory = false,
                    onRequest = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                )
                PermissionRow(
                    title = "Accessibility service",
                    reason = "Phone control (Phase 3). It will never read password fields " +
                        "and never reads banking, OTP or SMS apps.",
                    granted = accessibilityOn,
                    mandatory = false,
                    statusOverride = if (accessibilityOn) null else "Available in Phase 3",
                    onRequest = { showAccessibilityHelp = true },
                )
                PermissionRow(
                    title = "App storage",
                    reason = "The terminal and coding agent work inside the app's own folder, " +
                        "so no storage permission is needed at all.",
                    granted = true,
                    mandatory = false,
                    statusOverride = "No permission needed",
                    onRequest = {},
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Jarvis never asks for SMS, call log or contacts access in Phase 1. " +
                    "If a future phase needs more, it will explain why here first.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))
            NeonButton(
                text = if (mandatoryOk) "Continue" else "Grant the required permissions",
                onClick = {
                    ServiceLocator.prefs.setPermissionsDone(true)
                    ServiceLocator.audit.info("SETUP", "permissions step completed")
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = mandatoryOk,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAccessibilityHelp) {
            AlertDialog(
                onDismissRequest = { showAccessibilityHelp = false },
                title = { Text(text = "About accessibility") },
                text = {
                    Text(
                        text = "The Jarvis accessibility service is added in Phase 3. " +
                            "Opening Android settings now shows where it will appear — " +
                            "nothing will be enabled yet.\n\n" +
                            "When it ships it will skip password fields (isPassword = true) " +
                            "and skip banking, OTP and SMS apps completely.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAccessibilityHelp = false
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityHelp = false }) { Text("Close") }
                },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    granted: Boolean,
    mandatory: Boolean,
    onRequest: () -> Unit,
    statusOverride: String? = null,
) {
    val accent = LocalAccent.current
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = statusOverride ?: if (granted) "Granted" else "Not granted",
                color = when {
                    statusOverride != null -> TextMuted
                    granted -> SuccessGreen
                    mandatory -> DangerRed
                    else -> WarningAmber
                },
                filled = granted && statusOverride == null,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = reason,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        if (!granted) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = if (mandatory) "Allow" else "Open settings",
                    onClick = onRequest,
                    accent = accent,
                )
            }
        }
        if (granted && !mandatory && statusOverride == null) {
            Spacer(modifier = Modifier.height(8.dp))
            GhostButton(text = "Change in settings", onClick = onRequest, accent = TextMuted)
        }
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.startsWith(context.packageName) }
}
