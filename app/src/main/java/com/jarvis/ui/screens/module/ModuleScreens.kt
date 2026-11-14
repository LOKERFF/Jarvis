package com.jarvis.ui.screens.module

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.data.db.CommandLogEntity
import com.jarvis.data.db.SavedSetupEntity
import com.jarvis.data.db.SetupStepEntity
import com.jarvis.domain.learning.LearningRepository
import com.jarvis.ui.screens.settings.SettingsHint
import com.jarvis.ui.screens.settings.SettingsPage
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassBorder
import com.jarvis.ui.theme.GlassCard
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.SectionLabel
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber
import com.jarvis.ui.vm.MemoryViewModel
import com.jarvis.ui.vm.SavedSetupsViewModel
import com.jarvis.ui.vm.TerminalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun stamp(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(millis))

@Composable
private fun statusColor(status: String): Color = when (status) {
    "success", "done", "ok" -> SuccessGreen
    "failed" -> DangerRed
    "cancelled", "skipped", "timeout" -> WarningAmber
    "running" -> LocalAccent.current
    else -> TextMuted
}

// ---------------------------------------------------------------------------
// Saved Setups
// ---------------------------------------------------------------------------
@Composable
fun SavedSetupsScreen(
    onBack: () -> Unit,
    vm: SavedSetupsViewModel = viewModel(),
) {
    val setups by vm.setups.collectAsStateWithLifecycle()
    val detail = vm.detail
    var toDelete by remember { mutableStateOf<SavedSetupEntity?>(null) }

    SettingsPage(
        title = "Saved Setups",
        subtitle = "${setups.size} saved · permanent",
        onBack = onBack,
    ) {
        SettingsHint(
            "Har setup ke steps, unhone ka output aur exit code yahan permanently save hote " +
                "hain. App restart ya phone reboot ke baad bhi ye list rehti hai."
        )

        if (setups.isEmpty()) {
            GlassCard {
                SectionLabel("Abhi kuch nahi")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chat me 📎 se file attach karo aur \"setup kar lo\" likho. " +
                        "Setup yahan apne aap aa jayega.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        setups.forEach { setup ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = setup.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${setup.sourceName} · ${setup.sourceKind}",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stamp(setup.createdAt),
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    StatusPill(text = setup.status.uppercase(), color = statusColor(setup.status))
                }

                if (setup.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = setup.summary,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row {
                    GhostButton(
                        text = "Steps dekho",
                        onClick = { vm.open(setup.id) },
                        accent = LocalAccent.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GhostButton(
                        text = "Delete",
                        onClick = { toDelete = setup },
                        accent = DangerRed,
                    )
                }
            }
        }
    }

    detail?.let { current ->
        AlertDialog(
            onDismissRequest = { vm.closeDetail() },
            title = {
                Text(
                    text = current.setup.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (current.steps.isEmpty()) {
                        Text(text = "Koi step saved nahi hua.", color = TextMuted, fontSize = 12.sp)
                    }
                    current.steps.forEach { step ->
                        StepDetail(step)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.closeDetail() }) {
                    Text(text = "Band karo", color = LocalAccent.current)
                }
            },
            containerColor = Color(0xFF0A0F16),
        )
    }

    toDelete?.let { setup ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(text = "Setup delete karna hai?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    text = "\"${setup.name}\" aur uske saare steps permanently delete ho " +
                        "jayenge. Ye wapas nahi aayenge.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(setup.id)
                    toDelete = null
                }) {
                    Text(text = "Haan, delete", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(text = "Nahi", color = TextMuted)
                }
            },
            containerColor = Color(0xFF0A0F16),
        )
    }
}

