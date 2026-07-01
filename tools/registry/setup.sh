#!/usr/bin/env bash
# 在共用 RDS 上初始化 giso 库（需 RDS 主账号 + 网络可达）。
# 用法见 tools/registry/setup_rds.py 文档。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
pip3 install -q -r tools/registry/requirements.txt
python3 tools/registry/setup_rds.py "$@"
