package com.jarvis.domain.tools

import com.jarvis.data.remote.gemini.FunctionDeclaration
import com.jarvis.data.remote.gemini.Tool
import com.jarvis.domain.orb.MiniExpr
import com.jarvis.domain.orb.OrbSpec
import kotlinx.serialization.json.JsonArray
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

    /** Writes a real file on the phone and shows it in chat. */
    suspend fun generateFile(name: String, content: String): Pair<Boolean, String>

    /** Renders a real MP4 on the phone and shows it in chat. */
    suspend fun generateVideo(title: String, lines: List<String>, seconds: Int): Pair<Boolean, String>
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
                FunctionDeclaration(
                    name = "create_file",
                    description = "Create a real file on the user's phone and show it in the chat. " +
                        "Use this whenever the user asks for a file: \"txt.txt bhejo\", " +
                        "\"index.html likho\", \"code file bana do\". Always send the complete " +
                        "file content, never a summary.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") {
                                put("type", "string")
                                put("description", "File name with extension, e.g. index.html or notes.txt")
                            }
                            putJsonObject("content") {
                                put("type", "string")
                                put("description", "Full file content. No placeholders.")
                            }
                        }
                        put("required", buildJsonArray {
                            add("name")
                            add("content")
                        })
                    },
                ),
                FunctionDeclaration(
                    name = "make_video",
                    description = "Render a real MP4 video on the phone and show it in the chat " +
                        "with a player. Use when the user asks for a video. Only text cards are " +
                        "rendered locally - no footage, no download.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "Video title card text")
                            }
                            putJsonObject("lines") {
                                put("type", "array")
                                put("description", "3 to 8 short lines shown one by one")
                                putJsonObject("items") { put("type", "string") }
                            }
                            putJsonObject("seconds") {
                                put("type", "integer")
                                put("description", "Length in seconds, 2 to 15 (default 6)")
                            }
                        }
                        put("required", buildJsonArray {
                            add("title")
                            add("lines")
                        })
                    },
                ),
            )
        )
    )

    suspend fun execute(name: String, args: JsonObject): JsonObject {
        return when (name) {
            "set_orb" -> setOrb(args)
            "set_model" -> setModel(args)
            "create_file" -> createFile(args)
            "make_video" -> makeVideo(args)
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

    private suspend fun createFile(args: JsonObject): JsonObject {
        val name = args.string("name") ?: return error("Missing argument \"name\".")
        val content = args.string("content").orEmpty()
        if (name.contains('/') || name.contains('\\')) {
            return error("File name me / ya \\ nahi ho sakta. Sirf naam bhejo, jaise index.html")
        }
        if (content.isBlank()) return error("File content khali hai.")
        if (content.length > 120_000) return error("File content bahut bada hai (limit 120k characters).")

        val (ok, message) = host.generateFile(name, content)
        if (!ok) return error(message)
        return buildJsonObject {
            put("status", "ok")
            put("message", message)
            put("file", name)
            put("bytes", content.toByteArray(Charsets.UTF_8).size)
        }
    }

    private suspend fun makeVideo(args: JsonObject): JsonObject {
        val title = args.string("title") ?: return error("Missing argument \"title\".")
        val lines = args.stringList("lines")
        if (lines.isEmpty()) return error("Video ke liye kam se kam 3 lines chahiye.")
        val seconds = (args.int("seconds") ?: 6).coerceIn(2, 15)

        val (ok, message) = host.generateVideo(title, lines.take(8), seconds)
        if (!ok) return error(message)
        return buildJsonObject {
            put("status", "ok")
            put("message", message)
            put("seconds", seconds)
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

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
