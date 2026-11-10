package com.jarvis.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.domain.setup.PendingConfirmation
import com.jarvis.domain.setup.SelfSetupEngine
import com.jarvis.domain.setup.SetupStepUi
import com.jarvis.domain.setup.SetupUiState
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GhostButton
import com.jarvis.ui.theme.GlassBorder
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.SuccessGreen
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import com.jarvis.ui.theme.WarningAmber

/**
 * The live setup panel: plan, per-step status, streamed command output, the
 * dangerous-command confirmation gate and the honest final report.
 *
 * Nothing here is optimistic: a step turns green only after the runner returned
 * exit code 0 AND the verification passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupProgressSheet(
    state: SetupUiState,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.running && !state.finished) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!state.running) onDismiss() },
        sheetState = sheetState,
        containerColor = Color(0xFF0A0F16),
        contentColor = TextPrimary,
    ) {
        SetupSheetBody(
            state = state,
            onConfirm = onConfirm,
            onCancel = onCancel,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SetupSheetBody(
    state: SetupUiState,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccent.current
    val done = state.steps.count { it.status == SelfSetupEngine.DONE }
    val failed = state.steps.count { it.status == SelfSetupEngine.FAILED }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.title.isBlank()) "Self setup" else state.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when {
                        state.running -> "Chal raha hai · $done/${state.steps.size} steps done"
                        state.success -> "Setup safal · $done steps verified"
                        else -> "Setup ruk gaya · $failed step failed"
                    },
                    color = when {
                        state.running -> accent
                        state.success -> SuccessGreen
                        else -> DangerRed
                    },
                    fontSize = 12.sp,
                )
            }
            if (state.running) {
                StatusPill(text = "LIVE", color = accent)
            } else {
                StatusPill(
                    text = if (state.success) "DONE" else "STOPPED",
                    color = if (state.success) SuccessGreen else DangerRed,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.steps.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.steps, key = { it.index }) { step ->
                    StepRow(step = step, isActive = step.index == state.activeIndex)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.liveOutput.isNotBlank()) {
            Text(
                text = "LIVE OUTPUT",
                color = TextMuted,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, GlassBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = state.liveOutput.takeLast(1500),
                    color = Color(0xFF9BE7B0),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        state.pending?.let { pending ->
            ConfirmationCard(pending = pending, onConfirm = onConfirm)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.error != null) {
            Text(
                text = state.error,
                color = DangerRed,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.finished && state.summary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.success) SuccessGreen.copy(alpha = 0.12f)
                        else DangerRed.copy(alpha = 0.12f)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = state.summary,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.running) {
                GhostButton(text = "Setup rok do", onClick = onCancel, accent = DangerRed)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Saved Setups me sab kuch save ho raha hai.",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            } else {
                GhostButton(text = "Band karo", onClick = onDismiss, accent = accent)
            }
        }
    }
}

@Composable
private fun StepRow(step: SetupStepUi, isActive: Boolean) {
    val color = when (step.status) {
        SelfSetupEngine.DONE -> SuccessGreen
        SelfSetupEngine.FAILED -> DangerRed
        SelfSetupEngine.RUNNING -> LocalAccent.current
        SelfSetupEngine.WAITING -> WarningAmber
        SelfSetupEngine.SKIPPED -> TextMuted
        else -> TextMuted.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) color.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${step.index + 1}. ${step.title}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (step.command.isNotBlank()) {
                Text(
                    text = step.command.take(90),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = step.status.replace('_', ' ').uppercase(),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConfirmationCard(pending: PendingConfirmation, onConfirm: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarningAmber.copy(alpha = 0.12f))
            .border(1.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "Confirm chahiye",
                color = WarningAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pending.reasons.joinToString(" · "),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = pending.command.take(180),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                GhostButton(text = "Haan, chalao", onClick = { onConfirm(true) }, accent = WarningAmber)
                Spacer(modifier = Modifier.width(10.dp))
                GhostButton(text = "Nahi", onClick = { onConfirm(false) }, accent = TextMuted)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
            .clickable(enabled = false) {}
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
