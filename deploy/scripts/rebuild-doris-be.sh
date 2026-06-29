#!/usr/bin/env bash
# Apple Silicon：拉取 arm64 镜像，修复 FE/BE（避免 amd64+QEMU / 构建 EOF / FE unhealthy 阻塞 BE）
set -euo pipefail
cd "$(dirname "$0")/.."

ARCH=$(uname -m)
if [[ "$ARCH" == "arm64" || "$ARCH" == "aarch64" ]]; then
  PLATFORM=linux/arm64
else
  PLATFORM=linux/amd64
fi
export DOCKER_DEFAULT_PLATFORM="$PLATFORM"
echo "[rebuild-be] host=$ARCH platform=$PLATFORM"

pull_retry() {
  local img=$1
  for n in 1 2 3 4 5; do
    echo "[rebuild-be] pull $img ($n/5)..."
    if docker pull --platform "$PLATFORM" "$img"; then
      return 0
    fi
    echo "[rebuild-be] pull failed, retry in 10s..."
    sleep 10
  done
  echo "[rebuild-be] ERROR: cannot pull $img" >&2
  return 1
}

fe_healthy() {
  curl -sf http://localhost:8030/api/health >/dev/null 2>&1
}

wait_fe() {
  echo "[rebuild-be] wait FE healthy..."
  for i in $(seq 1 60); do
    if fe_healthy; then
      echo "[rebuild-be] FE healthy"
      return 0
    fi
    if (( i % 6 == 0 )); then
      echo "[rebuild-be] FE waiting ($i/60)..."
    fi
    sleep 5
  done
  echo "[rebuild-be] ERROR: FE still unhealthy — docker logs qy-doris-fe" >&2
  return 1
}

docker compose --profile doris stop doris-init doris-be 2>/dev/null || true

pull_retry apache/doris:fe-ubuntu-2.1.7
pull_retry apache/doris:be-ubuntu-2.1.7

if ! fe_healthy; then
  echo "[rebuild-be] FE unhealthy — reset FE+BE meta volumes (local demo only)..."
  docker compose --profile doris stop doris-fe doris-be 2>/dev/null || true
  docker rm -f qy-doris-fe qy-doris-be 2>/dev/null || true
  docker volume rm deploy_doris-fe-data deploy_doris-be-data 2>/dev/null || true
  docker compose --profile doris up -d --no-deps --force-recreate doris-fe
  wait_fe
  NEED_FRESH_BE=1
else
  NEED_FRESH_BE=0
fi

echo "[rebuild-be] drop local BE image..."
docker rmi deploy-doris-be:latest 2>/dev/null || true

echo "[rebuild-be] build BE..."
docker compose --profile doris build --no-cache doris-be

echo "[rebuild-be] recreate BE (--no-deps, won't block on FE check)..."
docker rm -f qy-doris-be 2>/dev/null || true
if [[ "${NEED_FRESH_BE:-0}" == "1" ]]; then
  docker volume rm deploy_doris-be-data 2>/dev/null || true
fi
docker compose --profile doris up -d --no-deps --force-recreate doris-be

sleep 8
echo "[rebuild-be] process (arm64 should NOT show [qemu]):"
docker exec qy-doris-be ps aux 2>/dev/null | grep doris_be | grep -v grep || true

echo "[rebuild-be] register BE if needed..."
BE_IP=172.28.0.3
docker run --rm mysql:8.4 mysql -h host.docker.internal -P 9030 -uroot \
  -e "ALTER SYSTEM ADD BACKEND \"${BE_IP}:9050\";" 2>/dev/null || true

# FE 重置后 BE 卷未清会导致 cluster id 冲突
if docker run --rm mysql:8.4 mysql -h host.docker.internal -P 9030 -uroot \
  -e "SHOW BACKENDS\G" 2>/dev/null | grep -q "invalid cluster id"; then
  echo "[rebuild-be] cluster id mismatch — reset BE volume and recreate..."
  docker compose --profile doris stop doris-be 2>/dev/null || true
  docker rm -f qy-doris-be 2>/dev/null || true
  docker volume rm deploy_doris-be-data 2>/dev/null || true
  docker compose --profile doris up -d --no-deps --force-recreate doris-be
  sleep 15
  docker run --rm mysql:8.4 mysql -h host.docker.internal -P 9030 -uroot \
    -e "ALTER SYSTEM ADD BACKEND \"${BE_IP}:9050\";" 2>/dev/null || true
fi

echo "[rebuild-be] wait BE Alive (up to 5min)..."
for i in $(seq 1 60); do
  alive=$(docker run --rm mysql:8.4 mysql -h host.docker.internal -P 9030 -uroot \
    -e "SHOW BACKENDS\G" 2>/dev/null | grep -c "Alive: true" || true)
  if [[ "$alive" -ge 1 ]]; then
    echo "[rebuild-be] BE alive — next: ./scripts/run-doris-init.sh"
    exit 0
  fi
  if (( i % 6 == 0 )); then
    echo "[rebuild-be] waiting ($i/60)..."
  fi
  sleep 5
done
echo "[rebuild-be] BE not alive yet — docker logs qy-doris-be" >&2
exit 1
