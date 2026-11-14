package com.jarvis.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceGenderHint { ANY, MALE, FEMALE, INDIA }

enum class VoicePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val pitch: Float,
    val rate: Float,
    val genderHint: VoiceGenderHint,
) {
    JARVIS_CLASSIC(
        id = "jarvis_classic",
        title = "Jarvis Classic",
        subtitle = "Balanced AI awaaz · crisp aur clear Hinglish",
        pitch = 0.94f,
        rate = 1.00f,
        genderHint = VoiceGenderHint.MALE,
    ),
    ARIA_NOVA(
        id = "aria_nova",
        title = "Aria Soft",
        subtitle = "Smooth female-style tone · natural clarity",
        pitch = 1.15f,
        rate = 1.02f,
        genderHint = VoiceGenderHint.FEMALE,
    ),
    COMMAND_DEEP(
        id = "command_deep",
        title = "Command Deep",
        subtitle = "Heavy serious baritone · pro reasoning vibe",
        pitch = 0.78f,
        rate = 0.93f,
        genderHint = VoiceGenderHint.MALE,
    ),
    TURBO_SPARK(
        id = "turbo_spark",
        title = "Spark Fast",
        subtitle = "Tez aur energetic · instant short answers",
        pitch = 1.08f,
        rate = 1.22f,
        genderHint = VoiceGenderHint.ANY,
    ),
    DESI_HINDI(
        id = "desi_hindi",
        title = "Desi Hinglish",
        subtitle = "Indian accent focus · Hindi aur Hinglish friendly",
        pitch = 1.02f,
        rate = 0.98f,
        genderHint = VoiceGenderHint.INDIA,
    ),
    CUSTOM(
        id = "custom",
        title = "Custom Tuning",
        subtitle = "Apne hisaab se pitch aur speed set karo",
        pitch = 1.00f,
        rate = 1.00f,
        genderHint = VoiceGenderHint.ANY,
    );

    companion object {
        val SELECTABLE: List<VoicePreset> = listOf(
            JARVIS_CLASSIC,
            ARIA_NOVA,
            COMMAND_DEEP,
            TURBO_SPARK,
            DESI_HINDI,
        )

        fun fromId(id: String?): VoicePreset =
            entries.firstOrNull { it.id == id } ?: JARVIS_CLASSIC
    }
}

data class TtsVoiceInfo(
    val name: String,
    val label: String,
    val localeTag: String,
    val isNetwork: Boolean,
    val quality: Int,
)

/**
 * Text-to-speech controller with:
 *  - Curated Jarvis voice presets (Classic, Aria Soft, Command Deep, Spark Fast, Desi Hinglish)
 *  - Real device / Google TTS voice selection via [TextToSpeech.getVoices]
 *  - Automatic Hindi (hi-IN) / Indian English (en-IN) fallback
 */
