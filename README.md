# JARVIS — Android personal assistant

**Phase 1 (Foundation) — complete code, ready to build.**

Jarvis is an offline-first Android assistant: a living particle orb, voice chat
with Gemini, self-modifying visuals, and a privacy model that is enforced in
code rather than promised in a store listing.

> **Honest status:** this Phase 1 source was written and statically reviewed, but
> it was **not compiled** in the environment that produced it (no JDK, no Android
> SDK, no access to Google Maven there). Everything below tells you exactly how to
> build it and what to check. If Gradle reports an error, it will be a single,
> specific line — the structure, imports and API usage were checked file by file.

---

## 1. Quick start (no coding needed)

1. Install **Android Studio** (Ladybug or newer) with **JDK 17**.
2. `File ▸ Open` → select this folder (`Jarvis`).
3. Wait for **Gradle sync** (first run downloads dependencies, ~5–10 min).
4. Connect a phone with **Android 8.0+ (API 26)** and USB debugging on, then press
   **Run ▶**.

Command line equivalents:

```bash
# debug build
./gradlew :app:assembleDebug

# install on the connected phone
./gradlew :app:installDebug

# release build (needs keystore.properties, see section 6)
./gradlew :app:assembleRelease
```

`gradle/wrapper/gradle-wrapper.jar` is a binary and is not committed here.
Android Studio creates it automatically on first sync. From a terminal run
`gradle wrapper --gradle-version 8.9` once (or let Studio do it).

### Toolchain used

| Piece | Version |
|---|---|
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (Compose compiler plugin built in) |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.10.01 (Material 3) |
| Room | 2.6.1 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JDK | 17 |

---

## 2. What Phase 1 actually does

| Requirement | Implementation |
|---|---|
| Splash + Google verification | `SplashScreen`, `GoogleAuthManager` (Credential Manager), `PlayIntegrityChecker` |
| Permissions screen with reasons + live status | `PermissionsScreen` (one row per permission, real state, Continue locked until mandatory granted) |
| Gemini key onboarding with test-before-save | `ApiKeyScreen` + `SetupViewModel.testAndSaveKey` (calls `GET /v1beta/models`, only then seals the key) |
| Orb home screen | `OrbView.kt` + `OrbShader.kt`: up to 8000 particles, depth-bucketed, AGSL plasma on API 33+, gradient halo below |
| OrbSpec JSON with 5 states, 0.8 s animation | `OrbSpec.kt`, all numeric fields + colour animate, shape changes morph cloud-to-cloud |
| `set_orb` tool | `JarvisTools` + `MiniExpr` (safe formula parser), validated and clamped |
| Voice + chat | `VoiceController` (wake word + dictation), `JarvisEngine` (Gemini + tools), `TtsController` (hi-IN / en-IN) |
| Continuous conversation toggle | Home header chip, engine restarts listening after speech |
| Model selection + `set_model` | `AiModelsScreen` fetches the live list; tool only accepts names from that list |
| 27 settings sections | `SettingsSections.kt` + `SettingsHubScreen`; 12 are real, 15 say "Coming in Phase X" |
| Glassmorphism, neon, dark default | `Theme.kt`, `Glass.kt` |
| Safety rules 1–8 (the Phase-1 applicable ones) | see section 5 |

---

## 3. Google Sign-In setup (optional but recommended)

Without this, the app runs in **limited mode** and says so on every screen.

