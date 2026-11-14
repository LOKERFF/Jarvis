#!/usr/bin/env bash
# Builds the genuine debug APK, copies it into apk-distribution/ with its
# sha256 file, verifies both, and prints the exact next commands.
#
# Requires: JDK 17, Android SDK Platform 35 (ANDROID_HOME or local.properties),
# and Gradle 8.9 via the wrapper already in this repo.
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '%s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || fail "java (JDK 17) nahi mila. Android Studio ka bundled JDK ya koi bhi JDK 17 install karo."

# This repo currently ships no gradlew script, so accept a system Gradle too.
if [ -x ./gradlew ]; then
  GRADLE_CMD=./gradlew
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=gradle
else
  fail "./gradlew nahi hai aur 'gradle' bhi install nahi hai. Gradle 8.9 install karo, ya Android Studio me project khol kar Sync chalayein (wo wrapper bana dega)."
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  say "warning: ANDROID_HOME / ANDROID_SDK_ROOT / local.properties me se koi nahi mila."
  say "         Gradle ko Android SDK chahiye, warna build fail hoga."
fi

say "==> Building debug APK ($GRADLE_CMD clean assembleDebug)"
"$GRADLE_CMD" clean assembleDebug

SRC=app/build/outputs/apk/debug/app-debug.apk
[ -s "$SRC" ] || fail "Build ke baad $SRC nahi mila. Build output check karo."

say "==> Copying into apk-distribution/"
mkdir -p apk-distribution
cp "$SRC" apk-distribution/app-debug.apk

say "==> Writing checksum"
( cd apk-distribution && sha256sum app-debug.apk > app-debug.apk.sha256 )

say "==> Verifying"
unzip -tq apk-distribution/app-debug.apk > /dev/null || fail "APK archive invalid hai."
EXPECTED=$(awk '{print $1}' apk-distribution/app-debug.apk.sha256 | head -n1)
ACTUAL=$(sha256sum apk-distribution/app-debug.apk | awk '{print $1}')
[ "$EXPECTED" = "$ACTUAL" ] || fail "Checksum mismatch: $EXPECTED != $ACTUAL"

SIZE=$(stat -c%s apk-distribution/app-debug.apk 2>/dev/null || stat -f%z apk-distribution/app-debug.apk)
say ""
say "OK  apk-distribution/app-debug.apk"
say "    size   : $SIZE bytes"
say "    sha256 : $ACTUAL"
say ""
say "Ab ye chalao:"
say "    git add apk-distribution"
say "    git commit -m \"APK: debug build\""
say "    git push origin main"
say ""
say "Push ke baad workflow khud release publish karega:"
say "    <repo>/releases/latest/download/app-debug.apk"
