-- 管理台账号（与 GIDO 应用内登录对应层；GISO 使用 HTTP Basic + 本表持久化）
CREATE TABLE IF NOT EXISTS ${schema}.admin_users (
    username      TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('admin', 'viewer')),
    display_name  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_admin_users_role
    ON ${schema}.admin_users (role) WHERE disabled_at IS NULL;
