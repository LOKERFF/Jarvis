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
import androidx.compose.ui.platform.testTag
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
 * Ek screen par har permission ka saaf Hinglish kaaran aur live status.
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

    val finishPermissions: () -> Unit = {
        ServiceLocator.prefs.setPermissionsDone(true)
        ServiceLocator.audit.info("SETUP", "permissions step completed (mandatoryOk=$mandatoryOk)")
        onContinue()
    }

    val askAllRequired: () -> Unit = {
        val toRequest = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions.launch(toRequest.toTypedArray())
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
            SectionLabel("Step 3 of 4")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Koi bhi permission chupke se nahi li jaati. Har ek ka kaaran aur real status neeche hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))

            // ---- mandatory
            GlassCard {
                SectionLabel("Zaroori Permissions")
                PermissionRow(
                    title = "Microphone",
                    reason = "Jarvis se bolkar baat karne aur \"Jarvis\" wake word sunne ke liye.",
                    granted = micGranted,
                    mandatory = true,
                    onRequest = askAllRequired,
                )
                PermissionRow(
                    title = "Notifications",
                    reason = "Emergency Stop alerts aur status updates dikhane ke liye.",
                    granted = notificationsGranted,
                    mandatory = true,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    },
                )
                PermissionRow(
                    title = "Internet",
                    reason = "Sirf Gemini AI se baat karne ke liye. Data bhejne se pehle redact hota hai.",
                    granted = true,
                    mandatory = false,
                    statusOverride = "Auto Granted",
                    onRequest = {},
                )
                PermissionRow(
                    title = "Foreground Service",
                    reason = "Future background voice listener ke liye (Phase 2).",
                    granted = true,
                    mandatory = false,
                    statusOverride = "Auto Granted",
                    onRequest = {},
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- optional
            GlassCard {
                SectionLabel("Optional Permissions")
                PermissionRow(
                    title = "Display over other apps",
                    reason = "Floating Jarvis button ke liye (Phase 2). Abhi ke liye zaroori nahi.",
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
                    title = "Accessibility Service",
                    reason = "Future phone control ke liye (Phase 3). Password/OTP/Bank apps ko kabhi touch nahi karega.",
                    granted = accessibilityOn,
                    mandatory = false,
                    statusOverride = if (accessibilityOn) null else "Phase 3 me aayega",
                    onRequest = { showAccessibilityHelp = true },
                )
                PermissionRow(
                    title = "App Storage",
                    reason = "Terminal aur files app ke private folder me chalti hain — extra storage permission ki zaroorat nahi.",
                    granted = true,
                    mandatory = false,
                    statusOverride = "Zaroorat nahi",
                    onRequest = {},
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Jarvis kabhi bhi SMS, call log ya contacts ki permission nahi mangta.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))
            if (mandatoryOk) {
                NeonButton(
                    text = "Aage Badho",
                    onClick = finishPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_continue_button"),
                )
            } else {
                NeonButton(
                    text = "Zaroori Permissions Allow Karein",
                    onClick = askAllRequired,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_allow_button"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                GhostButton(
                    text = "Abhi Skip Karein (Baad me allow kar sakte hain)",
                    onClick = finishPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_skip_button"),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAccessibilityHelp) {
            AlertDialog(
                onDismissRequest = { showAccessibilityHelp = false },
                title = { Text(text = "Accessibility ke baare me") },
                text = {
                    Text(
                        text = "Jarvis accessibility service Phase 3 me aayegi. " +
                            "Android settings kholne par abhi list dikhegi — kuch enable nahi hoga.\n\n" +
                            "Ye hamesha password fields aur banking/OTP/SMS apps ko skip karega.",
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
                    }) { Text("Settings Kholo") }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityHelp = false }) { Text("Band Karo") }
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
                text = statusOverride ?: if (granted) "Allowed" else "Allow karein",
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
                    text = if (mandatory) "Allow Karo" else "Settings Kholo",
                    onClick = onRequest,
                    accent = accent,
                )
            }
        }
        if (granted && !mandatory && statusOverride == null) {
            Spacer(modifier = Modifier.height(8.dp))
            GhostButton(text = "Settings me Badlo", onClick = onRequest, accent = TextMuted)
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
