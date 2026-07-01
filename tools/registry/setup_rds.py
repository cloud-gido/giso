#!/usr/bin/env python3
"""
在共用 RDS 上创建 giso 库、用户、表结构，并导入 schema/*.yaml。

阶段 1（需 RDS 主账号，一次性）:
  export RDS_MASTER_HOST=dev-db.proxy.sa-east-1.rds.amazonaws.com
  export RDS_MASTER_USER=postgres
  export RDS_MASTER_PASSWORD=***
  export GISO_DB_PASSWORD='强密码'
  python3 tools/registry/setup_rds.py --create-db

阶段 2（giso_app 账号，可重复）:
  export GISO_DB_HOST=$RDS_MASTER_HOST
  export GISO_DB_USER=giso_app
  export GISO_DB_PASSWORD=...
  python3 tools/registry/setup_rds.py --migrate --import

环境变量也可与 Doppler 对齐：GISO_DB_HOST / PORT / NAME / USER / PASSWORD
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "server/gateway/src/main/resources/db/V1__registry.sql"


def pg_connect(dsn: str):
    import psycopg2
    return psycopg2.connect(dsn)


def master_dsn() -> str:
    host = os.environ.get("RDS_MASTER_HOST") or os.environ.get("GISO_DB_HOST")
    user = os.environ.get("RDS_MASTER_USER", "postgres")
    password = os.environ["RDS_MASTER_PASSWORD"]
    port = os.environ.get("RDS_MASTER_PORT", os.environ.get("GISO_DB_PORT", "5432"))
    return f"postgresql://{user}:{password}@{host}:{port}/postgres"


def app_dsn() -> str:
    host = os.environ["GISO_DB_HOST"]
    port = os.environ.get("GISO_DB_PORT", "5432")
    name = os.environ.get("GISO_DB_NAME", "giso")
    user = os.environ["GISO_DB_USER"]
    password = os.environ["GISO_DB_PASSWORD"]
    return f"postgresql://{user}:{password}@{host}:{port}/{name}"


def create_db() -> int:
    app_password = os.environ.get("GISO_DB_PASSWORD")
    if not app_password:
        print("GISO_DB_PASSWORD required", file=sys.stderr)
        return 1
    app_user = os.environ.get("GISO_DB_USER", "giso_app")
    db_name = os.environ.get("GISO_DB_NAME", "giso")

    conn = pg_connect(master_dsn())
    conn.autocommit = True
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT 1 FROM pg_database WHERE datname = %s", (db_name,))
            if cur.fetchone():
                print(f"database {db_name} already exists")
            else:
                cur.execute(f'CREATE DATABASE "{db_name}"')
                print(f"created database {db_name}")

            cur.execute("SELECT 1 FROM pg_roles WHERE rolname = %s", (app_user,))
            if cur.fetchone():
                cur.execute(f'ALTER USER "{app_user}" WITH PASSWORD %s', (app_password,))
                print(f"updated password for {app_user}")
            else:
                cur.execute(f'CREATE USER "{app_user}" WITH PASSWORD %s', (app_password,))
                print(f"created user {app_user}")

            cur.execute(f'GRANT ALL PRIVILEGES ON DATABASE "{db_name}" TO "{app_user}"')
    finally:
        conn.close()
    print("database and user ok — run --migrate next")
    return 0


def grant_schema() -> int:
    app_user = os.environ.get("GISO_DB_USER", "giso_app")
    conn = pg_connect(app_dsn())
    conn.autocommit = True
    try:
        with conn.cursor() as cur:
            cur.execute(f'GRANT ALL ON SCHEMA giso TO "{app_user}"')
            cur.execute(f'GRANT ALL ON ALL TABLES IN SCHEMA giso TO "{app_user}"')
            cur.execute(f'GRANT ALL ON ALL SEQUENCES IN SCHEMA giso TO "{app_user}"')
            cur.execute(
                f'ALTER DEFAULT PRIVILEGES IN SCHEMA giso GRANT ALL ON TABLES TO "{app_user}"'
            )
    finally:
        conn.close()
    print("schema grants ok")
    return 0


def migrate() -> int:
    sql = MIGRATION.read_text(encoding="utf-8")
    conn = pg_connect(app_dsn())
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
        conn.commit()
        print("migration ok")
    finally:
        conn.close()
    grant_schema()
    return 0


def main() -> int:
    try:
        import psycopg2  # noqa: F401
    except ImportError:
        print("pip install -r tools/registry/requirements.txt", file=sys.stderr)
        return 1

    parser = argparse.ArgumentParser()
    parser.add_argument("--create-db", action="store_true")
    parser.add_argument("--migrate", action="store_true")
    parser.add_argument("--import", dest="do_import", action="store_true")
    args = parser.parse_args()

    if not any([args.create_db, args.migrate, args.do_import]):
        parser.print_help()
        return 1

    if args.create_db:
        rc = create_db()
        if rc != 0:
            return rc

    if args.migrate:
        rc = migrate()
        if rc != 0:
            return rc

    if args.do_import:
        sys.path.insert(0, str(ROOT / "tools/registry"))
        from import_yaml import import_registry
        return import_registry("setup_rds")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