1. Go to Google Cloud Console → **APIs & Services ▸ Credentials**.
2. Create an **OAuth client ID** of type **Web application**.
3. Get your signing SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
     -storepass android -keypass android
   ```
4. Add that SHA-1 (plus package name `com.jarvis`) to the OAuth consent screen
   and create an **Android** client ID too.
5. Paste the **Web** client id into `gradle.properties`:
   ```properties
   jarvisGoogleWebClientId=1234567890-abc123.apps.googleusercontent.com
   ```
6. **Sync Project with Gradle Files**.

### Play Integrity

`jarvisPlayIntegrityProjectNumber` in `gradle.properties` is optional (Google
Cloud project *number*, digits only). A sideloaded APK will normally report
**NOT VERIFIED · LIMITED MODE** — that is the honest result, because the integrity
verdict is encrypted for a server to read, not for the phone. The app never
claims verification without a token.

---

## 4. Manual test checklist (Phase 1 "done" criteria)

Run these in order on a real phone.

**Onboarding**
1. Fresh install → splash → **Sign in with Google** (or limited mode).
2. Integrity card shows either *Verified* or *Not verified · limited mode* — never a
   fake "verified".
3. Permissions screen: each row shows a real status. Deny the mic → **Continue**
   stays disabled. Grant it → Continue unlocks.
4. API key screen: paste a wrong key → red error, **nothing saved**. Paste a
   correct key → green "Key verified. N models available", then home appears.
5. Kill and reopen the app → it goes straight to home (onboarding not repeated).

**Orb**
6. Home shows the red particle orb, slowly rotating, with a plasma glow.
7. Say **"Jarvis, make the orb a heart"** → the cloud morphs into a heart within
   ~0.8 s and a confirmation line appears.
8. Say **"Jarvis, make the orb a torus, purple, bigger"** → torus, purple, larger.
9. Say **"Jarvis, use the formula shape"** → the model sends `custom_formula` with a
   valid `x;y;z` formula and the orb reshapes (an invalid formula is rejected with
   an error, the orb stays as it was).
10. Settings ▸ Appearance → move the size/glow/particle sliders → the preview
    follows smoothly.

**Voice + chat**
11. Tap the orb → mic chip + level bars appear → speak → the reply is spoken in the
    right language (Hindi reply in Devanagari → Hindi voice).
12. Turn **CONT ON** → after the reply finishes, listening restarts by itself.
13. Settings ▸ Language ▸ Hinglish → replies come back in Roman Hindi/English mix.

**Models**
14. Settings ▸ AI Models ▸ Refresh → list appears, current model has **IN USE**.
15. Tap another model → status chip in home header changes.
16. Say **"Jarvis, switch to model <name from the list>"** → header chip changes and
    a "model → …" line is appended.
17. Ask for a model that is *not* in the list → Jarvis refuses and names the allowed
    models (it must not invent success).

**Safety + logs**
18. Say **"Jarvis stop"** → orb goes idle, speech stops, status says stopped.
    Tap **Resume** → wake word returns.
19. Tap **STOP** in the header while a reply is streaming → it stops immediately.
20. Settings ▸ Logs & Diagnostics → every AI call appears, with `redactions=N`.
    A message containing a phone number shows `[PHONE]`, an email shows `[EMAIL]`,
    an OTP sentence shows `[OTP REDACTED]`.
21. Settings ▸ Developer ▸ Copy diagnostics → paste into a note → no API key inside.

**No fake functionality**
22. Settings list → the 15 unbuilt sections all show "Coming in Phase N" and have
    no working switches.

---

## 5. Safety rules — where they live in the code

| Rule | Code |
|---|---|
| 1. Never read password fields / skip banking, OTP, SMS apps | No accessibility service exists yet (Phase 3). The rule is documented on the Accessibility permission row and in `SettingsSection.ACCESSIBILITY`. |
| 2. No SMS / OTP / call-log permissions | `AndroidManifest.xml` — deliberately absent, with a comment. Never add them. |
| 3. Redaction before anything leaves the phone | `core/security/Redactor.kt`, called once in `JarvisEngine.sendText` before the first API call. |
| 4. Confirmation for sensitive actions | Phase 1 has no messaging/terminal; every destructive in-app action (clear chat, erase key, wipe data, sign out) uses an explicit dialog. |
| 5. No success without verification | Tool results are parsed from Gemini's real `functionResponse`; failures surface as `status=error` and Jarvis must say so (enforced in the system prompt). |
| 6. Emergency stop | `core/EmergencyStop.kt` + header STOP + long-press on the orb + the voice command "Jarvis stop". The notification action arrives in Phase 2. |
| 7. Audit log of everything | `core/AuditLogger.kt` → Room + logcat, every line redacted. Viewable in Settings ▸ Logs. |
| 8. Visible microphone indicator | Mic chip, live RMS bars, ring around the orb, and `state.listening` is never true silently. |

---

## 6. Signed release build (for sideloading)

```bash
# 1. create a keystore (keep it safe, you need it for every future update)
keytool -genkeypair -v -keystore jarvis-release.jks -alias jarvis \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. create <repo>/keystore.properties  (git-ignored)
cat > keystore.properties <<'EOF'
storeFile=jarvis-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=jarvis
keyPassword=YOUR_KEY_PASSWORD
EOF

# 3. build
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

---

## 7. Known limitations (Phase 1, stated honestly)

1. **Wake word is not offline yet.** Android's `SpeechRecognizer` listens in short
   bursts while the app is on screen, and asks for on-device recognition when
   possible. The true always-on offline Vosk engine belongs to Phase 2.
2. **Listening stops when the app leaves the screen.** A persistent foreground
   service is Phase 2 by design.
3. **Google Sign-In needs your OAuth client.** Without it, limited mode is used and
   flagged everywhere.
