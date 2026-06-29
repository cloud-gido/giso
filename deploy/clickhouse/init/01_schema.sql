-- GISO 本地栈 · ClickHouse ODS（由 event-bridge 从 Kafka 写入）
CREATE DATABASE IF NOT EXISTS tracking;

CREATE TABLE IF NOT EXISTS tracking.ods_events
(
    event_date Date,
    event      String,
    stime      DateTime64(3),
    ctime      Int64,
    log_id     String,
    env        LowCardinality(String) DEFAULT 'prod',
    app_id     String,
    platform   LowCardinality(String),
    app_vrsn   String,
    did        String,
    uid        String,
    session_id String,
    channel    String,
    pgid       String,
    ref_pgid   String,
    ref_eid    String,
    pg_stay    Int64,
    pg_params  String,
    eid        String,
    mod        String,
    pos        Int32,
    exp_dur    Int64,
    exp_ratio  Float64,
    el_params  String,
    biz_code   String,
    biz_params String,
    quality    LowCardinality(String),
    issues     String,
    common_ext String,
    pt         String,
    is_quarantine UInt8 DEFAULT 0
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_date)
ORDER BY (env, event, stime, did);

CREATE TABLE IF NOT EXISTS tracking.ods_events_quarantine
(
    event_date Date,
    stime      DateTime64(3),
    event      String,
    env        LowCardinality(String),
    app_id     String,
    platform   LowCardinality(String),
    did        String,
    issues     String,
    raw        String
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(event_date)
ORDER BY (stime, did);
