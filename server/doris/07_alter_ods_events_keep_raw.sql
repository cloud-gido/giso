-- ============================================================
-- 已有 ods_events：补 fg_dur / 嵌套整包 / raw，避免未拉平字段丢失
-- 执行后需 STOP 旧 Routine Load，按 02_routine_load*.sql 用新 group 重建
-- ============================================================
USE tracking;

ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS fg_dur BIGINT COMMENT '前台时长ms（仅app_background）';
ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS page_ext JSON COMMENT '完整 page 对象';
ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS element_ext JSON COMMENT '完整 element 对象';
ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS biz_ext JSON COMMENT '完整 biz 对象';
ALTER TABLE ods_events ADD COLUMN IF NOT EXISTS raw STRING COMMENT '完整 Kafka JSON 原文';

-- 验证
-- DESC ods_events;
-- 活跃时长：SELECT event_date, did, SUM(fg_dur) FROM ods_events WHERE event='app_background' GROUP BY 1,2;
-- 未拉平兜底：SELECT get_json_string(raw, '$.page.fg_dur') FROM ods_events WHERE event='app_background' LIMIT 5;
