package com.jarvis.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jarvis.domain.Prompts
import com.jarvis.domain.orb.OrbSpec
import com.jarvis.voice.VoicePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EffectiveVoice(
    val presetId: String,
    val voiceName: String,
    val rate: Float,
    val pitch: Float,
)

data class GeminiModelMeta(
    val id: String,
    val tier: String,
    val description: String,
)

/**
 * Non-secret settings. Secrets live in [com.jarvis.core.security.SecureStore].
 *
 * Every value is mirrored into a StateFlow so Compose screens and the engine
 * always see the same value; writes go to disk immediately.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ----------------------------------------------------------- account
    private val _accountEmail = MutableStateFlow(prefs.getString(K_EMAIL, null))
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _accountName = MutableStateFlow(prefs.getString(K_NAME, null))
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    private val _limitedMode = MutableStateFlow(prefs.getBoolean(K_LIMITED, false))
    val limitedMode: StateFlow<Boolean> = _limitedMode.asStateFlow()

    // ----------------------------------------------------------- setup gates
    private val _permissionsDone = MutableStateFlow(prefs.getBoolean(K_PERMISSIONS_DONE, false))
    val permissionsDone: StateFlow<Boolean> = _permissionsDone.asStateFlow()

    // ----------------------------------------------------------- integrity
    private val _integrityStatus = MutableStateFlow(prefs.getString(K_INTEGRITY, STATUS_UNKNOWN)!!)
    val integrityStatus: StateFlow<String> = _integrityStatus.asStateFlow()

    private val _integrityDetail = MutableStateFlow(prefs.getString(K_INTEGRITY_DETAIL, "")!!)
    val integrityDetail: StateFlow<String> = _integrityDetail.asStateFlow()

    // ----------------------------------------------------------- appearance
    private val _themeMode = MutableStateFlow(prefs.getString(K_THEME, "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(prefs.getString(K_ACCENT, "#00E5FF") ?: "#00E5FF")
    val accent: StateFlow<String> = _accent.asStateFlow()

    // ----------------------------------------------------------- ai
    private val _systemPrompt = MutableStateFlow(
        prefs.getString(K_SYSTEM_PROMPT, Prompts.BASE_SYSTEM_PROMPT) ?: Prompts.BASE_SYSTEM_PROMPT
    )
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(prefs.getFloat(K_TEMPERATURE, 0.7f))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        prefs.getString(K_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow(loadModelList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _modelsUpdatedAt = MutableStateFlow(prefs.getLong(K_MODELS_AT, 0L))
    val modelsUpdatedAt: StateFlow<Long> = _modelsUpdatedAt.asStateFlow()

    // ----------------------------------------------------------- voice
    private val _continuousMode = MutableStateFlow(prefs.getBoolean(K_CONTINUOUS, false))
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    private val _speakReplies = MutableStateFlow(prefs.getBoolean(K_SPEAK, true))
    val speakReplies: StateFlow<Boolean> = _speakReplies.asStateFlow()

    private val _hotwordEnabled = MutableStateFlow(prefs.getBoolean(K_HOTWORD, true))
    val hotwordEnabled: StateFlow<Boolean> = _hotwordEnabled.asStateFlow()

    private val _hotwordOffline = MutableStateFlow(prefs.getBoolean(K_HOTWORD_OFFLINE, true))
    val hotwordOffline: StateFlow<Boolean> = _hotwordOffline.asStateFlow()

    private val _ttsRate = MutableStateFlow(prefs.getFloat(K_TTS_RATE, VoicePreset.JARVIS_CLASSIC.rate))
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getFloat(K_TTS_PITCH, VoicePreset.JARVIS_CLASSIC.pitch))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsVoicePreset = MutableStateFlow(
        prefs.getString(K_VOICE_PRESET, VoicePreset.JARVIS_CLASSIC.id) ?: VoicePreset.JARVIS_CLASSIC.id
    )
    val ttsVoicePreset: StateFlow<String> = _ttsVoicePreset.asStateFlow()

    private val _ttsVoiceName = MutableStateFlow(
        prefs.getString(K_VOICE_NAME, "") ?: ""
    )
    val ttsVoiceName: StateFlow<String> = _ttsVoiceName.asStateFlow()

    private val _perModelVoiceEnabled = MutableStateFlow(prefs.getBoolean(K_PER_MODEL_VOICE, false))
    val perModelVoiceEnabled: StateFlow<Boolean> = _perModelVoiceEnabled.asStateFlow()

    private val _modelVoiceOverrides = MutableStateFlow(loadModelVoiceMap())
    val modelVoiceOverrides: StateFlow<Map<String, String>> = _modelVoiceOverrides.asStateFlow()

    private val _replyLanguage = MutableStateFlow(
        prefs.getString(K_LANGUAGE, Prompts.LANGUAGE_HINGLISH) ?: Prompts.LANGUAGE_HINGLISH
    )
    val replyLanguage: StateFlow<String> = _replyLanguage.asStateFlow()

    // ----------------------------------------------------------- orb
    private val _orbSpec = MutableStateFlow(OrbSpec.fromJson(prefs.getString(K_ORB, null)))
    val orbSpec: StateFlow<OrbSpec> = _orbSpec.asStateFlow()

    // ----------------------------------------------------------- developer
    private val _developerRawIo = MutableStateFlow(prefs.getBoolean(K_DEV_RAW, false))
    val developerRawIo: StateFlow<Boolean> = _developerRawIo.asStateFlow()

    // =======================================================================
    // setters
    // =======================================================================

    fun setAccount(email: String?, name: String?, limited: Boolean) {
        _accountEmail.value = email
        _accountName.value = name
        _limitedMode.value = limited
        prefs.edit {
            putString(K_EMAIL, email)
            putString(K_NAME, name)
            putBoolean(K_LIMITED, limited)
        }
    }

    fun setPermissionsDone(done: Boolean) {
        _permissionsDone.value = done
        prefs.edit { putBoolean(K_PERMISSIONS_DONE, done) }
    }

    fun setIntegrity(status: String, detail: String) {
        _integrityStatus.value = status
        _integrityDetail.value = detail
        prefs.edit {
            putString(K_INTEGRITY, status)
            putString(K_INTEGRITY_DETAIL, detail)
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit { putString(K_THEME, mode) }
    }

    fun setAccent(hex: String) {
        if (!OrbSpec.isValidHex(hex)) return
        _accent.value = hex.uppercase()
        prefs.edit { putString(K_ACCENT, hex.uppercase()) }
    }

    fun setSystemPrompt(value: String) {
        val safe = value.ifBlank { Prompts.BASE_SYSTEM_PROMPT }
        _systemPrompt.value = safe
        prefs.edit { putString(K_SYSTEM_PROMPT, safe) }
    }

    fun setTemperature(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _temperature.value = clamped
        prefs.edit { putFloat(K_TEMPERATURE, clamped) }
    }

    fun setSelectedModel(model: String) {
        val clean = model.removePrefix("models/").trim()
        if (clean.isEmpty()) return
        _selectedModel.value = clean
        if (!availableModels.value.contains(clean)) {
            val updated = (listOf(clean) + availableModels.value).distinct()
            _availableModels.value = updated
            prefs.edit { putString(K_MODELS, updated.joinToString("\n")) }
        }
        prefs.edit { putString(K_MODEL, clean) }
    }

    fun setAvailableModels(models: List<String>) {
        val clean = models.map { it.removePrefix("models/").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val finalModels = if (clean.isEmpty()) DEFAULT_MODELS else clean
        _availableModels.value = finalModels
        _modelsUpdatedAt.value = if (clean.isEmpty()) 0L else System.currentTimeMillis()
        prefs.edit {
            putString(K_MODELS, if (clean.isEmpty()) "" else finalModels.joinToString("\n"))
            putLong(K_MODELS_AT, _modelsUpdatedAt.value)
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        _continuousMode.value = enabled
        prefs.edit { putBoolean(K_CONTINUOUS, enabled) }
    }

    fun setSpeakReplies(enabled: Boolean) {
        _speakReplies.value = enabled
        prefs.edit { putBoolean(K_SPEAK, enabled) }
    }

    fun setHotwordEnabled(enabled: Boolean) {
        _hotwordEnabled.value = enabled
        prefs.edit { putBoolean(K_HOTWORD, enabled) }
    }

    fun setHotwordOffline(enabled: Boolean) {
        _hotwordOffline.value = enabled
        prefs.edit { putBoolean(K_HOTWORD_OFFLINE, enabled) }
    }

    fun setTtsRate(value: Float) {
        val clamped = value.coerceIn(0.5f, 2f)
        _ttsRate.value = clamped
        _ttsVoicePreset.value = VoicePreset.CUSTOM.id
        prefs.edit {
            putFloat(K_TTS_RATE, clamped)
            putString(K_VOICE_PRESET, VoicePreset.CUSTOM.id)
        }
    }

    fun setTtsPitch(value: Float) {
        val clamped = value.coerceIn(0.5f, 2f)
        _ttsPitch.value = clamped
        _ttsVoicePreset.value = VoicePreset.CUSTOM.id
        prefs.edit {
            putFloat(K_TTS_PITCH, clamped)
            putString(K_VOICE_PRESET, VoicePreset.CUSTOM.id)
        }
    }

    fun applyVoicePreset(presetId: String) {
        val preset = VoicePreset.fromId(presetId)
        _ttsVoicePreset.value = preset.id
        if (preset != VoicePreset.CUSTOM) {
            _ttsRate.value = preset.rate
            _ttsPitch.value = preset.pitch
            prefs.edit {
                putString(K_VOICE_PRESET, preset.id)
                putFloat(K_TTS_RATE, preset.rate)
                putFloat(K_TTS_PITCH, preset.pitch)
            }
        } else {
            prefs.edit { putString(K_VOICE_PRESET, preset.id) }
        }
    }

    fun setTtsVoiceName(voiceName: String) {
        _ttsVoiceName.value = voiceName
        prefs.edit { putString(K_VOICE_NAME, voiceName) }
    }

    fun setPerModelVoiceEnabled(enabled: Boolean) {
        _perModelVoiceEnabled.value = enabled
        prefs.edit { putBoolean(K_PER_MODEL_VOICE, enabled) }
    }

    fun setVoicePresetForModel(modelId: String, presetId: String) {
        val cleanModel = modelId.removePrefix("models/").trim()
        if (cleanModel.isEmpty()) return
        val next = _modelVoiceOverrides.value.toMutableMap()
        next[cleanModel] = VoicePreset.fromId(presetId).id
        _modelVoiceOverrides.value = next
        saveModelVoiceMap(next)
    }

    fun effectiveVoiceForModel(modelId: String = _selectedModel.value): EffectiveVoice {
        val cleanModel = modelId.removePrefix("models/").trim()
        if (_perModelVoiceEnabled.value) {
            val mappedPresetId = _modelVoiceOverrides.value[cleanModel]
            if (mappedPresetId != null && mappedPresetId != VoicePreset.CUSTOM.id) {
                val preset = VoicePreset.fromId(mappedPresetId)
                return EffectiveVoice(
                    presetId = preset.id,
                    voiceName = _ttsVoiceName.value,
                    rate = preset.rate,
                    pitch = preset.pitch,
                )
            }
        }
        return EffectiveVoice(
            presetId = _ttsVoicePreset.value,
            voiceName = _ttsVoiceName.value,
            rate = _ttsRate.value,
            pitch = _ttsPitch.value,
        )
    }

    fun setReplyLanguage(language: String) {
        _replyLanguage.value = language
        prefs.edit { putString(K_LANGUAGE, language) }
    }

    fun setOrbSpec(spec: OrbSpec) {
        val safe = spec.sanitized()
        _orbSpec.value = safe
        prefs.edit { putString(K_ORB, safe.toJson()) }
    }

    fun setDeveloperRawIo(enabled: Boolean) {
        _developerRawIo.value = enabled
        prefs.edit { putBoolean(K_DEV_RAW, enabled) }
    }

    /** Wipes the onboarding gates so the setup wizard runs again. */
    fun resetOnboarding() {
        setPermissionsDone(false)
        setIntegrity(STATUS_UNKNOWN, "")
        prefs.edit {
            putBoolean(K_PERMISSIONS_DONE, false)
            putString(K_INTEGRITY, STATUS_UNKNOWN)
            putString(K_INTEGRITY_DETAIL, "")
        }
    }

    /** Full local wipe (Settings > Developer > Clear all local data). */
    fun clearEverything() {
        prefs.edit { clear() }
        _accountEmail.value = null
        _accountName.value = null
        _limitedMode.value = false
        _permissionsDone.value = false
        _integrityStatus.value = STATUS_UNKNOWN
        _integrityDetail.value = ""
        _themeMode.value = "dark"
        _accent.value = "#00E5FF"
        _systemPrompt.value = Prompts.BASE_SYSTEM_PROMPT
        _temperature.value = 0.7f
        _selectedModel.value = DEFAULT_MODEL
        _availableModels.value = DEFAULT_MODELS
        _modelsUpdatedAt.value = 0L
        _continuousMode.value = false
        _speakReplies.value = true
        _hotwordEnabled.value = true
        _hotwordOffline.value = true
        _ttsRate.value = VoicePreset.JARVIS_CLASSIC.rate
        _ttsPitch.value = VoicePreset.JARVIS_CLASSIC.pitch
        _ttsVoicePreset.value = VoicePreset.JARVIS_CLASSIC.id
        _ttsVoiceName.value = ""
        _perModelVoiceEnabled.value = false
        _modelVoiceOverrides.value = DEFAULT_MODEL_VOICE_MAP
        _replyLanguage.value = Prompts.LANGUAGE_HINGLISH
        _orbSpec.value = OrbSpec.DEFAULT
        _developerRawIo.value = false
    }

    private fun loadModelList(): List<String> {
        val raw = prefs.getString(K_MODELS, null)?.trim().orEmpty()
        if (raw.isEmpty()) return DEFAULT_MODELS
        val saved = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return if (saved.isEmpty()) DEFAULT_MODELS else saved
    }

    private fun loadModelVoiceMap(): Map<String, String> {
        val raw = prefs.getString(K_MODEL_VOICE_MAP, null) ?: return DEFAULT_MODEL_VOICE_MAP
        val map = mutableMapOf<String, String>()
        raw.split("\n").forEach { line ->
            val idx = line.indexOf('=')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
            }
        }
        return if (map.isEmpty()) DEFAULT_MODEL_VOICE_MAP else map
    }

    private fun saveModelVoiceMap(map: Map<String, String>) {
        val encoded = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
        prefs.edit { putString(K_MODEL_VOICE_MAP, encoded) }
    }

    companion object {
        const val STATUS_UNKNOWN = "unknown"
        const val STATUS_VERIFIED = "verified"
        const val STATUS_LIMITED = "limited"
        const val STATUS_ERROR = "error"

        const val DEFAULT_MODEL = "gemini-2.5-flash"

        val DEFAULT_MODELS = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-pro",
            "gemini-1.5-flash",
        )

        val DEFAULT_MODEL_VOICE_MAP = mapOf(
            "gemini-2.5-pro" to VoicePreset.COMMAND_DEEP.id,
            "gemini-2.5-flash" to VoicePreset.JARVIS_CLASSIC.id,
            "gemini-2.5-flash-lite" to VoicePreset.TURBO_SPARK.id,
            "gemini-2.0-flash" to VoicePreset.ARIA_NOVA.id,
            "gemini-1.5-pro" to VoicePreset.COMMAND_DEEP.id,
        )

        fun describeModel(id: String): GeminiModelMeta {
            val clean = id.removePrefix("models/").lowercase()
            return when {
                "2.5-pro" in clean -> GeminiModelMeta(id, "PRO", "Deep reasoning, complex coding & architecture")
                "2.5-flash-lite" in clean -> GeminiModelMeta(id, "LITE", "Sabse tez, halka aur kam quota use")
                "2.5-flash" in clean -> GeminiModelMeta(id, "2.5 FLASH", "Balanced smart assistant · speed + accuracy")
                "2.0-flash-lite" in clean -> GeminiModelMeta(id, "LITE", "Fast lightweight chat & quick commands")
                "2.0-flash" in clean -> GeminiModelMeta(id, "FLASH", "Reliable tool-calling & smooth voice replies")
                "pro" in clean -> GeminiModelMeta(id, "PRO", "High accuracy & detailed planning")
                "lite" in clean -> GeminiModelMeta(id, "LITE", "Instant low-latency replies")
                "flash" in clean -> GeminiModelMeta(id, "FLASH", "Fast everyday Gemini model")
                else -> GeminiModelMeta(id, "GEMINI", "Live Gemini API model")
            }
        }

        private const val NAME = "jarvis_settings"
        private const val K_EMAIL = "account_email"
        private const val K_NAME = "account_name"
        private const val K_LIMITED = "limited_mode"
        private const val K_PERMISSIONS_DONE = "permissions_done"
        private const val K_INTEGRITY = "integrity_status"
        private const val K_INTEGRITY_DETAIL = "integrity_detail"
        private const val K_THEME = "theme_mode"
        private const val K_ACCENT = "accent"
        private const val K_SYSTEM_PROMPT = "system_prompt"
        private const val K_TEMPERATURE = "temperature"
        private const val K_MODEL = "selected_model"
        private const val K_MODELS = "available_models"
        private const val K_MODELS_AT = "models_updated_at"
        private const val K_CONTINUOUS = "continuous_mode"
        private const val K_SPEAK = "speak_replies"
        private const val K_HOTWORD = "hotword_enabled"
        private const val K_HOTWORD_OFFLINE = "hotword_offline"
        private const val K_TTS_RATE = "tts_rate"
        private const val K_TTS_PITCH = "tts_pitch"
        private const val K_VOICE_PRESET = "tts_voice_preset"
        private const val K_VOICE_NAME = "tts_voice_name"
        private const val K_PER_MODEL_VOICE = "per_model_voice_enabled"
        private const val K_MODEL_VOICE_MAP = "model_voice_map"
        private const val K_LANGUAGE = "reply_language"
        private const val K_ORB = "orb_spec"
        private const val K_DEV_RAW = "developer_raw_io"
    }
}
