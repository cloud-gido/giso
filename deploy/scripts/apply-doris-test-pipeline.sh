#!/usr/bin/env bash
# 已有 Doris 集群：补 env 列 + prod/test Routine Load（无需清卷）
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE_PROJECT="${COMPOSE_PROJECT:-deploy}"
DORIS_NETWORK="${DORIS_NETWORK:-${COMPOSE_PROJECT}_default}"
DORIS_HOST="${DORIS_HOST:-doris-fe}"
DORIS_PORT="${DORIS_PORT:-9030}"

mysql_doris() {
  docker run --rm -i --network "$DORIS_NETWORK" mysql:8.4 \
    mysql -h "$DORIS_HOST" -P "$DORIS_PORT" -uroot "$@"
}

routine_exists() {
  docker run --rm --network "$DORIS_NETWORK" mysql:8.4 \
    mysql -h "$DORIS_HOST" -P "$DORIS_PORT" -uroot tracking -N -e \
    "SHOW ROUTINE LOAD;" 2>/dev/null | grep -q "$1"
}

echo "[doris-test-pipeline] network=$DORIS_NETWORK host=$DORIS_HOST"

echo "[doris-test-pipeline] add env column..."
mysql_doris < doris/03_alter_add_env.sql 2>/dev/null || echo "  (column may already exist)"

if routine_exists load_ods_events_test; then
  echo "[doris-test-pipeline] load_ods_events_test already exists"
else
  if routine_exists load_ods_events; then
    echo "[doris-test-pipeline] stop old prod load (recreate with env jsonpaths)..."
    mysql_doris -e "STOP ROUTINE LOAD FOR tracking.load_ods_events" 2>/dev/null || true
    sleep 3
  fi
  echo "[doris-test-pipeline] create prod + test routine loads..."
  mysql_doris < doris/02_routine_load_events.docker.sql
fi

echo "[doris-test-pipeline] status:"
docker run --rm --network "$DORIS_NETWORK" mysql:8.4 \
  mysql -h "$DORIS_HOST" -P "$DORIS_PORT" -uroot tracking -e \
  "SHOW ROUTINE LOAD;" 2>/dev/null | awk -F'\t' 'NR==1 || $2 ~ /load_ods_events/'
echo "[doris-test-pipeline] verify: SELECT env, platform, count(*) FROM tracking.ods_events GROUP BY env, platform;"
