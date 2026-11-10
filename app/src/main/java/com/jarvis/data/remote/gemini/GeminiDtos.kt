package com.jarvis.data.remote.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Request / response models for generativelanguage.googleapis.com v1beta.
// Only the fields this app actually uses are declared; unknown keys are
// ignored by the Json configuration in GeminiApi.
// ---------------------------------------------------------------------------

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part> = emptyList(),
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
    /** Gemini 2.5 thinking parts set this; they must not be shown as an answer. */
    val thought: Boolean? = null,
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject? = null,
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject,
)

@Serializable
data class Tool(
    val functionDeclarations: List<FunctionDeclaration>,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class GenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
)

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
    val usageMetadata: UsageMetadata? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
    val index: Int = 0,
)

@Serializable
data class PromptFeedback(
    val blockReason: String? = null,
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
)

@Serializable
data class ModelListResponse(
    val models: List<ModelInfo> = emptyList(),
)

@Serializable
data class ModelInfo(
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList(),
) {
    val id: String get() = name.removePrefix("models/")

    val supportsGenerate: Boolean
        get() = supportedGenerationMethods.contains("generateContent")
}

@Serializable
data class ApiErrorEnvelope(
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)

/** Thrown with a message that is already safe to show to the user. */
class GeminiException(message: String) : Exception(message)
