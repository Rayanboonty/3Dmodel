#!/usr/bin/env bash
# Build Vortex Client for Android.
#
# Fetches Amethyst-Android (LGPL-3.0) at a pinned commit, applies the Vortex
# overlay (apply.py), and builds an APK. Needs: git, python3, JDK 21 and
# JDK 8, the Android SDK with NDK 27.3.13750724 (ANDROID_HOME).
#
#   ./build.sh            # release APKs -> out/Vortex-Client-android-<cpu>.apk
#   ./build.sh debug      # debug APK
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="${AMETHYST_DIR:-$HERE/.amethyst}"
COMMIT="28d6e12"   # Amethyst-Android commit the overlay was made for
KIND="${1:-release}"

if [ ! -d "$SRC/.git" ]; then
  GIT_LFS_SKIP_SMUDGE=1 git clone --filter=blob:none https://github.com/AngelAuraMC/amethyst-android "$SRC"
fi
git -C "$SRC" fetch --depth 1 origin "$COMMIT" 2>/dev/null || true
git -C "$SRC" checkout -q --force "$COMMIT"
# Amethyst lists one submodule by SSH; fetch every GitHub one over HTTPS.
git -C "$SRC" -c url."https://github.com/".insteadOf="git@github.com:" \
  submodule update --init --depth 1 androidnsbypass MioLibPatcher

(cd "$SRC" && bash scripts/languagelist_updater.sh >/dev/null)
python3 "$HERE/apply.py" "$SRC"

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
echo "sdk.dir=$ANDROID_HOME" > "$SRC/local.properties"
JDKS="${JDK_PATHS:-/usr/lib/jvm/java-8-openjdk-amd64,/usr/lib/jvm/java-21-openjdk-amd64}"
V="$( [ "$KIND" = debug ] && echo Debug || echo Release )"
# Lint's release check can't resolve one of upstream's dependencies; skip it.
SKIP=(); [ "$V" = Release ] && SKIP=(-x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease)
(cd "$SRC" && ./gradlew --no-daemon --max-workers=2 \
  -Porg.gradle.java.installations.paths="$JDKS" -Porg.gradle.java.installations.auto-detect=false \
  ":app_pojavlauncher:assemble$V" "${SKIP[@]}")

mkdir -p "$HERE/out"
# One APK per CPU type plus a universal one.
SUF="$( [ "$KIND" = debug ] && echo -debug )"
for apk in "$SRC"/app_pojavlauncher/build/outputs/apk/"$KIND"/*.apk; do
  case "$apk" in
    *arm64*) n=arm64 ;; *armeabi*) n=armv7 ;; *x86_64*) n=x86_64 ;; *universal*) n=universal ;; *) n=universal ;;
  esac
  cp "$apk" "$HERE/out/Vortex-Client-android-$n$SUF.apk"
  echo "Built $HERE/out/Vortex-Client-android-$n$SUF.apk"
done
