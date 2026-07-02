#!/usr/bin/env python3
"""
登记覆盖率周报：对比管理台 /coverage 与 registry 全量 live 条目。

用法:
  export GISO_ADMIN_URL=http://localhost:8123
  export GISO_ADMIN_USER=admin GISO_ADMIN_PASSWORD=admin123
  export GISO_SPACE=longvideo
  python3 server/ops/coverage_report.py
"""
from __future__ import annotations

import base64
import json
import os
import sys
import urllib.request


def get(path: str, space: str) -> dict:
    url = os.environ["GISO_ADMIN_URL"].rstrip("/") + "/admin/api" + path
    user = os.environ.get("GISO_ADMIN_USER", "admin")
    pwd = os.environ.get("GISO_ADMIN_PASSWORD", "admin123")
    auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
    req = urllib.request.Request(url, headers={
        "Authorization": f"Basic {auth}",
        "X-GISO-Space": space,
    })
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def main() -> int:
    space = os.environ.get("GISO_SPACE", "longvideo")
    cov = get("/coverage?env=prod", space)
    summary = cov.get("summary", {})
    print(f"# GISO 覆盖率周报 · space={space}\n")
    print(f"- 页面 live: {summary.get('pages_live', '?')} · 未覆盖: {summary.get('pages_missing', '?')}")
    print(f"- 元素 live: {summary.get('elements_live', '?')} · 未覆盖: {summary.get('elements_missing', '?')}")
    print(f"- 业务事件 live: {summary.get('events_live', '?')} · 未覆盖: {summary.get('events_missing', '?')}")
    for key in ("pages_missing", "elements_missing", "events_missing"):
        rows = cov.get(key, [])
        if not rows:
            continue
        print(f"\n## {key}")
        for row in rows[:20]:
            print(f"- {row}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
