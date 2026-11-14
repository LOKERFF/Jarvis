package com.jarvis.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * AGSL glow layer (Android 13+). Draws a breathing plasma aura behind the
 * 3D particles, expanding and pulsing in lockstep with the orb's reactive state.
 */
@RequiresApi(33)
internal class OrbShaderLayer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = RuntimeShader(AGSL)

    init {
        paint.shader = shader
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Float,
        red: Float,
        green: Float,
        blue: Float,
        glow: Float,
        energy: Float,
        pulse: Float = 1f,
    ) {
        if (width <= 1f || height <= 1f) return
        shader.setFloatUniform("uSize", width, height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uColor", red, green, blue)
        shader.setFloatUniform("uGlow", glow.coerceIn(0f, 1f))
        shader.setFloatUniform("uEnergy", energy.coerceIn(0f, 1f))
        shader.setFloatUniform("uPulse", pulse.coerceIn(0.85f, 1.25f))
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private companion object {
        const val AGSL = """
            uniform float2 uSize;
            uniform float  uTime;
            uniform float3 uColor;
            uniform float  uGlow;
            uniform float  uEnergy;
            uniform float  uPulse;

            half4 main(float2 fragCoord) {
                float2 p = (fragCoord - uSize * 0.5) / min(uSize.x, uSize.y);
                float r = length(p);
                float angle = atan(p.y, p.x);
                float wobble = (0.014 + 0.016 * uEnergy) * sin(uTime * (0.9 + uEnergy * 1.8) + angle * 3.0)
                             + 0.010 * cos(uTime * 1.9 - angle * 5.0);
                float scaledR = r / max(uPulse, 0.85);
                float core = smoothstep(0.42 + wobble, 0.02, scaledR);
                float halo = exp(-scaledR * (2.6 - 0.3 * uEnergy)) * (0.45 + 0.55 * uGlow);
                float ringRadius = 0.31 * uPulse + wobble;
                float ring = exp(-pow((r - ringRadius) * 16.0, 2.0)) * (0.16 + 0.60 * uEnergy);
                float a = clamp(core * 0.55 + halo * 0.45 + ring * 0.58, 0.0, 1.0);
                a = a * (0.45 + 0.55 * uGlow);
                float3 col = uColor * (0.45 + a * 1.15);
                return half4(half(col.r), half(col.g), half(col.b), half(a));
            }
        """
    }
}
