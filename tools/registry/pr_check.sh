#!/usr/bin/env bash
# CI / 本地：对比 PG 注册表导出与 Git schema/ 是否一致（有差异退出 1）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
python3 tools/registry/export_yaml.py --check "$@"
