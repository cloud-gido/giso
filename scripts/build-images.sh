#!/usr/bin/env bash
# 构建 GISO 应用镜像（本地 / CI 前置）
# 参考 gido/scripts 与 k8s/giso-deploy.env.example
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=/dev/null
[ -f k8s/giso-deploy.env ] && source k8s/giso-deploy.env

REGISTRY="${GISO_IMAGE_REGISTRY:-giso}"
TAG="${GISO_IMAGE_TAG:-local}"
PLATFORM="${GISO_BUILD_PLATFORM:-}"
PUSH="${GISO_PUSH:-0}"

build_one() {
  local name="$1" context="$2" dockerfile="$3"
  shift 3
  local full="${REGISTRY}/${name}:${TAG}"
  local -a args=()
  while [ $# -gt 0 ]; do args+=("$1"); shift; done

  echo "[build] ${full}"
  if [ -n "$PLATFORM" ]; then
    docker buildx build --platform "$PLATFORM" --provenance=false --sbom=false \
      ${PUSH:+--push} ${PUSH:---load} \
      -f "$dockerfile" "${args[@]}" -t "$full" "$context"
  else
    docker build -f "$dockerfile" "${args[@]}" -t "$full" "$context"
    if [ "$PUSH" = "1" ]; then docker push "$full"; fi
  fi
}

build_one giso-gateway "$ROOT" deploy/Dockerfile.gateway \
  --build-arg "MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}" \
  --build-arg "JRE_IMAGE=${JRE_IMAGE:-eclipse-temurin:21-jre-alpine}"

build_one giso-lark-webhook "$ROOT/deploy/lark-webhook" Dockerfile

if [ "${GISO_BUILD_NEWS_DEMO:-0}" = "1" ]; then
  build_one giso-news-demo "$ROOT" examples/web-news-demo/Dockerfile \
    --build-arg "NODE_IMAGE=${NODE_IMAGE:-node:20-alpine}" \
    --build-arg "NGINX_IMAGE=${NGINX_IMAGE:-nginx:alpine}"
fi

echo "[build] done — images tagged ${REGISTRY}/*:${TAG}"
