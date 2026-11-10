package com.jarvis.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.domain.model.ChatMessage
import com.jarvis.ui.theme.DangerRed
import com.jarvis.ui.theme.GlassBorder
import com.jarvis.ui.theme.GlassTop
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.sin

/** Five bars driven by the real microphone RMS from the recogniser. */
@Composable
fun MicLevelBars(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
) {
    val multipliers = listOf(0.45f, 0.8f, 1f, 0.7f, 0.5f)
    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        multipliers.forEachIndexed { index, multiplier ->
            val wave = if (active) {
                (0.35f + 0.65f * abs(sin(System.currentTimeMillis() / 180.0 + index))).toFloat()
            } else {
                0.15f
            }
            val height = (4f + 18f * level.coerceIn(0f, 1f) * multiplier * wave).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 0.9f else 0.35f))
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onOpenArtifact: (com.jarvis.domain.model.ChatArtifact) -> Unit = {},
    onShareArtifact: (com.jarvis.domain.model.ChatArtifact) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatMessage.Role.USER
    val accent = LocalAccent.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.14f))
                        )
                    } else {
                        Brush.linearGradient(listOf(GlassTop, Color(0x0AFFFFFF)))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) accent.copy(alpha = 0.45f) else GlassBorder.copy(alpha = 0.3f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (!isUser) {
                Text(
                    text = "JARVIS",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Text(
                text = message.text,
                color = if (isUser) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            message.artifact?.let { artifact ->
                ArtifactCard(
                    artifact = artifact,
                    onOpen = onOpenArtifact,
                    onShare = onShareArtifact,
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DangerRed.copy(alpha = 0.16f))
            .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = Color(0xFFFFC9C9),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = Color(0xFFFFC9C9),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = description, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04070C),
                checkedTrackColor = LocalAccent.current,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color(0xFF1B2233),
            ),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = TextSecondary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(text = value, color = valueColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun BulletLine(text: String, color: Color = TextSecondary) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "•", color = LocalAccent.current, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** Small letter badge used by the settings list instead of an icon font. */
@Composable
fun LetterBadge(letters: String, color: Color = LocalAccent.current) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letters,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
