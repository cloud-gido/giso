#!/usr/bin/env bash
# Bootstrap repo-local Android SDK for Flutter builds (China-friendly mirrors).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$REPO_ROOT/.android-sdk}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
MIRROR="${ANDROID_SDK_MIRROR:-https://googledownloads.cn/android/repository}"
CACHE="$ANDROID_HOME/.cache"
mkdir -p "$CACHE" "$ANDROID_HOME/build-tools" "$ANDROID_HOME/platforms" "$ANDROID_HOME/platform-tools" "$ANDROID_HOME/cmake"

fetch_zip() {
  local cache_name="$1" url="$2"
  local zip="$CACHE/$cache_name"
  if [[ ! -f "$zip" ]]; then
    echo "Downloading $cache_name ..." >&2
    curl -fL --retry 3 --continue-at - -o "$zip" "$url"
  fi
  printf '%s\n' "$zip"
}

install_dir_from_zip() {
  local dest="$1" cache_name="$2" url="$3"
  if [[ -d "$dest" && ( -f "$dest/package.xml" || -x "$dest/aapt2" || -f "$dest/source.properties" ) ]]; then
    echo "OK: $dest"
    return 0
  fi
  local zip tmp extracted
  zip="$(fetch_zip "$cache_name" "$url")"
  tmp="$(mktemp -d)"
  unzip -q "$zip" -d "$tmp"
  extracted="$(find "$tmp" -mindepth 1 -maxdepth 1 -type d | head -1)"
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  mv "$extracted" "$dest"
  rm -rf "$tmp"
  echo "Installed -> $dest"
}

# Flutter 3.44 plugins (video_player / shared_preferences) require compileSdk 36 + build-tools 35.0.0.
install_dir_from_zip \
  "$ANDROID_HOME/build-tools/35.0.0" \
  "build-tools_r35_macosx.zip" \
  "$MIRROR/build-tools_r35_macosx.zip"

install_dir_from_zip \
  "$ANDROID_HOME/platforms/android-36" \
  "platform-36_r02.zip" \
  "$MIRROR/platform-36_r02.zip"

if [[ ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  install_dir_from_zip \
    "$ANDROID_HOME/platform-tools" \
    "platform-tools_r35.0.2-darwin.zip" \
    "$MIRROR/platform-tools_r35.0.2-darwin.zip"
fi

bash "$REPO_ROOT/scripts/install-android-cmake.sh"
bash "$REPO_ROOT/scripts/install-android-ndk.sh"
