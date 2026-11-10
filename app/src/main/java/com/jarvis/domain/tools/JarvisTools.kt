package com.jarvis.domain.tools

import com.jarvis.data.remote.gemini.FunctionDeclaration
import com.jarvis.data.remote.gemini.Tool
import com.jarvis.domain.orb.MiniExpr
import com.jarvis.domain.orb.OrbSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What the tools are allowed to touch. Implemented by JarvisEngine. */
interface ToolHost {
    fun currentOrb(): OrbSpec

    /** Returns (success, message) and persists the change when successful. */
    fun applyOrb(spec: OrbSpec): Pair<Boolean, String>

    fun modelList(): List<String>

    fun currentModel(): String

    fun applyModel(requested: String): Pair<Boolean, String>
}

/**
 * Phase 1 function-calling tools. Every argument is validated here before it
 * reaches the app, and every reply is a JSON object the model can read.
 */
class JarvisTools(private val host: ToolHost) {

    fun declarations(): List<Tool> = listOf(
        Tool(
            functionDeclarations = listOf(
                FunctionDeclaration(
                    name = "set_orb",
                    description = "Change the visual orb shown on the home screen. " +
                        "Use this when the user asks to change the shape, colour, size, " +
                        "glow, rotation or particle count of the orb.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("shape") {
                                put("type", "string")
                                put("description", "sphere, heart, torus or custom_formula")
                            }
                            putJsonObject("size") {
                                put("type", "number")
                                put("description", "0.3 to 1.5, 1.0 is normal")
                            }
                            putJsonObject("color") {
                                put("type", "string")
                                put("description", "hex colour like #FF2B2B")
                            }
                            putJsonObject("particleCount") {
                                put("type", "integer")
                                put("description", "500 to 8000")
                            }
                            putJsonObject("glow") {
                                put("type", "number")
                                put("description", "0.0 to 1.0")
                            }
                            putJsonObject("rotationSpeed") {
                                put("type", "number")
                                put("description", "0.0 to 2.0")
                            }
                            putJsonObject("formula") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Only for custom_formula: three expressions separated by ';' " +
                                        "meaning x;y;z, using u and v from 0 to 1. Example: " +
                                        OrbSpec.EXAMPLE_FORMULA
                                )
                            }
                        }
                        put("required", buildJsonArray { add("shape") })
                    },
                ),
                FunctionDeclaration(
                    name = "set_model",
                    description = "Switch the Gemini model that answers the user. " +
                        "Only use a model id from the list given in the system prompt.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("model") {
                                put("type", "string")
                                put("description", "Exact model id, e.g. gemini-2.0-flash")
                            }
                        }
                        put("required", buildJsonArray { add("model") })
                    },
                ),
            )
        )
    )

    fun execute(name: String, args: JsonObject): JsonObject {
        return when (name) {
            "set_orb" -> setOrb(args)
            "set_model" -> setModel(args)
            else -> error("Unknown tool \"$name\". Available tools: set_orb, set_model.")
        }
    }

    // ------------------------------------------------------------------ tools

    private fun setOrb(args: JsonObject): JsonObject {
        val current = host.currentOrb()
        val requestedShape = (args.string("shape") ?: current.shape).lowercase()
        if (requestedShape !in OrbSpec.SHAPES) {
            return error(
                "Unknown shape \"$requestedShape\". Allowed: sphere, heart, torus, custom_formula."
            )
        }

        val formula = args.string("formula") ?: current.formula
        if (requestedShape == OrbSpec.SHAPE_CUSTOM) {
            if (formula.isNullOrBlank()) {
                return error(
                    "custom_formula needs a formula: three expressions separated by ';' for x;y;z. " +
                        "Example: " + OrbSpec.EXAMPLE_FORMULA
                )
            }
            if (MiniExpr.parseTriple(formula) == null) {
                return error(
                    "The formula could not be parsed. Use x;y;z separated by ';' with u, v (0..1), " +
                        "sin, cos, abs, sqrt, pi. Example: " + OrbSpec.EXAMPLE_FORMULA
                )
            }
        }

        val color = args.string("color")?.let { normaliseColor(it) }
        if (color != null && !OrbSpec.isValidHex(color)) {
            return error("Invalid colour \"$color\". Use a hex value like #FF2B2B.")
        }

        val candidate = OrbSpec(
            shape = requestedShape,
            size = args.float("size") ?: current.size,
            color = color ?: current.color,
            particleCount = args.int("particleCount") ?: current.particleCount,
            glow = args.float("glow") ?: current.glow,
            rotationSpeed = args.float("rotationSpeed") ?: current.rotationSpeed,
            formula = if (requestedShape == OrbSpec.SHAPE_CUSTOM) formula else null,
        ).sanitized()

        val (ok, message) = host.applyOrb(candidate)
        if (!ok) return error(message)

        return buildJsonObject {
            put("status", "ok")
            put("message", message)
            putJsonObject("orb") {
                put("shape", candidate.shape)
                put("size", candidate.size.toDouble())
                put("color", candidate.color)
                put("particleCount", candidate.particleCount)
                put("glow", candidate.glow.toDouble())
                put("rotationSpeed", candidate.rotationSpeed.toDouble())
                candidate.formula?.let { put("formula", it) }
            }
        }
    }

    private fun setModel(args: JsonObject): JsonObject {
        val requested = args.string("model")
            ?: return error("Missing argument \"model\".")
        val available = host.modelList()
        if (available.isEmpty()) {
            return error(
                "The model list is not loaded yet. The user must open Settings › AI Models and tap Refresh."
            )
        }
        val (ok, message) = host.applyModel(requested)
        if (!ok) return error(message)
        return buildJsonObject {
            put("status", "ok")
            put("message", message)
            put("model", host.currentModel())
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun error(message: String): JsonObject = buildJsonObject {
        put("status", "error")
        put("message", message)
    }

    private fun normaliseColor(raw: String): String {
        val trimmed = raw.trim()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return withHash.uppercase()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.float(key: String): Float? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()?.toInt()
}