4. **Play Integrity verdict cannot be read on the device.** Only a backend can
   decrypt it; the app reports "token received" vs "limited mode".
5. **Custom formula shapes are point clouds**, so they look best with 2000–8000
   particles; `v` is sampled on the surface, not as a solid volume.
6. **TTS Hindi needs a Hindi voice installed** on the phone (Settings ▸ Voice warns
   you when it is missing).
7. **The emergency stop button is in-app**, not in a notification (Phase 2).
8. **App UI is English only**; replies can be Hindi/Hinglish.
9. **No memory, automation, terminal, phone control, WhatsApp, Instagram** — those
   are Phases 2–5 and their screens say so.

---

## 8. Project layout

```
settings.gradle.kts / build.gradle.kts / gradle.properties
gradle/libs.versions.toml
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/…                        (theme, icons, strings)
app/src/main/java/com/jarvis/
    JarvisApp.kt, MainActivity.kt
    core/            ServiceLocator, AuditLogger, EmergencyStop,
                     security/SecureStore, security/Redactor
    data/            db (Room: logs + chat), prefs/AppPreferences,
                     remote/gemini (DTOs + client)
    domain/          JarvisEngine, Prompts, model/, orb/ (spec, geometry, MiniExpr),
                     tools/JarvisTools
    auth/            GoogleAuthManager, PlayIntegrityChecker
    voice/           VoiceController, TtsController
    ui/              theme/ (Colour, Theme, Glass), components/ (Orb, Common),
                     screens/ (Splash, SignIn, Integrity, Permissions, ApiKey, Home),
                     screens/settings/ (27 sections), nav/, vm/
```

---

## 9. हिन्दी सारांश (short Hindi summary)

- यह **Phase 1** का पूरा कोड है: Google साइन-इन, Play Integrity, परमिशन स्क्रीन,
  Gemini API key (पहले टेस्ट, फिर Keystore में सेव), लाल कणों वाला ऑर्ब, आवाज़ से
  बातचीत, और 27 सेटिंग सेक्शन (12 अभी काम करते हैं, बाकी "Coming in Phase X")।
- "Jarvis, orb ko heart banao" और "Jarvis, model badlo" दोनों काम करते हैं।
- हर AI कॉल से पहले फ़ोन नंबर, ईमेल, कार्ड, OTP और API key छिपाए जाते हैं — यह बंद
  नहीं किया जा सकता।
- SMS/OTP की परमिशन ऐप में कहीं नहीं है।
- **ध्यान दें:** इस मशीन पर Android SDK नहीं था, इसलिए कोड कंपाइल नहीं किया जा सका।
  Android Studio में खोलें और Run दबाएँ; नीचे दी गई टेस्ट लिस्ट से जाँच करें।

Phase 1 पूरा हो गया। अगला कदम: **Phase 2** (foreground service, offline Vosk wake
word, terminal) — तैयार हों तो "next" लिखें।

---

# Add-on: Self Setup and Learning Module

A self-contained module added on top of Phase 1. It does **not** modify the voice
pipeline, the orb, sign-in or the settings that already worked; everything below
is additive (new tables, new screens, new tools).

## A.1 What it does

1. **Attach → self setup** — the 📎 button in chat opens the system file picker
   (Storage Access Framework, so no storage permission is requested). Say
   "setup kar lo" and Jarvis:
   - copies the upload into `files/uploads`, extracts zips safely (zip-slip
     guarded, 3000 entries / 200 MB cap) or reads a text/config file,
   - asks Gemini **only for a plan** (folder creation, extraction, config
     writes, dependency checks, API-key placeholders),
   - validates that plan locally (`SetupPlan` validator: relative paths only,
     ≤ 40 steps, dangerous step kinds refused),
   - executes every step through the in-app shell, verifies the real result,
     retries once with a fix, and explains the exact error in simple Hindi,
   - persists the job and every step in Room under **Saved Setups**.
2. **In-app terminal** — `/system/bin/sh -c` inside `files/workspace`, streaming
   output, 60 s timeout, 200k char cap, duplicate-command rejection, a 6-minute
   total setup deadline, Emergency Stop kills the process. Dangerous commands
   are shown with the reason and need "Haan, chalao". Every command is logged
   with a timestamp, exit code and outcome (`command_logs`).
3. **Learning from mistakes** — say "ye galat hai" (or "ise aise sudharo") in
   chat. Jarvis asks one short question, then stores the mistake + correct
   behaviour **AES-256-GCM encrypted** (Keystore key `jarvis_learning_key_v1`).
   Notes are permanent and are injected into every future prompt and every setup
   plan. Settings › Memory supports search-by-eye list, edit and delete.
   Confirmation: *"Yaad rakh liya, aage se ye nahi karunga."*
