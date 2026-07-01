-- GISO 注册表元库（PostgreSQL，与 GIDO/DataEase 共用 RDS，独立库 giso）
-- schema `giso` 须由 DBA 预建（见 tools/registry/bootstrap_schema.sql）；本脚本仅建表。

CREATE TABLE IF NOT EXISTS giso.registry_entries (
    kind        TEXT NOT NULL CHECK (kind IN ('params', 'pages', 'elements', 'events')),
    entry_key   TEXT NOT NULL,
    body        JSONB NOT NULL,
    status      TEXT NOT NULL DEFAULT 'live'
                CHECK (status IN ('draft', 'dev', 'testing', 'live', 'deprecated')),
    revision    BIGINT NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  TEXT NOT NULL DEFAULT 'system',
    updated_by  TEXT NOT NULL DEFAULT 'system',
    deleted_at  TIMESTAMPTZ,
    PRIMARY KEY (kind, entry_key)
);

CREATE INDEX IF NOT EXISTS idx_registry_kind_status
    ON giso.registry_entries (kind, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_registry_updated
    ON giso.registry_entries (updated_at DESC);

CREATE TABLE IF NOT EXISTS giso.registry_audit (
    id          BIGSERIAL PRIMARY KEY,
    kind        TEXT NOT NULL,
    entry_key   TEXT NOT NULL,
    action      TEXT NOT NULL CHECK (action IN ('create', 'update', 'delete', 'publish', 'deprecate')),
    before_body JSONB,
    after_body  JSONB,
    operator    TEXT NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_entry
    ON giso.registry_audit (kind, entry_key, created_at DESC);

CREATE TABLE IF NOT EXISTS giso.registry_meta (
    key   TEXT PRIMARY KEY,
    value JSONB NOT NULL
);

INSERT INTO giso.registry_meta (key, value) VALUES
    ('global_revision', '{"revision": 0}'::jsonb),
    ('schema_version', '"1.0"'::jsonb),
    ('export_cursor', '{"last_export_revision": 0}'::jsonb)
ON CONFLICT (key) DO NOTHING;
