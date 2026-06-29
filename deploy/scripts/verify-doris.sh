#!/usr/bin/env bash
# 验证 Kafka → Doris Routine Load 链路
set -euo pipefail
GATEWAY="${GATEWAY:-http://localhost:8123}"
DORIS_HOST="${DORIS_HOST:-host.docker.internal}"
DORIS_PORT="${DORIS_PORT:-9030}"
APP_KEY="${APP_KEY:-demo-key}"
DID="doris-$(date +%s)"
LOG_ID="${DID}-1"

mysql_doris() {
  docker run --rm mysql:8.4 mysql -h "$DORIS_HOST" -P "$DORIS_PORT" -uroot "$@"
}

curl -sf -X POST "$GATEWAY/v1/track" \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -d "[{\"event\":\"app_launch\",\"log_id\":\"$LOG_ID\",\"ctime\":$(date +%s)000,\"common\":{\"app_id\":\"demo-key\",\"platform\":\"web\",\"did\":\"$DID\",\"env\":\"prod\"}}]" >/dev/null
echo "[doris-seed] posted event did=$DID"

for i in $(seq 1 30); do
  n=$(mysql_doris -N -e "SELECT count(*) FROM tracking.ods_events WHERE did='${DID}'" 2>/dev/null || echo 0)
  if [[ "$n" -ge 1 ]]; then
    echo "[doris-seed] ok rows=$n"
    mysql_doris -e "SELECT event, did, pgid, biz_code FROM tracking.ods_events WHERE did='${DID}' LIMIT 5"
    exit 0
  fi
  echo "[doris-seed] waiting ($i/30)..."
  sleep 3
done
echo "[doris-seed] timeout (routine load may be PAUSED — check: SHOW ROUTINE LOAD FOR tracking.load_ods_events)" >&2
exit 1