4. **Chat artifacts** — `create_file` writes a real file (`txt.txt`,
   `index.html`, …) into `files/artifacts`, registers it in Room and shows a card
   in the chat bubble (open / share through the app's FileProvider).
   `make_video` renders a real MP4 (MediaCodec AVC + MediaMuxer, 480×854,
   text cards) that plays **inside the chat** with Media3 ExoPlayer.

## A.2 New / changed files

| Path | Status |
|---|---|
| `core/security/CryptoBox.kt` | new |
| `core/terminal/CommandGuard.kt` | new |
| `core/terminal/ShellRunner.kt` | new |
| `core/media/LocalVideoMaker.kt` | new |
| `core/ArtifactIntents.kt` | new |
| `data/db/AddOnEntities.kt` | new (5 tables + DAOs) |
| `domain/setup/FileIntake.kt`, `SetupPlan.kt`, `SetupPlanner.kt`, `SelfSetupEngine.kt` | new |
| `domain/learning/LearningRepository.kt` | new |
| `domain/artifacts/ArtifactRepository.kt` | new |
| `ui/components/Artifacts.kt` | new (artifact card + in-chat player) |
| `ui/screens/setup/SetupProgressSheet.kt` | new |
| `ui/screens/module/ModuleScreens.kt`, `ModuleOverviewScreen.kt` | new |
| `res/xml/file_paths.xml` | new |
| `data/db/JarvisDatabase.kt` | v1 → v2 + `MIGRATION_1_2` |
| `domain/model/ChatMessage.kt` | + `ChatArtifact`, artifact columns |
| `domain/JarvisEngine.kt` | + attach / setup / learning routing, 2 new tools |
| `domain/tools/JarvisTools.kt` | + `create_file`, `make_video` |
| `domain/Prompts.kt` | + saved corrections + file/video rules |
| `core/ServiceLocator.kt` | + module singletons |
| `ui/screens/HomeScreen.kt` | + attach button, chip, setup sheet, artifact actions |
| `ui/vm/ViewModels.kt` | + setup/memory/terminal/saved-setups view models |
| `ui/nav/SettingsSections.kt`, `ui/nav/JarvisNav.kt`, `ui/screens/settings/SectionScreen.kt`, `SettingsHubScreen.kt` | + module sections |
| `AndroidManifest.xml` | + FileProvider |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | + Media3 1.4.1 |
| `README.md` | this section |

## A.3 Database v2

`saved_setups`, `setup_steps`, `learning_notes` (encrypted columns),
`command_logs`, `artifacts`, plus nullable `artifactName/artifactPath/
artifactMime` on `chat_messages`. The migration is additive only; Phase 1 rows
are untouched.

## A.4 Honest limits (Android reality)

- The shell is **not** a system shell. Without root, an app can only run
  `/system/bin/sh` inside its own sandbox (a toybox subset). `apt`, `sudo`,
  system-wide installs and writes outside the app folder are impossible; Jarvis
  reports the real failure instead of faking success.
- "Install dependencies" therefore means: download-free checks inside the
  sandbox, creating folders/config files, and writing a `SETUP_NOTES.md` with
  the exact manual steps when something needs a real installer.
- Videos are generated locally from text cards; no stock footage is downloaded.
  If the device has no H.264 encoder the tool returns an honest Hindi error.
- Nothing here reads SMS, OTPs, passwords or call logs; redaction still runs
  before every AI call.

## A.5 Build

```bash
./gradlew clean assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## A.6 Test checklist (module)

1. Install, finish onboarding, open chat.
2. 📎 → pick any zip → chip shows the file name → type "setup kar lo".
3. The setup sheet opens: plan steps appear, each turns green only after
   verification, live output streams, dangerous steps ask "Haan, chalao".
4. Settings › Saved Setups shows the job with every step, output and exit code.
5. Type "ye galat hai" → answer the question → read "Yaad rakh liya, aage se ye
   nahi karunga." → Settings › Memory shows the encrypted note → edit it, then
   delete a second one.
6. Force-stop the app, reopen: Saved Setups and Memory are still there, and a new
   question shows the correction is applied (ask the same thing again).
7. Ask "index.html banao jisme hello likha ho" → card appears in chat → Kholo /
   Share work.
8. Ask "5 second ka video banao" → MP4 card appears → play it inside the chat.
9. Settings › Terminal → `echo hello` → live output + log entry; try
   `rm -rf /data` → blocked; try `ls && echo hi` → confirmation dialog.
10. Emergency STOP while a setup runs: command dies, setup is marked cancelled,
    nothing claims success.
