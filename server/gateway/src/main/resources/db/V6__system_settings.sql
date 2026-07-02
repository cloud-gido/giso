-- 平台系统设置（Copilot LLM、出口管道选择等；对齐 GIDO 系统设置）
CREATE TABLE IF NOT EXISTS ${schema}.system_settings (
    setting_key   TEXT PRIMARY KEY,
    setting_value JSONB NOT NULL DEFAULT '{}',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    TEXT
);
