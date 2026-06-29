#!/usr/bin/env bash
# ============================================================
# 数据对账：网关接收量 vs Doris 落地量（小时级）
#
# 原理：网关在内存维护最近 48h 的 UTC 小时级接收计数
#       （GET /admin/api/hourly），与 Doris ods_events 按 stime
#       小时聚合的行数比对。偏差超过阈值视为链路丢数据。
#
# 用法：crontab 每小时第 10 分钟跑一次（给 Routine Load 留消费余量）：
#       10 * * * * /path/to/reconcile.sh >> /var/log/qy-reconcile.log 2>&1
#
# 退出码：0 = 一致；1 = 偏差超阈值（接告警通道，如 alertmanager webhook / 钉钉）
# ============================================================
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
ADMIN_AUTH="${ADMIN_AUTH:-}"               # 形如 admin:password，未开 Basic Auth 留空
DORIS_HOST="${DORIS_HOST:-127.0.0.1}"
DORIS_PORT="${DORIS_PORT:-9030}"
DORIS_USER="${DORIS_USER:-root}"
DORIS_PASS="${DORIS_PASS:-}"
THRESHOLD_PCT="${THRESHOLD_PCT:-1}"        # 允许偏差百分比
LOOKBACK_HOURS="${LOOKBACK_HOURS:-6}"      # 对账最近 N 个完整小时

auth_flag=()
[ -n "$ADMIN_AUTH" ] && auth_flag=(-u "$ADMIN_AUTH")

hourly_json=$(curl -sf "${auth_flag[@]}" "$GATEWAY/admin/api/hourly")

fail=0
now_hour=$(( $(date +%s) / 3600 ))

for offset in $(seq 1 "$LOOKBACK_HOURS"); do
  h=$(( now_hour - offset ))
  received=$(echo "$hourly_json" | python3 -c "
import json,sys
hours={x['epoch_hour']:x['received'] for x in json.load(sys.stdin)['hours']}
print(hours.get($h,0))")
  [ "$received" -eq 0 ] && continue   # 该小时网关无流量（或网关重启丢内存计数），跳过

  start_utc=$(date -u -r $(( h * 3600 )) '+%Y-%m-%d %H:00:00' 2>/dev/null \
    || date -u -d "@$(( h * 3600 ))" '+%Y-%m-%d %H:00:00')
  end_utc=$(date -u -r $(( (h+1) * 3600 )) '+%Y-%m-%d %H:00:00' 2>/dev/null \
    || date -u -d "@$(( (h+1) * 3600 ))" '+%Y-%m-%d %H:00:00')

  landed=$(mysql -h"$DORIS_HOST" -P"$DORIS_PORT" -u"$DORIS_USER" ${DORIS_PASS:+-p"$DORIS_PASS"} -N -e \
    "SELECT COUNT(*) FROM tracking.ods_events WHERE stime >= '$start_utc' AND stime < '$end_utc'")

  diff=$(( received - landed ))
  pct=$(python3 -c "print(abs($diff)/$received*100 if $received else 0)")
  status=$(python3 -c "print('FAIL' if $pct > $THRESHOLD_PCT else 'OK')")
  echo "$(date -u '+%FT%TZ') hour=$start_utc received=$received landed=$landed diff=$diff pct=${pct}% $status"
  [ "$status" = "FAIL" ] && fail=1
done

exit $fail
