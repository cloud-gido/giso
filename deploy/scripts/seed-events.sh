#!/usr/bin/env bash
# 向本地栈灌入演示事件，并轮询 ClickHouse 直到落地（验证 Kafka → event-bridge → CH 全链路）
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8123}"
CH="${CH:-http://localhost:8124}"
APP_KEY="${APP_KEY:-demo-key}"
DID="seed-$(date +%s)"

echo "[seed] gateway=$GATEWAY clickhouse=$CH did=$DID"

payload=$(cat <<EOF
[
  {"event":"app_launch","log_id":"$DID-1","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod","app_vrsn":"1.0.0-seed"}},
  {"event":"page_enter","log_id":"$DID-2","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_feed"}},
  {"event":"element_exposure","log_id":"$DID-3","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_feed"},"element":{"eid":"video_card","mod":"feed","pos":1,"exp_dur":800,"exp_ratio":0.6}},
  {"event":"element_click","log_id":"$DID-4","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_feed"},"element":{"eid":"video_card","mod":"feed","pos":1}},
  {"event":"page_enter","log_id":"$DID-5","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_detail","ref_pgid":"video_feed","ref_eid":"video_card","pg_params":{"vid":"v-demo-001","series_id":"s-demo-001","ep_num":1}}},
  {"event":"biz_event","log_id":"$DID-6","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_detail","pg_params":{"vid":"v-demo-001","series_id":"s-demo-001","ep_num":1}},"biz":{"code":"video_play_start","params":{"vid":"v-demo-001","series_id":"s-demo-001","ep_num":1,"is_auto":0,"definition":"hd"}}},
  {"event":"biz_event","log_id":"$DID-7","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_detail","pg_params":{"vid":"v-demo-001","series_id":"s-demo-001","ep_num":1}},"biz":{"code":"video_play_end","params":{"vid":"v-demo-001","play_dur":42,"play_pos":42,"video_dur":120}}},
  {"event":"page_exit","log_id":"$DID-8","ctime":$(date +%s)000,"common":{"app_id":"demo-key","platform":"android","did":"$DID","env":"prod"},"page":{"pgid":"video_detail","pg_stay":45000}}
]
EOF
)

curl -sf -X POST "$GATEWAY/v1/track" \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -d "$payload" >/dev/null
echo "[seed] posted 8 events"

for i in $(seq 1 30); do
  n=$(curl -sf "$CH/?query=SELECT%20count()%20FROM%20tracking.ods_events%20WHERE%20did%3D%27$DID%27" || echo 0)
  if [[ "$n" -ge 8 ]]; then
    echo "[seed] clickhouse rows for $DID: $n (ok)"
    echo "[seed] sample:"
    curl -sf "$CH/?query=SELECT%20event%2Cpgid%2Ceid%2Cbiz_code%20FROM%20tracking.ods_events%20WHERE%20did%3D%27$DID%27%20ORDER%20BY%20stime%20FORMAT%20PrettyCompact"
    exit 0
  fi
  echo "[seed] waiting for clickhouse ($i/30, got $n)..."
  sleep 2
done

echo "[seed] timeout: only $n/8 in ods_events" >&2
q=$(curl -sf "$CH/?query=SELECT%20count()%20FROM%20tracking.ods_events_quarantine%20WHERE%20did%3D%27$DID%27" || echo 0)
if [[ "$q" -gt 0 ]]; then
  echo "[seed] hint: $q event(s) in quarantine (schema validation failed) — check tracking.ods_events_quarantine" >&2
fi
exit 1
