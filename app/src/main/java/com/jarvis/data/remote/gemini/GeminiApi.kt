package com.jarvis.data.remote.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal Gemini REST client.
 *
 * Notes:
 *  - The API key travels in the `x-goog-api-key` header, never in the URL, so
 *    it cannot leak into proxy logs, crash reports or the audit log.
 *  - Callers must pass text that already went through Redactor.
 *  - All failures throw [GeminiException] with a message written for a
 *    non-technical user.
 */
class GeminiApi(
    private val client: OkHttpClient = defaultClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = true
    }

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /** GET /v1beta/models — used by "Test key" in onboarding and AI Models. */
    suspend fun listModels(apiKey: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val url = "$BASE/models".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("pageSize", "200")
            ?.build()
            ?: throw GeminiException("Internal error: bad base URL.")
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        execute(request) { raw, code ->
            if (code !in 200..299) throw GeminiException(errorMessage(code, raw))
            val parsed = try {
                json.decodeFromString<ModelListResponse>(raw)
            } catch (t: Throwable) {
                throw GeminiException("Gemini sent a reply this app could not read.")
            }
            parsed.models
                .filter { it.supportsGenerate }
                .sortedBy { it.id }
        }
    }

    /** POST /v1beta/{model}:generateContent */
    suspend fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        contents: List<Content>,
        tools: List<Tool>,
        temperature: Double,
    ): GenerateContentResponse = withContext(Dispatchers.IO) {
        val cleanModel = model.removePrefix("models/").ifBlank { "gemini-2.0-flash" }
        val url = "$BASE/$cleanModel:generateContent".toHttpUrlOrNull()
            ?: throw GeminiException("Internal error: bad model name \"$cleanModel\".")

        val payload = GenerateContentRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            tools = tools.ifEmpty { null },
            generationConfig = GenerationConfig(
                temperature = temperature.coerceIn(0.0, 2.0),
                maxOutputTokens = 2048,
            ),
        )

        val body = try {
            json.encodeToString(GenerateContentRequest.serializer(), payload)
        } catch (t: Throwable) {
            throw GeminiException("Could not build the request. Try a shorter message.")
        }

        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(mediaType))
            .build()

        execute(request) { raw, code ->
            if (code !in 200..299) throw GeminiException(errorMessage(code, raw))
            try {
                json.decodeFromString<GenerateContentResponse>(raw)
            } catch (t: Throwable) {
                throw GeminiException("Gemini sent a reply this app could not read.")
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private inline fun <T> execute(request: Request, block: (raw: String, code: Int) -> T): T {
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                block(raw, response.code)
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw GeminiException("No internet connection. Check Wi-Fi or mobile data.")
        } catch (e: java.net.SocketTimeoutException) {
            throw GeminiException("Gemini took too long to answer. Try again.")
        } catch (e: javax.net.ssl.SSLException) {
            throw GeminiException("Secure connection to Google failed. Check the device clock and network.")
        } catch (e: java.io.IOException) {
            throw GeminiException("Network error: ${e.message ?: "connection lost"}.")
        } catch (t: Throwable) {
            throw GeminiException("Unexpected error: ${t.message ?: t.javaClass.simpleName}.")
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val googleMessage = try {
            json.decodeFromString<ApiErrorEnvelope>(raw).error?.message.orEmpty()
        } catch (t: Throwable) {
            ""
        }
        val hint = when {
            code == 400 && googleMessage.contains("API key", ignoreCase = true) ->
                "The API key was rejected. Check it in Settings › API Keys."
            code == 400 -> "Gemini refused the request (400)."
            code == 401 || code == 403 ->
                "This key is not allowed to use that model."
            code == 404 ->
                "Model not found. Pick another one in Settings › AI Models."
            code == 429 ->
                "Quota or rate limit reached. Wait a minute, then try again."
            code == 500 || code == 503 ->
                "Gemini is overloaded right now. Try again in a moment."
            else -> "Gemini error $code."
        }
        return if (googleMessage.isBlank()) hint else "$hint (Google: ${googleMessage.take(160)})"
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
