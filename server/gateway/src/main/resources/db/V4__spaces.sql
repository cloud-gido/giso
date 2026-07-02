-- 空间（公司级多租户）+ 成员 + App Key 绑定

CREATE TABLE IF NOT EXISTS ${schema}.spaces (
    space_key     TEXT PRIMARY KEY,
    display_name  TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active', 'disabled')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ${schema}.space_members (
    username   TEXT NOT NULL,
    space_key  TEXT NOT NULL REFERENCES ${schema}.spaces (space_key),
    role       TEXT NOT NULL CHECK (role IN ('space_admin', 'editor', 'viewer')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (username, space_key)
);

CREATE INDEX IF NOT EXISTS idx_space_members_space
    ON ${schema}.space_members (space_key, role);

CREATE TABLE IF NOT EXISTS ${schema}.space_app_keys (
    app_key    TEXT PRIMARY KEY,
    space_key  TEXT NOT NULL REFERENCES ${schema}.spaces (space_key)
);

INSERT INTO ${schema}.spaces (space_key, display_name) VALUES
    ('default', '默认空间'),
    ('longvideo', '长视频'),
    ('sports', '体育')
ON CONFLICT (space_key) DO NOTHING;
