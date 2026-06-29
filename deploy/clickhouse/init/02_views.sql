-- Metabase / Grafana 可直接查询的分析视图

CREATE OR REPLACE VIEW tracking.v_daily_quality AS
SELECT
    event_date,
    env,
    event,
    count() AS total,
    countIf(quality = 'missing') AS missing,
    countIf(is_quarantine = 1) AS errors
FROM tracking.ods_events
GROUP BY event_date, env, event;

CREATE OR REPLACE VIEW tracking.v_daily_active AS
SELECT
    event_date,
    env,
    uniqExact(did) AS dau,
    uniqExactIf(did, event = 'app_launch') AS launch_users
FROM tracking.ods_events
WHERE is_quarantine = 0
GROUP BY event_date, env;

CREATE OR REPLACE VIEW tracking.v_video_play AS
SELECT
    event_date,
    env,
    JSONExtractString(biz_params, 'vid') AS vid,
    countIf(event = 'biz_event' AND biz_code = 'video_play_start') AS starts,
    countIf(event = 'biz_event' AND biz_code = 'video_play_end') AS ends,
    avgIf(JSONExtractFloat(biz_params, 'play_dur'),
          event = 'biz_event' AND biz_code = 'video_play_end') AS avg_play_dur
FROM tracking.ods_events
WHERE is_quarantine = 0
GROUP BY event_date, env, vid
HAVING starts > 0;

CREATE OR REPLACE VIEW tracking.v_element_ctr AS
SELECT
    event_date,
    env,
    pgid,
    eid,
    countIf(event = 'element_exposure') AS exposures,
    countIf(event = 'element_click') AS clicks,
    if(exposures = 0, 0, clicks / exposures) AS ctr
FROM tracking.ods_events
WHERE is_quarantine = 0 AND eid != ''
GROUP BY event_date, env, pgid, eid;
