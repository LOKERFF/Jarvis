package com.jarvis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.core.ArtifactIntents
import com.jarvis.core.EmergencyStop
import com.jarvis.core.ServiceLocator
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.domain.model.OrbState
import com.jarvis.ui.components.ChatBubble
import com.jarvis.ui.components.ErrorBanner
import com.jarvis.ui.components.JarvisOrb
import com.jarvis.ui.components.MicLevelBars
import com.jarvis.ui.nav.SettingsSection
import com.jarvis.ui.screens.setup.SetupProgressSheet
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.Glass3DDepthCard
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonPurple
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber
import com.jarvis.ui.vm.ChatViewModel

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit = {},
    vm: ChatViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spec by vm.orbSpec.collectAsStateWithLifecycle()
    val micLevel by vm.micLevel.collectAsStateWithLifecycle()
    val continuous by vm.continuousMode.collectAsStateWithLifecycle()
    val model by vm.selectedModel.collectAsStateWithLifecycle()
    val temperature by vm.temperature.collectAsStateWithLifecycle()
    val savedSetupsCount by vm.savedSetupsCount.collectAsStateWithLifecycle()
    val memoryNotesCount by vm.memoryNotesCount.collectAsStateWithLifecycle()

    val setup by vm.setupState.collectAsStateWithLifecycle()
    val emergency by EmergencyStop.active.collectAsStateWithLifecycle()
    val limited by ServiceLocator.prefs.limitedMode.collectAsStateWithLifecycle()
    val integrity by ServiceLocator.prefs.integrityStatus.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var hudExpanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val accent = LocalAccent.current
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.onOrbTap()
        } else {
            vm.reportError("Bolne ke liye microphone permission zaroori hai. App settings me allow karo.")
        }
    }

    val handleVoiceTrigger: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.onOrbTap()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Module: attach any file. SAF gives us a read handle, so no storage
    // permission is ever requested.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.attach(uri)
        }
    }

    LaunchedEffect(Unit) {
        vm.onScreenVisible()
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            hudExpanded = false
            runCatching { listState.animateScrollToItem(0) }
        }
    }

    val sendInput: () -> Unit = {
        val text = input.trim()
        if (text.isNotEmpty()) {
            vm.send(text)
            input = ""
        }
    }

    val showFullHud = hudExpanded || state.messages.isEmpty()

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---------------------------------------------------------- header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JARVIS",
                        color = accent,
                        fontSize = 21.sp,
                        letterSpacing = 7.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (label, color) = when {
                            limited -> "GUEST MODE" to WarningAmber
                            integrity == AppPreferences.STATUS_VERIFIED -> "VERIFIED" to SuccessGreen
                            integrity == AppPreferences.STATUS_UNKNOWN -> "READY" to accent
                            else -> "LOCAL MODE" to WarningAmber
                        }
                        StatusChip(text = label, color = color, filled = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onOpenSection(
                                        if (vm.hasApiKey) SettingsSection.AI_MODELS else SettingsSection.API_KEYS
                                    )
                                }
                        ) {
                            StatusChip(text = model.take(22), color = NeonPurple)
                        }
                    }
                }
                if (state.messages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .testTag("hud_toggle")
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { hudExpanded = !hudExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        StatusChip(
                            text = if (showFullHud) "CHAT" else "3D HUD",
                            color = accent,
                            filled = showFullHud,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.toggleContinuous(!continuous) },
                    contentAlignment = Alignment.Center,
                ) {
                    StatusChip(
                        text = if (continuous) "CONT ON" else "CONT",
                        color = if (continuous) SuccessGreen else TextMuted,
                        filled = continuous,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .testTag("stop_button")
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.stopEverything() },
                    contentAlignment = Alignment.Center,
                ) {
                    StatusChip(text = "STOP", color = DangerRed, filled = true)
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings kholo",
                        tint = TextPrimary,
                    )
                }
            }

            if (emergency) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WarningAmber.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Emergency stop active hai.",
                        color = WarningAmber,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(text = "Resume", onClick = { vm.resume() }, accent = WarningAmber)
                }
            }

            // ---------------------------- center orb + 3D glassmorphic depth cards
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Keep the glowing center particle sphere at the heart with reactive pulse frequencies
                JarvisOrb(
                    spec = spec,
                    state = state.orb,
                    micLevel = micLevel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.78f)
                        .aspectRatio(1f),
                    onTap = handleVoiceTrigger,
                    onLongPress = { vm.stopEverything() },
                )

                // Top 3D Depth Cards framing the orb
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val orbStatusText = when (state.orb) {
                        OrbState.LISTENING -> "SUN RAHA HOON"
                        OrbState.THINKING -> "SOCH RAHA HOON"
                        OrbState.SPEAKING -> "BOL RAHA HOON"
                        OrbState.ERROR -> "ERROR"
                        OrbState.IDLE -> "3D CORE READY"
                    }
                    DepthHudCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_orb_3d"),
                        icon = Icons.Filled.AutoAwesome,
                        badge = "ORB 3D",
                        title = "${spec.shape.uppercase()} · ${spec.particleCount}",
                        subtitle = orbStatusText,
                        accent = accent,
                        rotationX = -5f,
                        rotationY = 7f,
                        onClick = { vm.cycleOrbShape() },
                        secondaryActionLabel = "Edit",
                        onSecondaryClick = { onOpenSection(SettingsSection.APPEARANCE) },
                    )

                    val keyOk = vm.hasApiKey
                    DepthHudCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_ai_brain"),
                        icon = Icons.Filled.Psychology,
                        badge = if (keyOk) "GEMINI" else "KEY NEEDED",
                        title = model.removePrefix("models/").take(18),
                        subtitle = "Temp ${"%.1f".format(temperature)} · Tap model badlo",
                        accent = if (keyOk) NeonPurple else WarningAmber,
                        rotationX = -5f,
                        rotationY = -7f,
                        onClick = {
                            onOpenSection(
                                if (keyOk) SettingsSection.AI_MODELS else SettingsSection.API_KEYS
                            )
                        },
                        secondaryActionLabel = "Awaaz",
                        onSecondaryClick = { onOpenSection(SettingsSection.VOICE) },
                    )
                }

                // Bottom 3D Depth Cards when HUD is active (animates out smoothly during chat view)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFullHud,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DepthHudCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("card_self_setup"),
                                icon = Icons.Filled.Build,
                                badge = "SELF SETUP",
                                title = "$savedSetupsCount Saved",
                                subtitle = "File attach + setup kar lo",
                                accent = SuccessGreen,
                                rotationX = 5f,
                                rotationY = 7f,
                                onClick = { onOpenSection(SettingsSection.SAVED_SETUPS) },
                            )

                            DepthHudCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("card_memory_terminal"),
                                icon = Icons.Filled.Memory,
                                badge = "MEMORY",
                                title = "$memoryNotesCount Rules Yaad",
                                subtitle = "Terminal & Encrypted Notes",
                                accent = accent,
                                rotationX = 5f,
                                rotationY = -7f,
                                onClick = { onOpenSection(SettingsSection.MEMORY) },
                                secondaryActionLabel = "Shell",
                                secondaryIcon = Icons.Filled.Terminal,
                                onSecondaryClick = { onOpenSection(SettingsSection.TERMINAL) },
                            )
                        }
                    }
                }

                // Chat transcript overlay when messages exist and HUD isn't pinned open
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.50f),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.messages.isNotEmpty() && !showFullHud,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                        items(state.messages.asReversed(), key = { it.id }) { message ->
                            ChatBubble(
                                message = message,
                                onOpenArtifact = { artifact ->
                                    val ok = ArtifactIntents.open(context, artifact.path, artifact.mime)
                                    if (!ok) {
                                        vm.reportError(
                                            "Is file ko kholne wala koi app nahi mila. " +
                                                "Share se doosri app me bhej do."
                                        )
                                    }
                                },
                                onShareArtifact = { artifact ->
                                    val ok = ArtifactIntents.share(context, artifact.path, artifact.mime)
                                    if (!ok) vm.reportError("File share nahi ho payi.")
                                },
                            )
                        }
                    }
                }
                }
            }

            // ---------------------------------------------------------- footer
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.listening) {
                        MicLevelBars(level = micLevel, active = true)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Sun raha hoon…", color = accent, fontSize = 12.sp)
                    } else {
                        val dynamicStatus = when (state.orb) {
                            OrbState.THINKING -> state.status.ifBlank { "Gemini ($model) process kar raha hai…" }
                            OrbState.SPEAKING -> state.status.ifBlank { "Jarvis bol raha hai…" }
                            else -> state.status.ifBlank {
                                if (state.messages.isEmpty()) {
                                    "Orb pe tap karo, ya \"Jarvis\" bolo · 3D orbit drag karein"
                                } else {
                                    "Sab rokne ke liye orb ko long press karo."
                                }
                            }
                        }
                        Text(
                            text = dynamicStatus,
                            color = if (state.orb == OrbState.THINKING) NeonPurple else TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorBanner(message = state.error ?: "", onDismiss = { vm.dismissError() })
                }

                state.attachment?.let { attachment ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "📎 ${attachment.name} · ${attachment.kind}",
                            color = accent,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "setup kar lo",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { vm.clearAttachment() },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Attachment hatao",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("attach_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "File attach karo",
                            tint = if (state.attachment != null) accent else TextMuted,
                        )
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        placeholder = {
                            Text(text = "Jarvis se pucho…", color = TextMuted, fontSize = 14.sp)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendInput() }),
                        trailingIcon = {
                            IconButton(
                                onClick = sendInput,
                                modifier = Modifier.testTag("send_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Bhejo",
                                    tint = if (input.isBlank()) TextMuted else accent,
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent.copy(alpha = 0.7f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            cursorColor = accent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .testTag("mic_button")
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (state.listening) DangerRed else accent,
                                        NeonPurple,
                                    )
                                )
                            )
                            .clickable { handleVoiceTrigger() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = if (state.listening) "Sunna band karo" else "Jarvis se baat karo",
                            tint = Color(0xFF04070C),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        SetupProgressSheet(
            state = setup,
            onConfirm = { approved -> vm.confirmSetup(approved) },
            onCancel = { vm.cancelSetup() },
            onDismiss = { vm.dismissSetupReport() },
        )
    }
}

@Composable
private fun DepthHudCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    accent: Color,
    rotationX: Float,
    rotationY: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    secondaryIcon: ImageVector = Icons.Filled.Layers,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Glass3DDepthCard(
        modifier = modifier,
        accent = accent,
        rotationX = rotationX,
        rotationY = rotationY,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = badge,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = badge,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
            }
            if (secondaryActionLabel != null && onSecondaryClick != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .clickable { onSecondaryClick() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = secondaryIcon,
                        contentDescription = secondaryActionLabel,
                        tint = accent,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = secondaryActionLabel,
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
