package com.jarvis.domain.orb

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns an [OrbSpec] into a point cloud on the unit sphere.
 *
 * Output layout: FloatArray of size count*3, values (x, y, z), each component
 * roughly in -1..1, centred on the origin. Rendering (rotation, projection,
 * glow) happens on the Compose side.
 *
 * Everything is deterministic (a cheap integer hash instead of Random) so the
 * orb does not "boil" between recompositions.
 */
object OrbGeometry {

    fun build(spec: OrbSpec, count: Int): FloatArray {
        val safeCount = count.coerceIn(1, 8000)
        val out = FloatArray(safeCount * 3)
        when (spec.shape) {
            OrbSpec.SHAPE_HEART -> heart(out, safeCount)
            OrbSpec.SHAPE_TORUS -> torus(out, safeCount)
            OrbSpec.SHAPE_CUSTOM -> {
                val triple = spec.formula?.let { MiniExpr.parseTriple(it) }
                if (triple != null) custom(out, safeCount, triple) else sphere(out, safeCount)
            }
            else -> sphere(out, safeCount)
        }
        normalize(out, safeCount)
        return out
    }

    // ------------------------------------------------------------- shapes

    private fun sphere(out: FloatArray, count: Int) {
        val golden = PI * (3.0 - sqrt(5.0))
        for (i in 0 until count) {
            val t = if (count == 1) 0.0 else i / (count - 1.0)
            val y = 1.0 - t * 2.0
            val ringRadius = sqrt(max(0.0, 1.0 - y * y))
            val theta = golden * i
            // very small radial jitter keeps the surface alive instead of
            // looking like a perfect wireframe
            val jitter = 0.965 + 0.035 * hash01(i, 7)
            out[i * 3] = (cos(theta) * ringRadius * jitter).toFloat()
            out[i * 3 + 1] = (y * jitter).toFloat()
            out[i * 3 + 2] = (sin(theta) * ringRadius * jitter).toFloat()
        }
    }

    /**
     * Heart = the classic heart curve swept around the Y axis, so the front
     * silhouette is the recognisable heart and the body has real depth.
     */
    private fun heart(out: FloatArray, count: Int) {
        val golden = 3.883222077450933 // 2*pi golden fraction
        for (i in 0 until count) {
            val t = (i * golden) % (2.0 * PI)
            val hx = 16.0 * sin(t) * sin(t) * sin(t)
            val hy = 13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)
            val radius = abs(hx) / 17.0
            val phi = hash01(i, 11) * 2.0 * PI
            // thickness: keep near the silhouette but allow some depth so the
            // heart reads as a volume, not a flat card
            val depth = 0.55 + 0.45 * hash01(i, 23)
            out[i * 3] = (radius * cos(phi) * depth).toFloat()
            out[i * 3 + 1] = (hy / 17.0).toFloat()
            out[i * 3 + 2] = (radius * sin(phi) * depth).toFloat()
        }
    }

    private fun torus(out: FloatArray, count: Int) {
        val bigR = 0.68
        val smallR = 0.30
        for (i in 0 until count) {
            val u = hash01(i, 101) * 2.0 * PI
            val v = hash01(i, 307) * 2.0 * PI
            val r = bigR + smallR * cos(v)
            out[i * 3] = (r * cos(u)).toFloat()
            out[i * 3 + 1] = (smallR * sin(v)).toFloat()
            out[i * 3 + 2] = (r * sin(u)).toFloat()
        }
    }

    private fun custom(out: FloatArray, count: Int, triple: List<MiniExpr.Fn>) {
        for (i in 0 until count) {
            val u = hash01(i, 17)
            val v = hash01(i, 43)
            // t is fixed at 0 so the shape is stable; animation is done by the
            // renderer (rotation + pulse), not by re-evaluating the formula.
            out[i * 3] = triple[0](u, v, 0.0).toFloat()
            out[i * 3 + 1] = triple[1](u, v, 0.0).toFloat()
            out[i * 3 + 2] = triple[2](u, v, 0.0).toFloat()
        }
    }

    // ------------------------------------------------------------- helpers

    /** Scales the cloud so its largest axis is 1.0 and drops NaN values. */
    private fun normalize(out: FloatArray, count: Int) {
        var maxSq = 0f
        for (i in 0 until count) {
            val x = out[i * 3]
            val y = out[i * 3 + 1]
            val z = out[i * 3 + 2]
            if (x.isNaN() || y.isNaN() || z.isNaN() || x.isInfinite() || y.isInfinite() || z.isInfinite()) {
                out[i * 3] = 0f
                out[i * 3 + 1] = 0f
                out[i * 3 + 2] = 0f
                continue
            }
            val sum = x * x + y * y + z * z
            if (sum > maxSq) maxSq = sum
        }
        if (maxSq <= 0.000001f) {
            // degenerate formula -> draw something rather than nothing
            sphere(out, count)
            return
        }
        val scale = 1f / sqrt(maxSq)
        for (i in 0 until count * 3) {
            out[i] *= scale
        }
    }

    /** Deterministic 0..1 hash. */
    private fun hash01(index: Int, salt: Int): Double {
        var x = index.toLong() * 374761393L + salt.toLong() * 668265263L
        x = (x xor (x shr 13)) * 1274126177L
        x = x xor (x shr 16)
        val positive = x and 0x7FFFFFFFL
        return positive.toDouble() / 0x7FFFFFFF.toDouble()
    }
}
