# apk-distribution

Automated Release APK builds run via `.github/workflows/build-apk.yml` and publish directly to the GitHub Release tagged **`v2`** (`jarvis-v2-release.apk`).

If you build a prebuilt APK locally and commit it to this folder, `.github/workflows/publish-apk.yml` validates the archive + SHA-256 checksum and publishes/updates the **`v2`** GitHub Release.

## Producing a Release APK locally

Requirements: JDK 17, Android SDK Platform 35 + Build-Tools `35.0.0`, Gradle 8.9.

```bash
./build-apk.sh
```

This builds `:app:assembleRelease` (`versionCode = 2`, `versionName = "2.0.0"`), signs the APK, verifies it with `apksigner` and `unzip -tq`, and writes:

- `apk-distribution/jarvis-v2-release.apk`
- `apk-distribution/jarvis-v2-release.apk.sha256`

To build a debug APK instead:

```bash
./build-apk.sh debug
```

## Direct Download (`v2` Release)

```
https://github.com/<owner>/<repo>/releases/download/v2/jarvis-v2-release.apk
```

## Never commit here

- `jarvis-release.jks`, `debug.keystore`, `keystore.properties`, passwords, or API keys
- APKs from another project or unverified builds
