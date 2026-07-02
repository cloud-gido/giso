-- 将 default 空间注册表复制到 longvideo（video-* App Key 按前缀归属 longvideo 空间）
-- 非版本化脚本：由网关在 V5 迁移 + YAML bootstrap 之后幂等执行
INSERT INTO ${schema}.registry_entries
    (space_key, kind, entry_key, body, status, revision, created_by, updated_by)
SELECT 'longvideo', kind, entry_key, body, status, revision, created_by, updated_by
FROM ${schema}.registry_entries
WHERE space_key = 'default' AND deleted_at IS NULL
ON CONFLICT (space_key, kind, entry_key) DO NOTHING;
