#!/usr/bin/env bash
# 推送到 origin / gitlab / gitee，并通知测试 Lark（不触正式群）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env.lark.local" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/.env.lark.local"
fi

echo "→ push origin main"
git push origin main
echo "→ push gitlab main"
git push gitlab main
echo "→ push gitee main"
git push gitee main

COMMIT="$(git rev-parse HEAD)"
MSG="$(git log -1 --format=%s)"

if [[ -n "${LARK_WEBHOOK_URL_TEST:-}" ]]; then
  echo "→ Lark [测试]"
  python3 "$ROOT/scripts/lark-notify.py" --mode push --channel test \
    --commit "$COMMIT" --message "$MSG" --webhook "$LARK_WEBHOOK_URL_TEST"
else
  echo "skip Lark: set LARK_WEBHOOK_URL_TEST in .env.lark.local"
fi

echo "done."
