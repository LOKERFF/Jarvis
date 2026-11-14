package com.jarvis.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jarvis.domain.model.ChatArtifact
import com.jarvis.ui.theme.GlassBorder
import com.jarvis.ui.theme.LocalAccent
import com.jarvis.ui.theme.NeonPurple
import com.jarvis.ui.theme.TextMuted
import com.jarvis.ui.theme.TextPrimary
import com.jarvis.ui.theme.TextSecondary
import java.io.File

/**
 * A file or video that Jarvis produced, rendered inside its chat bubble.
 *
 * Videos get a real in-chat player (Media3 ExoPlayer). Everything else can be
 * opened or shared through the app's FileProvider - no storage permission is
 * requested anywhere.
 */
@Composable
fun ArtifactCard(
    artifact: ChatArtifact,
    onOpen: (ChatArtifact) -> Unit = {},
    onShare: (ChatArtifact) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    var playing by remember(artifact.path) { mutableStateOf(false) }
    val exists = remember(artifact.path) { File(artifact.path).exists() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        if (artifact.isVideo && playing) {
            InlineVideoPlayer(path = artifact.path, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), NeonPurple.copy(alpha = 0.45f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val label = artifact.name.substringAfterLast('.', "file").uppercase().take(4)
                Text(
                    text = label,
                    color = Color(0xFF04070C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        !exists -> "File app storage me nahi mili"
                        artifact.isVideo -> "Video · dekhne ke liye Play dabao"
                        else -> sizeLabel(artifact.path)
                    },
                    color = if (exists) TextMuted else TextSecondary,
                    fontSize = 11.sp,
                )
            }
            if (artifact.isVideo) {
                MiniAction(
                    label = if (playing) "Band" else "Play",
                    accent = accent,
                    enabled = exists,
                    onClick = { playing = !playing },
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            MiniAction(label = "Kholo", accent = accent, enabled = exists, onClick = { onOpen(artifact) })
            Spacer(modifier = Modifier.width(6.dp))
            MiniAction(label = "Share", accent = accent, enabled = exists, onClick = { onShare(artifact) })
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (enabled) accent.copy(alpha = 0.45f) else GlassBorder.copy(alpha = 0.2f),
                RoundedCornerShape(9.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) accent else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One ExoPlayer per visible card; released as soon as the card leaves. */
@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPlayer(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(File(path).toUri()))
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(path) {
        onDispose {
            runCatching {
                player.stop()
                player.release()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .aspectRatio(9f / 16f)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!player.isPlaying) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clickable { player.play() },
            )
        }
    }
}

private fun sizeLabel(path: String): String {
    val bytes = runCatching { File(path).length() }.getOrDefault(0L)
    return when {
        bytes <= 0L -> "Empty file"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/** Small helper kept next to the card so HomeScreen stays readable. */
fun artifactSubtitle(artifact: ChatArtifact): String =
    if (artifact.isVideo) "video" else artifact.mime.substringBefore('/')

@Composable
fun ArtifactRowPreview(artifact: ChatArtifact) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = artifact.name, color = TextSecondary, fontSize = 11.sp)
        Text(text = artifactSubtitle(artifact), color = TextMuted, fontSize = 11.sp)
    }
}
