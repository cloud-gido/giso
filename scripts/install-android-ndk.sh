#!/usr/bin/env bash
# Install Android NDK into repo-local .android-sdk (China-friendly mirror).
# Required for Flutter APK builds (video_player / engine native code).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$REPO_ROOT/.android-sdk}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

# video_player / Flutter 3.44 commonly require one of these revisions.
PREFERRED_NDK_REV="${PREFERRED_NDK_REV:-27.0.12077973}"
NDK_ZIP_NAME="${NDK_ZIP_NAME:-android-ndk-r27-darwin.zip}"
NDK_URL="${NDK_URL:-https://googledownloads.cn/android/repository/${NDK_ZIP_NAME}}"

ndk_installed() {
  local dir="$ANDROID_HOME/ndk/$PREFERRED_NDK_REV/source.properties"
  [[ -f "$dir" ]]
}

find_any_ndk() {
  local d rev
  for d in "$ANDROID_HOME"/ndk/*/source.properties; do
    [[ -f "$d" ]] || continue
    rev="$(grep -E '^Pkg\.Revision' "$d" | head -1 | cut -d= -f2 | tr -d ' ')"
    if [[ -n "$rev" ]]; then
      echo "$rev"
      return 0
    fi
  done
  return 1
}

if ndk_installed; then
  echo "NDK ${PREFERRED_NDK_REV} already present under ${ANDROID_HOME}/ndk/"
  exit 0
fi

if rev="$(find_any_ndk)"; then
  echo "Found NDK ${rev} under ${ANDROID_HOME}/ndk/ (expected ${PREFERRED_NDK_REV})."
  echo "Set PREFERRED_NDK_REV=${rev} or install ${PREFERRED_NDK_REV} if the build still fails."
  exit 0
fi

mkdir -p "$ANDROID_HOME/ndk" "$ANDROID_HOME/.cache"
CACHE="$ANDROID_HOME/.cache/$NDK_ZIP_NAME"

if [[ ! -f "$CACHE" ]]; then
  echo "Downloading NDK (~870MB) from ${NDK_URL}"
  echo "This is a one-time download; later builds reuse ${CACHE}"
  curl -fL --retry 3 --continue-at - -o "$CACHE" "$NDK_URL"
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Extracting NDK..."
unzip -q "$CACHE" -d "$TMP"
PROP="$(find "$TMP" -name source.properties | head -1)"
[[ -n "$PROP" ]] || { echo "Invalid NDK archive: source.properties not found"; exit 1; }

NDK_ROOT="$(dirname "$PROP")"
ACTUAL_REV="$(grep -E '^Pkg\.Revision' "$PROP" | head -1 | cut -d= -f2 | tr -d ' ')"
DEST="$ANDROID_HOME/ndk/$ACTUAL_REV"

rm -rf "$DEST"
mv "$NDK_ROOT" "$DEST"

echo "Installed Android NDK ${ACTUAL_REV} -> ${DEST}"
if [[ "$ACTUAL_REV" != "$PREFERRED_NDK_REV" ]]; then
  echo "Note: build may expect ${PREFERRED_NDK_REV}; set android.ndkVersion or PREFERRED_NDK_REV=${ACTUAL_REV}"
fi
