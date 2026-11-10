package com.jarvis.core

import android.content.Context
import com.jarvis.auth.GoogleAuthManager
import com.jarvis.auth.PlayIntegrityChecker
import com.jarvis.core.security.SecureStore
import com.jarvis.data.db.JarvisDatabase
import com.jarvis.data.prefs.AppPreferences
import com.jarvis.core.media.LocalVideoMaker
import com.jarvis.core.security.CryptoBox
import com.jarvis.core.terminal.ShellRunner
import com.jarvis.data.remote.gemini.GeminiApi
import com.jarvis.domain.JarvisEngine
import com.jarvis.domain.artifacts.ArtifactRepository
import com.jarvis.domain.learning.LearningRepository
import com.jarvis.domain.setup.FileIntake
import com.jarvis.domain.setup.SelfSetupEngine
import com.jarvis.domain.setup.SetupPlanner
import com.jarvis.voice.TtsController
import com.jarvis.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

enum class StartDestination { SIGN_IN, PERMISSIONS, API_KEY, HOME }

/**
 * Plain manual dependency graph. One instance of everything, created lazily on
 * the main thread, so the engine survives activity recreation and the voice
 * recogniser is never created twice.
 */
object ServiceLocator {

    @Volatile
    private var contextRef: Context? = null

    val appContext: Context
        get() = contextRef
            ?: throw IllegalStateException("ServiceLocator.init() must run in Application.onCreate()")

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(context: Context) {
        if (contextRef == null) {
            contextRef = context.applicationContext
        }
    }

    val prefs: AppPreferences by lazy { AppPreferences(appContext) }

    val secure: SecureStore by lazy { SecureStore(appContext) }

    val database: JarvisDatabase by lazy { JarvisDatabase.get(appContext) }

    val audit: AuditLogger by lazy { AuditLogger(database.auditLogDao(), appScope) }

    val gemini: GeminiApi by lazy { GeminiApi() }

    val tts: TtsController by lazy { TtsController(appContext) }

    val voice: VoiceController by lazy { VoiceController(appContext, prefs) }

    // ------------------------------------------------------- add-on module

    val cryptoNotes: CryptoBox by lazy { CryptoBox("jarvis_notes_key_v1") }

    val shell: ShellRunner by lazy { ShellRunner() }

    val fileIntake: FileIntake by lazy { FileIntake(appContext) }

    val learning: LearningRepository by lazy {
        LearningRepository(
            dao = database.learningNoteDao(),
            crypto = CryptoBox("jarvis_learning_key_v1"),
            audit = audit,
        )
    }

    val videoMaker: LocalVideoMaker by lazy { LocalVideoMaker() }

    val artifacts: ArtifactRepository by lazy {
        ArtifactRepository(
            context = appContext,
            dao = database.artifactDao(),
            intake = fileIntake,
            videoMaker = videoMaker,
            audit = audit,
        )
    }

    val selfSetup: SelfSetupEngine by lazy {
        SelfSetupEngine(
            context = appContext,
            setupDao = database.setupDao(),
            stepDao = database.setupStepDao(),
            commandLogDao = database.commandLogDao(),
            chatDao = database.chatDao(),
            planner = SetupPlanner(gemini),
            shell = shell,
            intake = fileIntake,
            secure = secure,
            prefs = prefs,
            learning = learning,
            audit = audit,
            scope = appScope,
        )
    }

    val engine: JarvisEngine by lazy {
        JarvisEngine(
            api = gemini,
            prefs = prefs,
            secure = secure,
            audit = audit,
            chatDao = database.chatDao(),
            voice = voice,
            tts = tts,
            intake = fileIntake,
            selfSetup = selfSetup,
            learning = learning,
            artifacts = artifacts,
            shell = shell,
            scope = appScope,
        )
    }

    val auth: GoogleAuthManager by lazy { GoogleAuthManager(appContext) }

    val integrity: PlayIntegrityChecker by lazy { PlayIntegrityChecker(appContext) }

    /** Where the splash screen should send the user. */
    fun startDestination(): StartDestination {
        val signedIn = prefs.accountEmail.value != null || prefs.limitedMode.value
        return when {
            !signedIn -> StartDestination.SIGN_IN
            !prefs.permissionsDone.value -> StartDestination.PERMISSIONS
            !secure.has(SecureStore.KEY_GEMINI) -> StartDestination.API_KEY
            else -> StartDestination.HOME
        }
    }
}
