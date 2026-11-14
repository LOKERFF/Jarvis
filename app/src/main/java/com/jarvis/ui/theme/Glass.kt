package com.jarvis.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Glassmorphism kit: dark neon theme with frosted cards + 3D depth cards.
 * Compose builds true 3D perspective using graphicsLayer rotationX/rotationY,
 * cameraDistance, specular rim borders, and corner ambient sheen.
 */

@Composable
fun JarvisBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val isDark = LocalIsDark.current
    val accent = LocalAccent.current
    val baseColors = if (isDark) {
        listOf(VoidBlack, DeepVoid, VoidBlack)
    } else {
        listOf(Color(0xFF0F1B33), Color(0xFF1A2544), Color(0xFF0D162B))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(baseColors))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = if (isDark) 0.14f else 0.22f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.38f),
                            radius = size.maxDimension * 0.65f,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.38f),
                        radius = size.maxDimension * 0.65f,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonPurple.copy(alpha = if (isDark) 0.10f else 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.82f),
                            radius = size.maxDimension * 0.55f,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.82f),
                        radius = size.maxDimension * 0.55f,
                    )
                }
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = LocalAccent.current,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(GlassTop, GlassBottom))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.45f), GlassBorder.copy(alpha = 0.25f))
                ),
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onClick() }
                } else Modifier
            )
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun Glass3DDepthCard(
    modifier: Modifier = Modifier,
    accent: Color = LocalAccent.current,
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    elevation: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                this.rotationX = rotationX
                this.rotationY = rotationY
                this.cameraDistance = 14f * density
                this.shadowElevation = elevation.toPx()
                this.shape = shape
                this.clip = true
                this.ambientShadowColor = accent.copy(alpha = 0.45f)
                this.spotShadowColor = accent.copy(alpha = 0.55f)
            }
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF162038).copy(alpha = 0.86f),
                        Color(0xFF0A1020).copy(alpha = 0.78f),
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.15f),
                        radius = size.maxDimension * 0.85f,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.15f),
                    radius = size.maxDimension * 0.85f,
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.22f),
                        GlassBorder.copy(alpha = 0.20f),
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = TextMuted,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalAccent.current,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.95f), NeonPurple.copy(alpha = 0.9f))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color(0xFF232A3A), Color(0xFF232A3A))
                    )
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Color(0xFF04070C) else TextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalAccent.current,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = if (enabled) 0.6f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) accent else TextMuted,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun JarvisTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun GlassDivider(alpha: Float = 0.12f) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}

@Composable
fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
