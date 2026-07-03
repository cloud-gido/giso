#!/usr/bin/env bash
# Build Flutter debug APK for phone sideload (EKS test gateway by default).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/../.." && pwd)"
export PATH="${HOME}/flutter/bin:${PATH}"
export ANDROID_HOME="${ANDROID_HOME:-$REPO_ROOT/.android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

cat > "$ROOT/android/local.properties" <<EOF
sdk.dir=$ANDROID_HOME
flutter.sdk=${HOME}/flutter
EOF

echo "==> Bootstrapping Android SDK (NDK + CMake + build-tools 35 + platform 36)..."
bash "$REPO_ROOT/scripts/bootstrap-android-sdk.sh"

cd "$ROOT"
flutter pub get
flutter analyze --fatal-infos
flutter test
flutter build apk --debug \
  --dart-define=GISO_ENDPOINT="${GISO_ENDPOINT:-https://gamelinelab-giso.envir.dev/v1/track}" \
  --dart-define=GISO_APP_KEY="${GISO_APP_KEY:-video-android-beta}"

APK="$ROOT/build/app/outputs/flutter-apk/app-debug.apk"
echo ""
echo "Built: $APK"
echo "Install: adb install -r \"$APK\""
