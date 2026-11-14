#!/usr/bin/env bash
# Builds the Jarvis APK (release by default, or pass 'debug'), ensures keystores
# exist when run locally, copies APK + .sha256 into apk-distribution/, and verifies it.
#
# Usage:
#   ./build-apk.sh           # builds signed release APK -> apk-distribution/jarvis-v2-release.apk
#   ./build-apk.sh debug     # builds debug APK -> apk-distribution/app-debug.apk
set -euo pipefail
cd "$(dirname "$0")"

VARIANT="${1:-release}"

say() { printf '%s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || fail "java (JDK 17) nahi mila. JDK 17 install karo."

if [ -x ./gradlew ]; then
  GRADLE_CMD=./gradlew
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=gradle
else
  fail "./gradlew ya 'gradle' nahi mila. Gradle 8.9 install karo."
fi

if [ ! -f debug.keystore ]; then
  command -v keytool >/dev/null 2>&1 || fail "debug.keystore missing hai aur 'keytool' nahi mila."
  say "==> Generating debug.keystore"
  keytool -genkeypair -v \
    -keystore debug.keystore \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

mkdir -p apk-distribution
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [ "$VARIANT" = "release" ]; then
  if [ ! -f keystore.properties ]; then
    command -v keytool >/dev/null 2>&1 || fail "keystore.properties missing hai aur 'keytool' nahi mila."
    say "==> Creating local jarvis-release.jks & keystore.properties for signed release APK"
    if [ ! -f jarvis-release.jks ]; then
      keytool -genkeypair -v \
        -keystore jarvis-release.jks \
        -storepass jarvis-local-release \
        -alias jarvis \
        -keypass jarvis-local-release \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Jarvis App,O=Jarvis,C=IN" >/dev/null 2>&1
    fi
    cat > keystore.properties <<EOF
storeFile=jarvis-release.jks
storePassword=jarvis-local-release
keyAlias=jarvis
keyPassword=jarvis-local-release
EOF
  fi

  say "==> Building release APK ($GRADLE_CMD :app:assembleRelease)"
  "$GRADLE_CMD" :app:assembleRelease --no-daemon --stacktrace

  SRC="$(find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' | head -n1 || true)"
  [ -n "$SRC" ] && [ -s "$SRC" ] || fail "Build ke baad release APK nahi mila."
  OUT="jarvis-v2-release.apk"
elif [ "$VARIANT" = "debug" ]; then
  say "==> Building debug APK ($GRADLE_CMD :app:assembleDebug)"
  "$GRADLE_CMD" :app:assembleDebug --no-daemon --stacktrace

  SRC="app/build/outputs/apk/debug/app-debug.apk"
  [ -s "$SRC" ] || fail "Build ke baad $SRC nahi mila."
  OUT="app-debug.apk"
else
  fail "Unknown variant '$VARIANT'. Use 'release' or 'debug'."
fi

if [ -n "$SDK" ] && [ -x "$SDK/build-tools/35.0.0/apksigner" ]; then
  say "==> Verifying APK signature via apksigner"
  "$SDK/build-tools/35.0.0/apksigner" verify --print-certs "$SRC"
fi

cp "$SRC" "apk-distribution/$OUT"
( cd apk-distribution && sha256sum "$OUT" > "${OUT}.sha256" )

unzip -tq "apk-distribution/$OUT" > /dev/null || fail "APK archive invalid hai."
ACTUAL=$(sha256sum "apk-distribution/$OUT" | awk '{print $1}')
SIZE=$(stat -c%s "apk-distribution/$OUT" 2>/dev/null || stat -f%z "apk-distribution/$OUT")

say ""
say "OK  apk-distribution/$OUT"
say "    size   : $SIZE bytes"
say "    sha256 : $ACTUAL"
