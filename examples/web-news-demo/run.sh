#!/usr/bin/env bash
# 启动资讯 Web Demo：本机有 npm 则 dev；否则用 Docker（无需安装 Node）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$(dirname "$0")"

if command -v npm >/dev/null 2>&1; then
  echo "[news-demo] npm dev → http://localhost:5180"
  npm install
  npm run dev
  exit 0
fi

echo "[news-demo] 未检测到 npm，使用 Docker 构建并启动…"
docker compose -f "$ROOT/deploy/docker-compose.yml" --profile demo up -d news-web-demo --build
echo "[news-demo] 已启动 → http://localhost:5180"
echo "[news-demo] 管理台联调 → http://localhost:8123/admin/"
echo "[news-demo] 停止: docker compose -f deploy/docker-compose.yml --profile demo stop news-web-demo"
