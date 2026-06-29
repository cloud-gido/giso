#!/usr/bin/env bash
# 补建 / 修复 Metabase 看板（CH + Doris）
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose build metabase-setup
docker compose run --rm metabase-setup
