-- ============================================================
-- GISO · MSK (SASL_SSL :9096) → Doris Routine Load
-- 用法：复制为本地文件，替换 <...> 占位符后在 FE 执行
--   mysql -h <doris-fe> -P9030 -uroot < 02_routine_load_msk.sql
-- ============================================================

-- 若已建过旧任务，先停掉（可选）
-- STOP ROUTINE LOAD FOR tracking.load_ods_events;
-- STOP ROUTINE LOAD FOR tracking.load_ods_events_test;
-- STOP ROUTINE LOAD FOR tracking.load_ods_quarantine;

USE tracking;

-- ── 生产流：giso_events_raw ─────────────────────────────────
CREATE ROUTINE LOAD tracking.load_ods_events ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env, space_key,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params, fg_dur,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    page_ext, element_ext, biz_ext, raw,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'prod')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",\"$.common.space\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",\"$.page.fg_dur\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\",
        \"$.page\",\"$.element\",\"$.biz\",\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "<b-1.host:9096,b-2.host:9096,b-3.host:9096>",
    "kafka_topic" = "giso_events_raw",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_events_v2",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

-- ── 测试流：giso_events_raw_test ─────────────────────────────
CREATE ROUTINE LOAD tracking.load_ods_events_test ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env, space_key,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params, fg_dur,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    page_ext, element_ext, biz_ext, raw,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'test')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",\"$.common.space\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",\"$.page.fg_dur\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\",
        \"$.page\",\"$.element\",\"$.biz\",\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "<b-1.host:9096,b-2.host:9096,b-3.host:9096>",
    "kafka_topic" = "giso_events_raw_test",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_events_test_v2",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

-- ── 隔离区：giso_events_quarantine ───────────────────────────
CREATE ROUTINE LOAD tracking.load_ods_quarantine ON ods_events_quarantine
COLUMNS(
    raw,
    stime = now(),
    event_date = curdate()
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "10000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "<b-1.host:9096,b-2.host:9096,b-3.host:9096>",
    "kafka_topic" = "giso_events_quarantine",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_giso_ods_quarantine_v8",
    "property.security.protocol" = "SASL_SSL",
    "property.sasl.mechanism" = "SCRAM-SHA-512",
    "property.sasl.username" = "<MSK_USERNAME>",
    "property.sasl.password" = "<MSK_PASSWORD>"
);

-- 验证
-- SHOW ROUTINE LOAD FROM tracking;
-- SELECT count(*) FROM tracking.ods_events WHERE env='test';
