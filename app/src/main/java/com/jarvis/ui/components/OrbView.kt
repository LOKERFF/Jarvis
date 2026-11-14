package com.jarvis.ui.components

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.domain.model.OrbState
import com.jarvis.domain.orb.OrbGeometry
import com.jarvis.domain.orb.OrbSpec
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_PARTICLES = 8000

/**
 * Reactive 3D Particle Orb.
 *
 * Smoothly adapts pulse frequency, orbital sweep speed, and per-particle wave
 * displacement based on live [OrbState] ('listening', 'thinking', 'speaking',
 * 'error', 'idle') and live microphone amplitude [micLevel].
 */
@Composable
fun JarvisOrb(
    spec: OrbSpec,
    state: OrbState,
    modifier: Modifier = Modifier,
    micLevel: Float = 0f,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val targetColor = Color(OrbSpec.colorInt(spec.color))
    val sizeScale by animateFloatAsState(
        targetValue = spec.size, animationSpec = tween(700), label = "orb-size"
    )
    val glow by animateFloatAsState(
        targetValue = spec.glow, animationSpec = tween(700), label = "orb-glow"
    )
    val spin by animateFloatAsState(
        targetValue = spec.rotationSpeed, animationSpec = tween(700), label = "orb-spin"
    )
    val countTarget by animateFloatAsState(
        targetValue = spec.particleCount.toFloat(), animationSpec = tween(700), label = "orb-count"
    )
    val color by animateColorAsState(
        targetValue = targetColor, animationSpec = tween(700), label = "orb-color"
    )

    // Smoothly interpolated reactive state targets so transitions never snap abruptly.
    val targetPulseFreq = when (state) {
        OrbState.IDLE -> 1.5f
        OrbState.LISTENING -> 5.2f
        OrbState.THINKING -> 3.4f
        OrbState.SPEAKING -> 9.0f
        OrbState.ERROR -> 16.0f
    }
    val targetPulseAmp = when (state) {
        OrbState.IDLE -> 0.014f
        OrbState.LISTENING -> 0.036f
        OrbState.THINKING -> 0.024f
        OrbState.SPEAKING -> 0.038f
        OrbState.ERROR -> 0.055f
    }
    val targetEnergy = when (state) {
        OrbState.IDLE -> 0.22f
        OrbState.LISTENING -> 0.85f
        OrbState.THINKING -> 0.68f
        OrbState.SPEAKING -> 0.95f
        OrbState.ERROR -> 1.0f
    }
    val targetSpinMult = when (state) {
        OrbState.IDLE -> 1.0f
        OrbState.LISTENING -> 1.38f
        OrbState.THINKING -> 3.10f
        OrbState.SPEAKING -> 1.75f
        OrbState.ERROR -> 2.30f
    }

    val pulseFreq by animateFloatAsState(targetPulseFreq, tween(450), label = "orb-freq")
    val pulseAmp by animateFloatAsState(targetPulseAmp, tween(450), label = "orb-amp")
    val energy by animateFloatAsState(targetEnergy, tween(450), label = "orb-energy")
    val spinMult by animateFloatAsState(targetSpinMult, tween(500), label = "orb-spin-mult")
    val smoothMic by animateFloatAsState(
        targetValue = if (state == OrbState.LISTENING) micLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(140),
        label = "orb-mic",
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

    // Continuous phase accumulators so changing pulse frequency never causes phase jumps.
    val timeState = remember { mutableFloatStateOf(0f) }
    val pulsePhaseState = remember { mutableFloatStateOf(0f) }
    val currentPulseFreq by rememberUpdatedState(pulseFreq)

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        val startNanos = lastNanos
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                timeState.floatValue = (now - startNanos) / 1_000_000_000f
                pulsePhaseState.floatValue += dt * currentPulseFreq
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
            .semantics {
                contentDescription = "Jarvis 3D orb. Bolne ke liye tap karo, sab rokne ke liye long press karo."
            }
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
        val time = timeState.floatValue
        val phase = pulsePhaseState.floatValue
        val width = size.width
        val height = size.height
        if (width <= 1f || height <= 1f) return@Canvas

        val center = Offset(width / 2f, height / 2f)
        val baseRadius = min(width, height) * 0.5f * 0.80f * sizeScale

        // Composite reactive pulse curve tailored per Jarvis activity state.
        val harmonic = when (state) {
            OrbState.SPEAKING -> 0.72f * sin(phase) + 0.28f * sin(phase * 2.15f)
            OrbState.THINKING -> 0.80f * sin(phase) + 0.20f * cos(phase * 1.5f)
            else -> sin(phase)
        }
        val micLift = smoothMic * 0.065f
        val pulse = 1f + (pulseAmp + micLift * 0.45f) * harmonic + micLift * 0.35f
        val radius = baseRadius * pulse

        val flicker = if (state == OrbState.ERROR) {
            0.55f + 0.45f * abs(sin(time * 9f))
        } else {
            1f
        }

        // 1) Plasma core glow (AGSL shader on API 33+, radial gradient fallback)
        val effectiveEnergy = (energy + smoothMic * 0.25f).coerceIn(0f, 1f)
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
                        energy = effectiveEnergy,
                        pulse = pulse,
                    )
                }
            }
        }
        if (shaderLayer == null) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (0.36f * glow + 0.05f) * flicker),
                        color.copy(alpha = 0.12f * glow * flicker),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 2.1f,
                ),
                radius = radius * 2.1f,
                center = center,
            )
        }

        // 2) State-reactive orbital rings & Gemini processing arcs
        val orbitAlpha = when (state) {
            OrbState.LISTENING -> 0.34f + smoothMic * 0.28f
            OrbState.SPEAKING -> 0.34f
            OrbState.THINKING -> 0.28f
            OrbState.ERROR -> 0.30f
            OrbState.IDLE -> 0.14f
        }
        drawCircle(
            color = color.copy(alpha = orbitAlpha.coerceIn(0f, 1f) * flicker),
            radius = radius * 1.15f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        when (state) {
            OrbState.LISTENING -> {
                val ringScale = 1.24f + smoothMic * 0.12f + 0.03f * sin(phase)
                val ringAlpha = 0.24f + 0.34f * (0.5f + 0.5f * sin(phase)) + smoothMic * 0.22f
                drawCircle(
                    color = color.copy(alpha = ringAlpha.coerceIn(0f, 0.9f)),
                    radius = radius * ringScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            OrbState.THINKING -> {
                val arcRadius = radius * 1.24f
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                val sweepStart = (time * 135f) % 360f
                drawArc(
                    color = color.copy(alpha = 0.58f),
                    startAngle = sweepStart,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = color.copy(alpha = 0.36f),
                    startAngle = sweepStart + 180f,
                    sweepAngle = 58f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            OrbState.SPEAKING -> {
                val outerR = radius * (1.26f + 0.045f * sin(phase * 1.2f))
                val ringAlpha = 0.22f + 0.32f * (0.5f + 0.5f * sin(phase))
                drawCircle(
                    color = color.copy(alpha = ringAlpha.coerceIn(0f, 0.85f)),
                    radius = outerR,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            else -> Unit
        }

        // 3) Rotate & project every particle into three depth buckets
        val yaw = time * spin * spinMult + dragYaw.value
        val cosY = cos(yaw)
        val sinY = sin(yaw)
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
            if (morphing) {
                x = outgoing[index] + (x - outgoing[index]) * morph
                y = outgoing[index + 1] + (y - outgoing[index + 1]) * morph
                z = outgoing[index + 2] + (z - outgoing[index + 2]) * morph
            }

            // State-specific subtle particle surface waves
            when (state) {
                OrbState.LISTENING -> {
                    val acousticWave = 1f + (0.018f + smoothMic * 0.045f) * sin(phase - y * 4.2f)
                    x *= acousticWave
                    y *= acousticWave
                    z *= acousticWave
                }
                OrbState.THINKING -> {
                    val neuralWave = 1f + 0.026f * sin(phase * 1.6f - y * 5.2f + x * 2.5f)
                    x *= neuralWave
                    y *= neuralWave
                    z *= neuralWave
                }
                OrbState.SPEAKING -> {
                    val ripple = 1f + 0.028f * sin(phase * 1.3f - z * 5.5f)
                    x *= ripple
                    y *= ripple
                    z *= ripple
                }
                else -> Unit
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

        // 4) Draw depth buckets cleanly in three native calls
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

/** Non-interactive orb used by settings screens. */
@Composable
fun OrbPreview(spec: OrbSpec, modifier: Modifier = Modifier) {
    JarvisOrb(spec = spec, state = OrbState.IDLE, modifier = modifier)
}
