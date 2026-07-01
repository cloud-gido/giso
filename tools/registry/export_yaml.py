#!/usr/bin/env python3
"""
PostgreSQL 注册表 → schema/*.yaml（供 codegen / Git 审计）。

用法:
  python3 tools/registry/export_yaml.py --out /tmp/schema-export
  python3 tools/registry/export_yaml.py --check   # 与仓库 schema/ diff，有差异退出 1
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "schema"

FILES = {
    "params": ("params.yaml", "params"),
    "pages": ("pages.yaml", "pages"),
    "elements": ("elements.yaml", "elements"),
    "events": ("biz_events.yaml", "events"),
}


def dsn_from_env() -> str:
    url = os.environ.get("GISO_DB_URL", "").strip()
    if url.startswith("jdbc:postgresql://"):
        url = url.replace("jdbc:postgresql://", "postgresql://", 1)
    if url:
        return url
    host = os.environ["GISO_DB_HOST"]
    port = os.environ.get("GISO_DB_PORT", "5432")
    name = os.environ.get("GISO_DB_NAME", "giso")
    user = os.environ["GISO_DB_USER"]
    password = os.environ["GISO_DB_PASSWORD"]
    return f"postgresql://{user}:{password}@{host}:{port}/{name}"


def fetch_entries(conn) -> dict[str, list[dict]]:
    reg = {k: [] for k in FILES}
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT kind, body::text FROM giso.registry_entries
            WHERE deleted_at IS NULL
            ORDER BY kind, entry_key
            """
        )
        for kind, body_text in cur.fetchall():
            reg[kind].append(json.loads(body_text))
    return reg


def write_yaml(reg: dict[str, list[dict]], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for kind, (fname, root) in FILES.items():
        doc = {"version": "1.0", root: reg[kind]}
        header = f"# {fname} — exported from PostgreSQL giso.registry_entries\n"
        text = header + yaml.dump(doc, allow_unicode=True, sort_keys=False, default_flow_style=False)
        (out_dir / fname).write_text(text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn")
    parser.add_argument("--out", type=Path, default=SCHEMA)
    parser.add_argument("--check", action="store_true", help="compare export with repo schema/")
    args = parser.parse_args()

    try:
        import psycopg2
    except ImportError:
        print("pip install psycopg2-binary", file=sys.stderr)
        return 1

    dsn = args.dsn or dsn_from_env()
    conn = psycopg2.connect(dsn)
    try:
        reg = fetch_entries(conn)
    finally:
        conn.close()

    if args.check:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            write_yaml(reg, tmp_path)
            diff = 0
            for kind, (fname, _) in FILES.items():
                a = (tmp_path / fname).read_text(encoding="utf-8")
                b = (SCHEMA / fname).read_text(encoding="utf-8")
                if a != b:
                    print(f"diff: {fname} ({len(reg[kind])} entries in DB)")
                    diff += 1
            return 1 if diff else 0

    write_yaml(reg, args.out)
    total = sum(len(v) for v in reg.values())
    print(f"exported {total} entries → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
