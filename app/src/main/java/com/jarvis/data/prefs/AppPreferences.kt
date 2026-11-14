package com.jarvis.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jarvis.domain.Prompts
import com.jarvis.domain.orb.OrbSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _ttsRate = MutableStateFlow(prefs.getFloat(K_TTS_RATE, 1.0f))
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getFloat(K_TTS_PITCH, 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

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
        prefs.edit { putString(K_MODEL, clean) }
    }

    fun setAvailableModels(models: List<String>) {
        val clean = models.map { it.removePrefix("models/") }.distinct().sorted()
        _availableModels.value = clean
        _modelsUpdatedAt.value = System.currentTimeMillis()
        prefs.edit {
            putString(K_MODELS, clean.joinToString("\n"))
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
        prefs.edit { putFloat(K_TTS_RATE, clamped) }
    }

    fun setTtsPitch(value: Float) {
        val clamped = value.coerceIn(0.5f, 2f)
        _ttsPitch.value = clamped
        prefs.edit { putFloat(K_TTS_PITCH, clamped) }
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
        _availableModels.value = emptyList()
        _modelsUpdatedAt.value = 0L
        _continuousMode.value = false
        _speakReplies.value = true
        _hotwordEnabled.value = true
        _hotwordOffline.value = true
        _ttsRate.value = 1.0f
        _ttsPitch.value = 1.0f
        _replyLanguage.value = Prompts.LANGUAGE_HINGLISH
        _orbSpec.value = OrbSpec.DEFAULT
        _developerRawIo.value = false
    }

    private fun loadModelList(): List<String> {
        val raw = prefs.getString(K_MODELS, null) ?: return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        const val STATUS_UNKNOWN = "unknown"
        const val STATUS_VERIFIED = "verified"
        const val STATUS_LIMITED = "limited"
        const val STATUS_ERROR = "error"

        const val DEFAULT_MODEL = "gemini-2.0-flash"

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
        private const val K_LANGUAGE = "reply_language"
        private const val K_ORB = "orb_spec"
        private const val K_DEV_RAW = "developer_raw_io"
    }
}
