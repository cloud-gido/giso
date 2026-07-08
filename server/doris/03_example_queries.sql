-- ============================================================
-- 常用指标查询示例（口径与 docs/tracking/04-业务埋点设计.md 一致）
-- ============================================================
USE tracking;

-- 视频卡 CTR（分页面、位置）
SELECT pgid, pos,
       sum(event = 'element_click')    AS clicks,
       sum(event = 'element_exposure') AS exposures,
       round(sum(event = 'element_click') / sum(event = 'element_exposure'), 4) AS ctr
FROM ods_events
WHERE event_date = curdate() - 1 AND eid = 'video_card'
GROUP BY pgid, pos ORDER BY pgid, pos;

-- 人均播放时长（心跳累计口径）
SELECT count(DISTINCT did) AS play_uv,
       sum(cast(biz_params->'$.play_dur' AS BIGINT)) / 1000 / count(DISTINCT did) AS avg_play_sec
FROM ods_events
WHERE event_date = curdate() - 1 AND biz_code = 'video_play_heartbeat';

-- 投注漏斗（曝光 → 选盘 → 提交 → 成功，按 UV）
SELECT
    count(DISTINCT IF(event = 'element_exposure' AND eid = 'match_card', did, NULL)) AS exposed,
    count(DISTINCT IF(event = 'element_click' AND eid = 'odds_btn', did, NULL))      AS selected,
    count(DISTINCT IF(biz_code = 'bet_submit', did, NULL))                            AS submitted,
    count(DISTINCT IF(biz_code = 'bet_placed', uid, NULL))                            AS placed
FROM ods_events
WHERE event_date = curdate() - 1;

-- 双链路对账：bet_submit(client) vs bet_placed+bet_rejected(server)，差异率>1%告警
SELECT
    count(DISTINCT IF(biz_code = 'bet_submit', biz_params->'$.bet_id', NULL))   AS client_submits,
    count(DISTINCT IF(biz_code IN ('bet_placed', 'bet_rejected'),
                      biz_params->'$.bet_id', NULL))                            AS server_acks
FROM ods_events
WHERE event_date = curdate() - 1;

-- 上报质量：缺失率按事件×版本（新版本灰度监控）
SELECT event, app_vrsn,
       count(*) AS total,
       sum(quality = 'missing') AS missing,
       round(sum(quality = 'missing') / count(*), 4) AS missing_rate
FROM ods_events
WHERE event_date = curdate() - 1
GROUP BY event, app_vrsn
HAVING missing_rate > 0.01
ORDER BY missing_rate DESC;

-- 隔离区今日错误 TOP（字段从 raw 查询时解析，入库不拆列）
SELECT get_json_string(raw, '$.event') AS event,
       get_json_string(raw, '$._issues') AS issues,
       count(*) AS cnt
FROM ods_events_quarantine
WHERE event_date = curdate()
GROUP BY event, issues ORDER BY cnt DESC LIMIT 20;
