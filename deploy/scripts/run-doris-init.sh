#!/usr/bin/env bash
# 仅重建/运行 doris-init，不触发 doris-be 的 build 或 recreate。
# 用法：等 BE Alive 后执行 ./scripts/run-doris-init.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[doris-init] build init image only (BE untouched)..."
docker build -t deploy-doris-init:latest -f doris/Dockerfile doris/

docker rm -f qy-doris-init 2>/dev/null || true

echo "[doris-init] starting (--no-deps, won't recreate FE/BE/Kafka)..."
docker compose --profile doris up -d --no-deps --force-recreate doris-init

echo "[doris-init] tailing logs (Ctrl+C to detach, init keeps running)..."
docker logs -f qy-doris-init
