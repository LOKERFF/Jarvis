package com.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.core.ArtifactIntents
import com.jarvis.core.EmergencyStop
import com.jarvis.core.ServiceLocator
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.domain.model.OrbState
import com.jarvis.domain.orb.OrbSpec
import com.jarvis.ui.components.ChatBubble
import com.jarvis.ui.components.ErrorBanner
import com.jarvis.ui.components.JarvisOrb
import com.jarvis.ui.components.MicLevelBars
import com.jarvis.ui.screens.setup.SetupProgressSheet
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonPurple
import com.jarvis.ui.theme.StatusChip
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.JarvisBackground
import com.jarvis.ui.vm.ChatViewModel
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    vm: ChatViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spec by vm.orbSpec.collectAsStateWithLifecycle()
    val micLevel by vm.micLevel.collectAsStateWithLifecycle()
    val continuous by vm.continuousMode.collectAsStateWithLifecycle()
    val model by vm.selectedModel.collectAsStateWithLifecycle()

    val setup by vm.setupState.collectAsStateWithLifecycle()
    val emergency by EmergencyStop.active.collectAsStateWithLifecycle()
    val limited by ServiceLocator.prefs.limitedMode.collectAsStateWithLifecycle()
    val integrity by ServiceLocator.prefs.integrityStatus.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val accent = LocalAccent.current
    val context = LocalContext.current

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
                    .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JARVIS",
                        color = accent,
                        fontSize = 22.sp,
                        letterSpacing = 7.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (label, color) = when {
                            limited -> "LIMITED MODE" to WarningAmber
                            integrity == AppPreferences.STATUS_VERIFIED -> "VERIFIED" to SuccessGreen
                            integrity == AppPreferences.STATUS_UNKNOWN -> "CHECK PENDING" to TextMuted
                            else -> "NOT VERIFIED" to WarningAmber
                        }
                        StatusChip(text = label, color = color, filled = true)
                        StatusChip(text = model.take(26), color = NeonPurple)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.toggleContinuous(!continuous) }
                ) {
                    StatusChip(
                        text = if (continuous) "CONT ON" else "CONT OFF",
                        color = if (continuous) SuccessGreen else TextMuted,
                        filled = continuous,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.stopEverything() }
                ) {
                    StatusChip(text = "STOP", color = DangerRed, filled = true)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
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
                        text = "Emergency stop is active.",
                        color = WarningAmber,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(text = "Resume", onClick = { vm.resume() }, accent = WarningAmber)
                }
            }

            // ------------------------------------------------- orb + transcript
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                JarvisOrb(
                    spec = spec,
                    state = state.orb,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 20.dp),
                    onTap = { vm.onOrbTap() },
                    onLongPress = { vm.stopEverything() },
                )

                if (state.messages.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.46f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
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

            // ---------------------------------------------------------- footer
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.listening) {
                        MicLevelBars(level = micLevel, active = true)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Listening…", color = accent, fontSize = 12.sp)
                    } else {
                        Text(
                            text = state.status.ifBlank {
                                if (state.messages.isEmpty()) {
                                    "Tap the orb, or say \"Jarvis\"."
                                } else {
                                    "Long press the orb to stop everything."
                                }
                            },
                            color = TextMuted,
                            fontSize = 12.sp,
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
                        )
                        Text(
                            text = "setup kar lo",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove attachment",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { vm.clearAttachment() },
                        )
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
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Attach a file",
                            tint = if (state.attachment != null) accent else TextMuted,
                        )
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        placeholder = {
                            Text(text = "Ask Jarvis…", color = TextMuted, fontSize = 14.sp)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendInput() }),
                        trailingIcon = {
                            IconButton(onClick = sendInput) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "Send",
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
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (state.listening) DangerRed else accent,
                                        NeonPurple,
                                    )
                                )
                            )
                            .clickable { vm.onOrbTap() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = if (state.listening) "Stop listening" else "Talk to Jarvis",
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
