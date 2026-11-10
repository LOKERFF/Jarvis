package com.jarvis.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * AGSL glow layer (Android 13+). It draws a soft, breathing plasma behind the
 * particles. On older versions the orb falls back to a radial-gradient halo in
 * OrbView, so the visual language stays the same.
 *
 * Everything is one full-canvas rect, so the cost is a single draw call.
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
    ) {
        if (width <= 1f || height <= 1f) return
        shader.setFloatUniform("uSize", width, height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uColor", red, green, blue)
        shader.setFloatUniform("uGlow", glow.coerceIn(0f, 1f))
        shader.setFloatUniform("uEnergy", energy.coerceIn(0f, 1f))
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private companion object {
        const val AGSL = """
            uniform float2 uSize;
            uniform float  uTime;
            uniform float3 uColor;
            uniform float  uGlow;
            uniform float  uEnergy;

            half4 main(float2 fragCoord) {
                float2 p = (fragCoord - uSize * 0.5) / min(uSize.x, uSize.y);
                float r = length(p);
                float angle = atan(p.y, p.x);
                float wobble = 0.018 * sin(uTime * 0.9 + angle * 3.0)
                             + 0.010 * sin(uTime * 1.7 - angle * 5.0);
                float core = smoothstep(0.42 + wobble, 0.02, r);
                float halo = exp(-r * 2.7) * (0.45 + 0.55 * uGlow);
                float ringRadius = 0.30 + wobble;
                float ring = exp(-pow((r - ringRadius) * 15.0, 2.0)) * (0.15 + 0.55 * uEnergy);
                float a = clamp(core * 0.55 + halo * 0.45 + ring * 0.55, 0.0, 1.0);
                a = a * (0.45 + 0.55 * uGlow);
                float3 col = uColor * (0.45 + a * 1.1);
                return half4(half(col.r), half(col.g), half(col.b), half(a));
            }
        """
    }
}
