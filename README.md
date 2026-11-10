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
