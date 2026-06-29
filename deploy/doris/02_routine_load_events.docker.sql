-- prod + test 事件流（不含 quarantine，便于已有集群增量创建）
USE tracking;

CREATE ROUTINE LOAD tracking.load_ods_events ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'prod')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "kafka:9092",
    "kafka_topic" = "events_raw",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_ods_events"
);

CREATE ROUTINE LOAD tracking.load_ods_events_test ON ods_events
COLUMNS(
    stime_ms, ctime, log_id, event,
    app_id, platform, app_vrsn, did, uid, session_id, channel, env,
    pgid, ref_pgid, ref_eid, pg_stay, pg_params,
    eid, mod, pos, exp_dur, exp_ratio, el_params,
    biz_code, biz_params, quality, common_ext, pt,
    stime = from_unixtime(stime_ms / 1000),
    event_date = to_date(from_unixtime(stime_ms / 1000)),
    env = IFNULL(env, 'test')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.stime\",\"$.ctime\",\"$.log_id\",\"$.event\",
        \"$.common.app_id\",\"$.common.platform\",\"$.common.app_vrsn\",\"$.common.did\",\"$.common.uid\",\"$.common.session_id\",\"$.common.channel\",\"$.common.env\",
        \"$.page.pgid\",\"$.page.ref_pgid\",\"$.page.ref_eid\",\"$.page.pg_stay\",\"$.page.pg_params\",
        \"$.element.eid\",\"$.element.mod\",\"$.element.pos\",\"$.element.exp_dur\",\"$.element.exp_ratio\",\"$.element.params\",
        \"$.biz.code\",\"$.biz.params\",\"$._quality\",\"$.common\",\"$.pt\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "kafka:9092",
    "kafka_topic" = "events_raw_test",
    "property.kafka_default_offsets" = "OFFSET_BEGINNING",
    "property.group.id" = "doris_ods_events_test"
);
