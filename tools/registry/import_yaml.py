#!/usr/bin/env python3
"""
将 schema/*.yaml 导入 PostgreSQL giso.registry_entries（首次迁移或灾备恢复）。

用法:
  export GISO_DB_HOST=dev-db.proxy.sa-east-1.rds.amazonaws.com
  export GISO_DB_PORT=5432
  export GISO_DB_NAME=giso
  export GISO_DB_USER=giso_app
  export GISO_DB_PASSWORD=***
  python3 tools/registry/import_yaml.py

  python3 tools/registry/import_yaml.py --dsn 'postgresql://giso_app:pass@host:5432/giso'
  python3 tools/registry/import_yaml.py --dry-run
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "schema"

FILES = {
    "params": ("params.yaml", "params", "key"),
    "pages": ("pages.yaml", "pages", "pgid"),
    "elements": ("elements.yaml", "elements", "eid"),
    "events": ("biz_events.yaml", "events", "code"),
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


def load_registry() -> dict[str, list[dict]]:
    reg: dict[str, list[dict]] = {}
    for kind, (fname, root, _) in FILES.items():
        doc = yaml.safe_load((SCHEMA / fname).read_text(encoding="utf-8"))
        reg[kind] = doc[root]
    return reg


def import_registry(operator: str = "import_yaml") -> int:
    reg = load_registry()
    total = sum(len(v) for v in reg.values())
    print(f"loaded {total} entries from {SCHEMA}")

    import psycopg2

    conn = psycopg2.connect(dsn_from_env())
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            cur.execute(open(ROOT / "server/gateway/src/main/resources/db/V1__registry.sql").read())
            for kind, items in reg.items():
                id_field = FILES[kind][2]
                for item in items:
                    key = item[id_field]
                    status = item.get("status") or "live"
                    body = json.dumps(item, ensure_ascii=False)
                    cur.execute(
                        """
                        INSERT INTO giso.registry_entries
                            (kind, entry_key, body, status, revision, created_by, updated_by)
                        VALUES (%s, %s, %s::jsonb, %s, 1, %s, %s)
                        ON CONFLICT (kind, entry_key) DO UPDATE SET
                            body = EXCLUDED.body,
                            status = EXCLUDED.status,
                            revision = giso.registry_entries.revision + 1,
                            updated_at = now(),
                            updated_by = EXCLUDED.updated_by,
                            deleted_at = NULL
                        """,
                        (kind, key, body, status, operator, operator),
                    )
            cur.execute(
                """
                UPDATE giso.registry_meta
                SET value = jsonb_set(value, '{revision}',
                    to_jsonb((SELECT COUNT(*) FROM giso.registry_entries WHERE deleted_at IS NULL)))
                WHERE key = 'global_revision'
                """
            )
        conn.commit()
        print("import ok")
        return 0
    except Exception as e:
        conn.rollback()
        print(f"import failed: {e}", file=sys.stderr)
        return 1
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Import schema YAML into PostgreSQL registry")
    parser.add_argument("--dsn", help="postgresql://user:pass@host:5432/giso")
    parser.add_argument("--operator", default="import_yaml")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    try:
        import psycopg2
        import psycopg2.extras
    except ImportError:
        print("pip install psycopg2-binary", file=sys.stderr)
        return 1

    dsn = args.dsn or dsn_from_env()
    reg = load_registry()
    total = sum(len(v) for v in reg.values())
    print(f"loaded {total} entries from {SCHEMA}")

    if args.dry_run:
        for kind, items in reg.items():
            print(f"  {kind}: {len(items)}")
        return 0

    return import_registry(args.operator)


if __name__ == "__main__":
    raise SystemExit(main())
