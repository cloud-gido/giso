#!/usr/bin/env bash
# 重置本地 Doris（清 FE/BE 数据卷，解决 BE 注册死循环）
set -euo pipefail
cd "$(dirname "$0")/.."
echo "[reset-doris] stopping..."
docker compose --profile doris down 2>/dev/null || true
docker rm -f qy-doris-be qy-doris-fe qy-doris-init 2>/dev/null || true
echo "[reset-doris] removing volumes..."
docker volume rm deploy_doris-fe-data deploy_doris-be-data 2>/dev/null || true
echo "[reset-doris] starting fresh cluster..."
docker run --rm --privileged --pid=host alpine sysctl -w vm.max_map_count=2000000 2>/dev/null || true
docker compose --profile doris up -d doris-fe
echo "[reset-doris] waiting FE..."
sleep 30
"$(dirname "$0")/rebuild-doris-be.sh" || {
  echo "[reset-doris] rebuild-doris-be failed; fix network/pull then re-run rebuild-doris-be.sh" >&2
  exit 1
}
echo "[reset-doris] starting init (won't recreate BE)..."
docker build -t deploy-doris-init:latest -f doris/Dockerfile doris/
docker rm -f qy-doris-init 2>/dev/null || true
docker compose --profile doris up -d --no-deps --force-recreate doris-init
sleep 5
docker logs qy-doris-init 2>&1 | tail -15
echo "[reset-doris] follow: docker logs -f qy-doris-init"
docker ps -a | grep doris
