#!/usr/bin/env bash
# ============================================================
# 数据对账：网关接收量 vs Doris 落地量（小时级，可按 space_key 过滤）
#
# 环境变量：
#   GATEWAY          网关地址（默认 http://localhost:8080）
#   SPACE            空间 key（默认 default），同时传 X-GISO-Space 与 Doris space_key 过滤
#   ADMIN_AUTH       Basic Auth，形如 admin:password
#   DORIS_HOST/PORT/USER/PASS
#   THRESHOLD_PCT    允许偏差百分比（默认 1）
#   LOOKBACK_HOURS   对账最近 N 个完整小时（默认 6）
# ============================================================
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
SPACE="${SPACE:-default}"
ADMIN_AUTH="${ADMIN_AUTH:-}"
DORIS_HOST="${DORIS_HOST:-127.0.0.1}"
DORIS_PORT="${DORIS_PORT:-9030}"
DORIS_USER="${DORIS_USER:-root}"
DORIS_PASS="${DORIS_PASS:-}"
THRESHOLD_PCT="${THRESHOLD_PCT:-1}"
LOOKBACK_HOURS="${LOOKBACK_HOURS:-6}"

auth_flag=()
[ -n "$ADMIN_AUTH" ] && auth_flag=(-u "$ADMIN_AUTH")

hourly_json=$(curl -sf "${auth_flag[@]}" -H "X-GISO-Space: $SPACE" "$GATEWAY/admin/api/hourly")

fail=0
now_hour=$(( $(date +%s) / 3600 ))

for offset in $(seq 1 "$LOOKBACK_HOURS"); do
  h=$(( now_hour - offset ))
  received=$(echo "$hourly_json" | python3 -c "
import json,sys
hours={x['epoch_hour']:x['received'] for x in json.load(sys.stdin)['hours']}
print(hours.get($h,0))")
  [ "$received" -eq 0 ] && continue

  start_utc=$(date -u -r $(( h * 3600 )) '+%Y-%m-%d %H:00:00' 2>/dev/null \
    || date -u -d "@$(( h * 3600 ))" '+%Y-%m-%d %H:00:00')
  end_utc=$(date -u -r $(( (h+1) * 3600 )) '+%Y-%m-%d %H:00:00' 2>/dev/null \
    || date -u -d "@$(( (h+1) * 3600 ))" '+%Y-%m-%d %H:00:00')

  landed=$(mysql -h"$DORIS_HOST" -P"$DORIS_PORT" -u"$DORIS_USER" ${DORIS_PASS:+-p"$DORIS_PASS"} -N -e \
    "SELECT COUNT(*) FROM tracking.ods_events WHERE stime >= '$start_utc' AND stime < '$end_utc' AND space_key = '$SPACE'")

  diff=$(( received - landed ))
  pct=$(python3 -c "print(abs($diff)/$received*100 if $received else 0)")
  status=$(python3 -c "print('FAIL' if $pct > $THRESHOLD_PCT else 'OK')")
  echo "$(date -u '+%FT%TZ') space=$SPACE hour=$start_utc received=$received landed=$landed diff=$diff pct=${pct}% $status"
  [ "$status" = "FAIL" ] && fail=1
done

exit $fail