@Composable
private fun StepDetail(step: SetupStepEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${step.stepIndex + 1}. ${step.title}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = step.status.uppercase(),
                color = statusColor(step.status),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (step.command.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.command.take(300),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (step.output.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.output.takeLast(400),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
            )
        }
        if (step.command.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "exit code: ${step.exitCode}",
                color = TextMuted,
                fontSize = 9.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Memory (learned corrections)
// ---------------------------------------------------------------------------
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    vm: MemoryViewModel = viewModel(),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val allNotes by vm.allNotes.collectAsStateWithLifecycle()
    val search by vm.searchQuery.collectAsStateWithLifecycle()
    val ui = vm.ui
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editMistake by remember { mutableStateOf("") }
    var editCorrect by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<LearningRepository.Note?>(null) }

    SettingsPage(
        title = "Memory",
        subtitle = if (search.isBlank()) {
            "${allNotes.size} learned corrections · encrypted"
        } else {
            "${notes.size} of ${allNotes.size} notes match"
        },
        onBack = onBack,
    ) {
        SettingsHint(
            "Har note AES-256-GCM se encrypt hota hai (Android Keystore key). Ye notes " +
                "permanent hain aur har naye jawab se pehle prompt me jaate hain, isliye " +
                "Jarvis wahi galti dobara nahi karta. Chat me \"ye galat hai\" bolne se note " +
                "apne aap banta hai."
        )

        OutlinedTextField(
            value = search,
            onValueChange = { vm.setQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(text = "Search: galti, sahi behaviour ya note #id", color = TextMuted, fontSize = 13.sp)
            },
            leadingIcon = {
                Text(text = "🔍", fontSize = 14.sp)
            },
            trailingIcon = {
                if (search.isNotBlank()) {
                    Text(
                        text = "Clear",
                        color = LocalAccent.current,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { vm.setQuery("") }
                            .padding(horizontal = 10.dp),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LocalAccent.current.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                cursorColor = LocalAccent.current,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
        )

        if (ui.error != null) {
            Text(
                text = ui.error ?: "",
                color = DangerRed,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (ui.message != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = ui.message ?: "", color = SuccessGreen, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OK",
                    color = LocalAccent.current,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { vm.dismissUi() },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (notes.isEmpty()) {
            GlassCard {
                SectionLabel(if (search.isBlank()) "Koi note nahi" else "Kuch nahi mila")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (search.isBlank()) {
                        "Chat me kuch galat hone par \"ye galat hai\" likho. Main poochhunga " +
                            "kya sahi hai, phir note permanently yaad rakh lunga."
                    } else {
                        "\"$search\" se koi note match nahi hua. Doosra shabd try karo ya " +
                            "search clear karo."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        notes.forEach { note ->
            val isEditing = editingId == note.id
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (note.active) "ACTIVE" else "OFF",
                        color = if (note.active) SuccessGreen else TextMuted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stamp(note.createdAt),
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "#${note.id}",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                SectionLabel("Galat tha")
                Text(
                    text = note.mistake.ifBlank { "(khali)" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                SectionLabel("Aage se aisa")
                Text(
                    text = note.correct,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                if (isEditing) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Field(
                        value = editMistake,
                        onValueChange = { editMistake = it },
                        label = "Galat kya hua",
                        minLines = 2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Field(
                        value = editCorrect,
                        onValueChange = { editCorrect = it },
                        label = "Aage se kya karna hai",
                        minLines = 2,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        GhostButton(
                            text = "Save",
                            onClick = {
                                vm.save(note.id, editMistake, editCorrect) { editingId = null }
                            },
                            accent = LocalAccent.current,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GhostButton(
                            text = "Cancel",
                            onClick = { editingId = null },
                            accent = TextMuted,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        GhostButton(
                            text = "Edit",
                            onClick = {
                                editingId = note.id
                                editMistake = note.mistake
                                editCorrect = note.correct
                            },
                            accent = LocalAccent.current,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GhostButton(
                            text = "Delete",
                            onClick = { toDelete = note },
                            accent = DangerRed,
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(text = "Note delete karna hai?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Ye note hamesha ke liye chala jayega aur Jarvis is correction ko " +
                        "bhool jayega.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(note.id)
                    toDelete = null
                }) {
                    Text(text = "Haan, delete", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(text = "Nahi", color = TextMuted)
                }
            },
            containerColor = Color(0xFF0A0F16),
        )
    }
}

// ---------------------------------------------------------------------------
// Terminal (in-app sandbox)
// ---------------------------------------------------------------------------
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    vm: TerminalViewModel = viewModel(),
) {
    val output by vm.output.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    var command by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    LaunchedEffect(output) {
        runCatching { scroll.animateScrollTo(scroll.maxValue) }
    }

    SettingsPage(
        title = "Terminal",
        subtitle = "App sandbox · /system/bin/sh",
        onBack = onBack,
    ) {
        SettingsHint(
            "Ye shell sirf app ke private folder me chalti hai. System files, doosre apps " +
                "ka data, uninstall aur root commands block hain. Khatarnak command par pehle " +
                "\"Haan\" confirmation maangi jati hai. Har command timestamp ke saath log hoti hai."
        )

        Text(
            text = "workdir: ${vm.workDir.absolutePath}",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, GlassBorder.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            if (output.isBlank()) {
                Text(
                    text = "Output yahan aayega…\nTry: echo hello",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = output,
                    color = Color(0xFF9BE7B0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(scroll),
                )
            }
        }

        vm.error?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = message, color = DangerRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.dismissError() }) {
                    Text(text = "OK", color = TextMuted)
                }
            }
        }

        val runTerminalCommand: () -> Unit = {
            if (command.isNotBlank() && !vm.running) {
                vm.submit(command)
                command = ""
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                singleLine = true,
                placeholder = {
                    Text(text = "command likho…", color = TextMuted, fontSize = 13.sp)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { runTerminalCommand() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalAccent.current.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                    cursorColor = LocalAccent.current,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            GhostButton(
                text = if (vm.running) "Chal raha…" else "Run",
                onClick = runTerminalCommand,
                modifier = Modifier.testTag("terminal_run_button"),
                accent = LocalAccent.current,
            )
        }

        Row {
            GhostButton(
                text = "Stop",
                onClick = { vm.cancelRunning() },
                accent = DangerRed,
            )
            Spacer(modifier = Modifier.width(8.dp))
            GhostButton(
                text = "Output clear",
                onClick = { vm.clearOutput() },
                accent = TextMuted,
            )
            Spacer(modifier = Modifier.width(8.dp))
            GhostButton(
                text = "Log clear",
                onClick = { vm.clearLog() },
                accent = TextMuted,
            )
        }

        SectionLabel("Command log")
        if (log.isEmpty()) {
            Text(text = "Abhi koi command nahi chali.", color = TextMuted, fontSize = 12.sp)
        }
        log.forEach { entry ->
            LogRow(entry)
        }
    }

    vm.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelPending() },
            title = {
                Text(text = "Khatarnak command — confirm?", color = WarningAmber, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Wajah: ${pending.reasons.joinToString(", ")}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pending.command,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirm(true) }) {
                    Text(text = "Haan, chalao", color = WarningAmber)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.confirm(false) }) {
                    Text(text = "Nahi", color = TextMuted)
                }
            },
            containerColor = Color(0xFF0A0F16),
        )
    }
}

@Composable
private fun LogRow(entry: CommandLogEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stamp(entry.timestamp),
                color = TextMuted,
                fontSize = 9.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${entry.outcome} · ${entry.exitCode}",
                color = statusColor(entry.outcome),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = entry.command.take(140),
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label, color = TextMuted, fontSize = 12.sp) },
        minLines = minLines,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary,
            fontSize = 13.sp,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalAccent.current.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
            cursorColor = LocalAccent.current,
        ),
    )
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}
