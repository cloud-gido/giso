#!/usr/bin/env bash
# Install Android SDK CMake (required for Flutter native / NDK CMake builds).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$REPO_ROOT/.android-sdk}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

CMAKE_VERSION="${CMAKE_VERSION:-3.22.1}"
DEST="$ANDROID_HOME/cmake/$CMAKE_VERSION"
MIRROR="${ANDROID_SDK_MIRROR:-https://googledownloads.cn/android/repository}"
CACHE_DIR="$ANDROID_HOME/.cache"
mkdir -p "$CACHE_DIR" "$ANDROID_HOME/cmake"

cmake_installed() {
  [[ -x "$DEST/bin/cmake" ]]
}

if cmake_installed; then
  echo "CMake ${CMAKE_VERSION} already present under ${DEST}"
  exit 0
fi

install_from_android_zip() {
  local zip_name="cmake-${CMAKE_VERSION}-darwin.zip"
  local zip="$CACHE_DIR/$zip_name"
  local url="${CMAKE_URL:-$MIRROR/$zip_name}"

  if [[ ! -f "$zip" ]]; then
    echo "Downloading ${zip_name} from ${url} ..." >&2
    if ! curl -fL --retry 3 --continue-at - -o "$zip" "$url"; then
      rm -f "$zip"
      return 1
    fi
  fi

  rm -rf "$DEST"
  mkdir -p "$(dirname "$DEST")"
  unzip -q "$zip" -d "$DEST"
  [[ -x "$DEST/bin/cmake" ]]
}

install_from_kitware_tarball() {
  local tar_name="cmake-${CMAKE_VERSION}-macos-universal.tar.gz"
  local tar="$CACHE_DIR/$tar_name"
  local url="${CMAKE_KITWARE_URL:-https://cmake.org/files/v3.22/cmake-${CMAKE_VERSION}-macos-universal.tar.gz}"
  local tmp ninja_zip ninja_tmp

  if [[ ! -f "$tar" ]]; then
    echo "Android mirror has no cmake-${CMAKE_VERSION}; downloading Kitware ${tar_name} ..."
    curl -fL --retry 3 --continue-at - -o "$tar" "$url"
  fi

  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  tar -xzf "$tar" -C "$tmp"

  local app_bin app_share app_doc
  app_bin="$(find "$tmp" -path '*/CMake.app/Contents/bin' -type d | head -1)"
  app_share="$(find "$tmp" -path '*/CMake.app/Contents/share/cmake-*' -type d | head -1)"
  app_doc="$(find "$tmp" -path '*/CMake.app/Contents/doc/cmake-*' -type d | head -1)"
  [[ -n "$app_bin" && -x "$app_bin/cmake" ]] || {
    echo "Invalid Kitware CMake archive: CMake.app not found"
    return 1
  }

  rm -rf "$DEST"
  mkdir -p "$DEST/bin" "$DEST/share" "$DEST/doc"
  cp -R "$app_bin/"* "$DEST/bin/"
  [[ -n "$app_share" ]] && cp -R "$app_share" "$DEST/share/"
  [[ -n "$app_doc" ]] && cp -R "$app_doc" "$DEST/doc/"

  # AGP Android CMake packages also ship ninja; borrow from a newer mirror package when needed.
  if [[ ! -x "$DEST/bin/ninja" ]]; then
    ninja_zip="$CACHE_DIR/cmake-ninja-donor-darwin.zip"
    if [[ ! -f "$ninja_zip" ]]; then
      echo "Fetching ninja from cmake-3.31.6-darwin.zip (googledownloads.cn) ..."
      curl -fL --retry 3 --continue-at - -o "$ninja_zip" \
        "$MIRROR/cmake-3.31.6-darwin.zip"
    fi
    ninja_tmp="$(mktemp -d)"
    unzip -q -j "$ninja_zip" "bin/ninja" -d "$ninja_tmp"
    cp "$ninja_tmp/ninja" "$DEST/bin/ninja"
    chmod +x "$DEST/bin/ninja"
    rm -rf "$ninja_tmp"
  fi

  cat >"$DEST/source.properties" <<EOF
Pkg.Revision = ${CMAKE_VERSION}
Pkg.Path = cmake;${CMAKE_VERSION}
Pkg.Desc = CMake ${CMAKE_VERSION}
EOF
}

if install_from_android_zip 2>/dev/null; then
  echo "Installed Android CMake ${CMAKE_VERSION} -> ${DEST}"
  exit 0
fi

install_from_kitware_tarball
echo "Installed CMake ${CMAKE_VERSION} (Kitware repack) -> ${DEST}"