class TtsController(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var counter = 0

    private var pendingText: String? = null
    private var pendingRate = 1f
    private var pendingPitch = 1f
    private var pendingVoiceName = ""
    private var pendingPresetId = VoicePreset.JARVIS_CLASSIC.id

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    /** Called when an utterance finishes (or fails) so the engine can loop. */
    var onFinished: (() -> Unit)? = null

    /** Called with a user-facing message when speech cannot start. */
    var onError: ((String) -> Unit)? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            onFinished?.invoke()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            onFinished?.invoke()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            onError?.invoke("Text-to-Speech error ($errorCode).")
            onFinished?.invoke()
        }
    }

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setOnUtteranceProgressListener(progressListener)
                    refreshVoices()
                    val queued = pendingText
                    if (queued != null) {
                        pendingText = null
                        speakNow(
                            text = queued,
                            rate = pendingRate,
                            pitch = pendingPitch,
                            voiceName = pendingVoiceName,
                            presetId = pendingPresetId,
                        )
                    }
                } else {
                    onError?.invoke("Is device par Text-to-Speech available nahi hai.")
                }
            }
        } catch (t: Throwable) {
            tts = null
            ready = false
        }
    }

    fun speak(
        text: String,
        rate: Float = 1f,
        pitch: Float = 1f,
        voiceName: String = "",
        presetId: String = VoicePreset.JARVIS_CLASSIC.id,
    ) {
        val clean = forSpeech(text)
        if (clean.isBlank()) {
            onFinished?.invoke()
            return
        }
        val engine = tts
        if (engine == null || !ready) {
            pendingText = clean
            pendingRate = rate
            pendingPitch = pitch
            pendingVoiceName = voiceName
            pendingPresetId = presetId
            return
        }
        speakNow(clean, rate, pitch, voiceName, presetId)
    }

    fun refreshVoices(): List<TtsVoiceInfo> {
        val engine = tts
        if (engine == null || !ready) return emptyList()
        val rawVoices: Set<Voice> = try {
            engine.voices.orEmpty()
        } catch (_: Throwable) {
            emptySet()
        }
        val mapped = rawVoices
            .asSequence()
            .filter { voice ->
                val lang = voice.locale?.language?.lowercase().orEmpty()
                lang == "hi" || lang == "en"
            }
            .filter { voice ->
                !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .map { voice ->
                val tag = voice.locale?.toLanguageTag().orEmpty()
                val country = voice.locale?.displayCountry?.takeIf { it.isNotBlank() }
                val langName = voice.locale?.displayLanguage?.takeIf { it.isNotBlank() } ?: tag
                val region = if (country != null) "$langName ($country)" else langName
                val shortCode = voice.name
                    .substringAfterLast('-')
                    .substringBefore('#')
                    .uppercase(Locale.US)
                    .take(10)
                val hint = inferVoiceHint(voice.name)
                val label = buildString {
                    append(region)
                    if (hint.isNotEmpty()) append(" · ").append(hint)
                    if (shortCode.isNotEmpty() && !shortCode.equals(tag, ignoreCase = true)) {
                        append(" [").append(shortCode).append("]")
                    }
                }
                TtsVoiceInfo(
                    name = voice.name,
                    label = label,
                    localeTag = tag,
                    isNetwork = voice.isNetworkConnectionRequired,
                    quality = voice.quality,
                )
            }
            .distinctBy { it.name }
            .sortedWith(
                compareBy<TtsVoiceInfo>(
                    { localeRank(it.localeTag) },
                    { if (it.isNetwork) 1 else 0 },
                    { -it.quality },
                    { it.label },
                )
            )
            .take(30)
            .toList()
        _availableVoices.value = mapped
        return mapped
    }

    fun stop() {
        pendingText = null
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    fun shutdown() {
        pendingText = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ready = false
    }

    fun hasHindiVoice(): Boolean {
        val engine = tts ?: return false
        return try {
            engine.isLanguageAvailable(Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Throwable) {
            false
        }
    }

    fun openSystemTtsSettings() {
        runCatching {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    fun isReady(): Boolean = ready

    // ------------------------------------------------------------- internals

    private fun speakNow(
        text: String,
        rate: Float,
        pitch: Float,
        voiceName: String,
        presetId: String,
    ) {
        val engine = tts ?: return
        try {
            engine.stop()
            engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
            engine.setPitch(pitch.coerceIn(0.5f, 2f))
            applyVoiceOrLanguage(engine, text, voiceName, presetId)
            val id = "jarvis-${++counter}"
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (queued != TextToSpeech.SUCCESS) {
                onError?.invoke("Text-to-Speech shuru nahi ho paya.")
                onFinished?.invoke()
            }
        } catch (t: Throwable) {
            onError?.invoke("Text-to-Speech fail ho gaya: ${t.message ?: "unknown error"}")
            onFinished?.invoke()
        }
    }

    private fun applyVoiceOrLanguage(
        engine: TextToSpeech,
        text: String,
        voiceName: String,
        presetId: String,
    ) {
        val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())

        // 1. Explicit device voice chosen by user
        if (voiceName.isNotBlank()) {
            val explicit = voices.firstOrNull { it.name == voiceName }
            if (explicit != null && engine.setVoice(explicit) == TextToSpeech.SUCCESS) {
                return
            }
        }

        // 2. Smart locale + preset match
        val wantsHindi = DEVANAGARI.containsMatchIn(text)
        val preset = VoicePreset.fromId(presetId)
        val preferredTag = when {
            wantsHindi -> "hi-IN"
            preset == VoicePreset.DESI_HINDI -> "en-IN"
            else -> "en-IN"
        }

        if (voices.isNotEmpty()) {
            val candidates = voices.filter { v ->
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                    (
                        v.locale?.toLanguageTag().equals(preferredTag, ignoreCase = true) ||
                            (wantsHindi && v.locale?.language.equals("hi", ignoreCase = true)) ||
                            (!wantsHindi && v.locale?.language.equals("en", ignoreCase = true))
                        )
            }
            val matched = when (preset.genderHint) {
                VoiceGenderHint.MALE -> candidates.firstOrNull {
                    it.name.contains("male", ignoreCase = true) ||
                        it.name.contains("-iom", ignoreCase = true) ||
                        it.name.contains("-iod", ignoreCase = true) ||
                        it.name.contains("-eng-m", ignoreCase = true)
                }
                VoiceGenderHint.FEMALE -> candidates.firstOrNull {
                    it.name.contains("female", ignoreCase = true) ||
                        it.name.contains("-iog", ignoreCase = true) ||
                        it.name.contains("-sfg", ignoreCase = true) ||
                        it.name.contains("-eng-f", ignoreCase = true)
                }
                VoiceGenderHint.INDIA -> candidates.firstOrNull {
                    it.locale?.country.equals("IN", ignoreCase = true)
                }
                VoiceGenderHint.ANY -> null
            } ?: candidates.firstOrNull {
                it.locale?.toLanguageTag().equals(preferredTag, ignoreCase = true)
            }

            if (matched != null && engine.setVoice(matched) == TextToSpeech.SUCCESS) {
                return
            }
        }

        // 3. Language fallback
        val primary = if (wantsHindi) Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(primary)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
        }
    }

    private fun localeRank(tag: String): Int = when {
        tag.equals("hi-IN", ignoreCase = true) -> 0
        tag.equals("en-IN", ignoreCase = true) -> 1
        tag.startsWith("hi", ignoreCase = true) -> 2
        tag.equals("en-US", ignoreCase = true) -> 3
        tag.equals("en-GB", ignoreCase = true) -> 4
        else -> 5
    }

    private fun inferVoiceHint(name: String): String {
        val low = name.lowercase(Locale.US)
        return when {
            "female" in low || "-iog-" in low || "-hie-" in low -> "Soft"
            "male" in low || "-iom-" in low || "-hid-" in low -> "Deep"
            "network" in low -> "Neural"
            else -> ""
        }
    }

    /** Removes markdown and code so the voice does not read punctuation aloud. */
    private fun forSpeech(text: String): String {
        var value = text
        value = value.replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
        value = value.replace(Regex("`([^`]*)`"), "\$1")
        value = value.replace(Regex("\\*\\*([^*]*)\\*\\*"), "\$1")
        value = value.replace(Regex("\\*([^*]*)\\*"), "\$1")
        value = value.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        value = value.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "\$1")
        value = value.replace(Regex("https?://\\S+"), "link")
        value = value.replace(Regex("[ \\t]+"), " ")
        value = value.replace(Regex("\\n{2,}"), ". ")
        value = value.replace("\n", ". ")
        value = value.replace(Regex("\\.{2,}"), ".")
        return value.trim()
    }

    companion object {
        private val DEVANAGARI = Regex("[\\u0900-\\u097F]")
    }
}
