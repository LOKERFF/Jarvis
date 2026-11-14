package com.jarvis.ui.components

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.domain.model.OrbState
import com.jarvis.domain.orb.OrbGeometry
import com.jarvis.domain.orb.OrbSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_PARTICLES = 8000

/**
 * The orb.
 *
 * - thousands of particles on a parametric surface, drawn with three depth
 *   buckets so it looks volumetric without per-particle gradients;
 * - AGSL plasma glow on Android 13+, radial-gradient halo before that;
 * - every OrbSpec field animates over 0.8 s, and a shape change morphs the
 *   outgoing cloud into the incoming one;
 * - the animation state follows the engine: idle / listening / thinking /
 *   speaking / error.
 *
 * Implemented entirely in Compose Canvas so no GL context or extra permission
 * is needed.
 */
@Composable
fun JarvisOrb(
    spec: OrbSpec,
    state: OrbState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val targetColor = Color(OrbSpec.colorInt(spec.color))
    val sizeScale by animateFloatAsState(
        targetValue = spec.size, animationSpec = tween(800), label = "orb-size"
    )
    val glow by animateFloatAsState(
        targetValue = spec.glow, animationSpec = tween(800), label = "orb-glow"
    )
    val spin by animateFloatAsState(
        targetValue = spec.rotationSpeed, animationSpec = tween(800), label = "orb-spin"
    )
    val countTarget by animateFloatAsState(
        targetValue = spec.particleCount.toFloat(), animationSpec = tween(800), label = "orb-count"
    )
    val color by animateColorAsState(
        targetValue = targetColor, animationSpec = tween(800), label = "orb-color"
    )

    val particleCount = countTarget.roundToInt().coerceIn(500, MAX_PARTICLES)
    val points = remember(spec.shape, spec.formula, particleCount) {
        OrbGeometry.build(spec, particleCount)
    }

    // Morph support: `previous` holds the outgoing cloud while `blend` runs.
    val previous = remember { CloudHolder() }
    val lastDrawn = remember { CloudHolder() }
    val blend = remember { Animatable(1f) }
    val shapeKey = remember(spec.shape, spec.formula) { spec.shape + "|" + (spec.formula ?: "") }
    var lastShapeKey by remember { mutableStateOf(shapeKey) }

    LaunchedEffect(shapeKey) {
        if (lastShapeKey != shapeKey) {
            previous.value = lastDrawn.value
            blend.snapTo(0f)
            lastShapeKey = shapeKey
            blend.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
            previous.value = null
        }
    }

    // Frame clock. Read inside the draw scope, so a new frame only repaints.
    val timeState = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                timeState.value = (now - start) / 1_000_000_000f
            }
        }
    }

    val shaderLayer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { OrbShaderLayer() }.getOrNull()
        } else {
            null
        }
    }

    val paintFront = remember { newParticlePaint() }
    val paintMid = remember { newParticlePaint() }
    val paintBack = remember { newParticlePaint() }
    val bufferFront = remember { FloatArray(MAX_PARTICLES * 2) }
    val bufferMid = remember { FloatArray(MAX_PARTICLES * 2) }
    val bufferBack = remember { FloatArray(MAX_PARTICLES * 2) }

    val dragScope = rememberCoroutineScope()
    val dragYaw = remember { Animatable(0f) }
    val dragPitch = remember { Animatable(0f) }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Jarvis 3D orb. Bolne ke liye tap karo, sab rokne ke liye long press karo." }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        dragScope.launch { dragYaw.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                        dragScope.launch { dragPitch.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                    },
                    onDragCancel = {
                        dragScope.launch { dragYaw.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                        dragScope.launch { dragPitch.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragScope.launch {
                            dragYaw.snapTo((dragYaw.value + dragAmount.x * 0.008f).coerceIn(-1.4f, 1.4f))
                            dragPitch.snapTo((dragPitch.value + dragAmount.y * 0.006f).coerceIn(-0.85f, 0.85f))
                        }
                    },
                )
            }
    ) {
        val time = timeState.value
        val width = size.width
        val height = size.height
        if (width <= 1f || height <= 1f) return@Canvas

        val center = Offset(width / 2f, height / 2f)
        val baseRadius = min(width, height) * 0.5f * 0.80f * sizeScale

        val pulse = when (state) {
            OrbState.LISTENING -> 1f + 0.045f * sin(time * 6.5f)
            OrbState.THINKING -> 1f + 0.020f * sin(time * 3.2f)
            OrbState.SPEAKING -> 1f + 0.030f * sin(time * 9.0f)
            OrbState.ERROR -> 1f + 0.060f * sin(time * 22f)
            OrbState.IDLE -> 1f + 0.012f * sin(time * 1.6f)
        }
        val radius = baseRadius * pulse

        val energy = when (state) {
            OrbState.LISTENING -> 0.85f
            OrbState.THINKING -> 0.65f
            OrbState.SPEAKING -> 0.95f
            OrbState.ERROR -> 1f
            OrbState.IDLE -> 0.2f
        }
        val flicker = if (state == OrbState.ERROR) {
            0.55f + 0.45f * abs(sin(time * 9f))
        } else {
            1f
        }

        // 1) plasma glow (shader on API 33+, gradient everywhere)
        shaderLayer?.let { layer ->
            drawIntoCanvas { canvas ->
                runCatching {
                    layer.draw(
                        canvas = canvas.nativeCanvas,
                        width = width,
                        height = height,
                        time = time,
                        red = color.red,
                        green = color.green,
                        blue = color.blue,
                        glow = glow,
                        energy = energy,
                    )
                }
            }
        }
        if (shaderLayer == null) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (0.34f * glow + 0.05f) * flicker),
                        color.copy(alpha = 0.10f * glow * flicker),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 2.1f,
                ),
                radius = radius * 2.1f,
                center = center,
            )
        }

        // 2) listening ring + subtle 3D orbital depth halo
        val orbitAlpha = when (state) {
            OrbState.LISTENING -> 0.34f
            OrbState.SPEAKING -> 0.30f
            OrbState.THINKING -> 0.24f
            OrbState.ERROR -> 0.28f
            OrbState.IDLE -> 0.14f
        }
        drawCircle(
            color = color.copy(alpha = orbitAlpha * flicker),
            radius = radius * 1.15f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (state == OrbState.LISTENING || state == OrbState.SPEAKING) {
            val ringAlpha = 0.20f + 0.30f * (0.5f + 0.5f * sin(time * 4.5f))
            drawCircle(
                color = color.copy(alpha = ringAlpha),
                radius = radius * 1.28f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // 3) rotate + project every particle into three depth buckets
        val yaw = time * spin * speedFactor(state) + dragYaw.value
        val cosY = cos(yaw).toFloat()
        val sinY = sin(yaw).toFloat()
        val tilt = 0.32f + dragPitch.value
        val cosX = cos(tilt)
        val sinX = sin(tilt)

        val cloud = points
        val outgoing = previous.value
        val morph = blend.value
        val morphing = outgoing != null && outgoing.size == cloud.size && morph < 0.999f

        var front = 0
        var mid = 0
        var back = 0

        for (i in 0 until particleCount) {
            val index = i * 3
            var x = cloud[index]
            var y = cloud[index + 1]
            var z = cloud[index + 2]
            if (morphing && outgoing != null) {
                x = outgoing[index] + (x - outgoing[index]) * morph
                y = outgoing[index + 1] + (y - outgoing[index + 1]) * morph
                z = outgoing[index + 2] + (z - outgoing[index + 2]) * morph
            }

            // ripple while speaking
            if (state == OrbState.SPEAKING) {
                val ripple = 1f + 0.02f * sin(time * 8f - z * 6f)
                x *= ripple
                y *= ripple
                z *= ripple
            }

            val rotatedX = x * cosY + z * sinY
            val rotatedZ = -x * sinY + z * cosY
            val rotatedY = y * cosX - rotatedZ * sinX
            val depthZ = y * sinX + rotatedZ * cosX

            val depth = ((depthZ + 1f) * 0.5f).coerceIn(0f, 1f)
            val depthScale = 0.74f + 0.26f * depth
            val screenX = center.x + rotatedX * radius * depthScale
            val screenY = center.y + rotatedY * radius * depthScale

            when {
                depth >= 0.68f -> {
                    bufferFront[front++] = screenX
                    bufferFront[front++] = screenY
                }

                depth >= 0.34f -> {
                    bufferMid[mid++] = screenX
                    bufferMid[mid++] = screenY
                }

                else -> {
                    bufferBack[back++] = screenX
                    bufferBack[back++] = screenY
                }
            }
        }

        // 4) one native draw call per depth bucket: no per-particle allocation
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (back > 1) {
                paintBack.color = argb(color, 0.26f * flicker)
                paintBack.strokeWidth = 1.1.dp.toPx()
                native.drawPoints(bufferBack, 0, back, paintBack)
            }
            if (mid > 1) {
                paintMid.color = argb(color, 0.60f * flicker)
                paintMid.strokeWidth = 1.7.dp.toPx()
                native.drawPoints(bufferMid, 0, mid, paintMid)
            }
            if (front > 1) {
                paintFront.color = argb(color, 0.98f * flicker)
                paintFront.strokeWidth = 2.4.dp.toPx()
                native.drawPoints(bufferFront, 0, front, paintFront)
            }
        }

        lastDrawn.value = cloud
    }
}

private class CloudHolder {
    var value: FloatArray? = null
}

private fun newParticlePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    strokeCap = Paint.Cap.ROUND
    style = Paint.Style.STROKE
}

private fun argb(color: Color, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return android.graphics.Color.argb(
        a,
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
    )
}

private fun speedFactor(state: OrbState): Float = when (state) {
    OrbState.THINKING -> 3.2f
    OrbState.LISTENING -> 1.35f
    OrbState.SPEAKING -> 1.6f
    OrbState.ERROR -> 2.2f
    OrbState.IDLE -> 1f
}

/** Non-interactive orb used by settings screens. */
@Composable
fun OrbPreview(spec: OrbSpec, modifier: Modifier = Modifier) {
    JarvisOrb(spec = spec, state = OrbState.IDLE, modifier = modifier)
}
