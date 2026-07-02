#!/usr/bin/env bash
# 内网制品发布骨架（需配置 NEXUS_URL / NPM_REGISTRY）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-1.0.0-SNAPSHOT}"

echo "==> Web SDK @giso/tracker-web@${VERSION}"
cd "$ROOT/sdk/web"
npm run build
if [[ -n "${NPM_REGISTRY:-}" ]]; then
  npm publish --registry "$NPM_REGISTRY" --access restricted
else
  echo "skip npm publish (set NPM_REGISTRY)"
fi

echo "==> Android SDK com.giso:tracker:${VERSION}"
cd "$ROOT/sdk/android"
if [[ -n "${NEXUS_URL:-}" ]]; then
  ./gradlew publish -Pversion="$VERSION" -PnexusUrl="$NEXUS_URL"
else
  ./gradlew assembleRelease
  echo "skip maven publish (set NEXUS_URL); aar at sdk/android/build/outputs/"
fi

echo "done"
