package com.jarvis.domain.orb

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The orb is described by data, not code, so Jarvis can reshape it with a
 * "set_orb" tool call.
 *
 * {
 *   "shape": "sphere" | "heart" | "torus" | "custom_formula",
 *   "size": 0.3 .. 1.5,
 *   "color": "#RRGGBB",
 *   "particleCount": 500 .. 8000,
 *   "glow": 0.0 .. 1.0,
 *   "rotationSpeed": 0.0 .. 2.0,
 *   "formula": "x;y;z"   // only for custom_formula
 * }
 */
@Serializable
data class OrbSpec(
    val shape: String = SHAPE_SPHERE,
    val size: Float = 1.0f,
    val color: String = DEFAULT_COLOR,
    val particleCount: Int = 2600,
    val glow: Float = 0.78f,
    val rotationSpeed: Float = 0.35f,
    val formula: String? = null,
) {

    /**
     * Clamps every field into the documented range and repairs anything
     * unparseable. This is the only place values are trusted, so both the AI
     * and the settings screen go through it.
     */
    fun sanitized(): OrbSpec {
        val safeShape = if (shape.lowercase() in SHAPES) shape.lowercase() else SHAPE_SPHERE
        val safeFormula = when (safeShape) {
            SHAPE_CUSTOM -> formula?.takeIf { MiniExpr.parseTriple(it) != null }
            else -> null
        }
        return OrbSpec(
            shape = if (safeShape == SHAPE_CUSTOM && safeFormula == null) SHAPE_SPHERE else safeShape,
            size = size.coerceIn(0.3f, 1.5f),
            color = if (isValidHex(color)) color.uppercase() else DEFAULT_COLOR,
            particleCount = particleCount.coerceIn(500, 8000),
            glow = glow.coerceIn(0f, 1f),
            rotationSpeed = rotationSpeed.coerceIn(0f, 2f),
            formula = safeFormula,
        )
    }

    fun toJson(): String = try {
        json.encodeToString(this)
    } catch (t: Throwable) {
        "{}"
    }

    /** Human readable summary used in chat + logs, e.g. "heart · #FF2B2B". */
    fun describe(): String = "$shape · ${size.round1()}× · $color · ${particleCount}p"

    private fun Float.round1(): String = ((this * 10).toInt() / 10.0).toString()

    companion object {
        const val SHAPE_SPHERE = "sphere"
        const val SHAPE_HEART = "heart"
        const val SHAPE_TORUS = "torus"
        const val SHAPE_CUSTOM = "custom_formula"
        const val DEFAULT_COLOR = "#FF3B30"

        val SHAPES = setOf(SHAPE_SPHERE, SHAPE_HEART, SHAPE_TORUS, SHAPE_CUSTOM)

        /** Example the AI can copy when the user asks for a formula shape. */
        const val EXAMPLE_FORMULA =
            "sin(6.2832*u)*(1+0.35*cos(6.2832*v)); cos(6.2832*u)*(1+0.35*cos(6.2832*v)); 0.35*sin(6.2832*v)"

        val DEFAULT = OrbSpec()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun fromJson(raw: String?): OrbSpec {
            if (raw.isNullOrBlank()) return DEFAULT
            return try {
                json.decodeFromString<OrbSpec>(raw).sanitized()
            } catch (t: Throwable) {
                DEFAULT
            }
        }

        fun isValidHex(value: String): Boolean =
            Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$").matches(value.trim())

        /** "#FF3B30" -> 0xFFFF3B30 (ARGB int). Falls back to red. */
        fun colorInt(value: String): Int {
            val clean = value.trim().removePrefix("#")
            return try {
                when (clean.length) {
                    6 -> (0xFF000000L or clean.toLong(16)).toInt()
                    8 -> clean.toLong(16).toInt()
                    else -> 0xFFFF3B30.toInt()
                }
            } catch (t: Throwable) {
                0xFFFF3B30.toInt()
            }
        }
    }
}
