-- 管理台登录会话（opaque id，支持多副本网关与可靠退出）
CREATE TABLE IF NOT EXISTS admin_sessions (
    session_id   VARCHAR(64) PRIMARY KEY,
    username     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_username ON admin_sessions (username);
