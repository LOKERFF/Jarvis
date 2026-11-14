# apk-distribution

This folder is the single place the *genuine, compiled* debug APK is stored so the
release workflow (`.github/workflows/publish-apk.yml`) can publish it.

## Status of the APK right now

**No APK is committed yet.** The build agent that prepared this folder has no JDK,
no Android SDK and no Gradle, and its network blocks `dl.google.com`,
`services.gradle.org` and `repo.maven.apache.org`, so a real build could not be
run there. No placeholder, renamed zip or simulated APK was created on purpose.

## Producing the APK (one real build, outside GitHub Actions)

Requirements: JDK 17, Android SDK Platform 35 + Build-Tools, Gradle 8.9 (the
wrapper already pins it).

```bash
cd <repo>
./gradlew clean assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk apk-distribution/app-debug.apk
cd apk-distribution
sha256sum app-debug.apk > app-debug.apk.sha256
```

`app-debug.apk.sha256` must stay in the standard `sha256sum` format:

```
<64 hex characters>  app-debug.apk
```

## Then

Commit `apk-distribution/app-debug.apk` and `apk-distribution/app-debug.apk.sha256`
and push to `main` (or `master`). The workflow validates the APK (exists, non-empty,
checksum matches, `unzip -tq` passes) and only then creates a release with the tag
`debug-apk-build-<run>-<attempt>`, marking it latest. It never builds the app and
never installs an Android SDK.

Direct download after a successful run:

```
https://github.com/<owner>/<repo>/releases/latest/download/app-debug.apk
```

## Never put here

- release keystores, `keystore.properties`, passwords or API keys
- APKs from another project or an older/failed build
- renamed zips or empty files
